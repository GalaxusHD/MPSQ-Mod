package de.galaxushd.mpsqcamera;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Main entry point for MPSQ Team. Buttons are enabled by the shared role profile. */
public final class TeamHubScreen extends Screen {
    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 6;
    private final Screen parent;
    private boolean requested; private String statusKey = "gui.mpsqcamera.team.loading";

    public TeamHubScreen(Screen parent) {
        super(Component.translatable("gui.mpsqcamera.team.title"));
        this.parent = parent;
    }

    @Override protected void init() {
        int x = width / 2 - BUTTON_WIDTH / 2;
        int y = Math.max(92, height / 2 - 68);
        if (TeamStateStore.self().map(p -> p.baseRank().level() >= TeamRank.OFFICER.level()).orElse(false)) {
            addRenderableWidget(Button.builder(Component.literal("Replay"), b -> minecraft.gui.setScreen(new MpsqReplayScreen(this)))
                .bounds(x,y,BUTTON_WIDTH,BUTTON_HEIGHT).build());
            y += BUTTON_HEIGHT + GAP;
            addRenderableWidget(Button.builder(Component.literal("Logs"), b -> minecraft.gui.setScreen(new TeamLogsScreen(this)))
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
            y += BUTTON_HEIGHT + GAP;
        }
        TeamRank rank = TeamStateStore.self().map(TeamProfile::permissionRank).orElse(TeamRank.VIP);
        boolean available = rank.level() >= TeamRank.UNDERCOVER_001.level();
        boolean canEditTodos = rank.level() >= TeamRank.WORKER.level();
        boolean canManageEvent = rank.level() >= TeamRank.OFFICER.level();
        addRenderableWidget(Button.builder(Component.translatable("gui.mpsqcamera.team.members"), b -> minecraft.gui.setScreen(new TeamMembersScreen(this)))
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build()).active = available;
        addRenderableWidget(Button.builder(Component.translatable("gui.mpsqcamera.team.todo"), b -> minecraft.gui.setScreen(new TeamTodoScreen(this)))
                .bounds(x, y += BUTTON_HEIGHT + GAP, BUTTON_WIDTH, BUTTON_HEIGHT).build()).active = available || canEditTodos;
        addRenderableWidget(Button.builder(Component.translatable("gui.mpsqcamera.team.timer"), b -> minecraft.gui.setScreen(new TeamBoardScreen(this, TeamBoardScreen.Mode.TIMER)))
                .bounds(x, y += BUTTON_HEIGHT + GAP, BUTTON_WIDTH, BUTTON_HEIGHT).build()).active = available || canManageEvent;
        addRenderableWidget(Button.builder(Component.translatable("gui.mpsqcamera.team.templates"), b -> minecraft.gui.setScreen(new TeamTemplatesScreen(this)))
                .bounds(x, y += BUTTON_HEIGHT + GAP, BUTTON_WIDTH, BUTTON_HEIGHT).build()).active = available || canManageEvent;
        addRenderableWidget(Button.builder(Component.translatable("gui.mpsqcamera.back"), b -> minecraft.gui.setScreen(parent))
                .bounds(width / 2 - 75, height - 36, 150, 20).build());

        if (requested) return;
        requested = true;
        MpsqApiClient.refreshTeamProfile().thenCompose(profile -> MpsqApiClient.refreshTeamMembers())
                .whenComplete((members, error) -> minecraft.execute(() -> {
                    statusKey = error == null ? "gui.mpsqcamera.team.ready" : "gui.mpsqcamera.team.unavailable";
                    rebuildWidgets();
                }));
    }

    @Override public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        MpsqTheme.drawBackground(context, width, height);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(font, title, width / 2, 28, MpsqTheme.TEXT_TITEL);
        context.fill(width / 2 - 135, 45, width / 2 + 135, 47, MpsqTheme.TEXT_GEDAEMPT);
        TeamStateStore.self().ifPresent(profile -> {
            int tagHeight = 14;
            int tagWidth = profile.displayedRank().widthForHeight(tagHeight);
            int x = width / 2 - tagWidth / 2;
            profile.displayedRank().draw(context, x, 48, tagHeight);
        });
        context.centeredText(font, Component.translatable(statusKey), width / 2, 68, MpsqTheme.TEXT_GEDAEMPT);
    }

    @Override public boolean isPauseScreen() { return false; }
}

