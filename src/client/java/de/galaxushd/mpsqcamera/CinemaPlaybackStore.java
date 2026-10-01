package de.galaxushd.mpsqcamera;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Shared playback state received from the MPSQ API for cinema screens. */
public final class CinemaPlaybackStore {
    private static final PlaybackState STOPPED = new PlaybackState(false, 0L, 0L, 0L);
    private static final Map<UUID, PlaybackState> STATES = new HashMap<>();

    private CinemaPlaybackStore() { }

    public static void replace(Map<UUID, PlaybackState> states) {
        STATES.clear();
        STATES.putAll(states);
    }

    public static PlaybackState get(UUID screenId) {
        return STATES.getOrDefault(screenId, STOPPED);
    }

    public static void set(UUID screenId, PlaybackState state) { STATES.put(screenId, state); }

    /** updatedAtMs is the server's timestamp of the last playback command. */
    public record PlaybackState(boolean playing, long positionMs, long revision, long updatedAtMs) { }
}
