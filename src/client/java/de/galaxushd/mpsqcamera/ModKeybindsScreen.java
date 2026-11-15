package de.galaxushd.mpsqcamera;


import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Zeigt nur die Tastenbelegungen der MPSQ-Kameras-Mod an und erlaubt das
 * direkte Neubinden per Klick auf den jeweiligen Button.
 */
public class ModKeybindsScreen extends Screen {

    private static final KeyMapping.Category MOD_CATEGORY = MpsqKeyCategories.MAIN;

    private final Screen parent;
    private final List<KeyMapping> modKeys = new ArrayList<>();
    private KeyMapping pendingRebind = null;

    public ModKeybindsScreen(Screen parent) {
        super(Component.literal("Tastenbelegung – MPSQ Team"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        modKeys.clear();
        for (KeyMapping key : this.minecraft.options.keyMappings) {
            if (MOD_CATEGORY.equals(key.getCategory())) {
                modKeys.add(key);
            }
        }

        int cx      = this.width / 2;
        int btnW    = 130;
        int startY  = this.height / 2 - modKeys.size() * 14;

        for (KeyMapping key : modKeys) {
            final KeyMapping kb = key;
            boolean isPending = (pendingRebind == kb);

            Component btnText = isPending
                    ? Component.literal("› Taste drücken ‹")
                    : kb.getTranslatedKeyMessage();

            int rowY = startY + modKeys.indexOf(key) * 28;

            addRenderableWidget(Button.builder(btnText, b -> {
                pendingRebind = kb;
                rebuildWidgets();
            }).bounds(cx + 10, rowY, btnW, 20).build());
        }

        // Alle zurücksetzen
        addRenderableWidget(Button.builder(
                Component.literal("Alle zurücksetzen"),
                b -> {
                    for (KeyMapping k : modKeys) k.setKey(k.getDefaultKey());
                    KeyMapping.resetMapping();
                    pendingRebind = null;
                    rebuildWidgets();
                }
        ).bounds(cx - 155, this.height - 28, 150, 20).build());

        // Zurück
        addRenderableWidget(Button.builder(
                Component.literal("Zurück"),
                b -> {
                    pendingRebind = null;
                    this.minecraft.gui.setScreen(parent);
                }
        ).bounds(cx + 5, this.height - 28, 150, 20).build());
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
 int keyCode=event.key(), scanCode=event.keycode(), modifiers=event.modifiers();

        if (pendingRebind != null) {
            if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
                pendingRebind.setKey(InputConstants.UNKNOWN);
            } else {
                pendingRebind.setKey(InputConstants.Type.KEYBOARD.getOrCreate(keyCode));
            }
            KeyMapping.resetMapping();
            pendingRebind = null;
            rebuildWidgets();
            return true;
        }
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
            this.minecraft.gui.setScreen(parent);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();

        if (pendingRebind != null) {
            pendingRebind.setKey(InputConstants.Type.MOUSE.getOrCreate(button));
            KeyMapping.resetMapping();
            pendingRebind = null;
            rebuildWidgets();
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        MpsqTheme.drawBackground(context, this.width, this.height);

        int panelW = 380;
        int panelH = modKeys.size() * 28 + 70;
        MpsqTheme.drawPanel(context,
                (this.width - panelW) / 2,
                this.height / 2 - modKeys.size() * 14 - 30,
                panelW, panelH);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        int cx     = this.width / 2;
        int startY = this.height / 2 - modKeys.size() * 14;

        // Titel
        context.centeredText(
                this.font, this.title,
                cx, startY - 22, MpsqTheme.TEXT_TITEL);

        // Tastenbelegungs-Labels (linke Spalte)
        for (int i = 0; i < modKeys.size(); i++) {
            KeyMapping key = modKeys.get(i);
            int rowY = startY + i * 28 + 5;

            // Hervorhebung wenn Taste gerade umbelegt wird
            if (pendingRebind == key) {
                context.fill(cx - 170, rowY - 2, cx + 145, rowY + 16, 0x33FFFFFF);
            }

            context.text(
                    this.font,
                    Component.translatable(key.getName()),
                    cx - 170, rowY,
                    key.isDefault() ? MpsqTheme.TEXT_NORMAL : MpsqTheme.TEXT_TITEL);
        }

        // Hinweis wenn im Warte-Modus
        if (pendingRebind != null) {
            context.centeredText(
                    this.font,
                    Component.literal("Drücke eine Taste oder Maustaste • ESC zum Löschen"),
                    cx, this.height - 55, MpsqTheme.TEXT_GEDAEMPT);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
