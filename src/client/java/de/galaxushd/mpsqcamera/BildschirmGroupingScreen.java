package de.galaxushd.mpsqcamera;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Übersicht aller Bildschirm-Gruppen.
 * - Erstelle neue Gruppen über den GroupCreationScreen
 * - Zeigt bestehende Gruppen mit Code und Anzahl Bildschirme
 */
public class BildschirmGroupingScreen extends Screen {

    private final Screen parent;
    private int scrollOffset = 0;

    public BildschirmGroupingScreen(Screen parent) {
        super(Component.literal("Bildschirme Gruppieren"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Neue Gruppe erstellen → öffnet GroupCreationScreen
        addRenderableWidget(Button.builder(
                Component.literal("Neue Gruppe"),
                b -> this.minecraft.gui.setScreen(new GroupCreationScreen(this))
        ).bounds(this.width / 2 - 70, 40, 140, 20).build());

        // Zurück-Button
        addRenderableWidget(Button.builder(
                Component.literal("Zurück"),
                b -> this.minecraft.gui.setScreen(parent)
        ).bounds(this.width / 2 - 75, this.height - 28, 150, 20).build());
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
        int startY = 28;

        context.centeredText(
                this.font, this.title, cx, startY, MpsqTheme.TEXT_TITEL);
        startY += 16;

        context.fill(cx - 130, startY, cx + 130, startY + 1, 0x44FFFFFF);
        startY += 16;

        List<LocalScreenStore.LocalGroupData> groups = LocalScreenStore.getAllGroups();

        if (groups.isEmpty()) {
            context.centeredText(
                    this.font,
                    Component.literal("Keine Gruppen vorhanden."),
                    cx, startY + 10, MpsqTheme.TEXT_GEDAEMPT);
            return;
        }

        // Gruppen-Liste
        int lineY = startY - scrollOffset;
        int listBottom = this.height - 50;

        for (LocalScreenStore.LocalGroupData group : groups) {
            if (lineY < startY || lineY > listBottom) {
                lineY += 18;
                continue;
            }
            List<LocalScreenStore.LocalScreenData> screens = LocalScreenStore.getScreensInGroup(group.id());
            String label = "Code: " + group.sharedCode() + "  (" + screens.size() + " Bildschirme)";
            context.centeredText(
                    this.font, Component.literal(label), cx, lineY, MpsqTheme.TEXT_NORMAL);
            lineY += 18;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset -= (int)(verticalAmount * 16);
        scrollOffset = Math.max(0, scrollOffset);
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
