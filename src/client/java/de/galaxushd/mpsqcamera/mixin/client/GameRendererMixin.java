package de.galaxushd.mpsqcamera.mixin.client;

import de.galaxushd.mpsqcamera.ScreenCreationManager;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The held player item must not be rendered from a remote camera view. */
@Mixin(GameRenderer.class)
public final class GameRendererMixin {
    @Inject(method = "extract", at = @At("HEAD"))
    private void mpsq$cinematicFrame(CallbackInfo ci) {
        de.galaxushd.mpsqcamera.MpsqCinematicPreview.frame();
        de.galaxushd.mpsqcamera.MpsqCameraCavity.update();
    }
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void mpsq$hideHeldItemForCamera(CallbackInfo ci) {
        if (ScreenCreationManager.isCameraViewActive() || de.galaxushd.mpsqcamera.MpsqCinematicPreview.active()) {
            ci.cancel();
        }
    }
}
