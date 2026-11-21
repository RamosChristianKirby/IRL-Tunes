package dev.irltunes.media;

import java.util.List;
import java.util.Optional;

/**
 * macOS: asks Spotify and Apple Music through a single {@code osascript} call.
 * Each app is only queried if it is already running, so this never launches anything.
 */
final class MacSource implements MediaSource {
    private static final String SEP = "\u001f";
    private static final String SCRIPT = """
            set sep to (ASCII character 31)
            if application "Spotify" is running then
                tell application "Spotify"
                    if player state is not stopped then
                        set t to current track
                        return "Spotify" & sep & (player state as text) & sep & (artist of t) & sep & (name of t) & sep & (album of t) & sep & ((player position * 1000) as integer) & sep & (duration of t) & sep & (artwork url of t)
                    end if
                end tell
            end if
            if application "Music" is running then
                tell application "Music"
                    if player state is not stopped then
                        set t to current track
                        return "Music" & sep & (player state as text) & sep & (artist of t) & sep & (name of t) & sep & (album of t) & sep & ((player position * 1000) as integer) & sep & ((duration of t * 1000) as integer) & sep & ""
                    end if
                end tell
            end if
            return ""
            """;

    @Override
    public String name() {
        return "macos-applescript";
    }

    @Override
    public Optional<TrackInfo> poll() {
        String out = Processes.run(List.of("osascript", "-e", SCRIPT), 2500);
        if (out == null || out.isBlank()) return Optional.empty();
        String[] f = out.trim().split(SEP, -1);
        if (f.length < 8 || f[3].isBlank()) return Optional.empty();
        return Optional.of(new TrackInfo(f[0], f[3].trim(), f[2].trim(), f[4].trim(),
                f[1].equalsIgnoreCase("playing"), parse(f[5]), parse(f[6]), System.currentTimeMillis(), null,
                f[7].isBlank() ? null : f[7].trim()));
    }

    private static long parse(String s) {
        try {
            return Long.parseLong(s.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
