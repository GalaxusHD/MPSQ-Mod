package de.galaxushd.mpsqcamera.mixin.client;
import de.galaxushd.mpsqcamera.MpsqMovementSensorSystem;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Entity.class)
public abstract class MpsqMovementSensorColorMixin {
    @Inject(method = "getTeamColor", at = @At("RETURN"), cancellable = true)
    private void mpsq$sensorColor(CallbackInfoReturnable<Integer> cir) {
        Entity entity = (Entity) (Object) this;
        Integer replayColor = de.galaxushd.mpsqcamera.MpsqReplayPlayback.color(entity);
        if (replayColor != null) { cir.setReturnValue(replayColor); return; }
        if (de.galaxushd.mpsqcamera.MpsqGamesRuntime.outline(entity)) cir.setReturnValue(de.galaxushd.mpsqcamera.MpsqGamesRuntime.color(entity));
        if (MpsqMovementSensorSystem.shouldOutlineEntity(entity))
            cir.setReturnValue(MpsqMovementSensorSystem.entityOutlineColor(entity));
    }
}
