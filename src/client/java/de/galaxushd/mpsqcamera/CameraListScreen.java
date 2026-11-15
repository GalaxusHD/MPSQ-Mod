package de.galaxushd.mpsqcamera;

import com.google.gson.JsonObject;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Scrollable camera list. Rows deliberately use the same MPSQ rendering as screen rows. */
public final class CameraListScreen extends Screen {
    private static final int LIST_TOP = 82;
    private static final int LIST_BOTTOM_MARGIN = 62;
    private static final int ROW_HEIGHT = 22;
    private static final int ROW_SPACING = 3;
    private final Screen parent;
    private final Set<UUID> selected = new LinkedHashSet<>();
    private int scrollOffset;

    public CameraListScreen(Screen parent) {
        super(Component.translatable("gui.mpsqcamera.cameras.title"));
        this.parent = parent;
    }

    @Override protected void init() {
        // A bodycam may have been accepted while this client was not in the
        // menu. Refresh once whenever the list opens so it appears immediately.
        MpsqApiClient.refreshCameras();
        addRenderableWidget(Button.builder(Component.translatable("gui.mpsqcamera.back"), b -> minecraft.gui.setScreen(parent))
                .bounds(width / 2 - 75, height - 36, 150, 20).build());
        clampScroll();
    }

    @Override public void extractBackground(GuiGraphicsExtractor c, int x, int y, float d) { super.extractBackground(c, x, y, d); MpsqTheme.drawBackground(c, width, height); }

    @Override public void extractRenderState(GuiGraphicsExtractor c, int mouseX, int mouseY, float d) {
        super.extractRenderState(c, mouseX, mouseY, d);
        int cx = width / 2;
        c.centeredText(font, title, cx, 28, MpsqTheme.TEXT_TITEL);
        c.fill(cx - 140, 44, cx + 140, 45, 0x44FFFFFF);
        c.centeredText(font, Component.literal("Klick: verwalten   |   Rechtsklick: auswählen"), cx, 58, MpsqTheme.TEXT_GEDAEMPT);
        if (!selected.isEmpty()) {
            c.fill(cx - 140, 66, cx + 140, 78, 0x55000000);
            c.centeredText(font, Component.literal(selected.size() + " ausgewählt – Entf zum Löschen"), cx, 68, MpsqTheme.TEXT_GEDAEMPT);
        }
        List<LocalCameraStore.CameraData> cameras = LocalCameraStore.getAll();
        if (cameras.isEmpty()) { c.centeredText(font, Component.translatable("gui.mpsqcamera.cameras.empty"), cx, LIST_TOP + 20, MpsqTheme.TEXT_GEDAEMPT); return; }
        int bottom = height - LIST_BOTTOM_MARGIN;
        int y = LIST_TOP - scrollOffset;
        for (LocalCameraStore.CameraData camera : cameras) {
            if (y + ROW_HEIGHT > LIST_TOP && y < bottom) {
                boolean marked = selected.contains(camera.id());
                c.fill(cx - 140, y, cx + 140, y + ROW_HEIGHT, marked ? 0x66557A9B : 0x55000000);
                c.text(font, Component.literal(marked ? "[x]" : "[ ]"), cx - 134, y + 7, MpsqTheme.TEXT_NORMAL);
                // The bodycam name already says whose bodycam it is. Adding
                // " - Bodycam" a second time wastes space and looks duplicated.
                Component label = camera.kind() == LocalCameraStore.CameraKind.BODYCAM
                        ? Component.literal(camera.name())
                        : Component.translatable("gui.mpsqcamera.cameras.entry", camera.name(),
                                Component.translatable("gui.mpsqcamera.cameras.static"));
                c.centeredText(font, label, cx + 12, y + 7, MpsqTheme.TEXT_NORMAL);
            }
            y += ROW_HEIGHT + ROW_SPACING;
        }
    }

    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();

        int cx = width / 2, y = LIST_TOP - scrollOffset, bottom = height - LIST_BOTTOM_MARGIN;
        for (LocalCameraStore.CameraData camera : LocalCameraStore.getAll()) {
            if (y >= LIST_TOP && y < bottom && mouseX >= cx - 140 && mouseX <= cx + 140 && mouseY >= y && mouseY < y + ROW_HEIGHT) {
                if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT || mouseX < cx - 108) toggle(camera.id());
                else minecraft.gui.setScreen(new CameraDetailScreen(this, camera.id()));
                return true;
            }
            y += ROW_HEIGHT + ROW_SPACING;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
 int key=event.key(), scanCode=event.keycode(), modifiers=event.modifiers();

        if ((key == com.mojang.blaze3d.platform.InputConstants.KEY_DELETE || key == com.mojang.blaze3d.platform.InputConstants.KEY_BACKSPACE) && !selected.isEmpty()) { confirmDeleteSelected(); return true; }
        return super.keyPressed(event);
    }
    private void toggle(UUID id) { if (!selected.add(id)) selected.remove(id); }
    private void confirmDeleteSelected() {
        minecraft.gui.setScreen(new ConfirmScreen(ok -> { if (!ok) { minecraft.gui.setScreen(this); return; } deleteNext(List.copyOf(selected), 0); }, Component.literal("Kameras löschen"), Component.literal(selected.size() + " Kamera(s) wirklich löschen?"), Component.literal("Löschen"), Component.literal("Abbrechen")));
    }
    private void deleteNext(List<UUID> ids, int index) {
        if (index >= ids.size()) { MpsqApiClient.refreshCameras().whenComplete((v,e) -> minecraft.execute(() -> { selected.clear(); minecraft.gui.setScreen(this); })); return; }
        MpsqApiClient.delete("/cameras/" + ids.get(index)).whenComplete((v,e) -> deleteNext(ids, index + 1));
    }
    @Override public boolean mouseScrolled(double x, double y, double h, double v) { scrollOffset -= (int) Math.signum(v) * (ROW_HEIGHT + ROW_SPACING) * 2; clampScroll(); return true; }
    private int maxScroll() { return Math.max(0, LocalCameraStore.getAll().size() * (ROW_HEIGHT + ROW_SPACING) - (height - LIST_TOP - LIST_BOTTOM_MARGIN)); }
    private void clampScroll() { scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll())); }
    @Override public boolean isPauseScreen() { return false; }
}
