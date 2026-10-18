package eu.amadeos.mail.domain;

import static eu.amadeos.mail.i18n.Texts.tr;

/**
 * How a connection to an IMAP or SMTP server is protected.
 */
public enum ConnectionSecurity {
    /** Implicit TLS from the first byte (IMAPS 993, SMTPS 465). */
    TLS("SSL/TLS", 993, 465),
    /** Plain connection upgraded with STARTTLS; the upgrade is required, not optional. */
    STARTTLS("STARTTLS", 143, 587),
    /** Unencrypted. Only for local test servers. */
    NONE("None", 143, 25);

    private final String label;
    private final int defaultImapPort;
    private final int defaultSmtpPort;

    ConnectionSecurity(String label, int defaultImapPort, int defaultSmtpPort) {
        this.label = label;
        this.defaultImapPort = defaultImapPort;
        this.defaultSmtpPort = defaultSmtpPort;
    }

    public int defaultImapPort() {
        return defaultImapPort;
    }

    public int defaultSmtpPort() {
        return defaultSmtpPort;
    }

    @Override
    public String toString() {
        return this == NONE ? tr("security.none") : label;
    }
}
