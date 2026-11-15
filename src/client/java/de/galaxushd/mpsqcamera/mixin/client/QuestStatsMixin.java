package de.galaxushd.mpsqcamera.mixin.client;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPacketListener.class)
public class QuestStatsMixin {
 @Inject(method="handleAwardStats",at=@At("TAIL"))
 private void mpsq$stats(ClientboundAwardStatsPacket packet,CallbackInfo ci){de.galaxushd.mpsqcamera.MpsqQuestProgress.statsReceived();}
}
