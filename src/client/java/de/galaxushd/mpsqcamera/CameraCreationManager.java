package de.galaxushd.mpsqcamera;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;


/** Creates a client-only camera marker in front of the player's head. */
public final class CameraCreationManager {
    private static KeyMapping createCameraKey;
    private static Vec3 headAnchor;
    private static Object anchorWorld;

    private CameraCreationManager() { }

    public static void initialize() {
        createCameraKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mpsqcamera.create_camera", InputConstants.Type.KEYBOARD, com.mojang.blaze3d.platform.InputConstants.KEY_C,
                MpsqKeyCategories.MAIN));
        ClientTickEvents.END_CLIENT_TICK.register(CameraCreationManager::tick);
    }

    private static void tick(Minecraft client) {
        while (createCameraKey.consumeClick()) {
            if (client.player == null || client.level == null || client.gui.screen() != null || !TeamVisibilitySettings.visible()) return;

            // The key binding itself respects the player's configured camera
            // key. The sneak key is read from Minecraft's options as well, so
            // Shift+C is only the default, not a hard-coded combination.
            if (client.options.keyShift.isDown()) {
                if (client.hitResult instanceof EntityHitResult hit
                        && hit.getEntity() instanceof Player target
                        && client.player.distanceToSqr(target) <= 9.0D) {
                    BodycamRequestManager.request(client, target);
                } else {
                    de.galaxushd.mpsqcamera.compat.PlayerMessages.send(client.player, Component.literal("Sieh einen Spieler aus Schlagreichweite an."), true);
                }
                continue;
            }
            headAnchor = MpsqCameraInteraction.lookedAtHead(client);
            anchorWorld = client.level;
            client.gui.setScreen(new MpsqCreateMenuScreen());
        }
    }

    static void clearAnchor(){headAnchor=null;anchorWorld=null;}

    static void createNamedCamera(Minecraft client, String name) {
        if (client.player == null || client.level == null) return;
        Vec3 position = anchorWorld == client.level && headAnchor != null ? headAnchor : client.player.getEyePosition(1.0F).add(client.player.getViewVector(1.0F).scale(0.65));
        boolean anchored=anchorWorld==client.level&&headAnchor!=null;
        float yaw=anchored?MpsqCameraInteraction.headYaw(client,headAnchor):client.player.getYRot();
        float pitch=anchored?0:client.player.getXRot();
        headAnchor = null; anchorWorld = null;
        try{TeamVisibilitySettings.requirePlacement(position.x,position.y,position.z);}catch(IllegalArgumentException e){de.galaxushd.mpsqcamera.compat.PlayerMessages.send(client.player,Component.literal(e.getMessage()),false);return;}
        JsonObject body = new JsonObject();
        body.addProperty("name", name.trim()); body.addProperty("kind", "STATIC");
        body.addProperty("dimension", client.level.dimension().identifier().toString());
        body.addProperty("x", position.x); body.addProperty("y", position.y); body.addProperty("z", position.z);
        body.addProperty("yaw", yaw); body.addProperty("pitch", pitch);
        MpsqApiClient.post("/cameras", body)
                .thenCompose(ignored -> MpsqApiClient.refreshCameras())
                .whenComplete((ignored, error) -> client.execute(() -> {
                    if (client.player == null) return;
                    if (error != null) {
                        MpsqCameraClient.LOGGER.warn("Kamera konnte nicht synchronisiert werden", error);
                        de.galaxushd.mpsqcamera.compat.PlayerMessages.send(client.player, Component.literal("Kamera konnte nicht gespeichert werden."), true);
                    } else {
                        de.galaxushd.mpsqcamera.compat.PlayerMessages.send(client.player, Component.translatable("status.mpsqcamera.camera_created"), true);
                    }
                }));
    }
}
