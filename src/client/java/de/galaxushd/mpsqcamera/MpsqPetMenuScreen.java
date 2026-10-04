package de.galaxushd.mpsqcamera;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

/** Squid Game-styled Mini You catalogue and local preset selector. */
public final class MpsqPetMenuScreen extends Screen {
    private static final int PANEL = 0xE6191D28;
    private static final int ACCENT = 0xFFFF526F;
    private static final int ROW_HEIGHT = 31;
    private final Screen parent;
    private MpsqPetCatalog.Group group = MpsqPetCatalog.Group.MINI_YOU;
    private String selectedId = MpsqPetSelectionStore.selectedId();
    private int scroll;
    private String status = "Wähle ein vorgefertigtes Mini You.";

    public MpsqPetMenuScreen(Screen parent) {
        super(Text.literal("MPSQ · Pet-Menü"));
        this.parent = parent;
    }

    @Override protected void init() {
        int left = panelLeft();
        int panelWidth = panelWidth();
        int bottom = height - 28;
        addDrawableChild(ButtonWidget.builder(Text.literal("Schließen"), b -> close())
                .dimensions(left + 8, bottom, 110, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Auswahl merken"), b -> saveSelection())
                .dimensions(left + panelWidth - 148, bottom, 140, 20).build());
    }

    private int panelWidth() { return Math.min(480, Math.max(260, width - 28)); }
    private int panelLeft() { return (width - panelWidth()) / 2; }
    private int contentTop() { return 73; }
    private int contentBottom() { return height - 56; }
    private List<MpsqPetCatalog.Pet> pets() { return MpsqPetCatalog.group(group); }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        MpsqTheme.drawBackground(context, width, height);
        int x = panelLeft(), y = 14, w = panelWidth(), h = height - 28;
        context.fill(x + 4, y + 5, x + w + 4, y + h + 5, 0x99000000);
        context.fill(x, y, x + w, y + h, PANEL);
        context.fill(x, y, x + w, y + 3, ACCENT);
        context.fill(x, y, x + 3, y + h, 0xFF9C203C);
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int x = panelLeft(), w = panelWidth(), split = x + (w * 53 / 100);
        context.drawTextWithShadow(textRenderer, "MPSQ  /  MINI YOU", x + 16, 24, ACCENT);
        context.drawTextWithShadow(textRenderer, "Vorgefertigte Pets", x + 16, 42, 0xFFFFFFFF);
        context.fill(x + 12, 63, x + w - 12, 64, 0x77FFFFFF);
        drawTab(context, x + 12, 66, split - x - 16, "Mini You", MpsqPetCatalog.Group.MINI_YOU);
        drawTab(context, split + 4, 66, x + w - split - 16, "Team-Pets", MpsqPetCatalog.Group.TEAM_ROLE);

        context.enableScissor(x + 8, contentTop(), split - 4, contentBottom());
        List<MpsqPetCatalog.Pet> list = pets();
        for (int i = 0; i < list.size(); i++) {
            int rowY = contentTop() + i * ROW_HEIGHT - scroll;
            if (rowY + ROW_HEIGHT < contentTop() || rowY > contentBottom()) continue;
            MpsqPetCatalog.Pet pet = list.get(i);
            boolean active = pet.id().equals(selectedId);
            context.fill(x + 10, rowY, split - 8, rowY + ROW_HEIGHT - 2, active ? 0xAA9C203C : (i % 2 == 0 ? 0x553B4050 : 0x333B4050));
            drawHead(context, pet, x + 14, rowY + 4, 22);
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(pet.name(), split - x - 68), x + 44, rowY + 10,
                    active ? 0xFFFFA0AA : 0xFFFFFFFF);
            if (active) context.drawTextWithShadow(textRenderer, "✓", split - 23, rowY + 9, ACCENT);
        }
        context.disableScissor();

