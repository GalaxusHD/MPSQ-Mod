package de.galaxushd.mpsqcamera;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Plays the optional MPSQ sound when the player right-clicks a placed furniture model. */
public final class MpsqFurnitureInteraction {
    private MpsqFurnitureInteraction() { }

    public static void initialize() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!world.isClientSide() || player != net.minecraft.client.Minecraft.getInstance().player) return InteractionResult.PASS;
            Vec3 start = player.getEyePosition(1.0f);
            Vec3 end = start.add(player.getViewVector(1.0f).scale(6.0));
            double blockDistance = start.distanceToSqr(hit.getLocation());
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
                AABB bounds = new AABB(x + 0.5 - scale * 0.5, y, z + 0.5 - scale * 0.5,
                        x + 0.5 + scale * 0.5, y + 2.0 * scale, z + 0.5 + scale * 0.5);
                var intersection = bounds.clip(start, end);
                if (intersection.isEmpty()) continue;
                double distance = start.distanceToSqr(intersection.get());
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    selectedSound = soundId;
                }
            }
            if (selectedSound != null) {
                MpsqMediaAudioManager.play("mp3", List.of(selectedSound));
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
    }
}
