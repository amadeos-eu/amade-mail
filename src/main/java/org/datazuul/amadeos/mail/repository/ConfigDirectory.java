package org.datazuul.amadeos.mail.repository;

import java.nio.file.Path;

/**
 * Location of the mail configuration: {@code $XDG_CONFIG_HOME/amadeos/mail}, by default
 * {@code ~/.config/amadeos/mail}, next to the other amadeOS settings.
 */
public record ConfigDirectory(Path path) {

    public static ConfigDirectory fromEnvironment() {
        String xdg = System.getenv("XDG_CONFIG_HOME");
        Path base = xdg != null && !xdg.isBlank()
                ? Path.of(xdg)
                : Path.of(System.getProperty("user.home"), ".config");
        return new ConfigDirectory(base.resolve("amadeos").resolve("mail"));
    }

    public Path resolve(String fileName) {
        return path.resolve(fileName);
    }
}
