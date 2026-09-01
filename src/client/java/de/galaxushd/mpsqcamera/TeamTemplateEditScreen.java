package de.galaxushd.mpsqcamera;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Erstellen, Bearbeiten und Löschen einer Skriptzeile samt Sprecherseite. */
public final class TeamTemplateEditScreen extends Screen {
    private static final int WIDTH = 360;
    private final TeamTemplatesScreen parent;
    private final TeamTemplate existing;
    private TextFieldWidget input;
    private TeamTemplate.Speaker speaker;
    private ButtonWidget speakerButton;
    private String status = "";

    public TeamTemplateEditScreen(TeamTemplatesScreen parent, TeamTemplate existing) {
        super(Text.translatable(existing == null ? "gui.mpsqcamera.team.templates.create" : "gui.mpsqcamera.team.templates.edit"));
        this.parent = parent;
        this.existing = existing;
        this.speaker = existing == null ? TeamTemplate.Speaker.OFFICER : existing.speaker();
    }

    @Override protected void init() {
        int left = width / 2 - WIDTH / 2;
        input = new TextFieldWidget(textRenderer, left, 72, WIDTH, 20, Text.translatable("gui.mpsqcamera.team.input"));
        input.setMaxLength(512);
        input.setPlaceholder(Text.translatable("gui.mpsqcamera.team.input"));
        if (existing != null) input.setText(existing.text());
        addDrawableChild(input);
        speakerButton = addDrawableChild(ButtonWidget.builder(speakerText(), button -> toggleSpeaker())
                .dimensions(left, 100, WIDTH, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mpsqcamera.team.templates.save"), button -> save())
                .dimensions(left, 128, existing == null ? WIDTH : 174, 20).build());
        if (existing != null) addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mpsqcamera.team.templates.delete"), button -> delete())
                .dimensions(left + 186, 128, 174, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mpsqcamera.back"), button -> client.setScreen(parent))
                .dimensions(width / 2 - 75, height - 36, 150, 20).build());
        setInitialFocus(input);
    }

    private Text speakerText() { return Text.translatable("gui.mpsqcamera.team.templates.speaker." + speaker.id()); }
    private void toggleSpeaker() {
        speaker = speaker == TeamTemplate.Speaker.OFFICER ? TeamTemplate.Speaker.FRONTMAN : TeamTemplate.Speaker.OFFICER;
        speakerButton.setMessage(speakerText());
    }

    private void save() {
        String text = input.getText().trim();
        if (text.isEmpty()) return;
        var future = existing == null ? MpsqApiClient.addTeamTemplate(text, speaker)
                : MpsqApiClient.updateTeamTemplate(existing.id(), text, speaker);
        future.whenComplete((ignored, error) -> client.execute(() -> {
            if (error == null) { parent.reload(); client.setScreen(parent); } else status = "!";
        }));
    }

    private void delete() {
        if (existing == null) return;
        MpsqApiClient.deleteTeamTemplate(existing.id()).whenComplete((ignored, error) -> client.execute(() -> {
            if (error == null) { parent.reload(); client.setScreen(parent); } else status = "!";
        }));
    }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta); MpsqTheme.drawBackground(context, width, height);
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int left = width / 2 - WIDTH / 2;
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 24, MpsqTheme.TEXT_TITEL);
        context.fill(left, 45, left + WIDTH, 47, MpsqTheme.TEXT_GEDAEMPT);
        context.drawTextWithShadow(textRenderer, Text.translatable("gui.mpsqcamera.team.templates.hint"), left, 54, MpsqTheme.TEXT_GEDAEMPT);
        if (!status.isEmpty()) context.drawCenteredTextWithShadow(textRenderer, status, width / 2, 156, 0xFF5555);
    }

    @Override public boolean shouldPause() { return false; }
}
