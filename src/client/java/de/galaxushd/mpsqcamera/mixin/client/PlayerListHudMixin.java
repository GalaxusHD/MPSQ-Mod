package de.galaxushd.mpsqcamera.mixin.client;

import de.galaxushd.mpsqcamera.MpsqNametags;
import de.galaxushd.mpsqcamera.TeamVisibilitySettings;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerListHudMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void mpsq$replaceTabRank(PlayerInfo entry, CallbackInfoReturnable<Component> cir) {
        if (TeamVisibilitySettings.inArea()) cir.setReturnValue(MpsqNametags.forPlayer(entry.getProfile().name()));
    }
}
