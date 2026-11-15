package de.galaxushd.mpsqcamera.mixin.client;

import de.galaxushd.mpsqcamera.ScreenCreationManager;
import de.galaxushd.mpsqcamera.MpsqCinematicPreview;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Keep nearby cavity walls visible in remote views, including their culling frustum. */
@Mixin(Camera.class)
public abstract class CameraNearPlaneMixin {
    @ModifyConstant(method = {"update", "createProjectionMatrixForCulling"}, constant = @Constant(floatValue = 0.05F))
    private float mpsq$remoteNearPlane(float original) {
        return ScreenCreationManager.isCameraViewActive() || MpsqCinematicPreview.active() ? 0.01F : original;
    }
}
