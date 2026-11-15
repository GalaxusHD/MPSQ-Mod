package de.galaxushd.mpsqcamera;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Four shared task lists. Completion ticks are intentionally only local UI state. */
public final class TeamTodoScreen extends Screen {
    private static final int PAGE_MARGIN = 18;
    private static final int COLUMN_GAP = 16;
    private static final int ROW_HEIGHT = 18;
    private final Screen parent;
    private List<TeamTodo> todos = List.of();
    private final Set<UUID> checked = new HashSet<>();
    private String status = "";

    public TeamTodoScreen(Screen parent) {
        super(Component.translatable("gui.mpsqcamera.team.todo"));
        this.parent = parent;
    }

    @Override protected void init() {
        if (canManage()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.mpsqcamera.team.todo.manage"),
                    button -> minecraft.gui.setScreen(new TeamTodoManageScreen(this)))
                    .bounds(width / 2 - 75, 52, 150, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.mpsqcamera.back"), button -> minecraft.gui.setScreen(parent))
                .bounds(width / 2 - 75, height - 36, 150, 20).build());
        reload();
    }

    private boolean canManage() {
        return TeamStateStore.self().map(TeamProfile::baseRank)
                .map(rank -> rank.level() >= TeamRank.OFFICER.level()).orElse(false);
    }

    private void reload() {
        MpsqApiClient.loadTeamTodos().whenComplete((rows, error) -> minecraft.execute(() -> {
            if (error == null) todos = rows; else status = "!";
        }));
    }

    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();

        for (TeamTodoList list : TeamTodoList.values()) {
            int x = columnX(list);
            int y = listY(list) + 17;
            int columnWidth = columnWidth();
            for (TeamTodo todo : todos) {
                if (todo.list() != list) continue;
                if (mouseX >= x && mouseX < x + columnWidth && mouseY >= y && mouseY < y + ROW_HEIGHT) {
                    if (!checked.add(todo.id())) checked.remove(todo.id());
                    return true;
                }
                y += ROW_HEIGHT;
                if (y > height - 48) break;
            }
        }
        return super.mouseClicked(event, doubled);
    }

    @Override public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        MpsqTheme.drawBackground(context, width, height);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(font, title, width / 2, 24, MpsqTheme.TEXT_TITEL);
        context.fill(PAGE_MARGIN, 45, width - PAGE_MARGIN, 47, MpsqTheme.TEXT_GEDAEMPT);
        for (TeamTodoList list : TeamTodoList.values()) drawList(context, list);
        if (!status.isEmpty()) context.centeredText(font, status, width / 2, height - 52, 0xFF5555);
    }

    private void drawList(GuiGraphicsExtractor context, TeamTodoList list) {
        int x = columnX(list);
        int y = listY(list);
        int columnWidth = columnWidth();
        context.text(font, list.title(), x, y, MpsqTheme.TEXT_NORMAL);
        y += 17;
        for (TeamTodo todo : todos) {
            if (todo.list() != list) continue;
            context.fill(x, y, x + columnWidth, y + ROW_HEIGHT - 2, 0x66000000);
            boolean isChecked = checked.contains(todo.id());
            context.fill(x + 4, y + 4, x + 13, y + 13, 0xFFDDDDDD);
            if (isChecked) {
                context.fill(x + 6, y + 6, x + 11, y + 11, 0xFF55FF55);
                context.text(font, "✓", x + 5, y + 3, 0xFF006600);
            }
            context.text(font, font.plainSubstrByWidth(todo.text(), columnWidth - 23),
                    x + 18, y + 5, isChecked ? MpsqTheme.TEXT_GEDAEMPT : MpsqTheme.TEXT_NORMAL);
            y += ROW_HEIGHT;
            if (y > height - 48) break;
        }
    }

    private int columnWidth() { return Math.max(120, (width - PAGE_MARGIN * 2 - COLUMN_GAP) / 2); }
    private int columnX(TeamTodoList list) { return (list == TeamTodoList.WORKER || list == TeamTodoList.OFFICER) ? PAGE_MARGIN : PAGE_MARGIN + columnWidth() + COLUMN_GAP; }
    private int listY(TeamTodoList list) { return (list == TeamTodoList.WORKER || list == TeamTodoList.SOLDIER) ? 86 : height / 2 + 8; }
    @Override public boolean isPauseScreen() { return false; }
}
