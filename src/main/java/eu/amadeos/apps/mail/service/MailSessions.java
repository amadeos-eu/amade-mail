package eu.amadeos.apps.mail.service;

import eu.amadeos.apps.mail.domain.AuthMethod;
import eu.amadeos.apps.mail.domain.ConnectionSecurity;
import eu.amadeos.apps.mail.domain.ServerSettings;

import java.util.Properties;

/**
 * Builds Jakarta Mail session properties for IMAP and SMTP servers.
 */
public final class MailSessions {
    static final String TIMEOUT_MILLIS = "30000";

    private MailSessions() {
    }

    public static String imapProtocol(ServerSettings server) {
        return server.security() == ConnectionSecurity.TLS ? "imaps" : "imap";
    }

    public static String smtpProtocol(ServerSettings server) {
        return server.security() == ConnectionSecurity.TLS ? "smtps" : "smtp";
    }

    public static Properties imapProperties(ServerSettings server) {
        return imapProperties(server, AuthMethod.PASSWORD);
    }

    public static Properties imapProperties(ServerSettings server, AuthMethod auth) {
        Properties p = new Properties();
        String protocol = imapProtocol(server);
        p.setProperty("mail.store.protocol", protocol);
        common(p, protocol, server, auth);
        // Messages are fetched in one piece instead of 16 KiB partial fetches.
        p.setProperty("mail." + protocol + ".partialfetch", "false");
        p.setProperty("mail." + protocol + ".fetchsize", "1048576");
        return p;
    }

    public static Properties smtpProperties(ServerSettings server) {
        return smtpProperties(server, AuthMethod.PASSWORD);
    }

    public static Properties smtpProperties(ServerSettings server, AuthMethod auth) {
        Properties p = new Properties();
        String protocol = smtpProtocol(server);
        p.setProperty("mail.transport.protocol", protocol);
        common(p, protocol, server, auth);
        p.setProperty("mail." + protocol + ".auth", "true");
        return p;
    }

    private static void common(Properties p, String protocol, ServerSettings server, AuthMethod auth) {
        String prefix = "mail." + protocol + ".";
        p.setProperty(prefix + "host", server.host());
        p.setProperty(prefix + "port", Integer.toString(server.port()));
        p.setProperty(prefix + "connectiontimeout", TIMEOUT_MILLIS);
        p.setProperty(prefix + "timeout", TIMEOUT_MILLIS);
        p.setProperty(prefix + "writetimeout", TIMEOUT_MILLIS);
        switch (server.security()) {
            case TLS -> {
                p.setProperty(prefix + "ssl.enable", "true");
                p.setProperty(prefix + "ssl.checkserveridentity", "true");
            }
            case STARTTLS -> {
                p.setProperty(prefix + "starttls.enable", "true");
                p.setProperty(prefix + "starttls.required", "true");
                p.setProperty(prefix + "ssl.checkserveridentity", "true");
            }
            case NONE -> {
                // plain connection, intended for local test servers only
            }
        }
        if (auth.isOAuth2()) {
            // SASL XOAUTH2 with the access token as password; never fall back to sending it as a password.
            p.setProperty(prefix + "auth.mechanisms", "XOAUTH2");
            p.setProperty(prefix + "auth.xoauth2.disable", "false");
            p.setProperty(prefix + "auth.login.disable", "true");
            p.setProperty(prefix + "auth.plain.disable", "true");
        }
    }
}
