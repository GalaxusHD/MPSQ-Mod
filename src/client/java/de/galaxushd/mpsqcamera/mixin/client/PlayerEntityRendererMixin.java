package de.galaxushd.mpsqcamera.mixin.client;

import de.galaxushd.mpsqcamera.MpsqNametags;
import com.mojang.blaze3d.vertex.PoseStack;
import de.galaxushd.mpsqcamera.MpsqKickAnimationManager;
import de.galaxushd.mpsqcamera.NametagRenderContext;
import de.galaxushd.mpsqcamera.TeamVisibilitySettings;
import net.minecraft.client.player.AbstractClientPlayer;
import de.galaxushd.mpsqcamera.compat.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("RETURN"))
    private void mpsq$replaceServerRank(net.minecraft.world.entity.Avatar source, AvatarRenderState state,
                                      float tickDelta, CallbackInfo ci) {
        de.galaxushd.mpsqcamera.MpsqPetPreview.capture(state);
        de.galaxushd.mpsqcamera.MpsqAccessoryPreview.capture(state);
        de.galaxushd.mpsqcamera.MpsqAccessoryRenderer.captureMenuPlayerPreview(state);
        NametagRenderContext.clear();
        if (!(source instanceof AbstractClientPlayer player)) return;
        if(player.entityTags().contains("mpsq_cinematic_actor")){
            de.galaxushd.mpsqcamera.compat.AvatarIdentity.set(state,"cinematic-"+player.getUUID());
            state.skin=player.getSkin();
            state.scoreText=null;
            state.showHat=state.showJacket=state.showLeftSleeve=state.showRightSleeve=state.showLeftPants=state.showRightPants=true;
            state.nameTag=null;
            return;
        }
        de.galaxushd.mpsqcamera.MpsqAccessoryRenderer.captureWornAccessory(player,state);
        de.galaxushd.mpsqcamera.compat.AvatarIdentity.set(state, player.getGameProfile().name());
        if (de.galaxushd.mpsqcamera.MpsqReplayPlayback.ghost(player)) {
            de.galaxushd.mpsqcamera.compat.AvatarIdentity.set(state, de.galaxushd.mpsqcamera.MpsqReplayPlayback.key(player));
            state.nameTag = null;
            state.scoreText = null;
            state.showHat=state.showJacket=state.showLeftSleeve=state.showRightSleeve=state.showLeftPants=state.showRightPants=true;
            return;
        }
        MpsqKickAnimationManager.rememberSourceModelParts(player, state);
        if (MpsqKickAnimationManager.isClone(player)) {
            de.galaxushd.mpsqcamera.compat.AvatarIdentity.set(state, MpsqKickAnimationManager.animationKey(player));
            state.nameTag = null;
            state.scoreText = null;
            var cloneSkin = MpsqKickAnimationManager.skinTextures(player);
            if (cloneSkin != null) state.skin = cloneSkin;
            boolean[] parts = MpsqKickAnimationManager.sourceModelParts(player);
            if (parts != null) {
                state.showHat = parts[0];
                state.showJacket = parts[1];
                state.showLeftSleeve = parts[2];
                state.showRightSleeve = parts[3];
                state.showLeftPants = parts[4];
                state.showRightPants = parts[5];
            }
            // Keep the outer head texture enabled on the animated copy. Empty
            // overlay pixels stay transparent, while actual hat pixels remain visible.
            if(parts==null)state.showHat=state.showJacket=state.showLeftSleeve=state.showRightSleeve=state.showLeftPants=state.showRightPants=true;
            return;
        }
        if (!TeamVisibilitySettings.inArea()) return;
        state.scoreText = null;
        // Respect vanilla visibility (distance, sneaking, invisibility, etc.).
        // The server label need not contain the account name to be replaceable.
        if (state.nameTag != null) state.nameTag = MpsqNametags.forPlayer(player.getGameProfile().name());
    }

    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("TAIL"))
    private void mpsq$applyKickFall(AvatarRenderState state, PoseStack matrices,
                                    float bodyYaw, float scale, CallbackInfo ci) {
        float fall = MpsqKickAnimationManager.rootPitchDegrees(de.galaxushd.mpsqcamera.compat.AvatarIdentity.name(state));
        float despawn = MpsqKickAnimationManager.despawnScale(de.galaxushd.mpsqcamera.compat.AvatarIdentity.name(state));
        if (fall != 0.0f) {
            // Positive X turns the front of the player upward, so the clone lands
            // on its back. Keep the torso as the pivot for the complete skin.
            matrices.translate(0.0, 22.56 / 16.0, 0.0);
            matrices.rotate(com.mojang.math.Axis.XP.rotationDegrees(fall));
            matrices.translate(0.0, -22.56 / 16.0, 0.0);
        }
        if (despawn < 1.0f) matrices.scale(despawn, despawn, despawn);
    }

    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"))
    private void mpsq$startNametag(AvatarRenderState state, PoseStack matrices,
                                  net.minecraft.client.renderer.SubmitNodeCollector consumers, net.minecraft.client.renderer.state.level.CameraRenderState camera, CallbackInfo ci) {
        if (TeamVisibilitySettings.inArea()) {
            // Vanilla's playerName is the separate below-name scoreboard line.
            state.scoreText = null;
            NametagRenderContext.activate();
        }
    }

    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("RETURN"))
    private void mpsq$finishNametag(AvatarRenderState state, PoseStack matrices,
                                   net.minecraft.client.renderer.SubmitNodeCollector consumers, net.minecraft.client.renderer.state.level.CameraRenderState camera, CallbackInfo ci) {
        NametagRenderContext.clear();
    }
}
