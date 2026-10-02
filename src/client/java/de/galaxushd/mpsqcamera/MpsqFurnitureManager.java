package de.galaxushd.mpsqcamera;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/** Handles right-click interaction with client-rendered furniture. */
public final class MpsqFurnitureManager {
    private MpsqFurnitureManager() { }

    /**
     * The virtual furniture hitbox is a one-block cube centered on the model.
     * Right-clicks on the hitbox are consumed even when no sound is configured.
     */
    public static boolean handleRightClick() {
        MinecraftClient client = MinecraftClient.getInstance();
        var player = client.player;
        if (player == null || client.world == null || client.currentScreen != null
                || !TeamVisibilitySettings.visible()) {
            return false;
        }

        Vec3d eye = player.getCameraPosVec(1.0f);
        Vec3d end = eye.add(player.getRotationVec(1.0f).multiply(6.0));
        JsonObject selected = null;
        double nearest = Double.MAX_VALUE;

        for (var element : MpsqAccessoryRenderer.objectsSnapshot()) {
            if (!element.isJsonObject()) continue;
            JsonObject furniture = element.getAsJsonObject();
            if (!furniture.has("x") || !furniture.has("y") || !furniture.has("z")) continue;

            double x = furniture.get("x").getAsDouble() + 0.5;
            double centerY = MpsqAccessoryRenderer.furnitureHitboxCenterY(furniture);
            double z = furniture.get("z").getAsDouble() + 0.5;
            Box hitbox = new Box(x - 0.5, centerY - 0.5, z - 0.5,
                    x + 0.5, centerY + 0.5, z + 0.5);
            var hit = hitbox.raycast(eye, end);
            if (hit.isEmpty()) continue;

            double distance = eye.squaredDistanceTo(hit.get());
            if (distance < nearest) {
                nearest = distance;
                selected = furniture;
            }
        }

        if (selected == null) return false;
        String soundId = selected.has("sound_id") && !selected.get("sound_id").isJsonNull()
                ? selected.get("sound_id").getAsString()
                : selected.has("soundId") && !selected.get("soundId").isJsonNull()
                ? selected.get("soundId").getAsString() : "";
        if (soundId.matches("[a-zA-Z0-9_-]{1,64}")) {
            MpsqMediaAudioManager.play("mp3", List.of(soundId));
        }
        return true;
    }
}
