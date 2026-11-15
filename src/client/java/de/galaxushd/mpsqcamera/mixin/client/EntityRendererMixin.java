package de.galaxushd.mpsqcamera.mixin.client;
import de.galaxushd.mpsqcamera.NametagRenderContext;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.state.OptionsRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
/** Preserve transparent MPSQ nametags in the 26.3 submission pipeline. */
@Mixin(SubmitNodeCollection.class)
public abstract class EntityRendererMixin {
    @Redirect(method = "submitNameTag", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/state/OptionsRenderState;getBackgroundOpacity(F)F"))
    private float mpsq$transparentNametagBackground(OptionsRenderState options, float fallback) {
        return NametagRenderContext.active() ? 0.0f : options.getBackgroundOpacity(fallback);
    }
}
