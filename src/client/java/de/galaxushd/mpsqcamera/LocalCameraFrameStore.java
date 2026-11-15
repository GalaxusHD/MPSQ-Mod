package de.galaxushd.mpsqcamera;

import net.fabricmc.loader.api.FabricLoader;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Stores the latest frame only on this Minecraft installation. */
public final class LocalCameraFrameStore {
    private static final Path DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve("mpsqcamera").resolve("local-camera-frames");
    private static final boolean LOCAL_FRAMES_ONLY = true;

    private LocalCameraFrameStore() { }

    public static boolean localFramesOnly() { return LOCAL_FRAMES_ONLY; }
    public static boolean hasFrame(UUID cameraId) { return cameraId != null && Files.isRegularFile(path(cameraId)); }

    /** Atomically replaces the previous frame, so stale frames never accumulate. */
    public static CompletableFuture<Void> replace(UUID cameraId, byte[] png) {
        return CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(DIRECTORY);
                Path target = path(cameraId);
                Path temporary = Files.createTempFile(DIRECTORY, cameraId + "-", ".tmp");
                try {
                    Files.write(temporary, png);
                    try {
                        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    } catch (AtomicMoveNotSupportedException ignored) {
                        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally {
                    Files.deleteIfExists(temporary);
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Lokales Kamera-Bild konnte nicht gespeichert werden", exception);
            }
        });
    }

    public static CompletableFuture<NativeImage> load(UUID cameraId) {
        return CompletableFuture.supplyAsync(() -> {
            try (InputStream input = Files.newInputStream(path(cameraId))) {
                return NativeImage.read(input);
            } catch (IOException exception) {
                throw new IllegalStateException("Lokales Kamera-Bild konnte nicht geladen werden", exception);
            }
        });
    }

    /** Removes local frames that no longer belong to a known camera. */
    public static void prune(Collection<UUID> existingCameraIds) {
        if (!Files.isDirectory(DIRECTORY)) return;
        try (var files = Files.list(DIRECTORY)) {
            files.filter(file -> file.getFileName().toString().endsWith(".png"))
                    .filter(file -> !existingCameraIds.contains(idFromFile(file)))
                    .forEach(file -> {
                        try { Files.deleteIfExists(file); } catch (IOException ignored) { }
                    });
        } catch (IOException ignored) { }
    }

    private static UUID idFromFile(Path file) {
        try {
            String name = file.getFileName().toString();
            return UUID.fromString(name.substring(0, name.length() - 4));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Path path(UUID cameraId) { return DIRECTORY.resolve(cameraId + ".png"); }
}