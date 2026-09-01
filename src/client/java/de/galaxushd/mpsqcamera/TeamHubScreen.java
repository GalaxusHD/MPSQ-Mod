package de.galaxushd.mpsqcamera;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Main entry point for MPSQ Team. Buttons are enabled by the shared role profile. */
public final class TeamHubScreen extends Screen {
    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 6;
    private final Screen parent;
    private String statusKey = "gui.mpsqcamera.team.loading";

    public TeamHubScreen(Screen parent) {
        super(Text.translatable("gui.mpsqcamera.team.title"));
        this.parent = parent;
    }

    @Override protected void init() {
        int x = width / 2 - BUTTON_WIDTH / 2;
        int y = height / 2 - 68;
        TeamRank rank = TeamStateStore.self().map(TeamProfile::permissionRank).orElse(TeamRank.VIP);
        boolean available = rank.level() >= TeamRank.UNDERCOVER_001.level();
        boolean canEditTodos = rank.level() >= TeamRank.WORKER.level();
        boolean canManageEvent = rank.level() >= TeamRank.OFFICER.level();
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mpsqcamera.team.members"), b -> client.setScreen(new TeamMembersScreen(this)))
                .dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build()).active = available;
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mpsqcamera.team.todo"), b -> client.setScreen(new TeamTodoScreen(this)))
                .dimensions(x, y += BUTTON_HEIGHT + GAP, BUTTON_WIDTH, BUTTON_HEIGHT).build()).active = available || canEditTodos;
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mpsqcamera.team.timer"), b -> client.setScreen(new TeamBoardScreen(this, TeamBoardScreen.Mode.TIMER)))
                .dimensions(x, y += BUTTON_HEIGHT + GAP, BUTTON_WIDTH, BUTTON_HEIGHT).build()).active = available || canManageEvent;
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mpsqcamera.team.templates"), b -> client.setScreen(new TeamTemplatesScreen(this)))
                .dimensions(x, y += BUTTON_HEIGHT + GAP, BUTTON_WIDTH, BUTTON_HEIGHT).build()).active = available || canManageEvent;
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mpsqcamera.back"), b -> client.setScreen(parent))
                .dimensions(width / 2 - 75, height - 36, 150, 20).build());

        MpsqApiClient.refreshTeamProfile().thenCompose(profile -> MpsqApiClient.refreshTeamMembers())
                .whenComplete((members, error) -> client.execute(() -> {
                    statusKey = error == null ? "gui.mpsqcamera.team.ready" : "gui.mpsqcamera.team.unavailable";
                    clearAndInit();
                }));
    }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        MpsqTheme.drawBackground(context, width, height);
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 28, MpsqTheme.TEXT_TITEL);
        context.fill(width / 2 - 135, 45, width / 2 + 135, 47, MpsqTheme.TEXT_GEDAEMPT);
        TeamStateStore.self().ifPresent(profile -> {
            int tagHeight = 14;
            int tagWidth = profile.displayedRank().widthForHeight(tagHeight);
            int x = width / 2 - tagWidth / 2;
            profile.displayedRank().draw(context, x, 48, tagHeight);
        });
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable(statusKey), width / 2, 68, MpsqTheme.TEXT_GEDAEMPT);
    }

    @Override public boolean shouldPause() { return false; }
}
