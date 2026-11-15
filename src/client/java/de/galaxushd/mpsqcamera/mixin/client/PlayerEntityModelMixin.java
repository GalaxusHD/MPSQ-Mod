package de.galaxushd.mpsqcamera.mixin.client;

import de.galaxushd.mpsqcamera.MpsqKickAnimationManager;
import de.galaxushd.mpsqcamera.MpsqPetModelContext;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the bundled death-animation limb keyframes to the live player's skin model.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerEntityModelMixin {

    @Shadow @Final private ModelPart leftSleeve;
    @Shadow @Final private ModelPart rightSleeve;
    @Shadow @Final private ModelPart leftPants;
    @Shadow @Final private ModelPart rightPants;
    @Shadow @Final private ModelPart jacket;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void mpsq$applyKickKeyframes(AvatarRenderState state, CallbackInfo ci) {
        if (state == null || de.galaxushd.mpsqcamera.compat.AvatarIdentity.name(state) == null) {
            return;
        }

        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;

        apply(model.head, MpsqKickAnimationManager.rotation(de.galaxushd.mpsqcamera.compat.AvatarIdentity.name(state), "head"));
        // In 1.21.8 overlays are children of their body parts. They inherit
        // transforms; applying the parent's transform again detaches the skin.
        apply(model.rightArm, MpsqKickAnimationManager.rotation(de.galaxushd.mpsqcamera.compat.AvatarIdentity.name(state), "rightArm"));
        apply(model.leftArm, MpsqKickAnimationManager.rotation(de.galaxushd.mpsqcamera.compat.AvatarIdentity.name(state), "leftArm"));
        apply(model.rightLeg, MpsqKickAnimationManager.rotation(de.galaxushd.mpsqcamera.compat.AvatarIdentity.name(state), "rightLeg"));
        apply(model.leftLeg, MpsqKickAnimationManager.rotation(de.galaxushd.mpsqcamera.compat.AvatarIdentity.name(state), "leftLeg"));
        if(MpsqKickAnimationManager.elapsedSeconds(de.galaxushd.mpsqcamera.compat.AvatarIdentity.name(state))>=0){
            move(model.rightArm,MpsqKickAnimationManager.position(de.galaxushd.mpsqcamera.compat.AvatarIdentity.name(state),"rightArm"));
            move(model.leftArm,MpsqKickAnimationManager.position(de.galaxushd.mpsqcamera.compat.AvatarIdentity.name(state),"leftArm"));
            move(model.rightLeg,MpsqKickAnimationManager.position(de.galaxushd.mpsqcamera.compat.AvatarIdentity.name(state),"rightLeg"));
            move(model.leftLeg,MpsqKickAnimationManager.position(de.galaxushd.mpsqcamera.compat.AvatarIdentity.name(state),"leftLeg"));
        }
    }

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void mpsq$applyPetProportions(AvatarRenderState state, CallbackInfo ci) {
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;

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
        model.rightLeg.y = 12.0f;
        model.leftLeg.y = 12.0f;

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
        model.rightLeg.y = 8.0f;
        model.leftLeg.y = 8.0f;
    }

    private static void scale(ModelPart part, float x, float y, float z) {
        if (part == null) return;
        part.xScale = x;
        part.yScale = y;
        part.zScale = z;
    }

    private static void move(ModelPart part,float[] position){
        if(position==null)return;
        part.x+=position[0];part.y+=position[1];part.z+=position[2];
    }
    private static void apply(ModelPart part, float[] rotation) {
        if (part == null || rotation == null) {
            return;
        }

        part.xRot = rotation[0];
        part.yRot = rotation[1];
        part.zRot = rotation[2];
    }
}
