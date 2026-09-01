package de.galaxushd.mpsqcamera;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Local, player-bound list of cameras shown by the MPSQ clock. */
public final class MobileCameraLinkStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<UUID>>() { }.getType();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("mpsqcamera").resolve("mobile-clock-cameras.json");
    private static List<UUID> linked = load();

    private MobileCameraLinkStore() { }

    public static List<UUID> linkedCameras() {
        return linked.stream().filter(id -> LocalCameraStore.find(id).isPresent()).toList();
    }

    public static void replace(List<UUID> cameraIds) {
        linked = new ArrayList<>(cameraIds.stream().distinct().toList());
        save();
    }

    private static List<UUID> load() {
        if (!Files.isRegularFile(FILE)) return new ArrayList<>();
        try {
            List<UUID> result = GSON.fromJson(Files.readString(FILE), LIST_TYPE);
            return result == null ? new ArrayList<>() : new ArrayList<>(result);
        } catch (Exception error) {
            MpsqCameraClient.LOGGER.warn("Mobile Kamera-Verknüpfungen konnten nicht geladen werden", error);
            return new ArrayList<>();
        }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(linked, LIST_TYPE));
        } catch (IOException error) {
            MpsqCameraClient.LOGGER.warn("Mobile Kamera-Verknüpfungen konnten nicht gespeichert werden", error);
        }
    }
}
