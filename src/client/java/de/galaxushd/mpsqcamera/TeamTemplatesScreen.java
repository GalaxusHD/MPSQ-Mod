package de.galaxushd.mpsqcamera;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Scrollbarer Skriptverlauf: Linksklick kopiert, Rechtsklick bearbeitet. */
public final class TeamTemplatesScreen extends Screen {
    private static final int WIDTH = 430;
    private static final int ROW_HEIGHT = 24;
    private final Screen parent;
    private List<TeamTemplate> templates = List.of();
    private int scroll;
    private String status = "";

    public TeamTemplatesScreen(Screen parent) {
        super(Component.translatable("gui.mpsqcamera.team.templates"));
        this.parent = parent;
    }

    @Override protected void init() {
        if (!allowed()) { minecraft.gui.setScreen(parent); return; }
        addRenderableWidget(Button.builder(Component.translatable("gui.mpsqcamera.team.templates.create"),
                button -> minecraft.gui.setScreen(new TeamTemplateEditScreen(this, null)))
                .bounds(width / 2 - 75, 52, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.mpsqcamera.back"), button -> minecraft.gui.setScreen(parent))
                .bounds(width / 2 - 75, height - 36, 150, 20).build());
        reload();
    }

    private boolean allowed() {
        return TeamStateStore.self().map(TeamProfile::permissionRank)
                .map(rank -> rank.level() >= TeamRank.OFFICER.level()).orElse(false);
    }

    void reload() {
        MpsqApiClient.loadTeamTemplates().whenComplete((rows, error) -> minecraft.execute(() -> {
            if (error == null) { templates = rows; status = ""; clampScroll(); }
            else status = "!";
        }));
    }

    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();

        int y = 84 - scroll;
        int left = width / 2 - WIDTH / 2;
        for (TeamTemplate template : templates) {
            int rowHeight = rowHeight(template);
            int rowWidth = Math.min(WIDTH - 36, Math.max(110, font.width(TeamChatText.fromAmpersandCodes(template.text(), net.minecraft.ChatFormatting.WHITE)) + 20));
            int x = template.speaker() == TeamTemplate.Speaker.FRONTMAN ? left : left + WIDTH - rowWidth;
            if (y + rowHeight > 78 && y < height - 46 && mouseX >= x && mouseX < x + rowWidth
                    && mouseY >= y && mouseY < y + rowHeight - 3 && mouseY >= 78 && mouseY < height - 46) {
                if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
                    minecraft.keyboardHandler.setClipboard(template.text());
                    status = Component.translatable("gui.mpsqcamera.team.templates.copied").getString();
                } else if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) minecraft.gui.setScreen(new TeamTemplateEditScreen(this, template));
                return true;
            }
            y += rowHeight;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll -= (int) Math.round(verticalAmount * ROW_HEIGHT);
        clampScroll();
        return true;
    }

    private int rowHeight(TeamTemplate template) {
        int rowWidth=Math.min(WIDTH-36,Math.max(110,font.width(TeamChatText.fromAmpersandCodes(template.text(),net.minecraft.ChatFormatting.WHITE))+20));
        return Math.max(ROW_HEIGHT,font.split(TeamChatText.fromAmpersandCodes(template.text(),net.minecraft.ChatFormatting.WHITE),rowWidth-12).size()*12+12);
    }
    private void clampScroll() {
        int viewport = Math.max(1, height - 130);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, templates.stream().mapToInt(this::rowHeight).sum() - viewport)));
    }

    @Override public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta); MpsqTheme.drawBackground(context, width, height);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        int left = width / 2 - WIDTH / 2;
        context.centeredText(font, title, width / 2, 24, MpsqTheme.TEXT_TITEL);
        context.fill(left, 45, left + WIDTH, 47, MpsqTheme.TEXT_GEDAEMPT);
        int y = 84 - scroll;
        context.enableScissor(left,78,left+WIDTH,height-46);
        for (TeamTemplate template : templates) {
            int rowHeight = rowHeight(template);
            int rowWidth = Math.min(WIDTH - 36, Math.max(110, font.width(TeamChatText.fromAmpersandCodes(template.text(), net.minecraft.ChatFormatting.WHITE)) + 20));
            int x = template.speaker() == TeamTemplate.Speaker.FRONTMAN ? left : left + WIDTH - rowWidth;
            if (y + rowHeight > 78 && y < height - 46) {
                context.fill(x, y, x + rowWidth, y + rowHeight - 3,
                        template.speaker() == TeamTemplate.Speaker.FRONTMAN ? 0x66242424 : 0x663B2020);
                int lineY=y+6;
                for(var line:font.split(TeamChatText.fromAmpersandCodes(template.text(),net.minecraft.ChatFormatting.WHITE),rowWidth-12)) {
                    context.text(font,line,x+6,lineY,MpsqTheme.TEXT_NORMAL);lineY+=12;
                }
            }
            y += rowHeight;
        }
        context.disableScissor();
        if (!status.isEmpty()) context.centeredText(font, status, width / 2, height - 51, 0x55FF55);
    }

    @Override public boolean isPauseScreen() { return false; }
}


