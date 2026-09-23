package de.galaxushd.mpsqcamera;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.MinecraftClient;

/** Renders active server-controlled MPSQ bossbars above the vanilla HUD. */
public final class MpsqBossbarHud {
    private MpsqBossbarHud() { }
    public static void initialize() {
        HudRenderCallback.EVENT.register((context, tickDelta) -> render(context));
    }
    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        int y = 12;
        for (MpsqBossbarState state : MpsqBossbarManager.all()) {
            if (!state.visible()) continue;
            int width = Math.min(320, client.getWindow().getScaledWidth() - 40);
            int left = (client.getWindow().getScaledWidth() - width) / 2;
            int filled = Math.max(0, Math.min(width, Math.round(width * state.value())));
            context.fill(left, y, left + width, y + 8, 0xAA222222);
            context.fill(left, y, left + filled, y + 8, color(state.color()));
            context.drawCenteredTextWithShadow(client.textRenderer, state.title(), client.getWindow().getScaledWidth() / 2, y - 12, 0xFFFFFFFF);
            y += 28;
        }
    }
    private static int color(String value) {
        return switch (String.valueOf(value).toLowerCase()) {
            case "red" -> 0xFFCC3333; case "blue" -> 0xFF3366CC; case "green" -> 0xFF33AA55;
            case "yellow" -> 0xFFE0B52A; default -> 0xFF9933CC;
        };
    }
}
