package eu.amadeos.apps.mail.i18n;

import eu.amadeos.apps.mail.bootstrap.AmadeMailApplication;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Texts;
import eu.amadeos.commons.i18n.TranslationCheck;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TranslationTest {
    private static Messages in(Locale locale) {
        return Messages.load(AmadeMailApplication.class, Texts.bundle(), locale);
    }

    @Test
    void everyLanguageHasEveryText() {
        assertEquals(Map.of(), TranslationCheck.problems(AmadeMailApplication.class, Texts.bundle()));
    }

    @Test
    void keysBuiltFromIdsExist() {
        Messages english = in(Locale.ENGLISH);
        for (String key : List.of("options.page.Accounts", "options.page.Message View", "options.page.Language",
                "auth.password", "auth.oauth2_google", "color.blue", "color.teal", "color.coral", "color.purple",
                "color.amber", "color.pink", "date.today", "date.this-year", "date.older", "date.full")) {
            assertTrue(english.has(key), key);
        }
    }

    @Test
    void countsAndDatesFollowTheLanguage() {
        assertEquals("Loaded 2 messages, 1 new", in(Locale.ENGLISH).format("status.loaded", 2, 1));
        assertEquals("1 Nachricht geladen, 0 neu", in(Locale.GERMANY).format("status.loaded", 1, 0));
        assertEquals("Supprimer définitivement 3 messages ?", in(Locale.FRANCE).format("delete.confirm", 3));
        ZonedDateTime date = ZonedDateTime.parse("2026-10-13T08:12:00+02:00[Europe/Berlin]");
        for (Locale locale : List.of(Locale.ENGLISH, Locale.GERMANY, Locale.FRANCE, Locale.of("es", "ES"),
                Locale.ITALY)) {
            Messages m = in(locale);
            for (String pattern : List.of("date.today", "date.this-year", "date.older", "date.full")) {
                assertFalse(DateTimeFormatter.ofPattern(m.get(pattern), locale).format(date).isBlank());
            }
        }
        assertEquals("Tue, 13 Oct 2026 08:12",
                DateTimeFormatter.ofPattern(in(Locale.ENGLISH).get("date.full"), Locale.ENGLISH).format(date));
    }

    @Test
    void menuMnemonicsAreUniquePerLanguage() {
        List<String> menus = List.of("menu.mailbox", "menu.message", "menu.edit", "menu.attachments", "menu.view",
                "menu.compose", "menu.move", "menu.help");
        for (Locale locale : List.of(Locale.ENGLISH, Locale.GERMANY, Locale.FRANCE, Locale.of("es", "ES"),
                Locale.ITALY)) {
            java.util.Set<Character> letters = new java.util.HashSet<>();
            for (String key : menus) {
                String text = in(locale).get(key);
                int amp = text.indexOf('&');
                assertTrue(amp >= 0, locale + " " + key);
                assertTrue(letters.add(Character.toLowerCase(text.charAt(amp + 1))), locale + " " + key + " " + text);
            }
        }
    }
}
