package eu.amadeos.apps.mail.service;

import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import eu.amadeos.apps.mail.domain.Account;
import eu.amadeos.apps.mail.domain.AccountColor;
import eu.amadeos.apps.mail.domain.MessageContent;
import eu.amadeos.apps.mail.domain.OutgoingMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class MessageComposerTest {
    @TempDir
    Path dir;

    private final MessageComposer composer = new MessageComposer();
    private final Session session = Session.getInstance(new Properties());

    private static Account account() {
        Account blank = Account.blank(AccountColor.BLUE);
        return new Account("a1", "Privat", "Ralf Eichinger", "ralf@example.org", "ralf", blank.imap(), blank.smtp(),
                "Ralf", AccountColor.BLUE);
    }

    @Test
    void buildsPlainMessage() throws Exception {
        MimeMessage m = composer.build(session, new OutgoingMessage(account(), "bob@example.org, carol@example.org",
                "", "dave@example.org", "Grüße", "Hallo", List.of(), "<orig@example.org>"));
        assertEquals("Ralf Eichinger <ralf@example.org>", m.getFrom()[0].toString());
        assertEquals(2, m.getRecipients(Message.RecipientType.TO).length);
        assertEquals(1, m.getRecipients(Message.RecipientType.BCC).length);
        assertEquals("Grüße", m.getSubject());
        assertEquals("<orig@example.org>", m.getHeader("In-Reply-To", null));
        assertTrue(m.isMimeType("text/plain"));
        assertEquals("Hallo", m.getContent());
    }

    @Test
    void attachesFiles() throws Exception {
        Path file = Files.writeString(dir.resolve("notes.txt"), "notes");
        MimeMessage m = composer.build(session, new OutgoingMessage(account(), "bob@example.org", "", "", "s", "b",
                List.of(file), ""));
        Multipart multipart = (Multipart) m.getContent();
        assertEquals(2, multipart.getCount());
        assertEquals("notes.txt", multipart.getBodyPart(1).getFileName());
    }

    @Test
    void rejectsMissingOrInvalidRecipients() {
        assertThrows(MailServiceException.class, () -> composer.build(session,
                new OutgoingMessage(account(), "", "", "", "s", "b", List.of(), "")));
        assertThrows(MailServiceException.class, () -> composer.build(session,
                new OutgoingMessage(account(), "not an address@@", "", "", "s", "b", List.of(), "")));
    }

    @Test
    void prefixesSubjectsOnce() {
        assertEquals("Re: Hello", MessageComposer.replySubject("Hello"));
        assertEquals("Re: Hello", MessageComposer.replySubject("Re: Hello"));
        assertEquals("AW: Hallo", MessageComposer.replySubject("AW: Hallo"));
        assertEquals("Fwd: Hello", MessageComposer.forwardSubject("Hello"));
        assertEquals("WG: Hallo", MessageComposer.forwardSubject("WG: Hallo"));
    }

    @Test
    void quotesOriginalAndAppendsSignature() {
        MessageContent original = new MessageContent("Bob <bob@example.org>", "bob@example.org", "ralf@example.org", "",
                "", "Hello", Instant.parse("2026-10-13T06:00:00Z"), "<x@y>", "line 1\n> older", null, List.of(), Map.of());
        String body = MessageComposer.quote(original, original.plainText(), "Ralf");
        assertTrue(body.contains("Bob <bob@example.org> wrote:"), body);
        assertTrue(body.contains("> line 1\n>> older\n"), body);
        assertTrue(body.endsWith("\n-- \nRalf\n"), body);
        assertEquals("Bob <bob@example.org>", MessageComposer.replyRecipient(original));
    }
}
