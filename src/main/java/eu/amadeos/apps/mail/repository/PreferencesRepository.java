package eu.amadeos.apps.mail.repository;

import com.google.inject.Inject;
import eu.amadeos.commons.config.AppDirectories;
import eu.amadeos.commons.config.PropertiesFile;
import eu.amadeos.apps.mail.domain.MailPreferences;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Stores {@link MailPreferences} in {@code mail.conf}.
 */
public class PreferencesRepository {
    static final String FILE_NAME = "mail.conf";

    private final Path file;

    @Inject
    public PreferencesRepository(AppDirectories directories) {
        this(directories.configFile(FILE_NAME));
    }

    PreferencesRepository(Path file) {
        this.file = file;
    }

    public synchronized MailPreferences load() {
        Properties p = PropertiesFile.load(file);
        MailPreferences d = MailPreferences.DEFAULTS;
        Set<String> senders = Arrays.stream(p.getProperty("view.trustedImageSenders", "").split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        return new MailPreferences(
                Boolean.parseBoolean(p.getProperty("view.preferHtml", Boolean.toString(d.preferHtml()))),
                Boolean.parseBoolean(p.getProperty("view.alwaysLoadRemoteImages",
                        Boolean.toString(d.alwaysLoadRemoteImages()))),
                senders,
                parseInt(p.getProperty("check.intervalMinutes"), d.checkIntervalMinutes()));
    }

    public synchronized void save(MailPreferences preferences) {
        Properties p = new Properties();
        p.setProperty("view.preferHtml", Boolean.toString(preferences.preferHtml()));
        p.setProperty("view.alwaysLoadRemoteImages", Boolean.toString(preferences.alwaysLoadRemoteImages()));
        p.setProperty("view.trustedImageSenders", String.join(",", preferences.trustedImageSenders()));
        p.setProperty("check.intervalMinutes", Integer.toString(preferences.checkIntervalMinutes()));
        PropertiesFile.store(file, p, "amade-mail preferences");
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
