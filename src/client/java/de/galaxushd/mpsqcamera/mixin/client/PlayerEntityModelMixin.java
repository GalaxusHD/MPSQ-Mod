package de.galaxushd.mpsqcamera.mixin.client;

import de.galaxushd.mpsqcamera.MpsqKickAnimationManager;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
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
        BipedEntityModel<?> model = (BipedEntityModel<?>) (Object) this;
        if (state != null && state.name != null) {
            apply(model.head, MpsqKickAnimationManager.rotation(state.name, "head"));
            apply(model.rightArm, MpsqKickAnimationManager.rotation(state.name, "rightArm"));
            apply(model.leftArm, MpsqKickAnimationManager.rotation(state.name, "leftArm"));
            apply(model.rightLeg, MpsqKickAnimationManager.rotation(state.name, "rightLeg"));
            apply(model.leftLeg, MpsqKickAnimationManager.rotation(state.name, "leftLeg"));
        }

        de.galaxushd.mpsqcamera.MpsqNpcSkinRenderer.JointPose pose =
                de.galaxushd.mpsqcamera.MpsqNpcSkinRenderer.activeNpcPose();
        if (pose != null) {
            add(model.head, pose.headX(), pose.headY(), pose.headZ());
            add(model.leftArm, pose.leftArmX(), pose.leftArmY(), pose.leftArmZ());
            add(model.rightArm, pose.rightArmX(), pose.rightArmY(), pose.rightArmZ());
        }
    }

    private static void apply(ModelPart part, float[] rotation) {
        if (part == null || rotation == null) {
            return;
        }

        part.pitch = rotation[0];
        part.yaw = rotation[1];
        part.roll = rotation[2];
    }

    private static void add(ModelPart part, float x, float y, float z) {
        if (part == null) return;
        part.pitch += x;
        part.yaw += y;
        part.roll += z;
    }
}
