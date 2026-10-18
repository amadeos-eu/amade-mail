package eu.amadeos.apps.mail.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import eu.amadeos.apps.mail.domain.MessageContent;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class MessageParserTest {
    private final MessageParser parser = new MessageParser();

    private static MimeMessage message(String raw) throws Exception {
        String crlf = raw.replace("\r\n", "\n").replace("\n", "\r\n");
        return new MimeMessage(Session.getInstance(new Properties()),
                new ByteArrayInputStream(crlf.getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Test
    void picksBothAlternativesInlineImagesAndAttachments() throws Exception {
        MessageContent c = parser.parse(message("""
                From: "Bob Example" <bob@example.org>
                To: ralf@example.org
                Cc: carol@example.org
                Subject: =?UTF-8?Q?Gr=C3=BC=C3=9Fe?=
                Date: Tue, 13 Oct 2026 07:55:00 +0200
                Message-ID: <abc@example.org>
                MIME-Version: 1.0
                Content-Type: multipart/mixed; boundary="mixed"

                --mixed
                Content-Type: multipart/alternative; boundary="alt"

                --alt
                Content-Type: text/plain; charset=UTF-8
                Content-Transfer-Encoding: 8bit

                Hallo Ralf
                --alt
                Content-Type: multipart/related; boundary="rel"

                --rel
                Content-Type: text/html; charset=UTF-8

                <p>Hallo <img src="cid:logo@example.org"></p>
                --rel
                Content-Type: image/png
                Content-ID: <logo@example.org>
                Content-Transfer-Encoding: base64

                iVBORw0KGgo=
                --rel--
                --alt--
                --mixed
                Content-Type: application/pdf; name="../../etc/report.pdf"
                Content-Disposition: attachment; filename="../../etc/report.pdf"
                Content-Transfer-Encoding: base64

                JVBERi0xLjQ=
                --mixed--
                """));
        assertEquals("Bob Example <bob@example.org>", c.from());
        assertEquals("bob@example.org", c.senderAddress());
        assertEquals("ralf@example.org", c.to());
        assertEquals("carol@example.org", c.cc());
        assertEquals("", c.replyTo());
        assertEquals("Grüße", c.subject());
        assertEquals("<abc@example.org>", c.messageId());
        assertEquals("Hallo Ralf", c.plainText().strip());
        assertTrue(c.html().contains("cid:logo@example.org"));
        assertEquals(1, c.inlineParts().size());
        assertEquals("image/png", c.inlineParts().get("logo@example.org").mimeType());
        assertEquals(1, c.attachments().size());
        MessageContent.Attachment pdf = c.attachments().getFirst();
        assertEquals("report.pdf", pdf.fileName());
        assertEquals("application/pdf", pdf.mimeType());
        assertEquals("%PDF-1.4", new String(pdf.data(), StandardCharsets.US_ASCII));
    }

    @Test
    void unknownCharsetFallsBackInsteadOfFailing() throws Exception {
        MessageContent c = parser.parse(message("""
                From: a@example.org
                Subject: x
                Content-Type: text/plain; charset=x-unknown-charset

                plain ascii text
                """));
        assertEquals("plain ascii text", c.plainText().strip());
        assertNull(c.html());
    }

    @Test
    void replyToHeaderIsReadOnlyWhenPresent() throws Exception {
        MessageContent c = parser.parse(message("""
                From: list@example.org
                Reply-To: Discussion <talk@example.org>
                Subject: x
                Content-Type: text/plain

                hi
                """));
        assertEquals("Discussion <talk@example.org>", c.replyTo());
    }
}
