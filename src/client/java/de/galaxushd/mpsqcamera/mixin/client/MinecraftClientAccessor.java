package de.galaxushd.mpsqcamera.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Access to the render target used only during an off-screen camera pass. */
@Mixin(Minecraft.class)
public interface MinecraftClientAccessor {
    @Accessor("mainRenderTarget")
    @Mutable
    void mpsq$setFramebuffer(RenderTarget framebuffer);

    @Accessor("gameRenderer")
    GameRenderer mpsq$getGameRenderer();
}
