package eu.amadeos.apps.mail.service;

import static eu.amadeos.commons.i18n.Texts.tr;

import com.google.inject.Inject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import eu.amadeos.commons.desktop.PasswordStore;
import eu.amadeos.commons.json.Json;
import eu.amadeos.apps.mail.domain.Account;
import eu.amadeos.apps.mail.domain.OAuth2Client;
import eu.amadeos.apps.mail.domain.OAuth2Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * OAuth 2.0 for native applications (RFC 8252): the user signs in once in the web browser, which
 * redirects to a short-lived HTTP listener on 127.0.0.1; the code is protected with PKCE (RFC 7636).
 * The refresh token is kept in the keyring, access tokens only in memory, and renewed when they expire.
 */
public final class OAuth2Service {
    private static final Logger LOG = LoggerFactory.getLogger(OAuth2Service.class);
    static final Duration SIGN_IN_TIMEOUT = Duration.ofMinutes(5);
    /** Access tokens are renewed this long before they expire. */
    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(60);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);

    /** Posts an {@code application/x-www-form-urlencoded} request to a token endpoint. */
    @FunctionalInterface
    interface FormPoster {
        Response post(URI uri, Map<String, String> form) throws IOException, InterruptedException;
    }

    record Response(int status, String body) {
    }

    private record AccessToken(String value, Instant expiresAt) {
    }

    private final PasswordStore store;
    private final FormPoster poster;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, AccessToken> accessTokens = new ConcurrentHashMap<>();

    @Inject
    public OAuth2Service(PasswordStore store) {
        this(store, httpPoster(), Clock.systemUTC());
    }

    OAuth2Service(PasswordStore store, FormPoster poster, Clock clock) {
        this.store = store;
        this.poster = poster;
        this.clock = clock;
    }

    /** Keyring key of the refresh token; separate from the password so switching methods is safe. */
    public static String refreshTokenKey(String accountId) {
        return accountId + "-oauth2";
    }

    /**
     * A valid access token from memory or renewed with the stored refresh token. Empty if the user has
     * to sign in: no refresh token stored, or the provider revoked it.
     *
     * @throws MailServiceException if the provider cannot be reached
     */
    public Optional<String> accessToken(Account account, OAuth2Client client) {
        AccessToken cached = accessTokens.get(account.id());
        if (cached != null && clock.instant().isBefore(cached.expiresAt().minus(EXPIRY_MARGIN))) {
            return Optional.of(cached.value());
        }
        Optional<String> refreshToken = store.lookup(refreshTokenKey(account.id()));
        if (refreshToken.isEmpty()) return Optional.empty();
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refreshToken.get());
        clientCredentials(form, client);
        Map<String, Object> answer = postToken(account.auth().provider(), form);
        if (answer.containsKey("error")) {
            if ("invalid_grant".equals(answer.get("error"))) {
                // Revoked, expired (7 days for apps in "Testing" status) or password changed: sign in again.
                LOG.info("Refresh token of account {} no longer valid: {}", account.id(), answer.get("error_description"));
                store.delete(refreshTokenKey(account.id()));
                return Optional.empty();
            }
            throw tokenError(account.auth().provider(), answer);
        }
        return Optional.of(accept(account, answer));
    }

    // --8<-- [start:sign-in]
    /**
     * Lets the user sign in in the web browser and waits for the redirect. Blocks up to
     * {@link #SIGN_IN_TIMEOUT}; must not run on the EDT.
     *
     * @param browser   opens the authorization URL (also shown to the user in case no browser opens)
     * @param cancelled completing it aborts the wait
     * @return a new access token; the refresh token is stored in the keyring
     * @throws MailServiceException if the user denied access, cancelled or did not finish in time
     */
    public String signIn(Account account, OAuth2Client client, Consumer<URI> browser, CompletableFuture<?> cancelled) {
        OAuth2Provider provider = account.auth().provider();
        String verifier = randomToken(32);
        String state = randomToken(16);
        CompletableFuture<Map<String, String>> callback = new CompletableFuture<>();
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        } catch (IOException e) {
            throw new MailServiceException(tr("oauth.error.listen", e.getMessage()), e);
        }
        server.createContext("/", exchange -> handleRedirect(exchange, state, callback));
        server.start();
        try {
            String redirectUri = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            cancelled.whenComplete((r, e) -> callback.cancel(false));
            browser.accept(authorizationUri(provider, client, redirectUri, state, challenge(verifier), account.username()));
            Map<String, String> result = callback.get(SIGN_IN_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (result.containsKey("error")) {
                throw new MailServiceException(tr("oauth.error.sign-in", provider.label(), String.valueOf(result.get("error"))));
            }
            Map<String, String> form = new LinkedHashMap<>();
            form.put("grant_type", "authorization_code");
            form.put("code", result.get("code"));
            form.put("redirect_uri", redirectUri);
            form.put("code_verifier", verifier);
            clientCredentials(form, client);
            Map<String, Object> answer = postToken(provider, form);
            if (answer.containsKey("error")) throw tokenError(provider, answer);
            return accept(account, answer);
        } catch (CancellationException e) {
            throw new MailServiceException(tr("oauth.cancelled", account.label()), e);
        } catch (TimeoutException e) {
            throw new MailServiceException(tr("oauth.timeout", account.label(), SIGN_IN_TIMEOUT.toMinutes()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MailServiceException(tr("oauth.interrupted", account.label()), e);
        } catch (ExecutionException e) {
            throw new MailServiceException(tr("oauth.failed", account.label()), e.getCause());
        } finally {
            server.stop(0);
        }
    }
    // --8<-- [end:sign-in]

    /** The server rejected the access token: the next connection renews it. */
    public void invalidate(String accountId) {
        accessTokens.remove(accountId);
    }

    /** Removes all tokens of the account, e.g. when it was deleted or switched to password login. */
    public void forget(String accountId) {
        accessTokens.remove(accountId);
        store.delete(refreshTokenKey(accountId));
    }

    public boolean hasRefreshToken(String accountId) {
        return store.lookup(refreshTokenKey(accountId)).isPresent();
    }

    // ---- protocol ---------------------------------------------------------------------------------

    static URI authorizationUri(OAuth2Provider provider, OAuth2Client client, String redirectUri, String state,
                                String codeChallenge, String loginHint) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("response_type", "code");
        query.put("client_id", client.clientId());
        query.put("redirect_uri", redirectUri);
        query.put("scope", provider.scope());
        query.put("state", state);
        query.put("code_challenge", codeChallenge);
        query.put("code_challenge_method", "S256");
        // A refresh token is only issued for offline access; "consent" makes Google issue one every time.
        query.put("access_type", "offline");
        query.put("prompt", "consent");
        if (loginHint != null && !loginHint.isBlank()) query.put("login_hint", loginHint);
        return URI.create(provider.authorizationUri() + "?" + formEncode(query));
    }

    // --8<-- [start:challenge]
    static String challenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
    // --8<-- [end:challenge]

    private String randomToken(int bytes) {
        byte[] data = new byte[bytes];
        random.nextBytes(data);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static void clientCredentials(Map<String, String> form, OAuth2Client client) {
        form.put("client_id", client.clientId());
        if (!client.clientSecret().isEmpty()) form.put("client_secret", client.clientSecret());
    }

    private String accept(Account account, Map<String, Object> answer) {
        if (!(answer.get("access_token") instanceof String accessToken) || accessToken.isEmpty()) {
            throw new MailServiceException(tr("oauth.error.no-token", account.auth().provider().label()));
        }
        long seconds = answer.get("expires_in") instanceof Number n ? n.longValue() : 3600;
        accessTokens.put(account.id(), new AccessToken(accessToken, clock.instant().plusSeconds(seconds)));
        if (answer.get("refresh_token") instanceof String refreshToken && !refreshToken.isEmpty()) {
            store.store(refreshTokenKey(account.id()), "amade-mail OAuth2 " + account.email(), refreshToken);
        }
        return accessToken;
    }

    private Map<String, Object> postToken(OAuth2Provider provider, Map<String, String> form) {
        Response response;
        try {
            response = poster.post(provider.tokenUri(), form);
        } catch (IOException e) {
            throw new MailServiceException(tr("oauth.error.unreachable", provider.label(), e.getMessage()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MailServiceException(tr("oauth.error.interrupted", provider.label()), e);
        }
        try {
            Map<String, Object> answer = Json.parseObject(response.body());
            if (response.status() / 100 != 2 && !answer.containsKey("error")) answer.put("error", "http_" + response.status());
            return answer;
        } catch (IllegalArgumentException e) {
            throw new MailServiceException(tr("oauth.error.unreadable", provider.label(), String.valueOf(response.status())), e);
        }
    }

    private static MailServiceException tokenError(OAuth2Provider provider, Map<String, Object> answer) {
        Object description = answer.get("error_description");
        Object error = answer.get("error");
        String fix = "invalid_client".equals(error) || "unauthorized_client".equals(error)
                ? " " + tr("oauth.error.check-client") : "";
        return new MailServiceException(tr("oauth.error.refused", provider.label(),
                error + (description != null ? " (" + description + ")" : "")) + fix, null, true);
    }

    // --8<-- [start:redirect]
    private static void handleRedirect(HttpExchange exchange, String state, CompletableFuture<Map<String, String>> callback)
            throws IOException {
        Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
        boolean ours = state.equals(params.get("state"));
        String message;
        if (!ours) message = "This is not the expected sign-in answer.";
        else if (params.containsKey("error")) message = "Sign-in was not completed (" + escape(params.get("error")) + ").";
        else message = "Signed in. You can close this window and return to the Mailer.";
        byte[] body = ("<!doctype html><html><head><meta charset=\"utf-8\"><title>amade-mail</title></head>"
                + "<body style=\"font-family:sans-serif;margin:3em\"><p>" + message + "</p></body></html>")
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(ours ? 200 : 400, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
        if (ours) callback.complete(params);
    }
    // --8<-- [end:redirect]

    static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> params = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) return params;
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            params.putIfAbsent(key, value);
        }
        return params;
    }

    private static String formEncode(Map<String, String> values) {
        return values.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static FormPoster httpPoster() {
        HttpClient http = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
        return (uri, form) -> {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(HTTP_TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(formEncode(form)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body());
        };
    }
}
