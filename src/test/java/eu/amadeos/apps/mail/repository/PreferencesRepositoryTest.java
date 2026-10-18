package eu.amadeos.apps.mail.repository;

import eu.amadeos.apps.mail.domain.MailPreferences;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PreferencesRepositoryTest {
    @TempDir
    Path dir;

    @Test
    void defaultsWithoutFile() {
        assertEquals(MailPreferences.DEFAULTS, new PreferencesRepository(dir.resolve("mail.conf")).load());
    }

    @Test
    void roundTrip() {
        PreferencesRepository repository = new PreferencesRepository(dir.resolve("mail.conf"));
        MailPreferences preferences = new MailPreferences(true, false, Set.of("News@Example.org", "a@b.c"), 15);
        repository.save(preferences);
        MailPreferences loaded = repository.load();
        assertEquals(preferences, loaded);
        assertTrue(loaded.loadsRemoteImagesFrom("news@example.org"));
        assertFalse(loaded.loadsRemoteImagesFrom("other@example.org"));
    }
}
