package de.galaxushd.mpsqcamera;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class BildschirmListScreen extends Screen {
    private static final int LIST_TOP = 54;
    private static final int LIST_BOTTOM_MARGIN = 62;
    private static final int ROW_HEIGHT = 18;
    private final Screen parent;
    private final boolean cinema;
    private final List<BildschirmEntry> bildschirme = new ArrayList<>();
    private int scrollOffset;
    private int selectedIndex = -1;

    public BildschirmListScreen(Screen parent) {
        this(parent,true);
    }
    public BildschirmListScreen(Screen parent, boolean cinema) {
        super(Component.literal("Bildschirme"));
        this.cinema=cinema;
        this.parent = parent;
    }

    private void loadJoinedScreens() {
        bildschirme.clear();
        for (LocalScreenStore.LocalScreenData screen : LocalScreenStore.getAllScreens()) {
            bildschirme.add(new BildschirmEntry(screen.id().toString(), screen.name(), ScreenAccessStore.isOwner(screen.id())));
        }
        clampScroll();
    }

    @Override
    protected void init() {
        loadJoinedScreens();
        addRenderableWidget(Button.builder(Component.literal("Zurück"), button -> minecraft.gui.setScreen(parent))
                .bounds(width / 2 - 75, height - 28, 150, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        MpsqTheme.drawBackground(context, width, height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        int centerX = width / 2;
        context.centeredText(font, title, centerX, 28, MpsqTheme.TEXT_TITEL);
        context.fill(centerX - 140, 44, centerX + 140, 45, 0x44FFFFFF);
        if (bildschirme.isEmpty()) {
            context.centeredText(font, Component.literal("Keine Bildschirme vorhanden."), centerX, LIST_TOP + 26, MpsqTheme.TEXT_GEDAEMPT);
            return;
        }
        int listBottom = height - LIST_BOTTOM_MARGIN;
        int rowY = LIST_TOP - scrollOffset;
        for (int i = 0; i < bildschirme.size(); i++) {
            if (rowY + ROW_HEIGHT > LIST_TOP && rowY < listBottom) {
                BildschirmEntry entry = bildschirme.get(i);
                context.fill(centerX - 140, rowY, centerX + 140, rowY + ROW_HEIGHT - 2,
                        i == selectedIndex ? 0x55AA0000 : 0x33000000);
                context.centeredText(font, Component.literal(entry.name + (entry.creator ? " [Eigener Bildschirm]" : " [Beigetreten]")), centerX, rowY + 4, MpsqTheme.TEXT_NORMAL);
            }
            rowY += ROW_HEIGHT;
        }
        if (maxScroll() > 0) context.centeredText(font, Component.literal("Mausrad zum Scrollen"), centerX, height - 54, MpsqTheme.TEXT_GEDAEMPT);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset -= (int) Math.signum(verticalAmount) * ROW_HEIGHT * 2;
        clampScroll();
        return true;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();

        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            int centerX = width / 2;
            int listBottom = height - LIST_BOTTOM_MARGIN;
            int rowY = LIST_TOP - scrollOffset;
            for (int i = 0; i < bildschirme.size(); i++) {
                if (rowY >= LIST_TOP && rowY < listBottom && mouseX >= centerX - 140 && mouseX <= centerX + 140
                        && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2) {
                    selectedIndex = i;
                    BildschirmEntry entry = bildschirme.get(i);
                    minecraft.gui.setScreen(new BildschirmDetailScreen(this, entry.id, entry.name, entry.creator));
                    return true;
                }
                rowY += ROW_HEIGHT;
            }
        }
        return super.mouseClicked(event, doubled);
    }

    private int maxScroll() { return Math.max(0, bildschirme.size() * ROW_HEIGHT - (height - LIST_TOP - LIST_BOTTOM_MARGIN)); }
    private void clampScroll() { scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll())); }
    @Override public boolean isPauseScreen() { return false; }

    public static final class BildschirmEntry {
        private final String id;
        private final String name;
        private final boolean creator;
        public BildschirmEntry(String id, String name, boolean creator) { this.id = id; this.name = name; this.creator = creator; }
    }
}
