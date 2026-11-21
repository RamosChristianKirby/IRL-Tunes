package dev.irltunes.media;

import java.util.List;
import java.util.Optional;

/**
 * Linux: every MPRIS player (Spotify, VLC, Rhythmbox, browsers, ...) through {@code playerctl},
 * one call for all players. Requires the {@code playerctl} package.
 */
final class LinuxMprisSource implements MediaSource {
    private static final String SEP = "\u001f";
    private static final String FORMAT = String.join(SEP,
            "{{status}}", "{{playerName}}", "{{xesam:artist}}", "{{xesam:title}}", "{{xesam:album}}",
            "{{position}}", "{{mpris:length}}", "{{mpris:artUrl}}");

    private boolean missing;

    @Override
    public String name() {
        return "linux-mpris";
    }

    @Override
    public boolean healthy() {
        return !missing;
    }

    @Override
    public Optional<TrackInfo> poll() {
        String out = Processes.run(List.of("playerctl", "--all-players", "metadata", "--format", FORMAT), 1500);
        if (out == null) {
            // Distinguish "no players" (exit 1) from "playerctl not installed".
            if (Processes.run(List.of("playerctl", "--version"), 1500) == null) missing = true;
            return Optional.empty();
        }
        TrackInfo paused = null;
        for (String line : out.split("\n")) {
            String[] f = line.split(SEP, -1);
            if (f.length < 8 || f[3].isBlank()) continue;
            boolean playing = f[0].equalsIgnoreCase("Playing");
            TrackInfo t = new TrackInfo(f[1], f[3].trim(), f[2].trim(), f[4].trim(), playing,
                    micros(f[5]), micros(f[6]), System.currentTimeMillis(), null,
                    f[7].isBlank() ? null : f[7].trim());
            if (playing) return Optional.of(t);
            if (paused == null && f[0].equalsIgnoreCase("Paused")) paused = t;
        }
        return Optional.ofNullable(paused);
    }

    private static long micros(String s) {
        try {
            return Long.parseLong(s.trim()) / 1000;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
