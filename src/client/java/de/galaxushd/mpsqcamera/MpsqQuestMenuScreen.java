package de.galaxushd.mpsqcamera;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Visual-only quest menu shell; quest entries and reward data are added later. */
public final class MpsqQuestMenuScreen extends Screen {
    private static final int ART_WIDTH = 352;
    private static final int ART_HEIGHT = 250;
    private static final Identifier BACKGROUND = Identifier.of(
            MpsqCameraClient.MOD_ID, "textures/gui/mpsq_quest_menu.png");
    private static final Identifier EMPTY_REWARD_CHEST = Identifier.of(
            MpsqCameraClient.MOD_ID, "textures/gui/mpsq_quest_reward_open.png");

    private final Screen parent;
    private float drawScale;
    private int drawLeft;
    private int drawTop;

    public MpsqQuestMenuScreen(Screen parent) {
        super(Text.literal("Quest"));
        this.parent = parent;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        context.fillGradient(0, 0, width, height, 0xD91A1A1A, 0xE6050505);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        drawScale = Math.min(1.0f, Math.min((width - 16.0f) / ART_WIDTH,
                (height - 16.0f) / ART_HEIGHT));
        drawScale = Math.max(0.1f, drawScale);
        int drawWidth = Math.round(ART_WIDTH * drawScale);
        int drawHeight = Math.round(ART_HEIGHT * drawScale);
        drawLeft = (width - drawWidth) / 2;
        drawTop = (height - drawHeight) / 2;

        context.drawTexture(RenderPipelines.GUI_TEXTURED, BACKGROUND,
                drawLeft, drawTop, 0, 0, drawWidth, drawHeight,
                ART_WIDTH, ART_HEIGHT, ART_WIDTH, ART_HEIGHT);
        int chestX = drawLeft + Math.round(160 * drawScale);
        int chestY = drawTop + Math.round(216 * drawScale);
        int chestSize = Math.max(1, Math.round(32 * drawScale));
        context.drawTexture(RenderPipelines.GUI_TEXTURED, EMPTY_REWARD_CHEST,
                chestX, chestY, 0, 0, chestSize, chestSize, 32, 32, 32, 32);

    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || drawScale <= 0) return super.mouseClicked(mouseX, mouseY, button);

        double x = (mouseX - drawLeft) / drawScale;
        double y = (mouseY - drawTop) / drawScale;
        if (inside(x, y, 51, 72, 105, 34)) {
            return true;
        }
        if (inside(x, y, 195, 72, 106, 34)) {
            return true;
        }
        if (inside(x, y, 14, 106, 324, 108)) {
            // The quest grid is intentionally empty in this visual-only stage.
            return true;
        }
        if (inside(x, y, 15, 216, 34, 33)) {
            close();
            return true;
        }
        if (inside(x, y, 307, 213, 34, 36)) {
            client.setScreen(new MpsqCollectionTutorialScreen(this, "Quests"));
            return true;
        }
        return true;
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
