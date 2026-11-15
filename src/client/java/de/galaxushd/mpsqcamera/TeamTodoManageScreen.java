package de.galaxushd.mpsqcamera;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Officer-and-higher editor for creating, moving, changing and deleting tasks. */
public final class TeamTodoManageScreen extends Screen {
    private static final int WIDTH = 360;
    private final Screen parent;
    private EditBox input;
    private Button listButton;
    private Button saveButton;
    private Button deleteButton;
    private List<TeamTodo> todos = List.of();
    private TeamTodo selected;
    private TeamTodoList selectedList;
    private String status = "";

    public TeamTodoManageScreen(Screen parent) {
        super(Component.translatable("gui.mpsqcamera.team.todo.manage"));
        this.parent = parent;
        this.selectedList = TeamStateStore.self().map(TeamProfile::baseRank).map(TeamTodoList::forRank).orElse(TeamTodoList.WORKER);
    }

    @Override protected void init() {
        int left = width / 2 - WIDTH / 2;
        input = new EditBox(font, left, 66, WIDTH - 126, 20, Component.translatable("gui.mpsqcamera.team.input"));
        input.setMaxLength(256);
        input.setHint(Component.translatable("gui.mpsqcamera.team.input"));
        addRenderableWidget(input);
        listButton = addRenderableWidget(Button.builder(selectedList.title(), button -> nextList())
                .bounds(left + WIDTH - 120, 66, 120, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.mpsqcamera.team.add"), button -> addTodo())
                .bounds(left, 92, 112, 20).build());
        saveButton = addRenderableWidget(Button.builder(Component.translatable("gui.mpsqcamera.team.todo.save"), button -> saveTodo())
                .bounds(left + 124, 92, 112, 20).build());
        deleteButton = addRenderableWidget(Button.builder(Component.translatable("gui.mpsqcamera.team.todo.delete"), button -> deleteTodo())
                .bounds(left + 248, 92, 112, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.mpsqcamera.back"), button -> minecraft.gui.setScreen(parent))
                .bounds(width / 2 - 75, height - 36, 150, 20).build());
        updateButtons();
        reload();
    }

    private void nextList() {
        TeamTodoList[] lists = TeamTodoList.values();
        selectedList = lists[(selectedList.ordinal() + 1) % lists.length];
        listButton.setMessage(selectedList.title());
    }

    private void addTodo() {
        String text = input.getValue().trim();
        if (text.isEmpty()) return;
        MpsqApiClient.addTeamTodo(text, selectedList).whenComplete((ignored, error) -> minecraft.execute(() -> {
            status = error == null ? "OK" : "!";
            if (error == null) input.setValue("");
            reload();
        }));
    }

    private void saveTodo() {
        if (selected == null || input.getValue().trim().isEmpty()) return;
        MpsqApiClient.updateTeamTodo(selected.id(), input.getValue().trim(), selectedList).whenComplete((ignored, error) -> minecraft.execute(() -> {
            status = error == null ? "OK" : "!";
            reload();
        }));
    }

    private void deleteTodo() {
        if (selected == null) return;
        MpsqApiClient.deleteTeamTodo(selected.id()).whenComplete((ignored, error) -> minecraft.execute(() -> {
            status = error == null ? "OK" : "!";
            if (error == null) { selected = null; input.setValue(""); updateButtons(); }
            reload();
        }));
    }

    private void reload() {
        MpsqApiClient.loadTeamTodos().whenComplete((rows, error) -> minecraft.execute(() -> {
            if (error == null) todos = rows; else status = "!";
        }));
    }

    private void updateButtons() { saveButton.active = selected != null; deleteButton.active = selected != null; }

    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();

        int left = width / 2 - WIDTH / 2;
        int y = 124;
        for (TeamTodo todo : todos) {
            if (mouseX >= left && mouseX < left + WIDTH && mouseY >= y && mouseY < y + 18) {
                selected = todo;
                selectedList = todo.list();
                input.setValue(todo.text());
                listButton.setMessage(selectedList.title());
                updateButtons();
                return true;
            }
            y += 19;
            if (y > height - 48) break;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta); MpsqTheme.drawBackground(context, width, height);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        int left = width / 2 - WIDTH / 2;
        context.centeredText(font, title, width / 2, 24, MpsqTheme.TEXT_TITEL);
        context.fill(left, 45, left + WIDTH, 47, MpsqTheme.TEXT_GEDAEMPT);
        int y = 124;
        for (TeamTodo todo : todos) {
            context.fill(left, y, left + WIDTH, y + 17, todo.equals(selected) ? 0x88557A9B : 0x66000000);
            context.text(font, todo.list().title(), left + 5, y + 5, MpsqTheme.TEXT_GEDAEMPT);
            int prefixWidth = font.width(todo.list().title());
            context.text(font, font.plainSubstrByWidth(todo.text(), WIDTH - prefixWidth - 18), left + 10 + prefixWidth, y + 5, MpsqTheme.TEXT_NORMAL);
            y += 19;
            if (y > height - 48) break;
        }
        if (!status.isEmpty()) context.text(font, status, left + WIDTH - 14, 52, status.equals("OK") ? 0x55FF55 : 0xFF5555);
    }

    @Override public boolean isPauseScreen() { return false; }
}
