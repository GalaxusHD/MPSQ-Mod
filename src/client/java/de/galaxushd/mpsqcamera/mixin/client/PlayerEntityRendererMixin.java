package de.galaxushd.mpsqcamera.mixin.client;

import de.galaxushd.mpsqcamera.MpsqNametags;
import de.galaxushd.mpsqcamera.MpsqKickAnimationManager;
import de.galaxushd.mpsqcamera.NametagRenderContext;
import de.galaxushd.mpsqcamera.TeamVisibilitySettings;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @Inject(method = "updateRenderState", at = @At("RETURN"))
    private void mpsq$replaceServerRank(AbstractClientPlayerEntity player, PlayerEntityRenderState state,
                                      float tickDelta, CallbackInfo ci) {
        NametagRenderContext.clear();
        MpsqKickAnimationManager.rememberSourceModelParts(player, state);
        if (MpsqKickAnimationManager.isClone(player)) {
            state.name = MpsqKickAnimationManager.animationKey(player);
            state.displayName = null;
            state.playerName = null;
            var cloneSkin = MpsqKickAnimationManager.skinTextures(player);
            if (cloneSkin != null) state.skinTextures = cloneSkin;
            boolean[] parts = MpsqKickAnimationManager.sourceModelParts(player);
            if (parts != null) {
                state.jacketVisible = parts[1];
                state.leftSleeveVisible = parts[2];
                state.rightSleeveVisible = parts[3];
                state.leftPantsLegVisible = parts[4];
                state.rightPantsLegVisible = parts[5];
            }
            // Keep the outer head texture enabled on the animated copy. Empty
            // overlay pixels stay transparent, while actual hat pixels remain visible.
            state.hatVisible = true;
            return;
        }
        if (!TeamVisibilitySettings.visible()) return;
        state.playerName = null;
        // Respect vanilla visibility (distance, sneaking, invisibility, etc.).
        // The server label need not contain the account name to be replaceable.
        if (state.displayName != null) state.displayName = MpsqNametags.forPlayer(player.getGameProfile().getName());
    }

    @Inject(method = "setupTransforms", at = @At("TAIL"))
    private void mpsq$applyKickFall(PlayerEntityRenderState state, MatrixStack matrices,
                                    float bodyYaw, float scale, CallbackInfo ci) {
        float fall = MpsqKickAnimationManager.rootPitchDegrees(state.name);
        float despawn = MpsqKickAnimationManager.despawnScale(state.name);
        if (despawn < 1.0f) matrices.translate(0.0, -(1.0f - despawn) * 0.45, 0.0);
        if (fall != 0.0f) {
            // Positive X turns the front of the player upward, so the clone lands
            // on its back. Keep the torso as the pivot for the complete skin.
            matrices.translate(0.0, 0.95, 0.0);
            matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(fall));
            matrices.translate(0.0, -0.95, 0.0);
        }
        if (despawn < 1.0f) matrices.scale(despawn, despawn, despawn);
    }

    @ModifyVariable(method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Text mpsq$useModFontAtDraw(Text original, PlayerEntityRenderState state, Text text,
                                    MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        if (!TeamVisibilitySettings.visible() || state.name == null || state.name.isBlank()) return original;
        return MpsqNametags.forPlayer(state.name);
    }

    @Inject(method = "renderLabelIfPresent", at = @At("HEAD"))
    private void mpsq$startNametag(PlayerEntityRenderState state, Text text, MatrixStack matrices,
                                  VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        if (TeamVisibilitySettings.visible()) {
            // Vanilla's playerName is the separate below-name scoreboard line.
            state.playerName = null;
            NametagRenderContext.activate();
        }
    }

    @Inject(method = "renderLabelIfPresent", at = @At("RETURN"))
    private void mpsq$finishNametag(PlayerEntityRenderState state, Text text, MatrixStack matrices,
                                   VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        NametagRenderContext.clear();
    }
}
