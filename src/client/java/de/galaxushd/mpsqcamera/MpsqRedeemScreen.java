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
    public MpsqRedeemScreen() { super(Text.literal("MPSQ Redeem")); }
    @Override protected void init() {
        int cx = width / 2;
        code = addDrawableChild(new TextFieldWidget(textRenderer, cx - 110, height / 2 - 24, 220, 20, Text.literal("Code")));
        addDrawableChild(ButtonWidget.builder(Text.literal("Einlösen"), button -> redeem()).dimensions(cx - 55, height / 2 + 8, 110, 20).build());
    }
    private void redeem() {
        String value = code.getText().trim(); if (value.isBlank()) { status = "Code fehlt"; return; }
        status = "Code wird geprüft…";
        MpsqApiClient.redeemCode(value).whenComplete((result, error) -> client.execute(() -> status = error == null ? "Accessoire freigeschaltet" : error.getMessage()));
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta); context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 58, 0xFFFFFFFF);
        if (!status.isBlank()) context.drawCenteredTextWithShadow(textRenderer, Text.literal(status), width / 2, height / 2 + 38, 0xFFFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }
}
