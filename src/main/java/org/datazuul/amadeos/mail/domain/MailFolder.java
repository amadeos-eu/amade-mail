package org.datazuul.amadeos.mail.domain;

import java.util.Objects;

/**
 * A mailbox on the server.
 *
 * @param accountId     owning account
 * @param fullName      full IMAP name including parents, e.g. "Projekte/amadeOS"
 * @param name          last segment, e.g. "amadeOS"
 * @param separator     hierarchy delimiter announced by the server
 * @param role          special purpose
 * @param selectable    false for pure container folders (\Noselect)
 * @param unread        number of unseen messages, -1 if unknown
 * @param total         number of messages, -1 if unknown
 */
public record MailFolder(String accountId, String fullName, String name, char separator, FolderRole role,
                         boolean selectable, int unread, int total) {

    public MailFolder {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(fullName, "fullName");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(role, "role");
    }

    public MailFolder withCounts(int unread, int total) {
        return new MailFolder(accountId, fullName, name, separator, role, selectable, unread, total);
    }
}
