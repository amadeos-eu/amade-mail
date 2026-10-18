package eu.amadeos.mail.domain;

import java.util.Locale;
import java.util.Map;

/**
 * Special purpose of a mailbox. Detected from IMAP SPECIAL-USE attributes (RFC 6154) or, if the
 * server does not announce them, from common folder names.
 */
public enum FolderRole {
    INBOX(0),
    DRAFTS(1),
    SENT(2),
    JUNK(3),
    TRASH(4),
    ARCHIVE(5),
    NORMAL(10);

    private static final Map<String, FolderRole> ATTRIBUTES = Map.of(
            "\\drafts", DRAFTS,
            "\\sent", SENT,
            "\\junk", JUNK,
            "\\trash", TRASH,
            "\\archive", ARCHIVE);

    private static final Map<String, FolderRole> NAMES = Map.ofEntries(
            Map.entry("drafts", DRAFTS), Map.entry("draft", DRAFTS), Map.entry("entwürfe", DRAFTS),
            Map.entry("sent", SENT), Map.entry("sent items", SENT), Map.entry("sent messages", SENT),
            Map.entry("sent mail", SENT), Map.entry("gesendet", SENT), Map.entry("gesendete objekte", SENT),
            Map.entry("junk", JUNK), Map.entry("spam", JUNK), Map.entry("junk e-mail", JUNK),
            Map.entry("trash", TRASH), Map.entry("deleted items", TRASH), Map.entry("deleted messages", TRASH),
            Map.entry("papierkorb", TRASH), Map.entry("gelöschte elemente", TRASH),
            Map.entry("archive", ARCHIVE), Map.entry("archiv", ARCHIVE));

    private final int order;

    FolderRole(int order) {
        this.order = order;
    }

    /** Sort key: special folders first, in the usual order of mail clients. */
    public int order() {
        return order;
    }

    /**
     * @param fullName   the full IMAP name, e.g. "INBOX" or "INBOX.Sent"
     * @param name       the last path segment
     * @param attributes IMAP LIST attributes, e.g. "\Sent"; may be empty
     */
    public static FolderRole detect(String fullName, String name, String... attributes) {
        if ("INBOX".equalsIgnoreCase(fullName)) return INBOX;
        for (String attribute : attributes) {
            FolderRole role = ATTRIBUTES.get(attribute.toLowerCase(Locale.ROOT));
            if (role != null) return role;
        }
        return NAMES.getOrDefault(name.toLowerCase(Locale.ROOT), NORMAL);
    }
}
