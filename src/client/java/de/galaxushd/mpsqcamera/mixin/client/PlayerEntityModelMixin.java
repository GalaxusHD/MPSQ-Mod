package de.galaxushd.mpsqcamera.mixin.client;

import de.galaxushd.mpsqcamera.MpsqKickAnimationManager;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the bundled death-animation limb keyframes to the live player's skin model.
 */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin {

    @Inject(method = "setAngles", at = @At("TAIL"))
    private void mpsq$applyKickKeyframes(PlayerEntityRenderState state, CallbackInfo ci) {
        if (state == null || state.name == null) {
            return;
        }

        PlayerEntityModel<?> model = (PlayerEntityModel<?>) (Object) this;

        apply(model.head, MpsqKickAnimationManager.rotation(state.name, "head"));
        apply(model.rightArm, MpsqKickAnimationManager.rotation(state.name, "rightArm"));
        apply(model.leftArm, MpsqKickAnimationManager.rotation(state.name, "leftArm"));
        apply(model.rightLeg, MpsqKickAnimationManager.rotation(state.name, "rightLeg"));
        apply(model.leftLeg, MpsqKickAnimationManager.rotation(state.name, "leftLeg"));
    }

    private static void apply(ModelPart part, float[] rotation) {
        if (part == null || rotation == null) {
            return;
        }

        part.pitch = rotation[0];
        part.yaw = rotation[1];
        part.roll = rotation[2];
    }
}
