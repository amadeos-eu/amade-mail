package eu.amadeos.apps.mail.domain;

import static eu.amadeos.apps.mail.i18n.Texts.tr;

/**
 * How the mailer logs in to the IMAP and SMTP servers of an account.
 */
public enum AuthMethod {
    /** User name and password (also app passwords). */
    PASSWORD("Password", null),
    /** Sign-in with Google in the web browser; IMAP and SMTP use SASL XOAUTH2 with an access token. */
    OAUTH2_GOOGLE("OAuth2 (Google)", OAuth2Provider.GOOGLE);

    private final String label;
    private final OAuth2Provider provider;

    AuthMethod(String label, OAuth2Provider provider) {
        this.label = label;
        this.provider = provider;
    }

    /** The authorization server, or null for {@link #PASSWORD}. */
    public OAuth2Provider provider() {
        return provider;
    }

    public boolean isOAuth2() {
        return provider != null;
    }

    /** Shown in the account settings, from {@code auth.<method>}; {@link #label} is the English fallback. */
    @Override
    public String toString() {
        String key = "auth." + name().toLowerCase(java.util.Locale.ROOT);
        return eu.amadeos.apps.mail.i18n.Texts.messages().has(key) ? tr(key) : label;
    }
}
