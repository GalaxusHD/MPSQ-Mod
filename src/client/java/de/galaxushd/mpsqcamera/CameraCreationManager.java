package de.galaxushd.mpsqcamera;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/** Creates a client-only camera marker in front of the player's head. */
public final class CameraCreationManager {
    private static KeyBinding createCameraKey;

    private CameraCreationManager() { }

    public static void initialize() {
        createCameraKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mpsqcamera.create_camera", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C,
                "category.mpsqcamera.main"));
        ClientTickEvents.END_CLIENT_TICK.register(CameraCreationManager::tick);
    }

    private static void tick(MinecraftClient client) {
        while (createCameraKey.wasPressed()) {
            if (client.player == null || client.world == null || client.currentScreen != null) return;

            // The key binding itself respects the player's configured camera
            // key. The sneak key is read from Minecraft's options as well, so
            // Shift+C is only the default, not a hard-coded combination.
            if (client.options.sneakKey.isPressed()) {
                if (client.crosshairTarget instanceof EntityHitResult hit
                        && hit.getEntity() instanceof PlayerEntity target
                        && client.player.squaredDistanceTo(target) <= 9.0D) {
                    BodycamRequestManager.request(client, target);
                } else {
                    client.player.sendMessage(Text.literal("Sieh einen Spieler aus Schlagreichweite an."), true);
                }
                continue;
            }
            client.setScreen(new MpsqCreateMenuScreen());
        }
    }

    static void createNamedCamera(MinecraftClient client, String name) {
        if (client.player == null || client.world == null) return;
        Vec3d position = client.player.getCameraPosVec(1.0F).add(client.player.getRotationVec(1.0F).multiply(0.65));
        JsonObject body = new JsonObject();
        body.addProperty("name", name.trim()); body.addProperty("kind", "STATIC");
        body.addProperty("dimension", client.world.getRegistryKey().getValue().toString());
        body.addProperty("x", position.x); body.addProperty("y", position.y); body.addProperty("z", position.z);
        body.addProperty("yaw", client.player.getYaw()); body.addProperty("pitch", client.player.getPitch());
        MpsqApiClient.post("/cameras", body)
                .thenCompose(ignored -> MpsqApiClient.refreshCameras())
                .whenComplete((ignored, error) -> client.execute(() -> {
                    if (client.player == null) return;
                    if (error != null) {
                        MpsqCameraClient.LOGGER.warn("Kamera konnte nicht synchronisiert werden", error);
                        client.player.sendMessage(Text.literal("Kamera konnte nicht gespeichert werden."), true);
                    } else {
                        client.player.sendMessage(Text.translatable("status.mpsqcamera.camera_created"), true);
                    }
                }));
    }
}
