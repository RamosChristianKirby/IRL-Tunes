package dev.irltunes.hud;

import java.util.Locale;

/** Color themes. Colors are 0xRRGGBB; alpha is applied at draw time. */
public enum Theme {
    MIDNIGHT("Midnight", 0x101018, 0x8A7CFF, 0xFFFFFF, 0xA8A8C0),
    SPOTIFY_GREEN("Green", 0x121212, 0x1DB954, 0xFFFFFF, 0xB3B3B3),
    SUNSET("Sunset", 0x2A1220, 0xFF7A59, 0xFFF1E8, 0xE0A890),
    OCEAN("Ocean", 0x0B1E2D, 0x3FC1E8, 0xEAF8FF, 0x8FB8CC),
    FOREST("Forest", 0x13200F, 0x7BC950, 0xF1FFE8, 0x9DB88E),
    CHERRY("Cherry", 0x24080E, 0xFF4D6D, 0xFFEFF2, 0xD9A0AB),
    GOLD("Gold", 0x1C1708, 0xF5C542, 0xFFF9E6, 0xC9B98A),
    PAPER("Paper", 0xF2EFE6, 0x3B6EA5, 0x1E1E1E, 0x5C5C5C),
    VAPOR("Vapor", 0x1A0B2E, 0xFF71CE, 0xE8FFFF, 0x01CDFE),
    MONO("Mono", 0x000000, 0xFFFFFF, 0xFFFFFF, 0x9A9A9A),
    REDSTONE("Redstone", 0x1B0F0F, 0xE0301E, 0xF5E6E6, 0xB89A9A),
    GLASS("Glass", 0x000000, 0xFFFFFF, 0xFFFFFF, 0xDDDDDD);

    private final String displayName;
    public final int background, accent, text, subtext;

    Theme(String displayName, int background, int accent, int text, int subtext) {
        this.displayName = displayName;
        this.background = background;
        this.accent = accent;
        this.text = text;
        this.subtext = subtext;
    }

    public String displayName() {
        return displayName;
    }

    /** GLASS uses a lighter background alpha so it reads as a translucent pane. */
    public float backgroundAlphaFactor() {
        return this == GLASS ? 0.35f : 1f;
    }

    public Theme next() {
        Theme[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static boolean exists(String name) {
        try {
            valueOf(name.toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static Theme byName(String name) {
        return exists(name) ? valueOf(name.toUpperCase(Locale.ROOT)) : MIDNIGHT;
    }
}
