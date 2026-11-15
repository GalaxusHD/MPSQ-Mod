package de.galaxushd.mpsqcamera;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;


/** Opens the shared MPSQ music player with P. */
public final class MpsqMusicPlayerManager {
    private static KeyMapping openPlayerKey;

    private MpsqMusicPlayerManager() { }

    public static void initialize() {
        openPlayerKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mpsqcamera.music_player", InputConstants.Type.KEYBOARD, com.mojang.blaze3d.platform.InputConstants.KEY_P,
                MpsqKeyCategories.MAIN));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openPlayerKey.consumeClick()) {
                if (client.player == null || client.level == null || client.gui.screen() != null) continue;
                client.gui.setScreen(new MpsqMusicPlayerScreen());
            }
        });
    }
}
