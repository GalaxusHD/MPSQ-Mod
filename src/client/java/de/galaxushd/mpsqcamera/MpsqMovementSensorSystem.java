package de.galaxushd.mpsqcamera;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Client-side movement sensor used by the MPSQ Redstone SWITCH_SYSTEM action. */
public final class MpsqMovementSensorSystem {
    public static final String SYSTEM_ID = "bewegungssensor";
    private static final long ACTIVITY_GLOW_MS = 900L;
    private static final int RESTING_GLOW = 0xFFFFFF;
    private static final int ACTIVE_GLOW = 0xFF5555;
    private static final Map<UUID, Sample> SAMPLES = new HashMap<>();
    private static final Map<UUID, Long> ACTIVE_UNTIL = new HashMap<>();
    private static volatile boolean active;
    private static boolean wasAttackPressed;
    private static boolean wasUsePressed;

    private MpsqMovementSensorSystem() { }

    public static void initialize() {
        MpsqSystemController.register(SYSTEM_ID, new MpsqSystemController.Listener() {
            @Override public void onActivated() { activate(); }
            @Override public void onDeactivated() { deactivate(); }
        });
        ClientTickEvents.END_CLIENT_TICK.register(MpsqMovementSensorSystem::tick);
    }

    public static boolean isActive() { return active; }

    public static int outlineColor(AbstractClientPlayerEntity player) {
        Long until = ACTIVE_UNTIL.get(player.getUuid());
        return until != null && System.currentTimeMillis() < until ? ACTIVE_GLOW : RESTING_GLOW;
    }

    private static synchronized void activate() {
        active = true;
        SAMPLES.clear();
        ACTIVE_UNTIL.clear();
        wasAttackPressed = false;
        wasUsePressed = false;
        seedSamples(MinecraftClient.getInstance());
    }

    private static synchronized void deactivate() {
        active = false;
        SAMPLES.clear();
        ACTIVE_UNTIL.clear();
        wasAttackPressed = false;
        wasUsePressed = false;
    }

    private static void tick(MinecraftClient client) {
        if (!active || !TeamVisibilitySettings.visible() || client.world == null || client.player == null) {
            if (active && (!TeamVisibilitySettings.visible() || client.world == null)) deactivate();
            return;
        }

        Set<UUID> seen = new HashSet<>();
        for (PlayerEntity entity : client.world.getPlayers()) {
            if (!(entity instanceof AbstractClientPlayerEntity player)) continue;
            UUID id = player.getUuid();
            seen.add(id);
            Vec3d position = player.getPos();
            boolean sneaking = player.isSneaking();
            boolean swinging = player.isHandSwinging();
            Sample previous = SAMPLES.put(id, new Sample(position, sneaking, swinging));
            if (previous == null) continue;

            if (position.squaredDistanceTo(previous.position) > 0.00001D
                    || sneaking != previous.sneaking
                    || (swinging && !previous.swinging)) {
                markActive(id);
            }
        }
        SAMPLES.keySet().removeIf(id -> !seen.contains(id));
        ACTIVE_UNTIL.keySet().removeIf(id -> !seen.contains(id));

        boolean attackPressed = client.options.attackKey.isPressed();
        boolean usePressed = client.options.useKey.isPressed();
        if ((attackPressed && !wasAttackPressed) || (usePressed && !wasUsePressed)) {
            markActive(client.player.getUuid());
        }
        wasAttackPressed = attackPressed;
        wasUsePressed = usePressed;
    }

    private static void seedSamples(MinecraftClient client) {
        if (client.world == null) return;
        for (PlayerEntity entity : client.world.getPlayers()) {
            if (!(entity instanceof AbstractClientPlayerEntity player)) continue;
            SAMPLES.put(player.getUuid(), new Sample(player.getPos(), player.isSneaking(), player.isHandSwinging()));
        }
    }

    private static synchronized void markActive(UUID playerId) {
        ACTIVE_UNTIL.put(playerId, System.currentTimeMillis() + ACTIVITY_GLOW_MS);
    }

    private record Sample(Vec3d position, boolean sneaking, boolean swinging) { }
}
