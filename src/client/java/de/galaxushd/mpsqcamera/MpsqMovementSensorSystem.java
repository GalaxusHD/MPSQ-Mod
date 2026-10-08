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

/** Client-side Red Light, Green Light phase and movement sensor. */
public final class MpsqMovementSensorSystem {
    public static final String SYSTEM_ID = "red_light_green_light";
    private static final long GREEN_COUNTDOWN_MS = 5_000L;
    private static final int WAITING_GLOW = 0xFFFFFF;
    private static final int GREEN_GLOW = 0x55FF55;
    private static final int MOVED_GLOW = 0xFF5555;
    private static final Map<UUID, Sample> SAMPLES = new HashMap<>();
    private static final Set<UUID> MOVED_DURING_RED = new HashSet<>();
    private static volatile boolean active;
    private static volatile Phase phase = Phase.WAITING;
    private static volatile long greenUntil;
    private static volatile long greenDurationMs = GREEN_COUNTDOWN_MS;

    private enum Phase { WAITING, GREEN, RED }

    private MpsqMovementSensorSystem() { }

    public static void initialize() {
        MpsqSystemController.Listener listener = new MpsqSystemController.Listener() {
            @Override public void onActivated() { activate(); }
            @Override public void onDeactivated() { deactivate(); }
        };
        MpsqSystemController.register(SYSTEM_ID, "Red Light, Green Light", listener);
        MpsqSystemController.registerLegacyAlias("bewegungssensor", listener);
        ClientTickEvents.END_CLIENT_TICK.register(MpsqMovementSensorSystem::tick);
    }

    public static boolean isActive() { return active; }

    /** Green-light signal: white outlines during the configurable short countdown, then red phase. */
    public static void beginGreenCountdown(int seconds) {
        if (!active) return;
        int clamped = Math.max(1, Math.min(120, seconds));
        phase = Phase.GREEN;
        greenDurationMs = clamped * 1000L;
        greenUntil = System.currentTimeMillis() + greenDurationMs;
        SAMPLES.clear();
        MpsqBossbarManager.apply(new MpsqBossbarState("rlgl_phase", "Red Light, Green Light · Grün", "green", 1f, true));
    }

    /** Red-light signal from the redstone circuit. */
    public static void beginRedPhase() {
        if (!active) return;
        phase = Phase.RED;
        greenUntil = 0L;
        SAMPLES.clear();
        seedSamples(MinecraftClient.getInstance());
        updateBossbar(System.currentTimeMillis());
    }

    public static boolean shouldOutline(AbstractClientPlayerEntity player) {
        TeamRank rank = TeamStateStore.byMinecraftName(player.getGameProfile().getName())
                .map(TeamProfile::permissionRank).orElse(TeamRank.PLAYER);
        return rank == TeamRank.PLAYER || rank == TeamRank.STREAMER || rank == TeamRank.VIP;
    }

    public static int outlineColor(AbstractClientPlayerEntity player) {
        if (TeamStateStore.byMinecraftName(player.getGameProfile().getName())
                .map(profile -> profile.permissionRank() == TeamRank.VIP).orElse(false)) return WAITING_GLOW;
        if (phase != Phase.RED) return WAITING_GLOW;
        return MOVED_DURING_RED.contains(player.getUuid()) ? MOVED_GLOW : GREEN_GLOW;
    }

    /** Shared preview color for world entities; player colors remain rank/movement-aware. */
    public static int worldEntityOutlineColor() { return phase == Phase.GREEN ? GREEN_GLOW : WAITING_GLOW; }

    private static synchronized void activate() {
        active = true;
        phase = Phase.WAITING;
        greenUntil = 0L;
        SAMPLES.clear();
        MOVED_DURING_RED.clear();
        seedSamples(MinecraftClient.getInstance());
        updateBossbar(System.currentTimeMillis());
        showActivationTitle();
    }

    private static synchronized void deactivate() {
        active = false;
        phase = Phase.WAITING;
        greenUntil = 0L;
        SAMPLES.clear();
        MOVED_DURING_RED.clear();
        MpsqBossbarManager.remove("rlgl_phase");
    }

    private static void tick(MinecraftClient client) {
        if (!active || !TeamVisibilitySettings.visible() || client.world == null || client.player == null) {
            if (active && (!TeamVisibilitySettings.visible() || client.world == null)) deactivate();
            return;
        }

        long now = System.currentTimeMillis();
        if (phase == Phase.GREEN && now >= greenUntil) beginRedPhase();
        updateBossbar(now);

        if (phase != Phase.RED) return;

        Set<UUID> seen = new HashSet<>();
        for (PlayerEntity entity : client.world.getPlayers()) {
            if (!(entity instanceof AbstractClientPlayerEntity player)) continue;
            if (!shouldOutline(player)) {
                MOVED_DURING_RED.remove(player.getUuid());
                SAMPLES.remove(player.getUuid());
                continue;
            }
            UUID id = player.getUuid();
            seen.add(id);
            Vec3d position = player.getPos();
            Sample previous = SAMPLES.put(id, new Sample(position));
            if (previous == null) continue;

            if (position.squaredDistanceTo(previous.position) > 0.0004D) MOVED_DURING_RED.add(id);
        }
        SAMPLES.keySet().removeIf(id -> !seen.contains(id));
        MOVED_DURING_RED.removeIf(id -> !seen.contains(id));
    }

    private static void seedSamples(MinecraftClient client) {
        if (client.world == null) return;
        for (PlayerEntity entity : client.world.getPlayers()) {
            if (!(entity instanceof AbstractClientPlayerEntity player)) continue;
            SAMPLES.put(player.getUuid(), new Sample(player.getPos()));
        }
    }

    private static void updateBossbar(long now) {
        if (!active) return;
        if (phase == Phase.GREEN && now < greenUntil) {
            long remaining = greenUntil - now;
            MpsqBossbarManager.apply(new MpsqBossbarState("rlgl_phase", "Red Light, Green Light · Grünphase", "green",
                    Math.max(0f, Math.min(1f, (float) remaining / greenDurationMs)), true));
        } else {
            if (phase == Phase.GREEN) beginRedPhase();
            MpsqBossbarManager.apply(new MpsqBossbarState("rlgl_phase", "Red Light, Green Light · Rotphase", "red", 1f, true));
        }
    }

    private static void showActivationTitle() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        TeamRank rank = TeamStateStore.self().map(TeamProfile::permissionRank).orElse(TeamRank.PLAYER);
        if (rank != TeamRank.WORKER && rank != TeamRank.SOLDIER && rank != TeamRank.OFFICER
                && rank != TeamRank.SENIOR_OFFICER && rank != TeamRank.FRONTMAN) return;
        client.inGameHud.setTitle(net.minecraft.text.Text.literal("Red Light, Green Light"));
        client.inGameHud.setSubtitle(net.minecraft.text.Text.literal("Ist aktiviert"));
        client.inGameHud.setTitleTicks(10, 70, 20);
    }

    private record Sample(Vec3d position) { }
}
