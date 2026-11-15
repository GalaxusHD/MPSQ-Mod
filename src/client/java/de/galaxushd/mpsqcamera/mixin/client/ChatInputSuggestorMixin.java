package de.galaxushd.mpsqcamera.mixin.client;

import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CommandSuggestions.class)
public abstract class ChatInputSuggestorMixin {
    private static final int COMMAND_TEXT_COLOR = 0xE0E0E0;
    private static final int MPSQ_TEXT_COLOR = 0x55FFFF;

    @Inject(method = "formatChat", at = @At("HEAD"), cancellable = true)
    private void mpsqteam$renderMpsqCommand(String text, int firstCharacterIndex,
                                            CallbackInfoReturnable<FormattedCharSequence> cir) {
        if (text.regionMatches(true, 0, "/mpsq ", 0, 6)) {
            FormattedCharSequence command = FormattedCharSequence.forward(
                    text.substring(0, 6), Style.EMPTY.withColor(COMMAND_TEXT_COLOR));
            FormattedCharSequence message = FormattedCharSequence.forward(
                    text.substring(6), Style.EMPTY.withColor(MPSQ_TEXT_COLOR));
            cir.setReturnValue(FormattedCharSequence.composite(command, message));
        }
    }
}
