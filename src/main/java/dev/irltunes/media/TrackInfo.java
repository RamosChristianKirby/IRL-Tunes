package dev.irltunes.media;

import org.jspecify.annotations.Nullable;

/**
 * A snapshot of what is playing.
 *
 * @param app        raw app id from the OS (e.g. "Spotify.exe", "spotify", "Music")
 * @param positionMs playback position when the snapshot was taken, or -1 if unknown
 * @param durationMs track length, or -1 if unknown
 * @param takenAtMs  {@link System#currentTimeMillis()} when the snapshot was taken
 * @param coverBytes encoded image (PNG/JPEG) provided by the player, if any
 * @param coverUrl   http(s) or file URL of the cover provided by the player, if any
 */
public record TrackInfo(
        String app,
        String title,
        String artist,
        String album,
        boolean playing,
        long positionMs,
        long durationMs,
        long takenAtMs,
        byte @Nullable [] coverBytes,
        @Nullable String coverUrl) {

    /** Identity of the track, used to detect track changes. */
    public String key() {
        return app + '\u0000' + title + '\u0000' + artist + '\u0000' + album;
    }

    /** Position extrapolated to now while playing. */
    public long positionNow() {
        if (positionMs < 0) return -1;
        long p = playing ? positionMs + (System.currentTimeMillis() - takenAtMs) : positionMs;
        return durationMs > 0 ? Math.min(p, durationMs) : p;
    }

    public TrackInfo withoutCover() {
        return new TrackInfo(app, title, artist, album, playing, positionMs, durationMs, takenAtMs, null, coverUrl);
    }
}
