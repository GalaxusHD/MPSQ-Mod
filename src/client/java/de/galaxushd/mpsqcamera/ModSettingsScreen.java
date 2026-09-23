package de.galaxushd.mpsqcamera;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Einstellungs-Screen mit drei Bereichen:
 *  1. Aktivierungs-Item  – Item-ID für das Erstellungs-Werkzeug
 *  2. Tasten-Belegung    – Öffnet Minecraft-Steuerungsmenü (eigene Kategorie)
 *  3. Lautstärke-Slider  – Globale Wiedergabe-Lautstärke (0 – 100 %)
 */
public class ModSettingsScreen extends Screen {

    private final Screen parent;
    private TextFieldWidget itemField;

    public ModSettingsScreen(Screen parent) {
        super(Text.translatable("gui.mpsqcamera.einstellungen.titel"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx   = this.width / 2;
        int btnW = 220;
        int y    = this.height / 2 - 64;

        // ── 1. Aktivierungs-Item ──────────────────────────────────────────────
        int fieldW = btnW - 56;
        itemField = new TextFieldWidget(
                this.textRenderer,
                cx - btnW / 2, y, fieldW, 20,
                Text.translatable("gui.mpsqcamera.einstellungen.item")
        );
        itemField.setMaxLength(128);
        itemField.setText(ModConfig.toolItemId);
        addDrawableChild(itemField);

        // Reset-Schaltfläche rechts neben dem Feld
        addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.mpsqcamera.einstellungen.item_reset"),
                b -> {
                    ModConfig.toolItemId = "minecraft:ink_sac";
                    itemField.setText(ModConfig.toolItemId);
                }
        ).dimensions(cx - btnW / 2 + fieldW + 4, y, 52, 20).build());
        y += 36;

        // ── 2. Tasten-Belegung öffnen ────────────────────────────────────────
        addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.mpsqcamera.einstellungen.tasten"),
                b -> this.client.setScreen(new ModKeybindsScreen(this))
        ).dimensions(cx - btnW / 2, y, btnW, 20).build());
        y += 36;

        // ── 3. Lautstärke-Slider ─────────────────────────────────────────────
        addDrawableChild(new LautstaerkeSlider(cx - btnW / 2, y, btnW, 20, ModConfig.volume));
        y += 40;

        // ── Schließen / Übernehmen ────────────────────────────────────────────
        addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.mpsqcamera.einstellungen.schliessen"),
                b -> {
                    String text = itemField.getText().trim();
                    if (!text.isEmpty()) ModConfig.toolItemId = text;
                    this.client.setScreen(parent);
                }
        ).dimensions(cx - btnW / 2, y, btnW, 20).build());
    }

    // ── Rendering ────────────────────────────────────────────────────────────

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        int panelW = 280;
        int panelH = 260;
        MpsqTheme.drawPanel(context,
                (this.width - panelW) / 2,
                this.height / 2 - 110,
                panelW, panelH);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int cx   = this.width / 2;
        int btnW = 220;
        int titleY = this.height / 2 - 102;

        // Titel
        context.drawCenteredTextWithShadow(
                this.textRenderer, this.title,
                cx, titleY, MpsqTheme.TEXT_TITEL);
        context.fill(cx - 120, titleY + 13, cx + 120, titleY + 14, 0x66888888);

        // Abschnitts-Beschriftungen (oberhalb der jeweiligen Widgets)
        int y = this.height / 2 - 64;
        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("gui.mpsqcamera.einstellungen.item_label"),
                cx - btnW / 2, y - 11, MpsqTheme.TEXT_NORMAL);

        y += 36;
        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("gui.mpsqcamera.einstellungen.tasten_label"),
                cx - btnW / 2, y - 11, MpsqTheme.TEXT_NORMAL);

        y += 36;
        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("gui.mpsqcamera.einstellungen.lautstaerke_label"),
                cx - btnW / 2, y - 11, MpsqTheme.TEXT_NORMAL);
    }

    @Override public void removed(){if(itemField!=null&&net.minecraft.util.Identifier.tryParse(itemField.getText())!=null)ModConfig.toolItemId=itemField.getText();ModConfig.save();}
    @Override
    public boolean shouldPause() { return false; }

    // ── Innere Klassen ────────────────────────────────────────────────────────

    /** Lautstärke-Slider 0 % – 100 % */
    private static final class LautstaerkeSlider extends SliderWidget {

        LautstaerkeSlider(int x, int y, int width, int height, float initial) {
            super(x, y, width, height, Text.empty(), initial);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal("Lautstärke: " + (int) (this.value * 100) + " %"));
        }

        @Override
        protected void applyValue() {
            ModConfig.volume = (float) this.value;
            MpsqAudioManager.setVolume(ModConfig.volume);
        }
    }
}

