package org.datazuul.amadeos.mail.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * One line of the message header list.
 *
 * @param accountId      owning account
 * @param folder         full name of the folder that holds the message
 * @param uid            IMAP UID, stable within the folder
 * @param sender         display name or address of the sender
 * @param senderAddress  bare address of the sender, used for "load images from this sender"
 * @param subject        decoded subject
 * @param date           sent date, or received date if the message carries none
 * @param size           size in bytes, -1 if unknown
 * @param seen           \Seen flag
 * @param answered       \Answered flag
 * @param flagged        \Flagged flag
 * @param hasAttachments multipart/mixed content
 */
public record MessageSummary(String accountId, String folder, long uid, String sender, String senderAddress,
                             String subject, Instant date, long size, boolean seen, boolean answered,
                             boolean flagged, boolean hasAttachments) {

    public MessageSummary {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(folder, "folder");
        sender = sender == null ? "" : sender;
        senderAddress = senderAddress == null ? "" : senderAddress;
        subject = subject == null ? "" : subject;
    }

    public MessageSummary withSeen(boolean value) {
        return new MessageSummary(accountId, folder, uid, sender, senderAddress, subject, date, size, value,
                answered, flagged, hasAttachments);
    }

    /** Same message, independent of the flags. */
    public boolean sameMessage(MessageSummary other) {
        return other != null && uid == other.uid && accountId.equals(other.accountId) && folder.equals(other.folder);
    }
}
