package eu.amadeos.apps.mail.service;

import java.io.IOException;
import java.nio.file.Path;

/** Hands a file with an appointment (iCalendar or dtcm text) to the Calendar. */
public interface CalendarHandoff {
    void schedule(Path file) throws IOException;
}
