package de.galaxushd.mpsqcamera.mixin.client;
import de.galaxushd.mpsqcamera.MpsqAccessoryRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** 26.3 detects submitted outlines itself; include locally rendered NPCs. */
@Mixin(LevelRenderer.class)
abstract class MpsqNpcGlowPostprocessorMixin {
    @Shadow @Final private LevelRenderState levelRenderState;
    @Inject(method = "render", at = @At("HEAD"))
    private void mpsq$includeNpcOutlines(CallbackInfo ci) {
        if (MpsqAccessoryRenderer.hasGlowingNpcs()) levelRenderState.shouldShowEntityOutlines = true;
    }
}
