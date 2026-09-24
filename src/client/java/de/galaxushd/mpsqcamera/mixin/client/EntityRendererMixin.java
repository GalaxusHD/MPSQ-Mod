package de.galaxushd.mpsqcamera.mixin.client;

import de.galaxushd.mpsqcamera.NametagRenderContext;
import de.galaxushd.mpsqcamera.MpsqKickAnimationManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Entfernt ausschließlich hinter MPSQ-Nametags den Vanilla-Textkasten. */
@Mixin(net.minecraft.client.render.entity.EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method="render",at=@At("HEAD"))
    private void mpsq$kickFallPose(EntityRenderState state,MatrixStack matrices,VertexConsumerProvider consumers,int light,CallbackInfo ci){
        if(!(state instanceof PlayerEntityRenderState player)||player.name==null)return;
        float elapsed=MpsqKickAnimationManager.elapsedSeconds(player.name);if(elapsed<0)return;
        matrices.translate(0,0.95,0);matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MpsqKickAnimationManager.rootPitchDegrees(player.name)));matrices.translate(0,-0.95,0);
    }

    @ModifyArg(
            method = "renderLabelIfPresent",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/font/TextRenderer;draw(Lnet/minecraft/text/Text;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)V"
            ),
            index = 8
    )
    private int mpsq$transparentNametagBackground(int originalBackgroundColor) {
        return NametagRenderContext.active() ? 0 : originalBackgroundColor;
    }
}
