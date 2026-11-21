package dev.irltunes.media;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.irltunes.IrlTunes;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Windows 10/11: reads the system media session (GlobalSystemMediaTransportControls), which every
 * modern player and browser publishes to: Spotify, Apple Music, TIDAL, VLC, YouTube in Chrome/Edge/Firefox, etc.
 *
 * <p>WinRT is not reachable from plain Java, so a small bundled PowerShell script does the WinRT calls
 * and streams one JSON line per poll. It is started once and lives as long as the game.
 */
final class WindowsSessionSource implements MediaSource {
    private static final long STALE_MS = 6000;
    private static final int MAX_RESTARTS = 3;

    private final int intervalMs;
    private Process process;
    private Thread reader;
    private volatile TrackInfo latest;
    private volatile boolean latestActive;
    private volatile long latestAt;
    private volatile boolean failed;
    private int restarts;
    private byte[] lastCover;
    private String lastCoverKey;

    WindowsSessionSource(int intervalMs) {
        this.intervalMs = intervalMs;
    }

    @Override
    public String name() {
        return "windows-media-session";
    }

    @Override
    public boolean healthy() {
        return !failed;
    }

    @Override
    public Optional<TrackInfo> poll() throws IOException {
        if (failed) return Optional.empty();
        if (process == null || !process.isAlive()) {
            if (process != null && ++restarts > MAX_RESTARTS) {
                IrlTunes.LOGGER.warn("Windows media session helper keeps exiting; falling back to window titles");
                failed = true;
                return Optional.empty();
            }
            start();
        }
        if (System.currentTimeMillis() - latestAt > STALE_MS) return Optional.empty();
        return latestActive ? Optional.ofNullable(latest) : Optional.empty();
    }

    private void start() throws IOException {
        Path script = Files.createTempFile("irltunes-gsmtc", ".ps1");
        script.toFile().deleteOnExit();
        try (InputStream in = WindowsSessionSource.class.getResourceAsStream("/assets/irltunes/scripts/gsmtc.ps1")) {
            if (in == null) throw new IOException("bundled gsmtc.ps1 missing");
            Files.copy(in, script, StandardCopyOption.REPLACE_EXISTING);
        }
        latestAt = System.currentTimeMillis(); // grace period while PowerShell starts
        process = new ProcessBuilder(List.of(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", script.toString(),
                "-ParentPid", Long.toString(ProcessHandle.current().pid()),
                "-IntervalMs", Integer.toString(intervalMs)))
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        Process p = process;
        reader = Thread.ofPlatform().daemon().name("irltunes-gsmtc-reader").start(() -> readLoop(p));
    }

    private void readLoop(Process p) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                handle(line);
            }
        } catch (IOException ignored) {
            // process ended
        }
    }

    private void handle(String line) {
        JsonObject o;
        try {
            o = JsonParser.parseString(line).getAsJsonObject();
        } catch (RuntimeException e) {
            return;
        }
        if (o.has("error") && o.get("error").getAsString().startsWith("init:")) {
            IrlTunes.LOGGER.warn("Windows media session unavailable: {}", o.get("error").getAsString());
            failed = true;
            return;
        }
        long now = System.currentTimeMillis();
        latestAt = now;
        boolean active = o.has("active") && o.get("active").getAsBoolean();
        String title = str(o, "title");
        if (!active || title.isBlank()) {
            latestActive = false;
            return;
        }
        String app = str(o, "app");
        String artist = str(o, "artist");
        String album = str(o, "album");

        // The helper only sends the cover when the track changes; remember it for the following lines.
        String coverKey = app + '\u0000' + title + '\u0000' + artist + '\u0000' + album;
        if (!coverKey.equals(lastCoverKey)) {
            lastCoverKey = coverKey;
            lastCover = null;
        }
        if (o.has("cover")) {
            try {
                lastCover = Base64.getDecoder().decode(o.get("cover").getAsString());
            } catch (IllegalArgumentException ignored) {
                lastCover = null;
            }
        }

        // Browsers often put " - YouTube" etc. in the title and the channel name in artist.
        if (AppNames.isBrowser(app)) {
            title = TitleParser.stripSiteSuffix(title);
        }
        latest = new TrackInfo(app, title, artist, album,
                o.has("playing") && o.get("playing").getAsBoolean(),
                num(o, "position"), num(o, "duration"), now, lastCover, null);
        latestActive = true;
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? "" : e.getAsString().trim();
    }

    private static long num(JsonObject o, String k) {
        JsonElement e = o.get(k);
        if (e == null || e.isJsonNull()) return -1;
        long v = e.getAsLong();
        return v > 0 ? v : (k.equals("position") ? 0 : -1);
    }

    @Override
    public void close() {
        if (process != null) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
        if (reader != null) reader.interrupt();
    }
}