        MpsqPetCatalog.Pet selected = MpsqPetCatalog.byId(selectedId);
        if (selected == null || selected.group() != group) selected = list.isEmpty() ? null : list.get(0);
        if (selected != null) drawDetails(context, selected, split + 8, x + w - 12);
        context.drawCenteredTextWithShadow(textRenderer, status, width / 2, height - 44, 0xFFDDDDDD);
        if (list.size() * ROW_HEIGHT > contentBottom() - contentTop())
            context.drawCenteredTextWithShadow(textRenderer, "Mausrad zum Scrollen", split / 2 + x / 2, height - 32, 0xFFAAAAAA);
    }

    private void drawTab(DrawContext context, int x, int y, int width, String label, MpsqPetCatalog.Group tab) {
        context.fill(x, y, x + width, y + 18, group == tab ? 0xAA9C203C : 0x553B4050);
        context.drawCenteredTextWithShadow(textRenderer, label, x + width / 2, y + 5, 0xFFFFFFFF);
    }

    private void chooseGroup(MpsqPetCatalog.Group next) {
        group = next;
        scroll = 0;
        if (pets().stream().noneMatch(pet -> pet.id().equals(selectedId)) && !pets().isEmpty()) {
            selectedId = pets().get(0).id();
            status = "Wähle ein Pet aus der Kategorie " + (group == MpsqPetCatalog.Group.MINI_YOU ? "Mini You" : "Team-Pets") + ".";
        }
    }

    private void drawHead(DrawContext context, MpsqPetCatalog.Pet pet, int x, int y, int size) {
        context.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xFF111318);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, pet.textureId(), x, y, 8, 8,
                size, size, 64, pet.textureHeight(), 64, pet.textureHeight());
        context.drawTexture(RenderPipelines.GUI_TEXTURED, pet.textureId(), x, y, 40, 8,
                size, size, 64, pet.textureHeight(), 64, pet.textureHeight());
    }

    private void drawDetails(DrawContext context, MpsqPetCatalog.Pet pet, int left, int right) {
        int center = (left + right) / 2;
        int portrait = Math.min(78, Math.max(40, (right - left) / 2));
        drawHead(context, pet, center - portrait / 2, 112, portrait);
        context.drawCenteredTextWithShadow(textRenderer, pet.name(), center, 199, 0xFFFFFFFF);
        String category = pet.group() == MpsqPetCatalog.Group.TEAM_ROLE ? "Team-Mini You" : "Mini You-Preset";
        context.drawCenteredTextWithShadow(textRenderer, category, center, 215, ACCENT);
        if (pet.issuerRole() != null) {
            context.drawCenteredTextWithShadow(textRenderer, "Ausgabe nur durch", center, 239, 0xFFBBBBBB);
            context.drawCenteredTextWithShadow(textRenderer, pet.issuerRole().label(), center, 252, 0xFFFFFFFF);
        }
        String note = pet.soundNotes();
        int lineY = pet.issuerRole() == null ? 239 : 274;
        for (String line : wrap(note, 28)) {
            context.drawCenteredTextWithShadow(textRenderer, line, center, lineY, 0xFFBBBBBB);
            lineY += 11;
        }
        context.drawCenteredTextWithShadow(textRenderer, "Pet-Folgen und Ausgabe", center, height - 76, 0xFFAAAAAA);
        context.drawCenteredTextWithShadow(textRenderer, "werden in den nächsten Schritten", center, height - 64, 0xFFAAAAAA);
        context.drawCenteredTextWithShadow(textRenderer, "angeschlossen.", center, height - 52, 0xFFAAAAAA);
    }

    private static List<String> wrap(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) return List.of(value == null ? "" : value);
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : value.split(" ")) {
            if (line.length() + word.length() + (line.isEmpty() ? 0 : 1) > maxChars) {
                lines.add(line.toString()); line.setLength(0);
            }
            if (!line.isEmpty()) line.append(' ');
            line.append(word);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    private void saveSelection() {
        MpsqPetCatalog.Pet pet = MpsqPetCatalog.byId(selectedId);
        if (pet == null) {
            status = "Wähle zuerst ein Pet aus.";
        } else if (pet.issuerRole() != null) {
            status = "Team-Pets werden über die rollenbeschränkte Ausgabe vergeben.";
        } else if (MpsqPetSelectionStore.select(selectedId)) {
            status = "Auswahl gespeichert: " + pet.name();
        } else {
            status = "Pet-Auswahl konnte nicht gespeichert werden.";
        }
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int x = panelLeft(), w = panelWidth(), split = x + (w * 53 / 100);
        if (mouseY >= 66 && mouseY < 84) {
            chooseGroup(mouseX < split ? MpsqPetCatalog.Group.MINI_YOU : MpsqPetCatalog.Group.TEAM_ROLE);
            return true;
        }
        if (mouseX >= x + 8 && mouseX < split - 4 && mouseY >= contentTop() && mouseY < contentBottom()) {
            int index = (int) (mouseY - contentTop() + scroll) / ROW_HEIGHT;
            if (index >= 0 && index < pets().size()) {
                selectedId = pets().get(index).id();
                status = "Ausgewählt: " + MpsqPetCatalog.byId(selectedId).name();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int max = Math.max(0, pets().size() * ROW_HEIGHT - (contentBottom() - contentTop()));
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(verticalAmount) * ROW_HEIGHT * 3));
        return true;
    }

    @Override public void close() { if (client != null) client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
