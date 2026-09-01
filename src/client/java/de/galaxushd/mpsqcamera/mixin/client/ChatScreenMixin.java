package de.galaxushd.mpsqcamera.mixin.client;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the field visible; per-part colors are supplied by ChatInputSuggestorMixin. */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    private static final int DEFAULT_TEXT_COLOR = 0xFFE0E0E0;

    @Shadow
    protected TextFieldWidget chatField;

    @Inject(method = "onChatFieldUpdate", at = @At("TAIL"))
    private void mpsqteam$colorMpsqInput(String value, CallbackInfo ci) {
        chatField.setEditableColor(DEFAULT_TEXT_COLOR);
    }
}
