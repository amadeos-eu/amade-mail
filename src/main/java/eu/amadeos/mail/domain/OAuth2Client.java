package eu.amadeos.mail.domain;

/**
 * The OAuth client the mailer is registered as with a provider. For desktop applications the
 * secret is not confidential (RFC 8252); PKCE protects the authorization code.
 */
public record OAuth2Client(String clientId, String clientSecret) {
    public OAuth2Client {
        clientId = clientId == null ? "" : clientId.strip();
        clientSecret = clientSecret == null ? "" : clientSecret.strip();
    }

    public boolean isComplete() {
        return !clientId.isEmpty();
    }
}
