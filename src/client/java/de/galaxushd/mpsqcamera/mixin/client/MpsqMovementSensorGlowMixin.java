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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the white/red sensor outline as an extra vanilla entity-outline pass. */
@Mixin(WorldRenderer.class)
public abstract class MpsqMovementSensorGlowMixin {
    @Unique private static final ThreadLocal<Boolean> MPSQ_RENDERING_SENSOR_OUTLINE = ThreadLocal.withInitial(() -> false);
    @Shadow @Final private BufferBuilderStorage bufferBuilders;
    @Shadow @Final private EntityRenderDispatcher entityRenderDispatcher;

    @Inject(method = "renderEntity", at = @At("TAIL"))
    private void mpsq$renderMovementSensorOutline(Entity entity, double x, double y, double z, float tickDelta,
                                                   MatrixStack matrices, VertexConsumerProvider consumers,
                                                   CallbackInfo ci) {
        if (MPSQ_RENDERING_SENSOR_OUTLINE.get() || !MpsqMovementSensorSystem.isActive()) return;
        int rgb;
        if (entity instanceof AbstractClientPlayerEntity player) {
            if (player.isSpectator() || !MpsqMovementSensorSystem.shouldOutline(player)) return;
            rgb = MpsqMovementSensorSystem.outlineColor(player);
        } else if (MpsqMovementSensorSystem.isTestVillager(entity)) {
            rgb = MpsqMovementSensorSystem.testVillagerOutlineColor();
        } else return;
        OutlineVertexConsumerProvider outline = bufferBuilders.getOutlineVertexConsumers();
        outline.setColor(ColorHelper.getRed(rgb), ColorHelper.getGreen(rgb), ColorHelper.getBlue(rgb), 255);
        matrices.push();
        matrices.scale(1.035F, 1.035F, 1.035F);
        MPSQ_RENDERING_SENSOR_OUTLINE.set(true);
        try {
            entityRenderDispatcher.render(entity, x / 1.035, y / 1.035, z / 1.035, tickDelta,
                    matrices, outline, entityRenderDispatcher.getLight(entity, tickDelta));
        } finally {
            MPSQ_RENDERING_SENSOR_OUTLINE.set(false);
            matrices.pop();
        }
    }
}
