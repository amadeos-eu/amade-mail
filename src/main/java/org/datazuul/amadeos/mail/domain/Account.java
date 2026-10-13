package org.datazuul.amadeos.mail.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * One configured mail account. The password is not part of the account; it lives in the
 * {@link org.datazuul.amadeos.commons.desktop.PasswordStore}.
 *
 * @param id          stable identifier, used as key in the config file and the keyring
 * @param name        label shown in the folder tree, e.g. "Privat"
 * @param displayName sender name, e.g. "Ralf Eichinger"
 * @param email       sender address
 * @param username    login name for IMAP and SMTP
 * @param imap        incoming server
 * @param smtp        outgoing server
 * @param signature   appended to new messages, may be empty
 * @param color       index into {@link AccountColor}
 * @param auth        password or OAuth2 login; OAuth2 tokens also live in the keyring
 */
public record Account(String id, String name, String displayName, String email, String username,
                      ServerSettings imap, ServerSettings smtp, String signature, AccountColor color,
                      AuthMethod auth) {

    public Account {
        Objects.requireNonNull(id, "id");
        if (id.isBlank() || !id.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Account id must be alphanumeric: " + id);
        }
        name = blankToEmpty(name);
        displayName = blankToEmpty(displayName);
        email = blankToEmpty(email);
        username = blankToEmpty(username);
        signature = signature == null ? "" : signature;
        Objects.requireNonNull(imap, "imap");
        Objects.requireNonNull(smtp, "smtp");
        color = color == null ? AccountColor.BLUE : color;
        auth = auth == null ? AuthMethod.PASSWORD : auth;
    }

    /** An account that logs in with a password. */
    public Account(String id, String name, String displayName, String email, String username,
                   ServerSettings imap, ServerSettings smtp, String signature, AccountColor color) {
        this(id, name, displayName, email, username, imap, smtp, signature, color, AuthMethod.PASSWORD);
    }

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /** A new account with sensible defaults (implicit TLS for IMAP, STARTTLS submission for SMTP). */
    public static Account blank(AccountColor color) {
        return new Account(newId(), "New account", "", "", "",
                new ServerSettings("", ConnectionSecurity.TLS.defaultImapPort(), ConnectionSecurity.TLS),
                new ServerSettings("", ConnectionSecurity.STARTTLS.defaultSmtpPort(), ConnectionSecurity.STARTTLS),
                "", color);
    }

    /** The name in the folder tree; falls back to the address when no name was given. */
    public String label() {
        if (!name.isEmpty()) return name;
        return email.isEmpty() ? id : email;
    }

    public boolean isComplete() {
        return !email.isEmpty() && !username.isEmpty() && imap.isComplete() && smtp.isComplete();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.strip();
    }
}
