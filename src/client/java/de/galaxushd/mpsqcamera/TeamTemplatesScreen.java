package de.galaxushd.mpsqcamera;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

/** Scrollbarer Skriptverlauf: Linksklick kopiert, Rechtsklick bearbeitet. */
public final class TeamTemplatesScreen extends Screen {
    private static final int WIDTH = 430;
    private static final int ROW_HEIGHT = 24;
    private final Screen parent;
    private List<TeamTemplate> templates = List.of();
    private int scroll;
    private String status = "";

    public TeamTemplatesScreen(Screen parent) {
        super(Text.translatable("gui.mpsqcamera.team.templates"));
        this.parent = parent;
    }

    @Override protected void init() {
        if (!allowed()) { client.setScreen(parent); return; }
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mpsqcamera.team.templates.create"),
                button -> client.setScreen(new TeamTemplateEditScreen(this, null)))
                .dimensions(width / 2 - 75, 52, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mpsqcamera.back"), button -> client.setScreen(parent))
                .dimensions(width / 2 - 75, height - 36, 150, 20).build());
        reload();
    }

    private boolean allowed() {
        return TeamStateStore.self().map(TeamProfile::permissionRank)
                .map(rank -> rank.level() >= TeamRank.OFFICER.level()).orElse(false);
    }

    void reload() {
        MpsqApiClient.loadTeamTemplates().whenComplete((rows, error) -> client.execute(() -> {
            if (error == null) { templates = rows; status = ""; clampScroll(); }
            else status = "!";
        }));
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int y = 84 - scroll;
        int left = width / 2 - WIDTH / 2;
        for (TeamTemplate template : templates) {
            int rowWidth = Math.min(WIDTH - 36, Math.max(110, textRenderer.getWidth(template.text()) + 20));
            int x = template.speaker() == TeamTemplate.Speaker.FRONTMAN ? left : left + WIDTH - rowWidth;
            if (y >= 78 && y + ROW_HEIGHT <= height - 46 && mouseX >= x && mouseX < x + rowWidth
                    && mouseY >= y && mouseY < y + ROW_HEIGHT - 3) {
                if (button == 0) {
                    client.keyboard.setClipboard(template.text());
                    status = Text.translatable("gui.mpsqcamera.team.templates.copied").getString();
                } else if (button == 1) client.setScreen(new TeamTemplateEditScreen(this, template));
                return true;
            }
            y += ROW_HEIGHT;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll -= (int) Math.round(verticalAmount * ROW_HEIGHT);
        clampScroll();
        return true;
    }

    private void clampScroll() {
        int viewport = Math.max(1, height - 130);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, templates.size() * ROW_HEIGHT - viewport)));
    }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta); MpsqTheme.drawBackground(context, width, height);
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int left = width / 2 - WIDTH / 2;
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 24, MpsqTheme.TEXT_TITEL);
        context.fill(left, 45, left + WIDTH, 47, MpsqTheme.TEXT_GEDAEMPT);
        int y = 84 - scroll;
        for (TeamTemplate template : templates) {
            int rowWidth = Math.min(WIDTH - 36, Math.max(110, textRenderer.getWidth(template.text()) + 20));
            int x = template.speaker() == TeamTemplate.Speaker.FRONTMAN ? left : left + WIDTH - rowWidth;
            if (y >= 78 && y + ROW_HEIGHT <= height - 46) {
                context.fill(x, y, x + rowWidth, y + ROW_HEIGHT - 3,
                        template.speaker() == TeamTemplate.Speaker.FRONTMAN ? 0x66242424 : 0x663B2020);
                context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(template.text(), rowWidth - 12),
                        x + 6, y + 7, MpsqTheme.TEXT_NORMAL);
            }
            y += ROW_HEIGHT;
        }
        if (!status.isEmpty()) context.drawCenteredTextWithShadow(textRenderer, status, width / 2, height - 51, 0x55FF55);
    }

    @Override public boolean shouldPause() { return false; }
}
