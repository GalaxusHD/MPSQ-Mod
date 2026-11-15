package de.galaxushd.mpsqcamera;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ScreenCameraStore {
    private static final Map<UUID, List<UUID>> CAMERAS = new HashMap<>();
    private static final Map<UUID, Integer> ACTIVE = new HashMap<>();

    private ScreenCameraStore() {
    }

    public static void put(UUID screenId, List<UUID> cameras) {
        CAMERAS.put(screenId, List.copyOf(cameras));
        if (cameras.isEmpty()) {
            ACTIVE.remove(screenId);
            return;
        }

        int previous = ACTIVE.getOrDefault(screenId, 0);
        ACTIVE.put(screenId, Math.floorMod(previous, cameras.size()));
    }

    public static boolean hasCameras(UUID screenId) {
        return !CAMERAS.getOrDefault(screenId, List.of()).isEmpty();
    }

    public static UUID active(UUID screenId) {
        List<UUID> cameras = CAMERAS.getOrDefault(screenId, List.of());

        if (cameras.isEmpty()) {
            return null;
        }

        int index = Math.floorMod(
                ACTIVE.getOrDefault(screenId, 0),
                cameras.size()
        );

        return cameras.get(index);
    }

    public static UUID next(UUID screenId, int direction) {
        List<UUID> cameras = CAMERAS.getOrDefault(screenId, List.of());

        if (cameras.isEmpty()) {
            return null;
        }

        int index = Math.floorMod(
                ACTIVE.getOrDefault(screenId, 0) + direction,
                cameras.size()
        );

        ACTIVE.put(screenId, index);
        return cameras.get(index);
    }

    /** Returns the assigned cameras in their persistent switch order. */
    public static List<UUID> cameras(UUID screenId) {
        return CAMERAS.getOrDefault(screenId, List.of());
    }

    /** One-based position of the active camera, or 0 when none is assigned. */
    public static int activePosition(UUID screenId) {
        List<UUID> cameras = CAMERAS.getOrDefault(screenId, List.of());
        if (cameras.isEmpty()) return 0;
        return Math.floorMod(ACTIVE.getOrDefault(screenId, 0), cameras.size()) + 1;
    }

    public static void clear() {
        CAMERAS.clear();
        ACTIVE.clear();
    }
}
