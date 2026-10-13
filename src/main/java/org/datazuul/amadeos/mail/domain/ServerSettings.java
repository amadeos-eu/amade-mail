package org.datazuul.amadeos.mail.domain;

import java.util.Objects;

/**
 * Host, port and transport security of one IMAP or SMTP server.
 */
public record ServerSettings(String host, int port, ConnectionSecurity security) {
    public ServerSettings {
        host = host == null ? "" : host.strip();
        Objects.requireNonNull(security, "security");
        if (port < 0 || port > 65535) throw new IllegalArgumentException("Invalid port: " + port);
    }

    public boolean isComplete() {
        return !host.isEmpty() && port > 0;
    }

    public String describe() {
        return host + ":" + port + " · " + security;
    }
}
