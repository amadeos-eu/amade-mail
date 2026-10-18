package eu.amadeos.apps.mail.domain;

import static eu.amadeos.apps.mail.i18n.Texts.tr;

import java.awt.Color;

/**
 * Marker colour of an account, shown in the folder tree, the message list and the compose window
 * so that it is always visible which account a message belongs to.
 */
public enum AccountColor {
    BLUE("Blue", new Color(0x37, 0x8A, 0xDD)),
    TEAL("Teal", new Color(0x1D, 0x9E, 0x75)),
    CORAL("Coral", new Color(0xD8, 0x5A, 0x30)),
    PURPLE("Purple", new Color(0x7F, 0x77, 0xDD)),
    AMBER("Amber", new Color(0xBA, 0x75, 0x17)),
    PINK("Pink", new Color(0xD4, 0x53, 0x7E));

    private final String label;
    private final Color color;

    AccountColor(String label, Color color) {
        this.label = label;
        this.color = color;
    }

    public Color color() {
        return color;
    }

    /** The colour for the n-th account, so that new accounts get distinct colours. */
    public static AccountColor forIndex(int index) {
        AccountColor[] values = values();
        return values[Math.floorMod(index, values.length)];
    }

    /** Shown in the account settings, from {@code color.<colour>}. */
    @Override
    public String toString() {
        String key = "color." + name().toLowerCase(java.util.Locale.ROOT);
        return eu.amadeos.apps.mail.i18n.Texts.messages().has(key) ? tr(key) : label;
    }
}
