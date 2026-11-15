package de.galaxushd.mpsqcamera;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import java.util.List;
import java.util.UUID;

/** Turns a vanilla clock into the player-bound MPSQ camera screen. */
public final class MobileCameraManager {
    private MobileCameraManager() { }

    public static void initialize() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (hand != InteractionHand.MAIN_HAND || !player.getItemInHand(hand).is(Items.CLOCK)) {
                return InteractionResult.PASS;
            }
            if (!world.isClientSide() || !TeamVisibilitySettings.inArea()) return InteractionResult.PASS;

            List<UUID> linked = MobileCameraLinkStore.linkedCameras();
            if (player.isShiftKeyDown() || linked.isEmpty()) {
                net.minecraft.client.Minecraft.getInstance().gui.setScreen(new MobileCameraLinkScreen());
                return InteractionResult.SUCCESS;
            }
            if (!ScreenCreationManager.enterMobileView(linked)) {
                de.galaxushd.mpsqcamera.compat.PlayerMessages.send(player, Component.translatable("status.mpsqcamera.mobile.offline"), true);
            }
            return InteractionResult.SUCCESS;
        });
    }
}
