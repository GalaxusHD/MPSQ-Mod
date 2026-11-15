package de.galaxushd.mpsqcamera;

import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/** Temporary suppression does not change the player's saved pet selection. */
final class MpsqPetVisibility {
    private static boolean vanished;
    static void initialize(){
        ClientSendMessageEvents.COMMAND.register(command->{
            if(command.trim().replaceFirst("^/","").equalsIgnoreCase("vanish"))vanished=!vanished;
        });
        ClientPlayConnectionEvents.JOIN.register((h,s,c)->vanished=false);
        ClientPlayConnectionEvents.DISCONNECT.register((h,c)->vanished=false);
    }
    static boolean suppressed(){var p=Minecraft.getInstance().player;return p==null||vanished||p.isSpectator()||p.isInvisible()||MpsqReplayPlayback.active();}
}
