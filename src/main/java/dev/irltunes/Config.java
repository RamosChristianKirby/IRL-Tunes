package dev.irltunes;

import dev.irltunes.hud.Anchor;
import dev.irltunes.hud.Theme;
import java.util.Arrays;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Client config, stored in config/irltunes-client.toml and editable in-game (Mods > IRL Tunes > Config, or the M key). */
public final class Config {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    // --- Widget -------------------------------------------------------------------------------
    static { B.push("widget"); }

    public static final ModConfigSpec.BooleanValue ENABLED = B
            .comment("Show the now-playing widget (toggle in game with N).")
            .define("enabled", true);

    public static final ModConfigSpec.EnumValue<Anchor> ANCHOR = B
            .comment("Where the widget sits on the screen.")
            .defineEnum("anchor", Anchor.TOP_LEFT);

    public static final ModConfigSpec.IntValue OFFSET_X = B
            .comment("Horizontal distance from the anchored edge, in GUI pixels.")
            .defineInRange("offsetX", 4, 0, 2000);

    public static final ModConfigSpec.IntValue OFFSET_Y = B
            .comment("Vertical distance from the anchored edge, in GUI pixels.")
            .defineInRange("offsetY", 4, 0, 2000);

    public static final ModConfigSpec.IntValue SCALE = B
            .comment("Widget size in percent.")
            .defineInRange("scalePercent", 100, 50, 250);

    public static final ModConfigSpec.IntValue OPACITY = B
            .comment("Background opacity in percent.")
            .defineInRange("opacityPercent", 85, 0, 100);

    public static final ModConfigSpec.ConfigValue<String> THEME = B
            .comment("Color theme (cycle in game with the comma key). One of: " + Arrays.toString(Theme.values()))
            .define("theme", Theme.MIDNIGHT.name(), o -> o instanceof String s && Theme.exists(s));

    public static final ModConfigSpec.BooleanValue COMPACT = B
            .comment("Single-line layout without cover art.")
            .define("compact", false);

    public static final ModConfigSpec.BooleanValue SHOW_COVER = B
            .comment("Show album / video cover art.")
            .define("showCover", true);

    public static final ModConfigSpec.BooleanValue SPIN_COVER = B
            .comment("Draw the cover as a spinning vinyl record while music plays.")
            .define("spinCover", false);

    public static final ModConfigSpec.IntValue SPIN_RPM = B
            .comment("Vinyl spin speed in rotations per minute (33 is a real LP).")
            .defineInRange("spinRpm", 33, 1, 300);

    public static final ModConfigSpec.BooleanValue SHOW_PROGRESS = B
            .comment("Show the playback progress bar and times.")
            .define("showProgress", true);

    public static final ModConfigSpec.BooleanValue SHOW_SOURCE = B
            .comment("Show which app is playing (Spotify, Chrome, VLC...).")
            .define("showSource", true);

    public static final ModConfigSpec.BooleanValue MARQUEE = B
            .comment("Scroll long titles instead of cutting them off.")
            .define("marquee", true);

    public static final ModConfigSpec.BooleanValue HIDE_WHEN_PAUSED = B
            .comment("Hide the widget a few seconds after playback pauses or stops.")
            .define("hideWhenPaused", true);

    static { B.pop(); }

    // --- Detection ----------------------------------------------------------------------------
    static { B.push("detection"); }

    public static final ModConfigSpec.IntValue POLL_MS = B
            .comment("How often to check what is playing, in milliseconds. Slows down automatically when idle.")
            .defineInRange("pollIntervalMs", 1000, 250, 10000);

    public static final ModConfigSpec.BooleanValue WINDOWS_MEDIA_SESSION = B
            .comment("Windows: read the system media session (title, artist, cover, progress from any app).",
                     "If disabled or unavailable, falls back to reading player window titles.")
            .define("useWindowsMediaSession", true);

    public static final ModConfigSpec.BooleanValue ONLINE_COVER_LOOKUP = B
            .comment("When the player does not provide cover art, look it up on the iTunes Search API.",
                     "Only the artist and title are sent. Disable for fully offline operation.")
            .define("onlineCoverLookup", true);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_APPS = B
            .comment("Apps to ignore, matched case-insensitively against the source app name or id",
                     "(for example \"chrome\", \"msedge\", \"vlc\", \"spotify\").")
            .defineListAllowEmpty("disabledApps", List.of(), () -> "", o -> o instanceof String);

    static { B.pop(); }

    public static final ModConfigSpec SPEC = B.build();

    private Config() {}
}
