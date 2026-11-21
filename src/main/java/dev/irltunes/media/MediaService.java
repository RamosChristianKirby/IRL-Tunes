package dev.irltunes.media;

import dev.irltunes.Config;
import dev.irltunes.IrlTunes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Polls the OS for the current track on a background thread and publishes the result for the HUD.
 * The render thread only reads the volatile fields; it never blocks on detection or downloads.
 */
public final class MediaService {
    private static final MediaService INSTANCE = new MediaService();
    private static final long IDLE_MAX_MS = 5000;

    public static MediaService get() {
        return INSTANCE;
    }

    /** Current track and its cover, swapped atomically together. */
    public record Snapshot(@Nullable TrackInfo track, @Nullable CoverImage cover, @Nullable String coverKey) {}

    private volatile Snapshot snapshot = new Snapshot(null, null, null);
    private volatile boolean restartRequested;
    private Thread thread;
    private final CoverArt coverArt = new CoverArt();

    private MediaService() {}

    public Snapshot snapshot() {
        return snapshot;
    }

    public synchronized void start() {
        if (thread != null) return;
        thread = Thread.ofPlatform().daemon().name("irltunes-media").priority(Thread.MIN_PRIORITY).start(this::run);
    }

    public synchronized void stop() {
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    /** Called when the config file changes, so source choice and intervals are re-read. */
    public void onConfigChanged() {
        restartRequested = true;
    }

    private List<MediaSource> createSources() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        int interval = Config.POLL_MS.get();
        List<MediaSource> list = new ArrayList<>();
        if (os.contains("win")) {
            if (Config.WINDOWS_MEDIA_SESSION.get()) list.add(new WindowsSessionSource(interval));
            list.add(new WindowsTitleSource());
        } else if (os.contains("mac")) {
            list.add(new MacSource());
        } else {
            list.add(new LinuxMprisSource());
        }
        return list;
    }

    private void run() {
        List<MediaSource> sources = createSources();
        IrlTunes.LOGGER.info("IRL Tunes media detection: {}", sources.stream().map(MediaSource::name).toList());
        String lastSourceName = null;
        long idleSleep = 0;
        try {
            while (!Thread.currentThread().isInterrupted()) {
                if (restartRequested) {
                    restartRequested = false;
                    sources.forEach(MediaSource::close);
                    sources = createSources();
                }

                Optional<TrackInfo> found = Optional.empty();
                for (MediaSource s : sources) {
                    if (!s.healthy()) continue;
                    if (!s.name().equals(lastSourceName)) {
                        IrlTunes.LOGGER.info("IRL Tunes using {}", s.name());
                        lastSourceName = s.name();
                    }
                    try {
                        found = s.poll().filter(this::allowed);
                    } catch (Exception e) {
                        IrlTunes.LOGGER.debug("{} poll failed: {}", s.name(), e.toString());
                    }
                    break; // only the first healthy source is used; later ones are fallbacks
                }
                publish(found.orElse(null));

                // Adaptive polling: back off while nothing is playing.
                int base = Config.POLL_MS.get();
                if (found.isPresent() && found.get().playing()) {
                    idleSleep = base;
                } else {
                    idleSleep = Math.min(IDLE_MAX_MS, Math.max(base, idleSleep + 500));
                }
                Thread.sleep(idleSleep);
            }
        } catch (InterruptedException ignored) {
            // shutting down
        } finally {
            sources.forEach(MediaSource::close);
        }
    }

    private boolean allowed(TrackInfo t) {
        List<? extends String> disabled = Config.DISABLED_APPS.get();
        if (disabled.isEmpty()) return true;
        String id = AppNames.normalize(t.app());
        String display = AppNames.display(t.app()).toLowerCase(Locale.ROOT);
        for (String d : disabled) {
            String x = d.trim().toLowerCase(Locale.ROOT);
            if (!x.isEmpty() && (id.contains(x) || display.equals(x))) return false;
        }
        return true;
    }

    private void publish(@Nullable TrackInfo track) {
        Snapshot prev = snapshot;
        if (track == null) {
            snapshot = new Snapshot(null, prev.cover(), prev.coverKey());
            return;
        }
        String key = track.key();
        TrackInfo slim = track.withoutCover();
        if (key.equals(prev.coverKey())) {
            snapshot = new Snapshot(slim, prev.cover(), key);
            return;
        }
        // New track: show the text immediately, then fill in the cover once loaded.
        snapshot = new Snapshot(slim, null, null);
        CoverImage cover = coverArt.resolve(track, Config.ONLINE_COVER_LOOKUP.get());
        Snapshot now = snapshot;
        if (now.track() != null && key.equals(now.track().key())) {
            snapshot = new Snapshot(now.track(), cover, key);
        }
    }
}
