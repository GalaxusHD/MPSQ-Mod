package de.galaxushd.mpsqcamera;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** In-game redeem dialog opened by a registered redeem board/trigger. */
public final class MpsqRedeemScreen extends Screen {
    private TextFieldWidget code;
    private String status = "";
    private ButtonWidget redeemButton;
    private boolean pending;
    public MpsqRedeemScreen() { super(Text.literal("MPSQ Redeem")); }
    @Override protected void init() {
        int cx = width / 2;
        code = addDrawableChild(new TextFieldWidget(textRenderer, cx - 110, height / 2 - 24, 220, 20, Text.literal("Code")));
        redeemButton = addDrawableChild(ButtonWidget.builder(Text.literal("Einlösen"), button -> redeem()).dimensions(cx - 55, height / 2 + 8, 110, 20).build());
        code.setMaxLength(64);
        code.setChangedListener(value -> { if (redeemButton != null) redeemButton.active = !pending && !value.isBlank(); });
        redeemButton.active = false;
    }
    private void redeem() {
        if (pending || client == null || client.player == null) return;
        String value = code.getText().trim(); if (value.isBlank()) { status = "Code fehlt."; return; }
        pending = true; status = "Code wird geprüft…"; if (redeemButton != null) redeemButton.active = false;
        try {
            MpsqApiClient.redeemCode(value).whenComplete((result, error) -> {
                var game = net.minecraft.client.MinecraftClient.getInstance();
                if (game == null) return;
                game.execute(() -> {
                    if (game.currentScreen != this) return;
                    pending = false;
                    if (error == null) { status = "Accessoire freigeschaltet."; code.setText(""); }
                    else status = readableError(error);
                    if (redeemButton != null) redeemButton.active = !code.getText().isBlank();
                });
            });
        } catch (RuntimeException error) {
            pending = false; status = readableError(error);
            if (redeemButton != null) redeemButton.active = !code.getText().isBlank();
        }
    }
    private static String readableError(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        String message = cause.getMessage();
        if (message == null || message.isBlank()) return "Einlösen fehlgeschlagen. Bitte Verbindung und Code prüfen.";
        return message.length() > 100 ? message.substring(0, 97) + "…" : message;
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta); context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 58, 0xFFFFFFFF);
        if (!status.isBlank()) context.drawCenteredTextWithShadow(textRenderer, Text.literal(status), width / 2, height / 2 + 38, 0xFFFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }
}
