package de.galaxushd.mpsqcamera.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Invokes Minecraft's normal world renderer for the private camera framebuffer. */
@Mixin(GameRenderer.class)
public interface GameRendererInvoker {
    @Invoker("renderLevel")
    void mpsq$renderWorld(DeltaTracker tickCounter);
}
