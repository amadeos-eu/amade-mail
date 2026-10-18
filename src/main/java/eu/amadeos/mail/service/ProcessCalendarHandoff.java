package eu.amadeos.mail.service;

import eu.amadeos.commons.desktop.LaunchFeedback;

import java.io.IOException;
import java.nio.file.Path;

/** Starts {@code amade-calendar <file>}, which schedules the appointments in the file. */
public final class ProcessCalendarHandoff implements CalendarHandoff {
    static final String PROGRAM = "amade-calendar";

    @Override
    public void schedule(Path file) throws IOException {
        LaunchFeedback.announce();
        new ProcessBuilder(PROGRAM, file.toAbsolutePath().toString())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
    }
}
