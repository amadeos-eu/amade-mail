package org.datazuul.amadeos.mail.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A fully loaded message, ready for display.
 *
 * @param from          sender, formatted for display
 * @param senderAddress bare sender address
 * @param to            recipients, formatted
 * @param cc            copy recipients, formatted; may be empty
 * @param replyTo       reply address, formatted; may be empty
 * @param subject       decoded subject
 * @param date          sent date, may be null
 * @param messageId     Message-ID header, may be empty
 * @param plainText     text/plain body, null if the message has none
 * @param html          text/html body, null if the message has none
 * @param attachments   parts shown in the attachment bar
 * @param inlineParts   parts referenced from the HTML via cid:, keyed by Content-ID without brackets
 */
public record MessageContent(String from, String senderAddress, String to, String cc, String replyTo,
                             String subject, Instant date, String messageId, String plainText, String html,
                             List<Attachment> attachments, Map<String, InlinePart> inlineParts) {

    public MessageContent {
        attachments = List.copyOf(attachments);
        inlineParts = Map.copyOf(inlineParts);
    }

    public boolean hasHtml() {
        return html != null && !html.isBlank();
    }

    public boolean hasPlainText() {
        return plainText != null;
    }

    /** An attachment or other non-inline part. */
    public record Attachment(String fileName, String mimeType, byte[] data) {
        public long size() {
            return data.length;
        }
    }

    /** An image or other resource embedded in the HTML. */
    public record InlinePart(String contentId, String mimeType, byte[] data) {
    }
}
