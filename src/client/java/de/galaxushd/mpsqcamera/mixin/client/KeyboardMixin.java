package de.galaxushd.mpsqcamera.mixin.client;

import de.galaxushd.mpsqcamera.ScreenCreationManager;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps Minecraft's pause menu from opening when ESC ends a camera view. */
@Mixin(KeyboardHandler.class)
public final class KeyboardMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void mpsq$consumeEscapeForCamera(long window, int action, net.minecraft.client.input.KeyEvent event, CallbackInfo ci) {
 int key=event.key();
 if(de.galaxushd.mpsqcamera.MpsqChess.key(key,action)){ci.cancel();return;}
 if(de.galaxushd.mpsqcamera.MpsqMotionCapture.cancelKey(key,action)){ci.cancel();return;}
 if(de.galaxushd.mpsqcamera.MpsqScenesTool.key(key,action)){ci.cancel();return;}
 if(action==com.mojang.blaze3d.platform.InputConstants.PRESS) de.galaxushd.mpsqcamera.MpsqAccessoryRenderer.onPreviewKey(key);
        if (key != com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE || !ScreenCreationManager.isCameraViewActive()) {
            return;
        }

        // Preserve ESC for the chat and every other open vanilla screen. The
        // camera manager is notified so it does not interpret this same press
        // as its leave-camera key.
        if (Minecraft.getInstance().gui.screen() != null) {
            if (action == com.mojang.blaze3d.platform.InputConstants.PRESS) {
                ScreenCreationManager.ignoreEscapeForOpenScreen();
            }
            return;
        }

        // With no UI open, ESC leaves the camera but must not open Minecraft's
        // pause menu in the same key press.
        if (action == com.mojang.blaze3d.platform.InputConstants.PRESS || action == com.mojang.blaze3d.platform.InputConstants.REPEAT) {
            ci.cancel();
        }
    }
}
