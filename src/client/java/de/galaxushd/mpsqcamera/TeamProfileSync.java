package de.galaxushd.mpsqcamera;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import java.util.concurrent.atomic.AtomicBoolean;

/** Refresh shared names/ranks even when nobody opens the team menu. */
public final class TeamProfileSync {
    private static final AtomicBoolean IN_FLIGHT = new AtomicBoolean();
    private static long nextRefresh;
    private TeamProfileSync() {}

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || !MpsqApiClient.isReady()) return;
            long now = System.nanoTime();
            if (now < nextRefresh || !IN_FLIGHT.compareAndSet(false, true)) return;
            nextRefresh = now + 5_000_000_000L;
            MpsqApiClient.refreshTeamProfile().thenCompose(ignored -> MpsqApiClient.refreshTeamMembers())
                    .whenComplete((ignored, error) -> {
                        IN_FLIGHT.set(false);
                        if (error != null) MpsqCameraClient.LOGGER.debug("MPSQ-Teamprofil-Abgleich fehlgeschlagen", error);
                    });
        });
    }
}
