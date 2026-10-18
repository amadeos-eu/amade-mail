package eu.amadeos.mail.domain;

import java.net.URI;

/**
 * An OAuth 2.0 authorization server that grants access to IMAP and SMTP (SASL XOAUTH2).
 *
 * @param id               key in {@code oauth2.conf}
 * @param label            shown in the user interface
 * @param authorizationUri where the user signs in, in the web browser
 * @param tokenUri         where codes and refresh tokens are exchanged for access tokens
 * @param scope            access to IMAP and SMTP
 * @param consoleHint      where to register the OAuth client
 */
public record OAuth2Provider(String id, String label, URI authorizationUri, URI tokenUri, String scope,
                             String consoleHint) {

    public static final OAuth2Provider GOOGLE = new OAuth2Provider("google", "Google",
            URI.create("https://accounts.google.com/o/oauth2/v2/auth"),
            URI.create("https://oauth2.googleapis.com/token"),
            "https://mail.google.com/",
            "Google Cloud console → APIs & Services → Credentials → Create OAuth client ID → Desktop app");
}
