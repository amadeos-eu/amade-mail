package eu.amadeos.mail.service;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimePart;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.internet.ParseException;
import eu.amadeos.mail.domain.MessageContent;
import eu.amadeos.mail.domain.MessageContent.Attachment;
import eu.amadeos.mail.domain.MessageContent.InlinePart;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Turns a MIME message into {@link MessageContent}: picks the text/plain and text/html bodies, collects
 * cid: resources for the HTML view and everything else as attachments.
 */
public final class MessageParser {
    /** Parts above this size are skipped to keep a malicious message from exhausting memory. */
    static final int MAX_PART_BYTES = 50 * 1024 * 1024;

    public MessageContent parse(MimeMessage message) throws MessagingException, IOException {
        Bodies bodies = new Bodies();
        walk(message, bodies, 0);
        Address[] from = message.getFrom();
        Instant date = message.getSentDate() == null ? null : message.getSentDate().toInstant();
        return new MessageContent(
                format(from),
                firstAddress(from),
                format(message.getRecipients(Message.RecipientType.TO)),
                format(message.getRecipients(Message.RecipientType.CC)),
                replyTo(message),
                nullToEmpty(message.getSubject()),
                date,
                nullToEmpty(message.getMessageID()),
                bodies.plain == null ? null : bodies.plain.toString(),
                bodies.html,
                bodies.attachments,
                bodies.inline);
    }

    private void walk(Part part, Bodies bodies, int depth) throws MessagingException, IOException {
        if (depth > 32) return; // absurd nesting, ignore the rest
        if (part.isMimeType("multipart/*")) {
            Object content = part.getContent();
            if (content instanceof Multipart multipart) {
                for (int i = 0; i < multipart.getCount(); i++) walk(multipart.getBodyPart(i), bodies, depth + 1);
                return;
            }
        }

        String disposition = disposition(part);
        boolean explicitAttachment = Part.ATTACHMENT.equalsIgnoreCase(disposition);
        String contentId = contentId(part);

        if (!explicitAttachment && part.isMimeType("text/plain")) {
            String text = text(part);
            if (bodies.plain == null) bodies.plain = new StringBuilder(text);
            else bodies.plain.append("\n\n").append(text);
            return;
        }
        if (!explicitAttachment && part.isMimeType("text/html") && bodies.html == null) {
            bodies.html = text(part);
            return;
        }
        byte[] data = bytes(part);
        if (data == null) return;
        String mimeType = baseType(part);
        if (contentId != null && !explicitAttachment) {
            bodies.inline.put(contentId, new InlinePart(contentId, mimeType, data));
            return;
        }
        bodies.attachments.add(new Attachment(fileName(part, mimeType, bodies.attachments.size() + 1), mimeType, data));
    }

    private static String text(Part part) throws MessagingException, IOException {
        try {
            Object content = part.getContent();
            if (content instanceof String s) return s;
        } catch (UnsupportedEncodingException e) {
            // Unknown charset label; decode the bytes ourselves below.
        }
        byte[] data = bytes(part);
        return data == null ? "" : new String(data, fallbackCharset(part));
    }

    private static Charset fallbackCharset(Part part) {
        try {
            String charset = new ContentType(part.getContentType()).getParameter("charset");
            if (charset != null) {
                String javaName = MimeUtility.javaCharset(charset);
                if (Charset.isSupported(javaName)) return Charset.forName(javaName);
            }
        } catch (MessagingException | RuntimeException e) {
            // fall through
        }
        return StandardCharsets.UTF_8;
    }

    private static byte[] bytes(Part part) throws MessagingException, IOException {
        try (InputStream in = part.getInputStream()) {
            byte[] data = in.readNBytes(MAX_PART_BYTES + 1);
            return data.length > MAX_PART_BYTES ? null : data;
        }
    }

    private static String disposition(Part part) {
        try {
            return part.getDisposition();
        } catch (MessagingException e) {
            return null; // malformed Content-Disposition header
        }
    }

    private static String contentId(Part part) throws MessagingException {
        if (!(part instanceof MimePart mime)) return null;
        String id = mime.getContentID();
        if (id == null) return null;
        id = id.strip();
        if (id.startsWith("<") && id.endsWith(">")) id = id.substring(1, id.length() - 1);
        return id.isEmpty() ? null : id;
    }

    private static String baseType(Part part) throws MessagingException {
        try {
            return new ContentType(part.getContentType()).getBaseType().toLowerCase(Locale.ROOT);
        } catch (ParseException e) {
            return "application/octet-stream";
        }
    }

    private static String fileName(Part part, String mimeType, int number) throws MessagingException {
        String name = null;
        try {
            name = part.getFileName();
        } catch (MessagingException e) {
            // malformed header
        }
        if (name != null) {
            try {
                name = MimeUtility.decodeText(name);
            } catch (UnsupportedEncodingException e) {
                // keep the encoded form
            }
            // Never trust path components from a message.
            name = name.replace('\\', '/');
            name = name.substring(name.lastIndexOf('/') + 1).strip();
        }
        if (name == null || name.isEmpty()) {
            name = "message/rfc822".equals(mimeType) ? "attached-message-" + number + ".eml" : "attachment-" + number;
        }
        return name;
    }

    /** The Reply-To header only; {@link MimeMessage#getReplyTo()} would fall back to From. */
    private static String replyTo(MimeMessage message) throws MessagingException {
        String header = message.getHeader("Reply-To", ",");
        if (header == null || header.isBlank()) return "";
        try {
            return format(InternetAddress.parseHeader(header, false));
        } catch (AddressException e) {
            return header.strip();
        }
    }

    static String format(Address[] addresses) {
        if (addresses == null) return "";
        StringJoiner joiner = new StringJoiner(", ");
        for (Address address : addresses) {
            joiner.add(address instanceof InternetAddress ia ? ia.toUnicodeString() : address.toString());
        }
        return joiner.toString();
    }

    static String displayName(Address[] addresses) {
        if (addresses == null || addresses.length == 0) return "";
        if (addresses[0] instanceof InternetAddress ia) {
            String personal = ia.getPersonal();
            return personal != null && !personal.isBlank() ? personal : nullToEmpty(ia.getAddress());
        }
        return addresses[0].toString();
    }

    static String firstAddress(Address[] addresses) {
        if (addresses == null || addresses.length == 0) return "";
        return addresses[0] instanceof InternetAddress ia ? nullToEmpty(ia.getAddress()) : addresses[0].toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static final class Bodies {
        StringBuilder plain;
        String html;
        final List<Attachment> attachments = new ArrayList<>();
        final Map<String, InlinePart> inline = new LinkedHashMap<>();
    }
}
