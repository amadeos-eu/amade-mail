package eu.amadeos.apps.mail.service;

import eu.amadeos.commons.desktop.SessionPasswordStore;
import eu.amadeos.apps.mail.domain.Account;
import eu.amadeos.apps.mail.domain.AccountColor;
import eu.amadeos.apps.mail.domain.AuthMethod;
import eu.amadeos.apps.mail.domain.ConnectionSecurity;
import eu.amadeos.apps.mail.domain.OAuth2Client;
import eu.amadeos.apps.mail.domain.OAuth2Provider;
import eu.amadeos.apps.mail.domain.ServerSettings;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class OAuth2ServiceTest {
    private static final OAuth2Client CLIENT = new OAuth2Client("client-1", "secret-1");
    private static final Account ACCOUNT = new Account("g1", "Gmail", "Ralf", "ralf@gmail.com", "ralf@gmail.com",
            new ServerSettings("imap.gmail.com", 993, ConnectionSecurity.TLS),
            new ServerSettings("smtp.gmail.com", 465, ConnectionSecurity.TLS), "", AccountColor.BLUE,
            AuthMethod.OAUTH2_GOOGLE);

    private final SessionPasswordStore store = new SessionPasswordStore();
    private final List<Map<String, String>> requests = new ArrayList<>();
    private final List<OAuth2Service.Response> answers = new ArrayList<>();
    private Instant now = Instant.parse("2026-10-13T10:00:00Z");

    private OAuth2Service service() {
        Clock clock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now;
            }
        };
        return new OAuth2Service(store, (uri, form) -> {
            assertEquals(OAuth2Provider.GOOGLE.tokenUri(), uri);
            requests.add(form);
            return answers.removeFirst();
        }, clock);
    }

    private void answer(int status, String json) {
        answers.add(new OAuth2Service.Response(status, json));
    }

    @Test
    void noRefreshTokenMeansSignIn() {
        assertTrue(service().accessToken(ACCOUNT, CLIENT).isEmpty());
        assertTrue(requests.isEmpty());
    }

    @Test
    void refreshesAndCachesUntilShortlyBeforeExpiry() {
        store.store(OAuth2Service.refreshTokenKey("g1"), "", "refresh-1");
        OAuth2Service service = service();
        answer(200, "{\"access_token\":\"access-1\",\"expires_in\":3599,\"token_type\":\"Bearer\"}");
        assertEquals("access-1", service.accessToken(ACCOUNT, CLIENT).orElseThrow());
        assertEquals("refresh_token", requests.getFirst().get("grant_type"));
        assertEquals("refresh-1", requests.getFirst().get("refresh_token"));
        assertEquals("client-1", requests.getFirst().get("client_id"));
        assertEquals("secret-1", requests.getFirst().get("client_secret"));

        now = now.plusSeconds(3000);
        assertEquals("access-1", service.accessToken(ACCOUNT, CLIENT).orElseThrow());
        assertEquals(1, requests.size());

        now = now.plusSeconds(560); // within the renewal margin
        answer(200, "{\"access_token\":\"access-2\",\"expires_in\":3599,\"refresh_token\":\"refresh-2\"}");
        assertEquals("access-2", service.accessToken(ACCOUNT, CLIENT).orElseThrow());
        assertEquals("refresh-2", store.lookup(OAuth2Service.refreshTokenKey("g1")).orElseThrow());
    }

    @Test
    void invalidateForcesRefresh() {
        store.store(OAuth2Service.refreshTokenKey("g1"), "", "refresh-1");
        OAuth2Service service = service();
        answer(200, "{\"access_token\":\"access-1\",\"expires_in\":3599}");
        answer(200, "{\"access_token\":\"access-2\",\"expires_in\":3599}");
        service.accessToken(ACCOUNT, CLIENT);
        service.invalidate("g1");
        assertEquals("access-2", service.accessToken(ACCOUNT, CLIENT).orElseThrow());
    }

    @Test
    void revokedRefreshTokenIsDeleted() {
        store.store(OAuth2Service.refreshTokenKey("g1"), "", "refresh-1");
        answer(400, "{\"error\":\"invalid_grant\",\"error_description\":\"Token has been expired or revoked.\"}");
        assertTrue(service().accessToken(ACCOUNT, CLIENT).isEmpty());
        assertTrue(store.lookup(OAuth2Service.refreshTokenKey("g1")).isEmpty());
    }

    @Test
    void otherTokenErrorsAreReported() {
        store.store(OAuth2Service.refreshTokenKey("g1"), "", "refresh-1");
        answer(401, "{\"error\":\"invalid_client\",\"error_description\":\"The OAuth client was not found.\"}");
        MailServiceException e = assertThrows(MailServiceException.class, () -> service().accessToken(ACCOUNT, CLIENT));
        assertTrue(e.getMessage().contains("invalid_client"), e.getMessage());
        assertTrue(e.getMessage().contains("oauth2.conf"), e.getMessage());
        assertTrue(store.lookup(OAuth2Service.refreshTokenKey("g1")).isPresent());
    }

    @Test
    void signInThroughLoopbackRedirect() {
        answer(200, "{\"access_token\":\"access-1\",\"expires_in\":3599,\"refresh_token\":\"refresh-1\"}");
        OAuth2Service service = service();
        List<URI> opened = new ArrayList<>();
        String token = service.signIn(ACCOUNT, CLIENT, uri -> {
            opened.add(uri);
            // Plays the browser: the provider redirects back with the code and the state.
            Map<String, String> query = OAuth2Service.parseQuery(uri.getRawQuery());
            assertEquals("S256", query.get("code_challenge_method"));
            assertEquals("offline", query.get("access_type"));
            assertEquals("https://mail.google.com/", query.get("scope"));
            assertEquals("ralf@gmail.com", query.get("login_hint"));
            assertTrue(query.get("redirect_uri").startsWith("http://127.0.0.1:"));
            get(query.get("redirect_uri") + "?state=wrong&code=evil", 400);
            get(query.get("redirect_uri") + "?state=" + query.get("state") + "&code=code-1&scope=x", 200);
        }, new CompletableFuture<>());

        assertEquals("access-1", token);
        assertEquals(1, opened.size());
        assertTrue(opened.getFirst().toString().startsWith("https://accounts.google.com/o/oauth2/v2/auth?"));
        Map<String, String> exchange = requests.getFirst();
        assertEquals("authorization_code", exchange.get("grant_type"));
        assertEquals("code-1", exchange.get("code"));
        String challenge = OAuth2Service.parseQuery(opened.getFirst().getRawQuery()).get("code_challenge");
        assertEquals(challenge, OAuth2Service.challenge(exchange.get("code_verifier")));
        assertEquals("refresh-1", store.lookup(OAuth2Service.refreshTokenKey("g1")).orElseThrow());
    }

    @Test
    void deniedConsentFails() {
        MailServiceException e = assertThrows(MailServiceException.class, () -> service().signIn(ACCOUNT, CLIENT, uri -> {
            Map<String, String> query = OAuth2Service.parseQuery(uri.getRawQuery());
            get(query.get("redirect_uri") + "?state=" + query.get("state") + "&error=access_denied", 200);
        }, new CompletableFuture<>()));
        assertTrue(e.getMessage().contains("access_denied"), e.getMessage());
        assertTrue(requests.isEmpty());
    }

    @Test
    void signInCanBeCancelled() {
        CompletableFuture<Void> cancelled = new CompletableFuture<>();
        MailServiceException e = assertThrows(MailServiceException.class,
                () -> service().signIn(ACCOUNT, CLIENT, uri -> cancelled.complete(null), cancelled));
        assertTrue(e.getMessage().contains("cancelled"), e.getMessage());
    }

    @Test
    void forgetRemovesRefreshToken() {
        store.store(OAuth2Service.refreshTokenKey("g1"), "", "refresh-1");
        store.store("g1", "", "password");
        OAuth2Service service = service();
        assertTrue(service.hasRefreshToken("g1"));
        service.forget("g1");
        assertFalse(service.hasRefreshToken("g1"));
        assertEquals("password", store.lookup("g1").orElseThrow());
    }

    @Test
    void challengeFollowsRfc7636() {
        // Example from RFC 7636, appendix B.
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                OAuth2Service.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"));
    }

    @Test
    void readsJson() {
        Map<String, Object> json = Json.parseObject(
                " {\"a\":\"x\\\"y\\u00e4\",\"n\":42,\"b\":true,\"z\":null,\"l\":[1,{\"k\":\"v\"}],\"o\":{}} ");
        assertEquals("x\"yä", json.get("a"));
        assertEquals(42.0, json.get("n"));
        assertEquals(Boolean.TRUE, json.get("b"));
        assertTrue(json.containsKey("z"));
        assertEquals(2, ((List<?>) json.get("l")).size());
        assertThrows(IllegalArgumentException.class, () -> Json.parseObject("<html>"));
        assertThrows(IllegalArgumentException.class, () -> Json.parseObject("{\"a\":1} x"));
    }

    private static void get(String uri, int expectedStatus) {
        try (HttpClient http = HttpClient.newHttpClient()) {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(uri)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(expectedStatus, response.statusCode());
        } catch (IOException e) {
            throw new AssertionError(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
