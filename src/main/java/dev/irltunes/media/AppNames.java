package dev.irltunes.media;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Known media apps: maps the process name / app id the OS reports to a friendly name.
 * To add an app, add one {@code put(...)} line. Keys are lowercase, without ".exe".
 */
public final class AppNames {
    private static final Map<String, String> NAMES = new LinkedHashMap<>();
    /** Apps whose window title is "Artist - Title" (or "Title - Artist") while playing; used by the window-title fallback. */
    private static final Map<String, Boolean> TITLE_ARTIST_FIRST = new LinkedHashMap<>();

    private static void put(String id, String name) {
        NAMES.put(id, name);
    }

    private static void player(String id, String name, boolean artistFirst) {
        NAMES.put(id, name);
        TITLE_ARTIST_FIRST.put(id, artistFirst);
    }

    static {
        // Streaming / desktop players (window-title fallback knows their title format)
        player("spotify", "Spotify", true);
        player("aimp", "AIMP", true);
        player("foobar2000", "foobar2000", true);
        player("winamp", "Winamp", true);
        player("musicbee", "MusicBee", true);
        player("mediamonkey", "MediaMonkey", true);
        player("vlc", "VLC", false);
        player("mpc-hc64", "MPC-HC", false);
        player("mpc-hc", "MPC-HC", false);
        player("potplayermini64", "PotPlayer", false);
        player("potplayer", "PotPlayer", false);
        player("mpv", "mpv", false);
        player("tidal", "TIDAL", false);
        player("deezer", "Deezer", false);
        player("qobuz", "Qobuz", false);
        player("amazon music", "Amazon Music", false);
        player("cider", "Cider", false);
        player("plexamp", "Plexamp", false);
        // Apps that only report through the media session
        put("applemusic", "Apple Music");
        put("appleinc.applemusicwin", "Apple Music");
        put("music", "Apple Music");
        put("itunes", "iTunes");
        put("microsoft.zunemusic", "Media Player");
        put("wmplayer", "Windows Media Player");
        put("plex", "Plex");
        put("jellyfin", "Jellyfin");
        put("kodi", "Kodi");
        put("emby", "Emby");
        put("youtube music", "YouTube Music");
        put("soundcloud", "SoundCloud");
        put("rhythmbox", "Rhythmbox");
        put("clementine", "Clementine");
        put("strawberry", "Strawberry");
        put("elisa", "Elisa");
        put("lollypop", "Lollypop");
        put("audacious", "Audacious");
        put("vox", "VOX");
        // Browsers
        put("chrome", "Chrome");
        put("msedge", "Edge");
        put("firefox", "Firefox");
        put("brave", "Brave");
        put("opera", "Opera");
        put("vivaldi", "Vivaldi");
        put("arc", "Arc");
        put("zen", "Zen");
        put("chromium", "Chromium");
        put("librewolf", "LibreWolf");
        put("floorp", "Floorp");
        put("waterfox", "Waterfox");
        put("safari", "Safari");
    }

    private AppNames() {}

    /** Normalizes "Spotify.exe", "C:\\...\\Spotify.exe", "org.mpris.MediaPlayer2.spotify", "308046B0AF4A39CB" etc. */
    public static String normalize(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        int slash = Math.max(s.lastIndexOf('\\'), s.lastIndexOf('/'));
        if (slash >= 0) s = s.substring(slash + 1);
        if (s.endsWith(".exe")) s = s.substring(0, s.length() - 4);
        // Store-app ids look like "SpotifyAB.SpotifyMusic_zpdnekdrzrea0!Spotify"
        int bang = s.indexOf('!');
        if (bang >= 0) s = s.substring(0, bang);
        int underscore = s.indexOf('_');
        if (underscore > 0) s = s.substring(0, underscore);
        // MPRIS instance suffixes: "chromium.instance1234", "firefox.instance_1_23"
        int inst = s.indexOf(".instance");
        if (inst > 0) s = s.substring(0, inst);
        return s;
    }

    /** Friendly display name for a raw app id; unknown apps get a cleaned-up version of the id. */
    public static String display(String raw) {
        String n = normalize(raw);
        String known = NAMES.get(n);
        if (known != null) return known;
        for (Map.Entry<String, String> e : NAMES.entrySet()) {
            if (n.contains(e.getKey())) return e.getValue();
        }
        if (n.isEmpty()) return "";
        String last = n.contains(".") ? n.substring(n.lastIndexOf('.') + 1) : n;
        return Character.toUpperCase(last.charAt(0)) + last.substring(1);
    }

    /** Players the window-title fallback can read. Value: true if the title is "Artist - Track". */
    static Map<String, Boolean> titlePlayers() {
        return TITLE_ARTIST_FIRST;
    }

    public static boolean isBrowser(String raw) {
        String n = normalize(raw);
        return n.matches("chrome|msedge|firefox|brave|opera.*|vivaldi|arc|zen|chromium|librewolf|floorp|waterfox|safari");
    }
}
