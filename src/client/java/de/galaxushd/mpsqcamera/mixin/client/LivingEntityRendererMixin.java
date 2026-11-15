package de.galaxushd.mpsqcamera.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import de.galaxushd.mpsqcamera.MpsqAccessoryRenderer;
import de.galaxushd.mpsqcamera.compat.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @org.spongepowered.asm.mixin.Shadow protected net.minecraft.client.model.EntityModel<?> model;
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void mpsq$petPreview(LivingEntityRenderState state,PoseStack matrices,
            net.minecraft.client.renderer.SubmitNodeCollector collector,net.minecraft.client.renderer.state.level.CameraRenderState camera,CallbackInfo ci){
        if(de.galaxushd.mpsqcamera.MpsqAccessoryPreview.render(state,matrices,new MultiBufferSource(collector,camera))){ci.cancel();return;}
        if(state instanceof AvatarRenderState avatar && de.galaxushd.mpsqcamera.MpsqIsolatedSkinRenderer.isBody(avatar)){
            var buffers=new MultiBufferSource(collector,camera);
            de.galaxushd.mpsqcamera.MpsqIsolatedSkinRenderer.render(avatar,matrices,buffers,true);
            buffers.endBatch();ci.cancel();return;
        }
        if(de.galaxushd.mpsqcamera.MpsqPetPreview.render(state,matrices,new MultiBufferSource(collector,camera)))ci.cancel();
    }
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At(value="INVOKE", target="Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V", shift=At.Shift.BEFORE))
    private void mpsq$drawMenuAccessoryPreview(LivingEntityRenderState state, PoseStack matrices,
                                                net.minecraft.client.renderer.SubmitNodeCollector collector, net.minecraft.client.renderer.state.level.CameraRenderState camera, CallbackInfo ci) {
        if (state instanceof AvatarRenderState playerState && model instanceof net.minecraft.client.model.player.PlayerModel playerModel) {
            var consumers=new MultiBufferSource(collector,camera);
            MpsqAccessoryRenderer.renderAttachedHat(playerState, playerModel.head, matrices, consumers);
            consumers.endBatch();
        }
    }
}
