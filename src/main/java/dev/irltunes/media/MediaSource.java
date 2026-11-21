package dev.irltunes.media;

import java.util.Optional;

/** One way of finding out what is playing on this computer. Implementations are polled from a background thread. */
public interface MediaSource extends AutoCloseable {
    /** Short name for logs. */
    String name();

    /** @return the current track, or empty if nothing is playing / paused-and-unknown. */
    Optional<TrackInfo> poll() throws Exception;

    /** @return false once the source has failed permanently and a fallback should be used. */
    default boolean healthy() {
        return true;
    }

    @Override
    default void close() {}
}
