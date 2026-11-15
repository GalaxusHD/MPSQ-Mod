package de.galaxushd.mpsqcamera.mixin.client;

import de.galaxushd.mpsqcamera.ScreenCreationManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps Minecraft's main HUD out of the camera view without hiding action messages. */
@Mixin(Hud.class)
public final class InGameHudMixin {
    @Inject(method = "extractHotbarAndDecorations", at = @At("HEAD"), cancellable = true)
    private void mpsq$hideMainHudForCamera(
            net.minecraft.client.gui.GuiGraphicsExtractor context,
            DeltaTracker tickCounter,
            CallbackInfo ci
    ) {
        if (ScreenCreationManager.isCameraViewActive()) {
            ci.cancel();
        }
    }
    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void mpsq$hideCinematicCrosshair(net.minecraft.client.gui.GuiGraphicsExtractor context, DeltaTracker delta, CallbackInfo ci) {
        if(de.galaxushd.mpsqcamera.MpsqCinematicPreview.active()) ci.cancel();
    }
}
