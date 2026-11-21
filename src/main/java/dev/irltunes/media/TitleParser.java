package dev.irltunes.media;

import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Turns window titles like "Daft Punk - One More Time" into artist/title pairs. */
final class TitleParser {
    private static final Pattern SITE_SUFFIX = Pattern.compile(
            "\\s+[-–—|]\\s+(YouTube Music|YouTube|SoundCloud|Spotify|Deezer|Twitch|Vimeo|Bandcamp|Apple Music|TIDAL)\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BROWSER_SUFFIX = Pattern.compile(
            "\\s+[-–—]\\s+(Google Chrome|Microsoft\u200B? ?Edge|Mozilla Firefox|Brave|Opera|Vivaldi|Chromium|LibreWolf|Floorp|Waterfox|Zen Browser|Arc)\\s*$",
            Pattern.CASE_INSENSITIVE);
    /** Leading "(3) " notification counters that YouTube adds to tab titles. */
    private static final Pattern COUNTER = Pattern.compile("^\\(\\d+\\)\\s+");
    private static final Pattern PLAYER_SUFFIX = Pattern.compile(
            "\\s+[-–—]\\s+(VLC media player|AIMP|foobar2000.*|Winamp|MusicBee|MediaMonkey|mpv|PotPlayer|MPC-HC)\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SPLIT = Pattern.compile("\\s+[-–—]\\s+");

    private TitleParser() {}

    static String stripSiteSuffix(String s) {
        String out = COUNTER.matcher(s).replaceFirst("");
        out = BROWSER_SUFFIX.matcher(out).replaceFirst("");
        return SITE_SUFFIX.matcher(out).replaceFirst("").trim();
    }

    /**
     * @param artistFirst whether the player formats its title "Artist - Track"
     * @return {artist, title}, or null if the window title does not look like a playing track
     */
    static String @Nullable [] parse(String windowTitle, boolean artistFirst) {
        String t = PLAYER_SUFFIX.matcher(windowTitle.trim()).replaceFirst("");
        // Idle player windows just show the app name.
        if (t.isEmpty() || t.equalsIgnoreCase("spotify") || t.equalsIgnoreCase("spotify premium")
                || t.equalsIgnoreCase("spotify free") || t.equalsIgnoreCase("vlc media player")
                || t.startsWith("AIMP") || t.startsWith("foobar2000") || t.equalsIgnoreCase("winamp")
                || t.equalsIgnoreCase("musicbee")) {
            return null;
        }
        String[] parts = SPLIT.split(t, 2);
        if (parts.length < 2) {
            return new String[] {"", stripExtension(t)};
        }
        return artistFirst
                ? new String[] {parts[0].trim(), parts[1].trim()}
                : new String[] {parts[1].trim(), stripExtension(parts[0].trim())};
    }

    /** VLC/mpv titles are often file names. */
    private static String stripExtension(String s) {
        return s.replaceFirst("\\.(mp3|flac|m4a|ogg|opus|wav|aac|wma|mp4|mkv|webm)$", "");
    }
}
