package de.galaxushd.mpsqcamera;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/** Plays the optional MPSQ sound when the player right-clicks a placed furniture model. */
public final class MpsqFurnitureInteraction {
    private MpsqFurnitureInteraction() { }

    public static void initialize() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!world.isClient || player != net.minecraft.client.MinecraftClient.getInstance().player) return ActionResult.PASS;
            Vec3d start = player.getCameraPosVec(1.0f);
            Vec3d end = start.add(player.getRotationVec(1.0f).multiply(6.0));
            double blockDistance = start.squaredDistanceTo(hit.getPos());
            String selectedSound = null;
            double nearestDistance = blockDistance;

            for (JsonElement element : MpsqAccessoryRenderer.objectsSnapshot()) {
                if (!element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                if (!object.has("sound_id")) continue;
                String soundId = object.get("sound_id").getAsString().trim();
                if (soundId.isEmpty()) continue;

                double x = object.get("x").getAsDouble();
                double y = object.get("y").getAsDouble();
                double z = object.get("z").getAsDouble();
                double scale = object.has("scale") ? object.get("scale").getAsDouble() : 1.0;
                scale = Double.isFinite(scale) ? Math.max(0.25, Math.min(3.0, scale)) : 1.0;
                Box bounds = new Box(x + 0.5 - scale * 0.5, y, z + 0.5 - scale * 0.5,
                        x + 0.5 + scale * 0.5, y + 2.0 * scale, z + 0.5 + scale * 0.5);
                var intersection = bounds.raycast(start, end);
                if (intersection.isEmpty()) continue;
                double distance = start.squaredDistanceTo(intersection.get());
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    selectedSound = soundId;
                }
            }
            if (selectedSound != null) {
                MpsqMediaAudioManager.play("mp3", List.of(selectedSound));
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
    }
}
