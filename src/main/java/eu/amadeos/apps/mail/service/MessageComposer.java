package eu.amadeos.apps.mail.service;

import static eu.amadeos.apps.mail.i18n.Texts.tr;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import eu.amadeos.apps.mail.domain.Account;
import eu.amadeos.apps.mail.domain.MessageContent;
import eu.amadeos.apps.mail.domain.OutgoingMessage;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

/**
 * Builds MIME messages from the compose window and prepares replies and forwards.
 */
public final class MessageComposer {
    static final String MAILER = "amade-mail";

    public MimeMessage build(Session session, OutgoingMessage outgoing) throws MessagingException, IOException {
        Account account = outgoing.account();
        MimeMessage message = new MimeMessage(session);
        message.setFrom(sender(account));
        InternetAddress[] to = parse(outgoing.to(), "To");
        if (to.length == 0) throw new MailServiceException(tr("compose.error.no-recipient"));
        message.setRecipients(Message.RecipientType.TO, to);
        if (!outgoing.cc().isEmpty()) message.setRecipients(Message.RecipientType.CC, parse(outgoing.cc(), "Cc"));
        if (!outgoing.bcc().isEmpty()) message.setRecipients(Message.RecipientType.BCC, parse(outgoing.bcc(), "Bcc"));
        message.setSubject(outgoing.subject(), "UTF-8");
        message.setSentDate(new Date());
        message.setHeader("X-Mailer", MAILER);
        if (!outgoing.inReplyTo().isEmpty()) {
            message.setHeader("In-Reply-To", outgoing.inReplyTo());
            message.setHeader("References", outgoing.inReplyTo());
        }

        if (outgoing.attachments().isEmpty()) {
            message.setText(outgoing.body(), "UTF-8");
        } else {
            MimeMultipart mixed = new MimeMultipart("mixed");
            MimeBodyPart text = new MimeBodyPart();
            text.setText(outgoing.body(), "UTF-8");
            mixed.addBodyPart(text);
            for (Path file : outgoing.attachments()) {
                MimeBodyPart attachment = new MimeBodyPart();
                attachment.attachFile(file.toFile());
                attachment.setFileName(file.getFileName().toString());
                mixed.addBodyPart(attachment);
            }
            message.setContent(mixed);
        }
        message.saveChanges();
        return message;
    }

    static InternetAddress sender(Account account) throws MessagingException {
        try {
            return account.displayName().isEmpty()
                    ? new InternetAddress(account.email(), true)
                    : new InternetAddress(account.email(), account.displayName(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new MessagingException("UTF-8 not supported", e);
        }
    }

    private static InternetAddress[] parse(String addresses, String field) {
        try {
            return InternetAddress.parse(addresses, true);
        } catch (AddressException e) {
            throw new MailServiceException(tr("compose.error.address", field, e.getRef()), e);
        }
    }

    // ---- replies and forwards -------------------------------------------------------------------

    /** "Re: subject", without stacking prefixes. */
    public static String replySubject(String subject) {
        String s = subject == null ? "" : subject.strip();
        return s.toLowerCase(Locale.ROOT).matches("^(re|aw|sv):.*") ? s : "Re: " + s;
    }

    public static String forwardSubject(String subject) {
        String s = subject == null ? "" : subject.strip();
        return s.toLowerCase(Locale.ROOT).matches("^(fwd?|wg):.*") ? s : "Fwd: " + s;
    }

    /** The address a reply goes to: Reply-To if present, otherwise the sender. */
    public static String replyRecipient(MessageContent original) {
        return original.replyTo().isBlank() ? original.from() : original.replyTo();
    }

    /** Body of a reply: attribution line and the original text quoted with "> ". */
    public static String quote(MessageContent original, String plainTextOfOriginal, String signature) {
        StringBuilder body = new StringBuilder("\n\n");
        String date = original.date() == null ? "" : DateTimeFormatter.ofPattern(tr("date.full"),
                eu.amadeos.apps.mail.i18n.Texts.messages().locale())
                .withZone(ZoneId.systemDefault()).format(original.date()) + ", ";
        body.append(tr("compose.quote.attribution", date, original.from())).append('\n');
        for (String line : (plainTextOfOriginal == null ? "" : plainTextOfOriginal).split("\\R", -1)) {
            body.append(line.startsWith(">") ? ">" : "> ").append(line).append('\n');
        }
        appendSignature(body, signature);
        return body.toString();
    }

    /** Body of a forwarded message: a header block and the original text. */
    public static String forwardBody(MessageContent original, String plainTextOfOriginal, String signature) {
        StringBuilder body = new StringBuilder("\n\n-------- " + tr("compose.forwarded") + " --------\n");
        body.append(tr("header.subject")).append(' ').append(original.subject()).append('\n');
        body.append(tr("header.from")).append(' ').append(original.from()).append('\n');
        body.append(tr("header.to")).append(' ').append(original.to()).append('\n');
        body.append('\n').append(plainTextOfOriginal == null ? "" : plainTextOfOriginal).append('\n');
        appendSignature(body, signature);
        return body.toString();
    }

    /** A new message body containing only the signature. */
    public static String newBody(String signature) {
        StringBuilder body = new StringBuilder();
        appendSignature(body, signature);
        return body.toString();
    }

    private static void appendSignature(StringBuilder body, String signature) {
        if (signature == null || signature.isBlank()) return;
        body.append("\n-- \n").append(signature.strip()).append('\n');
    }
}
