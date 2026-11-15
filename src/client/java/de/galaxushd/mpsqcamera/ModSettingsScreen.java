package de.galaxushd.mpsqcamera;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Einstellungs-Screen mit drei Bereichen:
 *  1. Aktivierungs-Item  – Item-ID für das Erstellungs-Werkzeug
 *  2. Zonen-Werkzeug – Item-ID für die Bereichsauswahl
 *  3. Tasten-Belegung – Öffnet die eigene Steuerung
 */
public class ModSettingsScreen extends Screen {

    private final Screen parent;
    private EditBox itemField;
    private EditBox zoneItemField;

    public ModSettingsScreen(Screen parent) {
        super(Component.translatable("gui.mpsqcamera.einstellungen.titel"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx   = this.width / 2;
        int btnW = 220;
        int y    = this.height / 2 - 64;

        // ── 1. Aktivierungs-Item ──────────────────────────────────────────────
        int fieldW = btnW - 56;
        itemField = new EditBox(
                this.font,
                cx - btnW / 2, y, fieldW, 20,
                Component.translatable("gui.mpsqcamera.einstellungen.item")
        );
        itemField.setMaxLength(128);
        itemField.setValue(ModConfig.toolItemId);
        addRenderableWidget(itemField);

        // Reset-Schaltfläche rechts neben dem Feld
        addRenderableWidget(Button.builder(
                Component.translatable("gui.mpsqcamera.einstellungen.item_reset"),
                b -> {
                    ModConfig.toolItemId = "minecraft:ink_sac";
                    itemField.setValue(ModConfig.toolItemId);
                }
        ).bounds(cx - btnW / 2 + fieldW + 4, y, 52, 20).build());
        y += 36;

        zoneItemField = new EditBox(this.font,cx-btnW/2,y,fieldW,20,
                Component.translatable("gui.mpsqcamera.einstellungen.zone_item_label"));
        zoneItemField.setMaxLength(128);
        zoneItemField.setValue(ModConfig.zoneToolItemId);
        addRenderableWidget(zoneItemField);
        addRenderableWidget(Button.builder(Component.translatable("gui.mpsqcamera.einstellungen.item_reset"),b->{
            zoneItemField.setValue("minecraft:glow_ink_sac");
        }).bounds(cx-btnW/2+fieldW+4,y,52,20).build());
        y += 36;

        // ── 2. Tasten-Belegung öffnen ────────────────────────────────────────
        addRenderableWidget(Button.builder(
                Component.translatable("gui.mpsqcamera.einstellungen.tasten"),
                b -> this.minecraft.gui.setScreen(new ModKeybindsScreen(this))
        ).bounds(cx - btnW / 2, y, btnW, 20).build());
        y += 40;

        // ── Schließen / Übernehmen ────────────────────────────────────────────
        addRenderableWidget(Button.builder(
                Component.translatable("gui.mpsqcamera.einstellungen.schliessen"),
                b -> {
                    this.minecraft.gui.setScreen(parent);
                }
        ).bounds(cx - btnW / 2, y, btnW, 20).build());
    }

    // ── Rendering ────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int panelW = 280;
        int panelH = 260;
        MpsqTheme.drawPanel(context,
                (this.width - panelW) / 2,
                this.height / 2 - 110,
                panelW, panelH);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        int cx   = this.width / 2;
        int btnW = 220;
        int titleY = this.height / 2 - 102;

        // Titel
        context.centeredText(
                this.font, this.title,
                cx, titleY, MpsqTheme.TEXT_TITEL);
        context.fill(cx - 120, titleY + 13, cx + 120, titleY + 14, 0x66888888);

        // Abschnitts-Beschriftungen (oberhalb der jeweiligen Widgets)
        int y = this.height / 2 - 64;
        context.text(this.font,
                Component.translatable("gui.mpsqcamera.einstellungen.item_label"),
                cx - btnW / 2, y - 11, MpsqTheme.TEXT_NORMAL);

        y += 36;
        context.text(this.font,
                Component.translatable("gui.mpsqcamera.einstellungen.zone_item_label"),
                cx - btnW / 2, y - 11, MpsqTheme.TEXT_NORMAL);

        y += 36;
        context.text(this.font,
                Component.translatable("gui.mpsqcamera.einstellungen.tasten_label"),
                cx - btnW / 2, y - 11, MpsqTheme.TEXT_NORMAL);
    }

    @Override public void removed(){
        if(itemField!=null&&validItem(itemField.getValue()))ModConfig.toolItemId=itemField.getValue().trim();
        if(zoneItemField!=null&&validItem(zoneItemField.getValue()))ModConfig.zoneToolItemId=zoneItemField.getValue().trim();
        ModConfig.save();
    }
    private static boolean validItem(String value){
        var id=net.minecraft.resources.Identifier.tryParse(value.trim());
        return id!=null&&!id.equals(net.minecraft.resources.Identifier.withDefaultNamespace("air"))&&net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override
    public boolean isPauseScreen() { return false; }

    // ── Innere Klassen ────────────────────────────────────────────────────────

}
