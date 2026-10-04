package de.galaxushd.mpsqcamera;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Stores the local Mini You preference for the upcoming companion implementation. */
public final class MpsqPetSelectionStore {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("mpsq-pets.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static String selected = load();

    private MpsqPetSelectionStore() { }

    public static synchronized String selectedId() { return selected; }

    public static synchronized boolean select(String id) {
        if (MpsqPetCatalog.byId(id) == null) return false;
        JsonObject data = new JsonObject();
        data.addProperty("schema", 1);
        data.addProperty("selected_pet", id);
        try {
            Files.createDirectories(FILE.getParent());
            Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(data), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
            selected = id;
            return true;
        } catch (Exception error) {
            MpsqCameraClient.LOGGER.warn("Mini-You-Auswahl konnte nicht gespeichert werden", error);
            return false;
        }
    }

    private static String load() {
        try {
            if (!Files.isRegularFile(FILE)) return null;
            JsonObject data = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
            String id = data.has("selected_pet") ? data.get("selected_pet").getAsString() : null;
            return MpsqPetCatalog.byId(id) == null ? null : id;
        } catch (Exception ignored) {
            return null;
        }
    }
}
