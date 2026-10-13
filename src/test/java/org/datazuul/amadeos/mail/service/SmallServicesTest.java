package org.datazuul.amadeos.mail.service;

import org.datazuul.amadeos.mail.domain.ConnectionSecurity;
import org.datazuul.amadeos.mail.domain.ServerSettings;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/** MailtoLink, ServerGuesser and MailSessions. */
class SmallServicesTest {

    @Test
    void parsesMailtoLinks() {
        MailtoLink link = MailtoLink.parse("mailto:ralf@example.org?subject=Hello%20World&cc=a@b.c&body=1+1%3D2");
        assertEquals("ralf@example.org", link.to());
        assertEquals("a@b.c", link.cc());
        assertEquals("Hello World", link.subject());
        assertEquals("1+1=2", link.body());
        assertTrue(MailtoLink.isMailto(" MAILTO:x@y.z"));
        assertFalse(MailtoLink.isMailto("https://example.org"));
    }

    @Test
    void mailtoIgnoresUnknownHeaders() {
        MailtoLink link = MailtoLink.parse("mailto:?to=a@b.c&in-reply-to=%3Cx%3E&bcc=d@e.f");
        assertEquals("a@b.c", link.to());
        assertEquals("d@e.f", link.bcc());
    }

    @Test
    void guessesKnownProvidersAndConventions() {
        ServerGuesser guesser = new ServerGuesser();
        ServerGuesser.Guess gmx = guesser.guess("someone@GMX.de").orElseThrow();
        assertEquals("imap.gmx.net", gmx.imap().host());
        assertEquals("mail.gmx.net", gmx.smtp().host());
        assertEquals("someone@GMX.de", gmx.username());

        ServerGuesser.Guess other = guesser.guess("ralf@datazuul.com").orElseThrow();
        assertEquals(new ServerSettings("imap.datazuul.com", 993, ConnectionSecurity.TLS), other.imap());
        assertEquals(new ServerSettings("smtp.datazuul.com", 587, ConnectionSecurity.STARTTLS), other.smtp());

        assertTrue(guesser.guess("no-at-sign").isEmpty());
        assertTrue(guesser.guess("trailing@").isEmpty());
    }

    @Test
    void imapsUsesImplicitTls() {
        ServerSettings server = new ServerSettings("imap.example.org", 993, ConnectionSecurity.TLS);
        Properties p = MailSessions.imapProperties(server);
        assertEquals("imaps", MailSessions.imapProtocol(server));
        assertEquals("imap.example.org", p.getProperty("mail.imaps.host"));
        assertEquals("993", p.getProperty("mail.imaps.port"));
        assertEquals("true", p.getProperty("mail.imaps.ssl.checkserveridentity"));
    }

    @Test
    void starttlsIsRequiredNotOptional() {
        ServerSettings server = new ServerSettings("smtp.example.org", 587, ConnectionSecurity.STARTTLS);
        Properties p = MailSessions.smtpProperties(server);
        assertEquals("smtp", MailSessions.smtpProtocol(server));
        assertEquals("true", p.getProperty("mail.smtp.starttls.enable"));
        assertEquals("true", p.getProperty("mail.smtp.starttls.required"));
        assertEquals("true", p.getProperty("mail.smtp.auth"));
    }
}
