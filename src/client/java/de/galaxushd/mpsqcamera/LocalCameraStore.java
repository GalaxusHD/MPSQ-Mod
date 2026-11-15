package de.galaxushd.mpsqcamera;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

/** Local cache of cameras. The API is the shared source of truth. */
public final class LocalCameraStore {
    private static final List<CameraData> CAMERAS = new ArrayList<>();

    private LocalCameraStore() { }

    public static List<CameraData> getAll() { return List.copyOf(CAMERAS); }
    public static Optional<CameraData> find(UUID id) { return CAMERAS.stream().filter(camera -> camera.id().equals(id)).findFirst(); }

    public static CameraData createStatic(String name, String dimension, Vec3 position, float yaw, float pitch) {
        CameraData camera = new CameraData(UUID.randomUUID(), name, CameraKind.STATIC, dimension, position, yaw, pitch, null, null);
        CAMERAS.add(camera);
        return camera;
    }

    public static CameraData createBodycam(String name, String dimension, UUID wearerId) {
        CameraData camera = new CameraData(UUID.randomUUID(), name, CameraKind.BODYCAM, dimension, null, 0, 0, wearerId, null);
        CAMERAS.add(camera);
        return camera;
    }

    public static void remove(UUID id) { CAMERAS.removeIf(camera -> camera.id().equals(id)); }
    public static void replaceAll(List<CameraData> cameras) { CAMERAS.clear(); CAMERAS.addAll(cameras); }

    public enum CameraKind { STATIC, BODYCAM }

    public record CameraData(UUID id, String name, CameraKind kind, String dimension, Vec3 position,
                             float yaw, float pitch, UUID wearerId, String wearerName) { }
}
