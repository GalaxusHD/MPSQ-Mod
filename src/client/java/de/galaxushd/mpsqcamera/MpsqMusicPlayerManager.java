package de.galaxushd.mpsqcamera;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Opens the shared MPSQ music player with P. */
public final class MpsqMusicPlayerManager {
    private static KeyBinding openPlayerKey;

    private MpsqMusicPlayerManager() { }

    public static void initialize() {
        openPlayerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mpsqcamera.music_player", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P,
                "category.mpsqcamera.main"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openPlayerKey.wasPressed()) {
                if (client.player == null || client.world == null || client.currentScreen != null) continue;
                client.setScreen(new MpsqMusicPlayerScreen());
            }
        });
    }
}
