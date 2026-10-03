package de.galaxushd.mpsqcamera.mixin.client;

import de.galaxushd.mpsqcamera.MpsqMovementSensorSystem;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.ColorHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the white/red sensor outline as an extra vanilla entity-outline pass. */
@Mixin(WorldRenderer.class)
public abstract class MpsqMovementSensorGlowMixin {
    @Shadow @Final private BufferBuilderStorage bufferBuilders;
    @Shadow @Final private EntityRenderDispatcher entityRenderDispatcher;

    @Inject(method = "renderEntity", at = @At("TAIL"))
    private void mpsq$renderMovementSensorOutline(Entity entity, double x, double y, double z, float tickDelta,
                                                   MatrixStack matrices, VertexConsumerProvider consumers,
                                                   CallbackInfo ci) {
        if (!MpsqMovementSensorSystem.isActive() || !(entity instanceof AbstractClientPlayerEntity player)
                || player.isSpectator()) return;

        int rgb = MpsqMovementSensorSystem.outlineColor(player);
        OutlineVertexConsumerProvider outline = bufferBuilders.getOutlineVertexConsumers();
        outline.setColor(ColorHelper.getRed(rgb), ColorHelper.getGreen(rgb), ColorHelper.getBlue(rgb), 255);
        matrices.push();
        matrices.scale(1.035F, 1.035F, 1.035F);
        entityRenderDispatcher.render(entity, x / 1.035, y / 1.035, z / 1.035, tickDelta,
                matrices, outline, entityRenderDispatcher.getLight(entity, tickDelta));
        matrices.pop();
    }
}
