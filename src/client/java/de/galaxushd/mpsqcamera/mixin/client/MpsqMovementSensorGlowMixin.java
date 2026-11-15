package de.galaxushd.mpsqcamera.mixin.client;
import de.galaxushd.mpsqcamera.MpsqMovementSensorSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Enables the existing entity's vanilla outline, without a second render pass. */
@Mixin(Minecraft.class)
public abstract class MpsqMovementSensorGlowMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true)
    private void mpsq$sensorOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if(de.galaxushd.mpsqcamera.MpsqNpcGlow.isNpc(entity)){cir.setReturnValue(de.galaxushd.mpsqcamera.MpsqNpcGlow.visibleGlow(entity));return;}
        if (de.galaxushd.mpsqcamera.MpsqReplayPlayback.ghost(entity)) {
            cir.setReturnValue(de.galaxushd.mpsqcamera.MpsqReplayPlayback.color(entity) != null);
            return;
        }
        if (MpsqMovementSensorSystem.shouldOutlineEntity(entity)) cir.setReturnValue(true);
        if (de.galaxushd.mpsqcamera.MpsqGamesRuntime.outline(entity)) cir.setReturnValue(true);
    }
}
