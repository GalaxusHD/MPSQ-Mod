package de.galaxushd.mpsqcamera.mixin.client;

import de.galaxushd.mpsqcamera.ScreenCreationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import de.galaxushd.mpsqcamera.CinemaBrowserManager;
import de.galaxushd.mpsqcamera.MpsqNpcManager;
import de.galaxushd.mpsqcamera.MpsqFurnitureManager;
import de.galaxushd.mpsqcamera.MpsqActionSetupScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sends mouse input either to the player or to the active client-only camera. */
@Mixin(MouseHandler.class)
public final class MouseMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void mpsq$interactWithNpc(long window, net.minecraft.client.input.MouseButtonInfo info, int action, CallbackInfo ci) {
 int button=info.button();
        if(de.galaxushd.mpsqcamera.MpsqChess.mouse(button,action)){ci.cancel();return;}
        if(de.galaxushd.mpsqcamera.MpsqScenesTool.mouse(button,action)){ci.cancel();return;}
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && action == com.mojang.blaze3d.platform.InputConstants.PRESS
                && MpsqNpcManager.handleLeftClick()) {
            ci.cancel();
            return;
        }
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT && action == com.mojang.blaze3d.platform.InputConstants.PRESS
                && (de.galaxushd.mpsqcamera.MpsqCameraInteraction.handleRightClick() || de.galaxushd.mpsqcamera.ScreenRenderer.handleRightClick() || de.galaxushd.mpsqcamera.MpsqTvFurniture.handleRightClick() || MpsqNpcManager.handleRightClick() || MpsqFurnitureManager.handleRightClick())) {
            ci.cancel();
            return;
        }
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT && action == com.mojang.blaze3d.platform.InputConstants.PRESS) {
            de.galaxushd.mpsqcamera.MpsqTriggerManager.onRightClick();
        }
    }

    @Inject(method="onScroll",at=@At("HEAD"),cancellable=true)
    private void mpsq$scenesScroll(long window,double x,double y,CallbackInfo ci){if(de.galaxushd.mpsqcamera.MpsqScenesTool.scroll(y))ci.cancel();}
    @Redirect(
            method = "turnPlayer",
            at = @At(
                    value = "INVOKE",
                    // Mouse.updateMouse invokes the method with its concrete
                    // ClientPlayerEntity owner, not the inherited Entity owner.
                    target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"
            )
    )
    private void mpsq$routeLookInput(LocalPlayer player, double cursorDeltaX, double cursorDeltaY) {
        // Closing/stopping an MCEF browser can leave one accumulated cursor
        // delta. Ignore that single spike so it cannot snap the view around.
        if (CinemaBrowserManager.discardCursorRestoreSpike(cursorDeltaX, cursorDeltaY)) return;
        // The redstone setup screen owns the mouse for its widgets. Do not also
        // feed that cursor movement into the player/camera look input.
        if (Minecraft.getInstance().gui.screen() instanceof MpsqActionSetupScreen) return;
        if (ScreenCreationManager.isCameraViewActive()) {
            ScreenCreationManager.applyCameraLook(cursorDeltaX, cursorDeltaY);
            return;
        }
        player.turn(cursorDeltaX, cursorDeltaY);
    }
}
