package org.datazuul.amadeos.mail.ui.view;

import org.datazuul.amadeos.mail.domain.MessageContent;
import org.datazuul.amadeos.mail.domain.MessageContent.Attachment;
import org.datazuul.amadeos.mail.domain.MessageSummary;
import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class MessageViewPanelTest {
    private static DisplayedMessage message(String text) {
        MessageSummary summary = new MessageSummary("a1", "INBOX", 1, "Bob", "bob@example.org", "Meeting", Instant.now(),
                100, true, false, false, false);
        MessageContent content = new MessageContent("Bob", "bob@example.org", "me", "", "", "Meeting", Instant.now(),
                "<1@x>", text, null, List.of(), Map.of());
        return new DisplayedMessage(summary, content, text, null, Map.of());
    }

    @Test
    void calendarBarShowsOnlyForAppointments() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        boolean[] scheduled = {false};
        SwingUtilities.invokeAndWait(() -> {
            MessageViewPanel panel = new MessageViewPanel(new MessageViewActions() {
                @Override
                public void loadRemoteImagesOnce() {
                }

                @Override
                public void alwaysLoadRemoteImagesFromSender() {
                }

                @Override
                public void linkClicked(String uri) {
                }

                @Override
                public void openAttachment(Attachment attachment) {
                }

                @Override
                public void saveAttachment(Attachment attachment) {
                }

                @Override
                public void scheduleInCalendar() {
                    scheduled[0] = true;
                }
            });
            JPanel bar = bar(panel);
            panel.show(message("See you at ten."));
            assertFalse(bar.isVisible());
            panel.show(message("\t** Calendar Appointment **\n\n\tDate:\t11/22/94\n\tWhat:\tStaff meeting\n"));
            assertTrue(bar.isVisible());
            for (java.awt.Component c : bar.getComponents()) {
                if (c instanceof javax.swing.JButton b) b.doClick();
            }
            assertTrue(scheduled[0]);
            panel.clear("");
            assertFalse(bar.isVisible());
        });
    }

    private static JPanel bar(MessageViewPanel panel) {
        try {
            Field f = MessageViewPanel.class.getDeclaredField("calendarBar");
            f.setAccessible(true);
            return (JPanel) f.get(panel);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
