package de.galaxushd.mpsqcamera;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Owner view for access granted through a screen's activation code. */
public final class BildschirmPermissionsScreen extends Screen {
    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_GAP = 26;
    private static final int MEMBER_START_Y = 132;

    private final Screen parent;
    private final UUID screenId;
    private final String activationCode;
    private final List<Member> members = new ArrayList<>();
    private String status = "Lade Berechtigungen...";
    private boolean membersLoaded;
    private int memberScrollOffset;
    private int maxMemberScrollOffset;
    private EditBox playerNameField;
    private Button grantButton;

    public BildschirmPermissionsScreen(Screen parent, UUID screenId, String activationCode) {
        super(Component.literal("Berechtigungen"));
        this.parent = parent;
        this.screenId = screenId;
        this.activationCode = activationCode;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int x = centerX - BUTTON_WIDTH / 2;
        addRenderableWidget(Button.builder(Component.literal("Code kopieren: " + activationCode), button -> {
                    minecraft.keyboardHandler.setClipboard(activationCode);
                    status = "Code kopiert. Teile ihn nur mit berechtigten Spielern.";
                })
                .bounds(x, 54, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        playerNameField = new EditBox(font, x, 84, 148, BUTTON_HEIGHT, Component.literal("Minecraft-Name"));
        playerNameField.setMaxLength(32);
        playerNameField.setHint(Component.literal("Spielername"));
        addRenderableWidget(playerNameField);
        grantButton = addRenderableWidget(Button.builder(Component.literal("Freigeben"), button -> grantPlayer())
                .bounds(x + 152, 84, 68, BUTTON_HEIGHT).build());
        grantButton.active = false;
        playerNameField.setResponder(value -> grantButton.active = !value.trim().isEmpty());
        addRenderableWidget(Button.builder(Component.literal("Aktualisieren"), button -> loadMembers())
                .bounds(x, height - 54, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.mpsqcamera.back"), button -> minecraft.gui.setScreen(parent))
                .bounds(width / 2 - 75, height - 28, 150, BUTTON_HEIGHT)
                .build());
        int memberY = MEMBER_START_Y - memberScrollOffset;
        for (Member member : members) {
            if (memberY >= MEMBER_START_Y && memberY <= height - 92) {
                addRenderableWidget(Button.builder(Component.literal("Entfernen"), button -> remove(member))
                        .bounds(centerX + BUTTON_WIDTH / 2 - 74, memberY, 74, BUTTON_HEIGHT).build());
            }
            memberY += ROW_GAP;
        }
        maxMemberScrollOffset = Math.max(0, members.size() * ROW_GAP - (height - 92 - MEMBER_START_Y + ROW_GAP));
        memberScrollOffset = Math.min(memberScrollOffset, maxMemberScrollOffset);
        if (!membersLoaded) loadMembers();
    }

    private void loadMembers() {
        membersLoaded = false;
        status = "Lade Berechtigungen...";
        MpsqApiClient.get("/screens/" + screenId + "/members")
                .whenComplete((response, error) -> minecraft.execute(() -> {
                    members.clear();
                    if (error != null) {
                        Throwable cause = error.getCause() == null ? error : error.getCause();
                        status = "Berechtigungen: " + (cause.getMessage() == null ? "Serverfehler" : cause.getMessage());
                    } else {
                        readMembers(response);
                        status = members.isEmpty() ? "Noch keine weiteren Spieler berechtigt." : "";
                    }
                    membersLoaded = true;
                    rebuildWidgets();
                }));
    }

    private void readMembers(JsonElement response) {
        if (response == null || !response.isJsonArray()) return;
        for (JsonElement element : response.getAsJsonArray()) {
            JsonObject row = element.getAsJsonObject();
            if (!row.has("client_id")) continue;
            String name = "Unbekannter Spieler";
            if (row.has("mpsq_clients") && row.get("mpsq_clients").isJsonObject()) {
                JsonObject client = row.getAsJsonObject("mpsq_clients");
                if (client.has("display_name") && !client.get("display_name").getAsString().isBlank()) {
                    name = client.get("display_name").getAsString();
                }
            }
            members.add(new Member(UUID.fromString(row.get("client_id").getAsString()), name));
        }
    }

    private void grantPlayer() {
        String playerName = playerNameField.getValue().trim();
        if (playerName.isEmpty()) return;

        JsonObject body = new JsonObject();
        body.addProperty("displayName", playerName);
        grantButton.active = false;
        MpsqApiClient.post("/screens/" + screenId + "/members", body)
                .whenComplete((ignored, error) -> minecraft.execute(() -> {
                    if (error != null) {
                        Throwable cause = error.getCause() == null ? error : error.getCause();
                        status = "Freigabe fehlgeschlagen: " + (cause.getMessage() == null ? "Spieler nicht gefunden" : cause.getMessage());
                        grantButton.active = !playerNameField.getValue().trim().isEmpty();
                    } else {
                        status = playerName + " wurde freigegeben.";
                        playerNameField.setValue("");
                        loadMembers();
                    }
                }));
    }

    private void remove(Member member) {
        minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            if (!confirmed) {
                minecraft.gui.setScreen(this);
                return;
            }
            MpsqApiClient.delete("/screens/" + screenId + "/members/" + member.clientId)
                    .whenComplete((ignored, error) -> minecraft.execute(() -> {
                        status = error == null ? member.name + " entfernt." : "Spieler konnte nicht entfernt werden.";
                        loadMembers();
                    }));
        }, Component.literal("Berechtigung entfernen"), Component.literal(member.name + " den Zugriff entziehen?"),
                Component.literal("Entfernen"), Component.literal("Abbrechen")));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        int centerX = width / 2;
        context.centeredText(font, title, centerX, 28, MpsqTheme.TEXT_TITEL);
        context.centeredText(font, Component.literal("Freigabe per Name oder Aktivierungscode."), centerX, 112, MpsqTheme.TEXT_GEDAEMPT);
        if (!status.isBlank()) context.centeredText(font, Component.literal(status), centerX, 122, MpsqTheme.TEXT_GEDAEMPT);

        int y = MEMBER_START_Y - memberScrollOffset;
        for (Member member : members) {
            if (y >= MEMBER_START_Y && y <= height - 92) {
                context.text(font, Component.literal(member.name), centerX - BUTTON_WIDTH / 2, y + 6, MpsqTheme.TEXT_NORMAL);
            }
            y += ROW_GAP;
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        MpsqTheme.drawBackground(context, width, height);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maxMemberScrollOffset <= 0) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        int oldScroll = memberScrollOffset;
        memberScrollOffset = Math.max(0, Math.min(maxMemberScrollOffset,
                memberScrollOffset - (int) (verticalAmount * ROW_GAP)));
        if (oldScroll != memberScrollOffset) rebuildWidgets();
        return true;
    }

    private record Member(UUID clientId, String name) { }
}
