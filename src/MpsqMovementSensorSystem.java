package de.galaxushd.mpsqcamera;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
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
    private static final int SAFE_GLOW = 0xFFAA00;
    private static final Map<UUID, Sample> SAMPLES = new HashMap<>();
    private static final Set<UUID> MOVED_DURING_RED = new HashSet<>();
    private static final Set<String> KICKED = new HashSet<>();
    public static void restore(String name){KICKED.remove(name.toLowerCase(java.util.Locale.ROOT));}
    public static void kick(String name){
        KICKED.add(name.toLowerCase(java.util.Locale.ROOT));
        var c=Minecraft.getInstance();if(c.level==null)return;
        for(var p:c.level.players())if(p.getGameProfile().name().equalsIgnoreCase(name)){
            SAMPLES.remove(p.getUUID());STATUSES.remove(p.getUUID());MOVED_DURING_RED.remove(p.getUUID());
        }
    }
    private static final Map<UUID, Status> STATUSES = new HashMap<>();
    private static volatile BlockPos safeCornerOne;
    private static volatile BlockPos safeCornerTwo;
    private static volatile boolean active;
    private static volatile Phase phase = Phase.WAITING;
    private static volatile long greenUntil;
    private static volatile long greenDurationMs = GREEN_COUNTDOWN_MS;

    private enum Phase { WAITING, GREEN, RED }
    private enum Status { ACTIVE, ELIMINATED, SAFE_WINNER }

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

    /** Sets the two opposite corners of the RLGL finish zone for the current world. */
    public static void setSafeZone(BlockPos first, BlockPos second) {
        safeCornerOne = first == null ? null : first.immutable();
        safeCornerTwo = second == null ? null : second.immutable();
    }

    public static void clearSafeZone() { safeCornerOne = null; safeCornerTwo = null; }

    /** Green-light signal: white outlines during the configurable short countdown, then red phase. */
    public static void beginGreenCountdown(int seconds) {
        if (!active) return;
        int clamped = Math.max(1, Math.min(120, seconds));
        phase = Phase.GREEN;
        greenDurationMs = clamped * 1000L;
        greenUntil = System.currentTimeMillis() + greenDurationMs;
        SAMPLES.clear();
        MpsqBossbarManager.apply(new MpsqBossbarState("rlgl_phase", "&aGrünes Licht", "green", 1f, true));
    }

    /** Red-light signal from the redstone circuit. */
    public static void beginRedPhase() {
        if (!active) return;
        phase = Phase.RED;
        greenUntil = 0L;
        SAMPLES.clear();
        seedSamples(Minecraft.getInstance());
        updateBossbar(System.currentTimeMillis());
    }

    public static boolean shouldOutline(AbstractClientPlayer player) {
        if(KICKED.contains(player.getGameProfile().name().toLowerCase(java.util.Locale.ROOT))||!MpsqGamesRuntime.activeParticipant(player))return false;
        TeamRank rank = TeamStateStore.byMinecraftName(player.getGameProfile().name())
                .map(TeamProfile::displayedRank).orElse(TeamRank.PLAYER);
        return !MpsqKickAnimationManager.isClone(player) && (rank == TeamRank.PLAYER || rank == TeamRank.UNDERCOVER_001 || rank == TeamRank.STREAMER || rank == TeamRank.VIP);
    }

    public static int outlineColor(AbstractClientPlayer player) {
        if (STATUSES.get(player.getUUID()) == Status.SAFE_WINNER) return SAFE_GLOW;
        if (TeamStateStore.byMinecraftName(player.getGameProfile().name())
                .map(profile -> profile.displayedRank() == TeamRank.VIP).orElse(false)) return WAITING_GLOW;
        if (MOVED_DURING_RED.contains(player.getUUID())) return MOVED_GLOW;
        return phase == Phase.RED ? GREEN_GLOW : WAITING_GLOW;
    }

    public static boolean shouldOutlineEntity(net.minecraft.world.entity.Entity entity) {
        if (TeamStateStore.self().map(TeamProfile::permissionRank).orElse(TeamRank.PLAYER) == TeamRank.PLAYER) return false;
        return tracked(entity);
    }

    private static boolean tracked(net.minecraft.world.entity.Entity entity) {
        if (MpsqNpcGlow.isNpc(entity))return false;
        if (!active || !TeamVisibilitySettings.inArea() || !MpsqZoneCatalog.inMaster(entity) || !(entity instanceof net.minecraft.world.entity.LivingEntity)
                || entity instanceof ArmorStand || entity.level() != Minecraft.getInstance().level) return false;
        return !(entity instanceof AbstractClientPlayer player)
                || !player.isSpectator() && shouldOutline(player);
    }

    public static int entityOutlineColor(net.minecraft.world.entity.Entity entity) {
        if (entity instanceof AbstractClientPlayer player) return outlineColor(player);
        if (STATUSES.get(entity.getUUID()) == Status.SAFE_WINNER) return SAFE_GLOW;
        if (MOVED_DURING_RED.contains(entity.getUUID())) return MOVED_GLOW;
        return phase == Phase.RED ? GREEN_GLOW : WAITING_GLOW;
    }

    /** Shared preview color for world entities; player colors remain rank/movement-aware. */
    public static int worldEntityOutlineColor() { return phase == Phase.GREEN ? GREEN_GLOW : WAITING_GLOW; }

    private static synchronized void activate() {
        KICKED.clear();
        active = true;
        phase = Phase.WAITING;
        greenUntil = 0L;
        SAMPLES.clear();
        MOVED_DURING_RED.clear();
        STATUSES.clear();
        seedSamples(Minecraft.getInstance());
        updateBossbar(System.currentTimeMillis());
        showActivationTitle();
    }

    private static synchronized void deactivate() {
        KICKED.clear();
        active = false;
        phase = Phase.WAITING;
        greenUntil = 0L;
        SAMPLES.clear();
        MOVED_DURING_RED.clear();
        STATUSES.clear();
        MpsqBossbarManager.remove("rlgl_phase");
    }

    private static void tick(Minecraft client) {
        if (!active || !TeamVisibilitySettings.visible() || client.level == null || client.player == null) {
            if (active && (!TeamVisibilitySettings.visible() || client.level == null)) deactivate();
            return;
        }

        long now = System.currentTimeMillis();
        if (phase == Phase.GREEN && now >= greenUntil) beginRedPhase();
        updateBossbar(now);


        Set<UUID> seen = new HashSet<>();
        for (net.minecraft.world.entity.Entity entity : client.level.entitiesForRendering()) {
            if (!tracked(entity)) {
                SAMPLES.remove(entity.getUUID());
                continue;
            }
            UUID id = entity.getUUID();
            if (entity instanceof AbstractClientPlayer player && TeamStateStore.byMinecraftName(player.getGameProfile().name())
                    .map(profile -> profile.displayedRank() == TeamRank.VIP).orElse(false)) {
                MOVED_DURING_RED.remove(id);
                STATUSES.remove(id);
                SAMPLES.remove(id);
                continue;
            }
            Vec3 position = entity.position();
            Sample previous = SAMPLES.put(id, new Sample(position));
            if (phase == Phase.RED && STATUSES.get(id) != Status.SAFE_WINNER && previous != null
                    && position.distanceToSqr(previous.position) > 0.0004D) {
                MOVED_DURING_RED.add(id);
                STATUSES.put(id, Status.ELIMINATED);
            }
            if (inSafeZone(entity)) {
                Status current = STATUSES.getOrDefault(entity.getUUID(), Status.ACTIVE);
                if (current == Status.ACTIVE) STATUSES.put(entity.getUUID(), Status.SAFE_WINNER);
                SAMPLES.remove(entity.getUUID());
                continue;
            }
            if (STATUSES.get(entity.getUUID()) == Status.SAFE_WINNER) {
                SAMPLES.remove(entity.getUUID());
                continue;
            }
            if (entity instanceof AbstractClientPlayer player
                    && TeamStateStore.byMinecraftName(player.getGameProfile().name())
                    .map(profile -> profile.displayedRank() == TeamRank.VIP).orElse(false)) {
                MOVED_DURING_RED.remove(entity.getUUID());
                continue;
            }
            seen.add(id);
        }
        SAMPLES.keySet().removeIf(id -> !seen.contains(id));
        // Keep red violations latched until mode reset or role exemption.
    }

    private static void seedSamples(Minecraft client) {
        if (client.level == null) return;
        for (net.minecraft.world.entity.Entity entity : client.level.entitiesForRendering()) {
            if (!tracked(entity)) continue;
            SAMPLES.put(entity.getUUID(), new Sample(entity.position()));
        }
    }

    private static boolean inSafeZone(net.minecraft.world.entity.Entity entity) {
        if (safeCornerOne == null || safeCornerTwo == null) return false;
        double minX = Math.min(safeCornerOne.getX(), safeCornerTwo.getX());
        double minY = Math.min(safeCornerOne.getY(), safeCornerTwo.getY());
        double minZ = Math.min(safeCornerOne.getZ(), safeCornerTwo.getZ());
        double maxX = Math.max(safeCornerOne.getX(), safeCornerTwo.getX()) + 1.0;
        double maxY = Math.max(safeCornerOne.getY(), safeCornerTwo.getY()) + 1.0;
        double maxZ = Math.max(safeCornerOne.getZ(), safeCornerTwo.getZ()) + 1.0;
        var box = entity.getBoundingBox();
        return box.minX >= minX && box.maxX <= maxX && box.minY >= minY && box.maxY <= maxY
                && box.minZ >= minZ && box.maxZ <= maxZ;
    }

    private static void updateBossbar(long now) {
        if (!active) return;
        if (phase == Phase.GREEN && now < greenUntil) {
            long remaining = greenUntil - now;
            MpsqBossbarManager.apply(new MpsqBossbarState("rlgl_phase", "&aGrünes Licht", "green",
                    Math.max(0f, Math.min(1f, (float) remaining / greenDurationMs)), true));
        } else {
            if (phase == Phase.GREEN) beginRedPhase();
            MpsqBossbarManager.apply(new MpsqBossbarState("rlgl_phase", "&cRotes Licht", "red", 1f, true));
        }
    }

    private static void showActivationTitle() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        TeamRank rank = TeamStateStore.self().map(TeamProfile::permissionRank).orElse(TeamRank.PLAYER);
        if (rank != TeamRank.UNDERCOVER_001 && rank != TeamRank.WORKER && rank != TeamRank.SOLDIER && rank != TeamRank.OFFICER
                && rank != TeamRank.SENIOR_OFFICER && rank != TeamRank.FRONTMAN) return;
        client.gui.hud.setTitle(net.minecraft.network.chat.Component.literal("Red Light, Green Light"));
        client.gui.hud.setSubtitle(net.minecraft.network.chat.Component.literal("Ist aktiviert"));
        client.gui.hud.setTimes(10, 70, 20);
    }

    private record Sample(Vec3 position) { }
}
