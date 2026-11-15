package de.galaxushd.mpsqcamera;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Erstellen, Bearbeiten und Löschen einer Skriptzeile samt Sprecherseite. */
public final class TeamTemplateEditScreen extends Screen {
    private static final int WIDTH = 360;
    private final TeamTemplatesScreen parent;
    private final TeamTemplate existing;
    private EditBox input, audio;
    private TeamTemplate.Speaker speaker;
    private Button speakerButton;
    private String status = "";

    public TeamTemplateEditScreen(TeamTemplatesScreen parent, TeamTemplate existing) {
        super(Component.translatable(existing == null ? "gui.mpsqcamera.team.templates.create" : "gui.mpsqcamera.team.templates.edit"));
        this.parent = parent;
        this.existing = existing;
        this.speaker = existing == null ? TeamTemplate.Speaker.OFFICER : existing.speaker();
    }

    @Override protected void init() {
        int left = width / 2 - WIDTH / 2;
        input = new EditBox(font, left, 72, WIDTH, 20, Component.translatable("gui.mpsqcamera.team.input"));
        input.setMaxLength(512);
        input.setHint(Component.translatable("gui.mpsqcamera.team.input"));
        if (existing != null) input.setValue(existing.text());
        addRenderableWidget(input);
        audio=addRenderableWidget(new EditBox(font,left,126,WIDTH,20,Component.literal("Sound-ID (optional)")));
        audio.setHint(Component.literal("Sound-ID (optional, z. B. mpsq:ansage.start)"));audio.setMaxLength(128);
        if(existing!=null)audio.setValue(existing.sound());
        speakerButton = addRenderableWidget(Button.builder(speakerText(), button -> toggleSpeaker())
                .bounds(left, 100, WIDTH, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.mpsqcamera.team.templates.save"), button -> save())
                .bounds(left, 152, existing == null ? WIDTH : 174, 20).build());
        if (existing != null) addRenderableWidget(Button.builder(Component.translatable("gui.mpsqcamera.team.templates.delete"), button -> delete())
                .bounds(left + 186, 152, 174, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.mpsqcamera.back"), button -> minecraft.gui.setScreen(parent))
                .bounds(width / 2 - 75, height - 36, 150, 20).build());
        setInitialFocus(input);
    }

    private Component speakerText() { return Component.translatable("gui.mpsqcamera.team.templates.speaker." + speaker.id()); }
    private void toggleSpeaker() {
        speaker = speaker == TeamTemplate.Speaker.OFFICER ? TeamTemplate.Speaker.FRONTMAN : TeamTemplate.Speaker.OFFICER;
        speakerButton.setMessage(speakerText());
    }

    private void save() {
        String text = input.getValue();
        if (text.isBlank()) return;
        if(!audio.getValue().isBlank()&&net.minecraft.resources.Identifier.tryParse(audio.getValue())==null){status="Ungültige Sound-ID";return;}
        com.google.gson.JsonObject body=new com.google.gson.JsonObject();body.addProperty("text",text);body.addProperty("speaker",speaker.id());body.addProperty("soundId",audio.getValue());
        var future=existing==null?MpsqApiClient.post("/team/templates",body):MpsqApiClient.patch("/team/templates/"+existing.id(),body);
        future.whenComplete((ignored, error) -> minecraft.execute(() -> {
            if (error == null) { parent.reload(); minecraft.gui.setScreen(parent); } else status = "!";
        }));
    }

    private void delete() {
        if (existing == null) return;
        MpsqApiClient.deleteTeamTemplate(existing.id()).whenComplete((ignored, error) -> minecraft.execute(() -> {
            if (error == null) { parent.reload(); minecraft.gui.setScreen(parent); } else status = "!";
        }));
    }

    @Override public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta); MpsqTheme.drawBackground(context, width, height);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        int left = width / 2 - WIDTH / 2;
        context.centeredText(font, title, width / 2, 24, MpsqTheme.TEXT_TITEL);
        context.fill(left, 45, left + WIDTH, 47, MpsqTheme.TEXT_GEDAEMPT);
        context.text(font, Component.translatable("gui.mpsqcamera.team.templates.hint"), left, 54, MpsqTheme.TEXT_GEDAEMPT);
        int previewY=192;
        context.enableScissor(Math.max(0,left),186,Math.min(width,left+WIDTH),height-44);
        for(var line:font.split(TeamChatText.fromAmpersandCodes(input.getValue(),net.minecraft.ChatFormatting.WHITE), WIDTH-12)) {
            context.text(font,line,left+6,previewY,0xFFFFFFFF);
            previewY+=12;
        }
        context.disableScissor();
        if (!status.isEmpty()) context.centeredText(font, status, width / 2, 177, 0xFF5555);
    }

    @Override public boolean isPauseScreen() { return false; }
}

