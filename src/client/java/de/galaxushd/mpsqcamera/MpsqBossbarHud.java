package de.galaxushd.mpsqcamera;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.Formatting;

/** Renders active server-controlled MPSQ bossbars above the vanilla HUD. */
public final class MpsqBossbarHud {
    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 5;
    private MpsqBossbarHud() { }
    public static void initialize() {
        HudRenderCallback.EVENT.register((context, tickDelta) -> render(context));
    }
    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        int y = 12;
        for (MpsqBossbarState state : MpsqBossbarManager.all()) {
            if (!state.visible()) continue;
            int width = Math.min(BAR_WIDTH, client.getWindow().getScaledWidth() - 20);
            int left = (client.getWindow().getScaledWidth() - width) / 2;
            int barY = y + 10;
            float value=Math.max(0f,Math.min(1f,state.value()));
            float preciseFilled=width*value;
            int filled=(int)preciseFilled;
            float fractionalPixel=preciseFilled-filled;
            Identifier background = barTexture(state.color(), "background");
            Identifier progress = barTexture(state.color(), "progress");
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, background, left, barY, width, BAR_HEIGHT);
            int customColor=customBarColor(state.color());
            if(customColor!=0&&filled>0) {
                context.fill(left,barY,left+filled,barY+BAR_HEIGHT,customColor);
            } else if (filled > 0) {
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, progress, BAR_WIDTH, BAR_HEIGHT,
                        0, 0, left, barY, filled, BAR_HEIGHT);
            }
            if(customColor!=0&&fractionalPixel>0f&&filled<width){
                context.getMatrices().pushMatrix();
                context.getMatrices().translate(left+filled,barY);
                context.getMatrices().scale(fractionalPixel,1f);
                context.fill(0,0,1,BAR_HEIGHT,customColor);
                context.getMatrices().popMatrix();
            } else if(fractionalPixel>0f&&filled<width){
                context.getMatrices().pushMatrix();
                context.getMatrices().translate(left+filled,barY);
                context.getMatrices().scale(fractionalPixel,1f);
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED,progress,BAR_WIDTH,BAR_HEIGHT,
                        filled,0,0,0,1,BAR_HEIGHT);
                context.getMatrices().popMatrix();
            }
            context.drawCenteredTextWithShadow(client.textRenderer,
                    TeamChatText.fromAmpersandCodes(state.title(), Formatting.WHITE),
                    client.getWindow().getScaledWidth() / 2, y, 0xFFFFFFFF);
            y += 28;
        }
    }
    private static Identifier barTexture(String color, String part) {
        String safeColor = switch (String.valueOf(color).toLowerCase()) {
            case "blue", "green", "pink", "purple", "red", "white", "yellow" -> color.toLowerCase();
            default -> "purple";
        };
        return Identifier.ofVanilla("boss_bar/" + safeColor + "_" + part);
    }
    private static int customBarColor(String color) {
        return switch(String.valueOf(color).toLowerCase(java.util.Locale.ROOT)) {
            case "pink" -> 0xFFEC2F53;
            case "red" -> 0xFFCF2020;
            default -> 0;
        };
    }
}
