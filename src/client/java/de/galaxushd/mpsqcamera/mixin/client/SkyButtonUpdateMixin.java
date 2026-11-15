package de.galaxushd.mpsqcamera.mixin.client;

import de.galaxushd.mpsqcamera.MpsqGamesRuntime;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe confirmed world updates, including buttons pressed by other players. */
@Mixin(ClientPacketListener.class)
public abstract class SkyButtonUpdateMixin {
    @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
    private void mpsq$button(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        MpsqGamesRuntime.skyButtonUpdate(packet.getPos(), packet.getBlockState());
        de.galaxushd.mpsqcamera.MpsqReplayManager.blockUpdate(packet.getPos(),packet.getBlockState());
    }
    @Inject(method = "handleChunkBlocksUpdate", at = @At("TAIL"))
    private void mpsq$buttons(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        packet.runUpdates(MpsqGamesRuntime::skyButtonUpdate);
        packet.runUpdates(de.galaxushd.mpsqcamera.MpsqReplayManager::blockUpdate);
    }
}
