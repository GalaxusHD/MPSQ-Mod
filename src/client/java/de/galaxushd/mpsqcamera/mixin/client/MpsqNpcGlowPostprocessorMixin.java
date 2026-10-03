package de.galaxushd.mpsqcamera.mixin.client;

import de.galaxushd.mpsqcamera.MpsqAccessoryRenderer;
import de.galaxushd.mpsqcamera.MpsqMovementSensorSystem;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(WorldRenderer.class)
abstract class MpsqNpcGlowPostprocessorMixin {
    @Inject(method = "getEntitiesToRender", at = @At("RETURN"), cancellable = true)
    private void mpsq$includeNpcOutlines(Camera camera, Frustum frustum, List<Entity> entities,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && (MpsqAccessoryRenderer.hasGlowingNpcs() || MpsqMovementSensorSystem.isActive())) {
            cir.setReturnValue(true);
        }
    }
}
