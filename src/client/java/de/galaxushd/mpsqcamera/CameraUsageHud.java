package de.galaxushd.mpsqcamera;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Zeigt unten links an, welche Kameras gerade verwendet werden. */
public final class CameraUsageHud {
    private static List<CameraUsage> active = List.of();
    private static int ticks;
    private static boolean loading;

    private CameraUsageHud() {
    }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(CameraUsageHud::tick);
        HudElementRegistry.addLast(net.minecraft.resources.Identifier.fromNamespaceAndPath("mpsqcamera", "camera_usage"), (context, tickDelta) -> render(context));
    }

    private static void tick(Minecraft client) {
        if (client.player == null || !TeamVisibilitySettings.inArea() || loading || !MpsqApiClient.isReady()) {
            return;
        }

        if (++ticks < 20) {
            return;
        }

        ticks = 0;
        TeamProfile self = TeamStateStore.self().orElse(null);
        if (self == null || !self.canOpenTeamArea()) {
            active = List.of();
            return;
        }

        loading = true;
        MpsqApiClient.loadCameraUsage().whenComplete((usages, error) -> client.execute(() -> {
            loading = false;
            if (error == null && usages != null) {
                active = usages;
            }
        }));
    }

    private static void render(net.minecraft.client.gui.GuiGraphicsExtractor context) {
        if (!TeamVisibilitySettings.inArea() || active.isEmpty()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        // Untere linke Ecke, oberhalb von Chat und Hotbar.
        int y = context.guiHeight() - 50;

        for (int i = active.size() - 1; i >= 0; i--) {
            CameraUsage usage = active.get(i);
            Component text = Component.translatable(
                    "gui.mpsqcamera.team.camera_usage",
                    usage.cameraName()
            );

            // Minecraft-Grün / §a
            context.text(client.font, text, 6, y, 0x55FF55);
            y -= 11;
        }
    }
}
