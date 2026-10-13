package org.datazuul.amadeos.mail.service;

import org.datazuul.amadeos.mail.domain.ConnectionSecurity;
import org.datazuul.amadeos.mail.domain.ServerSettings;

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
        Properties p = new Properties();
        String protocol = imapProtocol(server);
        p.setProperty("mail.store.protocol", protocol);
        common(p, protocol, server);
        // Messages are fetched in one piece instead of 16 KiB partial fetches.
        p.setProperty("mail." + protocol + ".partialfetch", "false");
        p.setProperty("mail." + protocol + ".fetchsize", "1048576");
        return p;
    }

    public static Properties smtpProperties(ServerSettings server) {
        Properties p = new Properties();
        String protocol = smtpProtocol(server);
        p.setProperty("mail.transport.protocol", protocol);
        common(p, protocol, server);
        p.setProperty("mail." + protocol + ".auth", "true");
        return p;
    }

    private static void common(Properties p, String protocol, ServerSettings server) {
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
    }
}
