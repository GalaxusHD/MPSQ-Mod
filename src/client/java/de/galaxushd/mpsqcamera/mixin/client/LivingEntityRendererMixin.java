package de.galaxushd.mpsqcamera.mixin.client;

import de.galaxushd.mpsqcamera.MpsqAccessoryRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At("TAIL"))
    private void mpsq$drawMenuAccessoryPreview(LivingEntityRenderState state, MatrixStack matrices,
                                                VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        if (state instanceof PlayerEntityRenderState playerState) {
            MpsqAccessoryRenderer.renderMenuPlayerPreviewAccessory(playerState, matrices, consumers);
        }
    }
}
