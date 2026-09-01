package de.galaxushd.mpsqcamera.mixin.client;

import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatInputSuggestor.class)
public abstract class ChatInputSuggestorMixin {
    private static final int COMMAND_TEXT_COLOR = 0xE0E0E0;
    private static final int MPSQ_TEXT_COLOR = 0x55FFFF;

    @Inject(method = "provideRenderText", at = @At("HEAD"), cancellable = true)
    private void mpsqteam$renderMpsqCommand(String text, int firstCharacterIndex,
                                            CallbackInfoReturnable<OrderedText> cir) {
        if (text.regionMatches(true, 0, "/mpsq ", 0, 6)) {
            OrderedText command = OrderedText.styledForwardsVisitedString(
                    text.substring(0, 6), Style.EMPTY.withColor(COMMAND_TEXT_COLOR));
            OrderedText message = OrderedText.styledForwardsVisitedString(
                    text.substring(6), Style.EMPTY.withColor(MPSQ_TEXT_COLOR));
            cir.setReturnValue(OrderedText.concat(command, message));
        }
    }
}
