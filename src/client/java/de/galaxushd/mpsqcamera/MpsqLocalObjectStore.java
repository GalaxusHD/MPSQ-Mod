package de.galaxushd.mpsqcamera;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Persists furniture placements per integrated single-player save. */
public final class MpsqLocalObjectStore {
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("mpsq-local-objects");
    private MpsqLocalObjectStore() { }

    private static Path file() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getServer() == null) return null;
        try {
            Path save = client.getServer().getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(save.toString().getBytes(StandardCharsets.UTF_8));
            return ROOT.resolve(HexFormat.of().formatHex(digest) + ".json");
        } catch (Exception error) {
            MpsqCameraClient.LOGGER.warn("Einzelspieler-Welt konnte nicht bestimmt werden", error);
            return null;
        }
    }

    public static JsonArray load() {
        Path path = file();
        if (path == null || !Files.isRegularFile(path)) return new JsonArray();
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
            return parsed.isJsonArray() ? parsed.getAsJsonArray() : new JsonArray();
        } catch (Exception error) {
            MpsqCameraClient.LOGGER.warn("Lokale MPSQ-Möbel konnten nicht geladen werden", error);
            return new JsonArray();
        }
    }

    public static JsonArray loadWorld(String worldId) {
        JsonArray result = new JsonArray();
        for (JsonElement value : load()) {
            if (!value.isJsonObject()) continue;
            JsonObject row = value.getAsJsonObject();
            String existingWorld = row.has("world_id") ? row.get("world_id").getAsString() : "minecraft:overworld";
            if (existingWorld.equals(worldId)) result.add(row);
        }
        return result;
    }

    public static boolean save(JsonArray objects) {
        Path path = file();
        if (path == null) return false;
        try {
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temporary, objects.toString(), StandardCharsets.UTF_8);
            try { Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE); }
            catch (IOException ignored) { Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch (IOException error) {
            MpsqCameraClient.LOGGER.warn("Lokale MPSQ-Möbel konnten nicht gespeichert werden", error);
            return false;
        }
    }

    public static boolean set(String worldId, int x, int y, int z, String modelId, int rotation, boolean remove) {
        JsonArray rows = load(), next = new JsonArray();
        for (JsonElement value : rows) {
            if (!value.isJsonObject()) continue;
            JsonObject row = value.getAsJsonObject();
            String existingWorld=row.has("world_id")?row.get("world_id").getAsString():"minecraft:overworld";
            if (existingWorld.equals(worldId)&&row.get("x").getAsInt() == x && row.get("y").getAsInt() == y && row.get("z").getAsInt() == z) continue;
            next.add(row);
        }
        if (!remove) {
            JsonObject row = new JsonObject();
            row.addProperty("world_id",worldId);
            row.addProperty("x", x); row.addProperty("y", y); row.addProperty("z", z);
            row.addProperty("model_id", modelId); row.addProperty("rotation", rotation);
            next.add(row);
        }
        return save(next);
    }
}
