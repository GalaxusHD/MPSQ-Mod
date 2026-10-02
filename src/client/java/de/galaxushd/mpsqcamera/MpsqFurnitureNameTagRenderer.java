package de.galaxushd.mpsqcamera;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

/** Small, camera-facing display name for configured furniture. */
public final class MpsqFurnitureNameTagRenderer {
    private MpsqFurnitureNameTagRenderer() { }

    public static void draw(WorldRenderContext context, MatrixStack matrices,
                            VertexConsumerProvider consumers, String name,
                            double x, double y, double z) {
        if (name == null || name.isBlank()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        var camera = context.camera().getPos();
        Text text = Text.literal(name);
        matrices.push();
        matrices.translate(x - camera.x, y - camera.y, z - camera.z);
        matrices.multiply(context.camera().getRotation());
        matrices.scale(-0.025f, -0.025f, 0.025f);
        float halfWidth = client.textRenderer.getWidth(text) / 2.0f;
        client.textRenderer.draw(text, -halfWidth, 0.0f, 0xFFFFFFFF, false,
                matrices.peek().getPositionMatrix(), consumers,
                TextRenderer.TextLayerType.SEE_THROUGH, 0x50000000, 0xF000F0);
        matrices.pop();
    }
}
