package org.datazuul.amadeos.mail.service;

import org.datazuul.amadeos.mail.domain.MessageContent;
import org.datazuul.amadeos.mail.domain.MessageContent.Attachment;

import java.util.Locale;
import java.util.Optional;

/**
 * Recognises mail that carries an appointment: the dtcm text that starts with
 * {@code ** Calendar Appointment **}, as the CDE Calendar mailed it, or an iCalendar attachment
 * ({@code text/calendar}, {@code .ics}). Such a message can be handed to the Calendar.
 */
public final class CalendarContent {
    public static final String MARKER = "** Calendar Appointment **";

    private CalendarContent() {
    }

    /** The first iCalendar attachment, if any. */
    public static Optional<Attachment> icsAttachment(MessageContent content) {
        for (Attachment a : content.attachments()) {
            String type = a.mimeType() == null ? "" : a.mimeType().toLowerCase(Locale.ROOT);
            String name = a.fileName() == null ? "" : a.fileName().toLowerCase(Locale.ROOT);
            if (type.startsWith("text/calendar") || name.endsWith(".ics")) return Optional.of(a);
        }
        return Optional.empty();
    }

    /** True if the shown text holds a dtcm appointment or the message has an iCalendar attachment. */
    public static boolean hasAppointment(MessageContent content, String plainText) {
        return (plainText != null && plainText.contains(MARKER)) || icsAttachment(content).isPresent();
    }
}
