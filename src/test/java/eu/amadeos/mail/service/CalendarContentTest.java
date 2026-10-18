package eu.amadeos.mail.service;

import eu.amadeos.mail.domain.MessageContent;
import eu.amadeos.mail.domain.MessageContent.Attachment;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CalendarContentTest {
    private static MessageContent message(String text, Attachment... attachments) {
        return new MessageContent("Bob", "bob@example.org", "me", "", "", "Meeting", Instant.now(), "<1@x>", text, null,
                List.of(attachments), Map.of());
    }

    @Test
    void dtcmTextIsRecognised() {
        MessageContent m = message("Hi,\n\n\t** Calendar Appointment **\n\n\tDate:\t11/22/94\n");
        assertTrue(CalendarContent.hasAppointment(m, m.plainText()));
        assertTrue(CalendarContent.icsAttachment(m).isEmpty());
        assertFalse(CalendarContent.hasAppointment(message("See you at ten."), "See you at ten."));
    }

    @Test
    void icsAttachmentIsRecognisedByTypeOrName() {
        Attachment byType = new Attachment("invite.bin", "text/calendar; method=REQUEST", new byte[]{1});
        Attachment byName = new Attachment("Dentist.ICS", "application/octet-stream", new byte[]{2});
        Attachment other = new Attachment("photo.png", "image/png", new byte[]{3});
        assertEquals(byType, CalendarContent.icsAttachment(message("x", other, byType, byName)).orElseThrow());
        assertEquals(byName, CalendarContent.icsAttachment(message("x", other, byName)).orElseThrow());
        assertTrue(CalendarContent.hasAppointment(message("x", byName), null));
        assertFalse(CalendarContent.hasAppointment(message("x", other), null));
    }
}
