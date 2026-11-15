package de.galaxushd.mpsqcamera;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Screen zum Erstellen einer neuen Gruppe.
 * Zeigt alle vorhandenen Bildschirme zur Auswahl an.
 * Ausgewählte Bildschirme erhalten einen gemeinsamen Code.
 */
public class GroupCreationScreen extends Screen {

    private final Screen parent;
    private final List<LocalScreenStore.LocalScreenData> allScreens;
    private final List<UUID> selectedScreenIds = new ArrayList<>();
    private int scrollOffset = 0;

    private static final int ROW_HEIGHT = 20;
    private static final int LIST_TOP_OFFSET = 60;
    private static final int LIST_BOTTOM_MARGIN = 60;

    public GroupCreationScreen(Screen parent) {
        super(Component.literal("Neue Gruppe erstellen"));
        this.parent = parent;
        this.allScreens = LocalScreenStore.getAllScreens();
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int bottomY = this.height - 28;

        // Gruppe erstellen
        addRenderableWidget(Button.builder(
                Component.literal("Gruppe erstellen"),
                b -> createGroup()
        ).bounds(cx - 90, bottomY, 86, 20).build());

        // Abbrechen
        addRenderableWidget(Button.builder(
                Component.literal("Abbrechen"),
                b -> this.minecraft.gui.setScreen(parent)
        ).bounds(cx + 4, bottomY, 86, 20).build());
    }

    private void createGroup() {
        if (selectedScreenIds.size() < 2) {
            MpsqCameraClient.LOGGER.info("[MPSQ] Mindestens 2 Bildschirme für eine Gruppe auswählen.");
            return;
        }
        JsonArray ids = new JsonArray();
        for (UUID id : selectedScreenIds) ids.add(id.toString());
        JsonObject body = new JsonObject();
        body.add("screenIds", ids);
        MpsqApiClient.post("/groups", body).thenCompose(result -> ScreenSyncManager.refresh()).thenRun(() ->
                this.minecraft.execute(() -> this.minecraft.gui.setScreen(parent))
        ).exceptionally(error -> { MpsqCameraClient.LOGGER.warn("Gruppe konnte nicht erstellt werden", error); return null; });
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        MpsqTheme.drawBackground(context, this.width, this.height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        int cx = this.width / 2;
        int y = 20;

        // Titel
        context.centeredText(this.font, this.title, cx, y, MpsqTheme.TEXT_TITEL);
        y += 14;

        // Trennlinie
        context.fill(cx - 130, y, cx + 130, y + 1, 0x44FFFFFF);
        y += 6;

        // Hinweis: Mindestens 2
        String hint = selectedScreenIds.size() < 2
                ? "Wähle mindestens 2 Bildschirme aus (" + selectedScreenIds.size() + " ausgewählt)"
                : selectedScreenIds.size() + " Bildschirme ausgewählt";
        int hintColor = selectedScreenIds.size() < 2 ? MpsqTheme.TEXT_GEDAEMPT : MpsqTheme.TEXT_TITEL;
        context.centeredText(this.font, Component.literal(hint), cx, y, hintColor);

        if (allScreens.isEmpty()) {
            context.centeredText(
                    this.font,
                    Component.literal("Keine Bildschirme vorhanden."),
                    cx, LIST_TOP_OFFSET + 20, MpsqTheme.TEXT_GEDAEMPT);
            return;
        }

        // Bildschirm-Liste mit Auswahl
        int listBottom = this.height - LIST_BOTTOM_MARGIN;
        int listY = LIST_TOP_OFFSET - scrollOffset;

        for (LocalScreenStore.LocalScreenData screen : allScreens) {
            if (listY + ROW_HEIGHT < LIST_TOP_OFFSET || listY > listBottom) {
                listY += ROW_HEIGHT + 2;
                continue;
            }

            boolean selected = selectedScreenIds.contains(screen.id());

            // Zeilen-Hintergrund: ausgewählt = leicht rot, sonst dunkel
            int bgColor = selected ? 0x55AA0000 : 0x33000000;
            context.fill(cx - 130, listY - 1, cx + 130, listY + ROW_HEIGHT - 1, bgColor);

            // Rahmen wenn ausgewählt
            if (selected) {
                context.fill(cx - 130, listY - 1, cx - 129, listY + ROW_HEIGHT - 1, MpsqTheme.ROT);
                context.fill(cx + 129, listY - 1, cx + 130, listY + ROW_HEIGHT - 1, MpsqTheme.ROT);
            }

            // Checkbox-Symbol
            String checkbox = selected ? "[✓] " : "[ ] ";
            context.text(
                    this.font, Component.literal(checkbox + screen.name()),
                    cx - 125, listY + 4,
                    selected ? MpsqTheme.TEXT_TITEL : MpsqTheme.TEXT_NORMAL);

            // Gruppen-Status
            if (screen.groupId() != null) {
                context.text(
                        this.font, Component.literal("(Gruppe)"),
                        cx + 70, listY + 4, MpsqTheme.TEXT_GEDAEMPT);
            }

            listY += ROW_HEIGHT + 2;
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();

        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            int cx = this.width / 2;
            int listBottom = this.height - LIST_BOTTOM_MARGIN;
            int listY = LIST_TOP_OFFSET - scrollOffset;

            for (LocalScreenStore.LocalScreenData screen : allScreens) {
                boolean rowVisible = (listY + ROW_HEIGHT >= LIST_TOP_OFFSET) && (listY <= listBottom);
                if (rowVisible
                        && mouseY >= listY - 1 && mouseY < listY + ROW_HEIGHT - 1
                        && mouseX >= cx - 130 && mouseX <= cx + 130) {
                    toggleSelection(screen.id());
                    return true;
                }
                listY += ROW_HEIGHT + 2;
            }
        }
        return super.mouseClicked(event, doubled);
    }

    private void toggleSelection(UUID screenId) {
        if (selectedScreenIds.contains(screenId)) {
            selectedScreenIds.remove(screenId);
        } else {
            selectedScreenIds.add(screenId);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset -= (int)(verticalAmount * 16);
        int maxScroll = Math.max(0, allScreens.size() * (ROW_HEIGHT + 2) - (this.height - LIST_TOP_OFFSET - LIST_BOTTOM_MARGIN));
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
