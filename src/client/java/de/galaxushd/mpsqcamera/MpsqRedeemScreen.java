package de.galaxushd.mpsqcamera;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** In-game redeem dialog opened by a registered redeem board/trigger. */
public final class MpsqRedeemScreen extends Screen {
    private EditBox code;
    private String status = "";
    public MpsqRedeemScreen() { super(Component.literal("MPSQ Redeem")); }
    @Override protected void init() {
        int cx = width / 2;
        code = addRenderableWidget(new EditBox(font, cx - 110, height / 2 - 24, 220, 20, Component.literal("Code")));
        addRenderableWidget(Button.builder(Component.literal("Einlösen"), button -> redeem()).bounds(cx - 55, height / 2 + 8, 110, 20).build());
    }
    private void redeem() {
        String value = code.getValue().trim(); if (value.isBlank()) { status = "Code fehlt"; return; }
        status = "Code wird geprüft…";
        MpsqApiClient.redeemCode(value).whenComplete((result, error) -> minecraft.execute(() -> status = error == null ? "Accessoire freigeschaltet" : error.getMessage()));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        extractBackground(context, mouseX, mouseY, delta); context.centeredText(font, title, width / 2, height / 2 - 58, 0xFFFFFFFF);
        if (!status.isBlank()) context.centeredText(font, Component.literal(status), width / 2, height / 2 + 38, 0xFFFFFFFF);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
}
