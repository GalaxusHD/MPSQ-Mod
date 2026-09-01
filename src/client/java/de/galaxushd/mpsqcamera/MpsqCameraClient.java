package de.galaxushd.mpsqcamera;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MpsqCameraClient implements ClientModInitializer {
    public static final String MOD_ID = "mpsqcamera";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("[MPSQ Team] Client mod initialized.");
        ScreenCreationManager.initialize();
        CameraCreationManager.initialize();
        BodycamRequestManager.initialize();
        CameraHologramManager.initialize();
        SelectionRenderer.initialize();
        ScreenRenderer.initialize();
        RemoteCameraFrameManager.initialize();
		TeamCommandManager.initialize();
		TeamChatRelayManager.initialize();
        CameraUsageHud.initialize();
        CinemaBrowserManager.initialize();
        MobileCameraManager.initialize();
        // Load the rank cache independently. A temporary camera or screen API
        // error must never prevent MPSQ nametags from replacing server ranks.
        var initialization = MpsqApiClient.initialize();
        initialization.thenCompose(ignored -> MpsqApiClient.refreshTeamProfile())
                .thenCompose(ignored -> MpsqApiClient.refreshTeamMembers())
                .exceptionally(error -> {
                    LOGGER.warn("MPSQ-Teamränge konnten nicht geladen werden", error);
                    return null;
                });
        initialization.thenCompose(ignored -> MpsqApiClient.refreshCameras())
                .thenCompose(ignored -> ScreenSyncManager.refresh())
                .exceptionally(error -> {
                    LOGGER.warn("MPSQ-Kameras oder Bildschirme konnten nicht geladen werden", error);
                    return null;
                });
        // The server's name prefix can be rendered before a menu is ever
        // opened. Refresh the public team cache on every world join so the
        // label mixin always has the MPSQ rank available to replace it.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                MpsqApiClient.initialize()
                        .thenCompose(ignored -> MpsqApiClient.refreshTeamProfile())
                        .thenCompose(ignored -> MpsqApiClient.refreshTeamMembers())
                        .exceptionally(error -> {
                            LOGGER.debug("MPSQ-Teamränge konnten beim Weltbeitritt nicht geladen werden", error);
                            return null;
                        }));
    }
}
