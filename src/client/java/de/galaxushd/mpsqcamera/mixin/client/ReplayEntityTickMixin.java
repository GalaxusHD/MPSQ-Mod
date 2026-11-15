package de.galaxushd.mpsqcamera.mixin.client;

import de.galaxushd.mpsqcamera.MpsqReplayPlayback;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Recorded entities follow the replay clock, not live physics or despawn timers. */
@Mixin(ClientLevel.class)
public class ReplayEntityTickMixin {
    @Inject(method="tickNonPassenger",at=@At("HEAD"),cancellable=true)
    private void mpsq$recordedEntity(Entity entity,CallbackInfo ci){
        if(MpsqReplayPlayback.ghost(entity))ci.cancel();
    }
    @Inject(method="tickPassenger",at=@At("HEAD"),cancellable=true)
    private void mpsq$recordedPassenger(Entity vehicle,Entity passenger,CallbackInfo ci){
        if(MpsqReplayPlayback.ghost(passenger))ci.cancel();
    }
}
