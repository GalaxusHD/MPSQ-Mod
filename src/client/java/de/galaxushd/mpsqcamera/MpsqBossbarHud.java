package de.galaxushd.mpsqcamera;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Renders active server-controlled MPSQ bossbars above the vanilla HUD. */
public final class MpsqBossbarHud {
    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 5;
    private MpsqBossbarHud() { }
    public static void initialize() {
        HudElementRegistry.addLast(net.minecraft.resources.Identifier.fromNamespaceAndPath("mpsqcamera", "game_bossbars"), (context, tickDelta) -> render(context));
    }
    private static void render(GuiGraphicsExtractor context) { if(!TeamVisibilitySettings.inArea())return;
        Minecraft client = Minecraft.getInstance();
        int y = 12;
        for (MpsqBossbarState state : MpsqBossbarManager.all()) {
            if (!state.visible()) continue;
            // Keep the game-phase meter below the ordinary MPSQ bar row.
            if ("rlgl_phase".equals(state.id())) y = Math.max(34, y - 6);
            int width = Math.min(BAR_WIDTH, client.getWindow().getGuiScaledWidth() - 20);
            int left = (client.getWindow().getGuiScaledWidth() - width) / 2;
            int barY = y + 10;
            float value=Math.max(0f,Math.min(1f,state.value()));
            float preciseFilled=width*value;
            int filled=(int)preciseFilled;
            float fractionalPixel=preciseFilled-filled;
            Identifier background = barTexture(state.color(), "background");
            Identifier progress = barTexture(state.color(), "progress");
            context.blitSprite(RenderPipelines.GUI_TEXTURED, background, left, barY, width, BAR_HEIGHT);
            if (filled > 0) {
                context.blitSprite(RenderPipelines.GUI_TEXTURED, progress, BAR_WIDTH, BAR_HEIGHT,
                        0, 0, left, barY, filled, BAR_HEIGHT);
            }
            if(fractionalPixel>0f&&filled<width){
                context.pose().pushMatrix();
                context.pose().translate(left+filled,barY);
                context.pose().scale(fractionalPixel,1f);
                context.blitSprite(RenderPipelines.GUI_TEXTURED,progress,BAR_WIDTH,BAR_HEIGHT,
                        filled,0,0,0,1,BAR_HEIGHT);
                context.pose().popMatrix();
            }
            context.centeredText(client.font,
                    TeamChatText.fromAmpersandCodes(state.title(), ChatFormatting.WHITE),
                    client.getWindow().getGuiScaledWidth() / 2, y, 0xFFFFFFFF);
            y += "rlgl_phase".equals(state.id()) ? 23 : 28;
        }
    }
    private static Identifier barTexture(String color, String part) {
        String safeColor = switch (String.valueOf(color).toLowerCase()) {
            case "blue", "green", "pink", "purple", "red", "white", "yellow" -> color.toLowerCase();
            default -> "purple";
        };
        return Identifier.withDefaultNamespace("boss_bar/" + safeColor + "_" + part);
    }
}
