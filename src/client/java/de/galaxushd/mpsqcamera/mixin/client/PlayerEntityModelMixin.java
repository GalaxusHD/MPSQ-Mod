package de.galaxushd.mpsqcamera.mixin.client;

import de.galaxushd.mpsqcamera.MpsqKickAnimationManager;
import de.galaxushd.mpsqcamera.MpsqPetModelContext;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the bundled death-animation limb keyframes to the live player's skin model.
 */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin {

    @Shadow @Final private ModelPart leftSleeve;
    @Shadow @Final private ModelPart rightSleeve;
    @Shadow @Final private ModelPart leftPants;
    @Shadow @Final private ModelPart rightPants;
    @Shadow @Final private ModelPart jacket;

    @Inject(method = "setAngles", at = @At("TAIL"))
    private void mpsq$applyKickKeyframes(PlayerEntityRenderState state, CallbackInfo ci) {
        if (state == null || state.name == null) {
            return;
        }

        BipedEntityModel<?> model = (BipedEntityModel<?>) (Object) this;

        apply(model.head, MpsqKickAnimationManager.rotation(state.name, "head"));
        // The hat part is the player's outer head/hat skin layer. It must follow
        // the same pivots as the head or the outer texture separates in the fall.
        apply(model.hat, MpsqKickAnimationManager.rotation(state.name, "head"));
        apply(model.rightArm, MpsqKickAnimationManager.rotation(state.name, "rightArm"));
        apply(rightSleeve, MpsqKickAnimationManager.rotation(state.name, "rightArm"));
        apply(model.leftArm, MpsqKickAnimationManager.rotation(state.name, "leftArm"));
        apply(leftSleeve, MpsqKickAnimationManager.rotation(state.name, "leftArm"));
        apply(model.rightLeg, MpsqKickAnimationManager.rotation(state.name, "rightLeg"));
        apply(rightPants, MpsqKickAnimationManager.rotation(state.name, "rightLeg"));
        apply(model.leftLeg, MpsqKickAnimationManager.rotation(state.name, "leftLeg"));
        apply(leftPants, MpsqKickAnimationManager.rotation(state.name, "leftLeg"));
    }

    @Inject(method = "setAngles", at = @At("TAIL"))
    private void mpsq$applyPetProportions(PlayerEntityRenderState state, CallbackInfo ci) {
        BipedEntityModel<?> model = (BipedEntityModel<?>) (Object) this;

        // The vanilla player geometry is shared by all players, so restore its
        // proportions first and only then reshape the marked pet render.
        scale(model.head, 1.0f, 1.0f, 1.0f);
        scale(model.hat, 1.0f, 1.0f, 1.0f);
        scale(model.body, 1.0f, 1.0f, 1.0f);
        scale(jacket, 1.0f, 1.0f, 1.0f);
        scale(model.rightArm, 1.0f, 1.0f, 1.0f);
        scale(model.leftArm, 1.0f, 1.0f, 1.0f);
        scale(rightSleeve, 1.0f, 1.0f, 1.0f);
        scale(leftSleeve, 1.0f, 1.0f, 1.0f);
        scale(model.rightLeg, 1.0f, 1.0f, 1.0f);
        scale(model.leftLeg, 1.0f, 1.0f, 1.0f);
        scale(rightPants, 1.0f, 1.0f, 1.0f);
        scale(leftPants, 1.0f, 1.0f, 1.0f);
        model.rightLeg.originY = 12.0f;
        model.leftLeg.originY = 12.0f;

        if (!MpsqPetModelContext.isRenderingPet()) return;

        // Mini-Me pets use a big 8x8 head and compact body/limbs, as in the
        // supplied Blockbench pet reference, while retaining the skin UVs.
        scale(model.head, 1.5f, 1.5f, 1.5f);
        scale(model.hat, 1.5f, 1.5f, 1.5f);
        scale(model.body, 0.9f, 0.65f, 0.9f);
        scale(jacket, 0.9f, 0.65f, 0.9f);
        scale(model.rightArm, 0.9f, 0.7f, 0.9f);
        scale(model.leftArm, 0.9f, 0.7f, 0.9f);
        scale(rightSleeve, 0.9f, 0.7f, 0.9f);
        scale(leftSleeve, 0.9f, 0.7f, 0.9f);
        scale(model.rightLeg, 0.9f, 0.65f, 0.9f);
        scale(model.leftLeg, 0.9f, 0.65f, 0.9f);
        scale(rightPants, 0.9f, 0.65f, 0.9f);
        scale(leftPants, 0.9f, 0.65f, 0.9f);
        model.rightLeg.originY = 8.0f;
        model.leftLeg.originY = 8.0f;
    }

    private static void scale(ModelPart part, float x, float y, float z) {
        if (part == null) return;
        part.xScale = x;
        part.yScale = y;
        part.zScale = z;
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
