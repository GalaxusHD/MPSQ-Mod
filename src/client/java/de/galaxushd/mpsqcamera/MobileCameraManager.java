package de.galaxushd.mpsqcamera;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

import java.util.List;
import java.util.UUID;

/** Turns a vanilla clock into the player-bound MPSQ camera screen. */
public final class MobileCameraManager {
    private MobileCameraManager() { }

    public static void initialize() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (hand != Hand.MAIN_HAND || !player.getStackInHand(hand).isOf(Items.CLOCK)) {
                return ActionResult.PASS;
            }
            if (!world.isClient || !TeamVisibilitySettings.visible()) return ActionResult.PASS;

            List<UUID> linked = MobileCameraLinkStore.linkedCameras();
            if (player.isSneaking() || linked.isEmpty()) {
                net.minecraft.client.MinecraftClient.getInstance().setScreen(new MobileCameraLinkScreen());
                return ActionResult.SUCCESS;
            }
            if (!ScreenCreationManager.enterMobileView(linked)) {
                player.sendMessage(Text.translatable("status.mpsqcamera.mobile.offline"), true);
            }
            return ActionResult.SUCCESS;
        });
    }
}
