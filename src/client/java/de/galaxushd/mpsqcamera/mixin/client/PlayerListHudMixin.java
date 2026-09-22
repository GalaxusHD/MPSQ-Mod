package de.galaxushd.mpsqcamera.mixin.client;

import de.galaxushd.mpsqcamera.MpsqNametags;
import de.galaxushd.mpsqcamera.TeamVisibilitySettings;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {
    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void mpsq$replaceTabRank(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        if (TeamVisibilitySettings.visible()) cir.setReturnValue(MpsqNametags.forPlayer(entry.getProfile().getName()));
    }
}
