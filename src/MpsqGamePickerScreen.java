package de.galaxushd.mpsqcamera;

import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Ordered catalogue; unavailable games cannot dispatch actions. */
public final class MpsqGamePickerScreen extends Screen {
    private final Screen parent;
    private final IntConsumer select;
    private final boolean zonesOnly;
    public MpsqGamePickerScreen(Screen parent, IntConsumer select) {
        this(parent, select, false);
    }
    public MpsqGamePickerScreen(Screen parent, IntConsumer select, boolean zonesOnly) {
        super(Component.literal("Spielsystem auswählen"));
        this.parent = parent;
        this.select = select;
        this.zonesOnly = zonesOnly;
    }
    @Override protected void init() {
        var games = MpsqSystemController.availableSystems();
        int columnWidth = Math.min(220, (width - 30) / 2);
        int step=Math.min(24,Math.max(22,(height-102)/7));
        for (int i = 0; i < games.size(); i++) {
            int index = i;
            var game = games.get(i);
            var button = addRenderableWidget(Button.builder(Component.literal(game.label()), b -> {
                select.accept(index);
                onClose();
            }).bounds(i == 0 ? width / 2 - columnWidth / 2 : width / 2 - columnWidth - 4 + (i <= 5 ? 0 : columnWidth + 8),
                    i == 0 ? 32 : 32 + (i <= 5 ? i : i - 5) * step, columnWidth, 20).build());
            button.active = MpsqSystemController.implemented(game.id()) && (!zonesOnly || MpsqZoneCatalog.KINDS.stream().anyMatch(k -> k.game().equals(game.id())));
        }
        addRenderableWidget(Button.builder(Component.literal("Schach"),b->MpsqChess.open()).bounds(width/2-columnWidth/2,32+6*step,columnWidth,20).build());
        if(zonesOnly){
            addRenderableWidget(Button.builder(Component.literal("Gesamtfläche auswählen"),b->{MpsqFinishZoneTool.selectMaster();onClose();}).bounds(width/2-204,height-52,200,20).build());
            addRenderableWidget(Button.builder(Component.literal("Kamera auslösen"),b->{MpsqFinishZoneTool.selectCamera();onClose();}).bounds(width/2+4,height-52,200,20).build());
            addRenderableWidget(Button.builder(Component.literal("Editor: "+(MpsqFinishZoneTool.editorEnabled()?"An":"Aus")),b->{MpsqFinishZoneTool.toggleEditor();b.setMessage(Component.literal("Editor: "+(MpsqFinishZoneTool.editorEnabled()?"An":"Aus")));}).bounds(width/2+4,height-28,120,20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Zurück"), b -> onClose()).bounds(width / 2 - (zonesOnly?124:60), height - 28, 120, 20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor context, int x, int y, float delta) {
        super.extractRenderState(context, x, y, delta);
        context.centeredText(font, title, width / 2, 14, 0xFFFFFFFF);
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
