package de.galaxushd.mpsqcamera.mixin.client;

import de.galaxushd.mpsqcamera.ScreenCreationManager;
import de.galaxushd.mpsqcamera.CinemaBrowserManager;
import de.galaxushd.mpsqcamera.MpsqNpcManager;
import de.galaxushd.mpsqcamera.MpsqFurnitureManager;
import de.galaxushd.mpsqcamera.MpsqActionSetupScreen;
import net.minecraft.client.Mouse;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sends mouse input either to the player or to the active client-only camera. */
@Mixin(Mouse.class)
public final class MouseMixin {
    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void mpsq$interactWithNpc(long window, int button, int action, int mods, CallbackInfo ci) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_PRESS
                && MpsqNpcManager.handleLeftClick()) {
            ci.cancel();
            return;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && action == GLFW.GLFW_PRESS
                && (MpsqNpcManager.handleRightClick() || MpsqFurnitureManager.handleRightClick())) {
            ci.cancel();
            return;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && action == GLFW.GLFW_PRESS) {
            de.galaxushd.mpsqcamera.MpsqTriggerManager.onRightClick();
        }
    }

    @Redirect(
            method = "updateMouse",
            at = @At(
                    value = "INVOKE",
                    // Mouse.updateMouse invokes the method with its concrete
                    // ClientPlayerEntity owner, not the inherited Entity owner.
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"
            )
    )
    private void mpsq$routeLookInput(ClientPlayerEntity player, double cursorDeltaX, double cursorDeltaY) {
        // Closing/stopping an MCEF browser can leave one accumulated cursor
        // delta. Ignore that single spike so it cannot snap the view around.
        if (CinemaBrowserManager.discardCursorRestoreSpike(cursorDeltaX, cursorDeltaY)) return;
        // The redstone setup screen owns the mouse for its widgets. Do not also
        // feed that cursor movement into the player/camera look input.
        if (MinecraftClient.getInstance().currentScreen instanceof MpsqActionSetupScreen) return;
        if (ScreenCreationManager.isCameraViewActive()) {
            ScreenCreationManager.applyCameraLook(cursorDeltaX, cursorDeltaY);
            return;
        }
        player.changeLookDirection(cursorDeltaX, cursorDeltaY);
    }
}
