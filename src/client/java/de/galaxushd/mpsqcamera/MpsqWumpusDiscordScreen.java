package de.galaxushd.mpsqcamera;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.net.URI;

/** Image-only Discord invitation opened by right-clicking the Wumpus NPC. */
public final class MpsqWumpusDiscordScreen extends Screen {
    private static final Identifier BANNER = Identifier.of("mpsqcamera", "textures/gui/wumpus_discord_banner.png");
    private static final String INVITE_URL = "https://discord.gg/x8xTsxtVS9";
    private static final int IMAGE_WIDTH = 1536;
    private static final int IMAGE_HEIGHT = 1024;

    public MpsqWumpusDiscordScreen(Screen parent) {
        super(Text.literal("Discord"));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xFF080A12);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int[] rect = imageRect();
        context.drawTexture(RenderPipelines.GUI_TEXTURED, BANNER, rect[0], rect[1], 0, 0,
                rect[2], rect[3], IMAGE_WIDTH, IMAGE_HEIGHT, IMAGE_WIDTH, IMAGE_HEIGHT);
    }

    private int[] imageRect() {
        float scale = Math.min((width - 24f) / IMAGE_WIDTH, (height - 24f) / IMAGE_HEIGHT);
        scale = Math.max(0.1f, scale);
        int drawWidth = Math.round(IMAGE_WIDTH * scale);
        int drawHeight = Math.round(IMAGE_HEIGHT * scale);
        return new int[]{(width - drawWidth) / 2, (height - drawHeight) / 2, drawWidth, drawHeight};
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int[] rect = imageRect();
        double relativeX = (mouseX - rect[0]) / rect[2];
        double relativeY = (mouseY - rect[1]) / rect[3];
        // The invitation button occupies the lower middle of the supplied 1536×1024 banner.
        if (relativeX >= 0.30 && relativeX <= 0.70 && relativeY >= 0.73 && relativeY <= 0.92) {
            if (client != null) client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            Util.getOperatingSystem().open(URI.create(INVITE_URL));
            return true;
        }
        return true;
    }

    @Override public boolean shouldPause() { return false; }
}
