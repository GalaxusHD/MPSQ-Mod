package de.galaxushd.mpsqcamera;

import com.mojang.blaze3d.vertex.PoseStack;
import de.galaxushd.mpsqcamera.compat.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import de.galaxushd.mpsqcamera.compat.MultiBufferSource;
import net.minecraft.network.chat.Component;

/** Small, camera-facing display name for configured furniture. */
public final class MpsqFurnitureNameTagRenderer {
    private MpsqFurnitureNameTagRenderer() { }

    public static void draw(WorldRenderContext context, PoseStack matrices,
                            MultiBufferSource consumers, String name,
                            double x, double y, double z) {
        if (name == null || name.isBlank()) return;
        Minecraft client = Minecraft.getInstance();
        var camera = context.camera().getPos();
        Component text = Component.literal(name);
        matrices.pushPose();
        matrices.translate(x - camera.x, y - camera.y, z - camera.z);
        matrices.rotate(context.camera().getRotation());
        matrices.scale(-0.025f, -0.025f, 0.025f);
        float halfWidth = client.font.width(text) / 2.0f;
        consumers.collector().submitText(matrices, -halfWidth, 0.0f, text.getVisualOrderText(), false,
                Font.DisplayMode.SEE_THROUGH, 0xF000F0, 0xFFFFFFFF, 0x50000000, 0);
        matrices.popPose();
    }
}
