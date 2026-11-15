package de.galaxushd.mpsqcamera;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Confirmation for a request received from another MPSQ user. */
public final class BodycamRequestScreen extends Screen {
    private final String requestId;
    private final String requesterName;

    public BodycamRequestScreen(String requestId, String requesterName) {
        super(Component.literal("Bodycam-Anfrage"));
        this.requestId = requestId;
        this.requesterName = requesterName;
    }

    @Override protected void init() {
        int x = width / 2 - 110;
        int y = height / 2 + 12;
        addRenderableWidget(Button.builder(Component.literal("Akzeptieren"), button -> respond(true)).bounds(x, y, 106, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Ablehnen"), button -> respond(false)).bounds(x + 114, y, 106, 20).build());
    }

    private void respond(boolean accepted) { if(accepted&&!TeamVisibilitySettings.inArea()){onClose();return;}
        JsonObject body = new JsonObject();
        body.addProperty("accepted", accepted);
        MpsqApiClient.post("/bodycam-requests/" + requestId + "/respond", body)
                .thenCompose(ignored -> MpsqApiClient.refreshCameras())
                .whenComplete((ignored, error) -> minecraft.execute(this::onClose));
    }

    @Override public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(font, title, width / 2, height / 2 - 42, MpsqTheme.TEXT_TITEL);
        context.centeredText(font, Component.literal(requesterName + " möchte deine Perspektive verwenden."), width / 2, height / 2 - 16, MpsqTheme.TEXT_NORMAL);
    }
    @Override public void extractBackground(GuiGraphicsExtractor c, int x, int y, float d) { super.extractBackground(c, x, y, d); MpsqTheme.drawBackground(c, width, height); }
    @Override public boolean isPauseScreen() { return false; }
}
