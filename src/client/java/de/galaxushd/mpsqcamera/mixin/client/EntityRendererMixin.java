package de.galaxushd.mpsqcamera.mixin.client;

import de.galaxushd.mpsqcamera.NametagRenderContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Entfernt ausschließlich hinter MPSQ-Nametags den Vanilla-Textkasten. */
@Mixin(net.minecraft.client.render.entity.EntityRenderer.class)
public abstract class EntityRendererMixin {
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