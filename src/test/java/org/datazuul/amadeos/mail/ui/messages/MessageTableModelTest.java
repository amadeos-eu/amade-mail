package org.datazuul.amadeos.mail.ui.messages;

import org.datazuul.amadeos.mail.domain.MessageSummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MessageTableModelTest {
    private static MessageSummary message(long uid, boolean seen) {
        return new MessageSummary("a", "INBOX", uid, "Bob", "bob@example.org", "Subject " + uid,
                Instant.parse("2026-10-13T06:00:00Z"), 2048, seen, uid == 2, uid == 3, uid == 3);
    }

    @Test
    void statusLettersFollowDtmail() {
        assertEquals("N", MessageTableModel.status(message(1, false)));
        assertEquals("A", MessageTableModel.status(message(2, true)));
        assertEquals("N!@", MessageTableModel.status(message(3, false)));
    }

    @Test
    void updatesAndRemovesRows() {
        MessageTableModel model = new MessageTableModel();
        model.setMessages(List.of(message(1, false), message(2, true), message(3, false)), false);
        assertEquals(2, model.unreadCount());
        model.update(message(1, true));
        assertEquals(1, model.unreadCount());
        model.remove(List.of(message(2, true)));
        assertEquals(2, model.getRowCount());
        assertEquals(-1, model.indexOf(message(2, true)));
    }

    @Test
    void formatsDatesLikeDtmail() {
        ZoneId zone = ZoneId.of("Europe/Berlin");
        Instant instant = Instant.parse("2026-10-13T06:12:00Z");
        assertEquals("08:12", MessageTableModel.formatDate(instant, zone, LocalDate.of(2026, 10, 13)));
        assertEquals("Tue Oct 13 08:12", MessageTableModel.formatDate(instant, zone, LocalDate.of(2026, 10, 20)));
        assertEquals("Oct 13 2026", MessageTableModel.formatDate(instant, zone, LocalDate.of(2027, 1, 2)));
        assertEquals("", MessageTableModel.formatDate(null, zone, LocalDate.of(2026, 10, 13)));
    }

    @Test
    void formatsSizes() {
        assertEquals("512", MessageTableModel.formatSize(512));
        assertEquals("2k", MessageTableModel.formatSize(2048));
        assertEquals("1.5M", MessageTableModel.formatSize(1024 * 1024 * 3 / 2));
        assertEquals("", MessageTableModel.formatSize(-1));
    }
}
