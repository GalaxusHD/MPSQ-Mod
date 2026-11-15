package de.galaxushd.mpsqcamera;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

/** Shared client-side state layer for the staged MPSQ game systems. */
public final class MpsqGameModeManager {
    public enum Mode { DALGONA, TUG_OF_WAR, MARBLES, GLASS_BRIDGE, FINALE, SIX_LEG, ROOM_GAME, HIDE_AND_SEEK, SKY_SQUID, ROPE_JUMP }
    public enum Glow { NONE, WHITE, YELLOW, GOLD, GREEN, BLUE, RED, DARK_RED, PINK, DARK_BLUE, DARK_GREEN, PURPLE, BLACK }
    public enum Status { ACTIVE, ELIMINATED, SAFE, REGISTERED }

    private static final Map<Mode, Map<Integer, MpsqGameZone>> ZONES = new EnumMap<>(Mode.class);
    private static final Map<UUID, Status> STATUS = new HashMap<>();
    private static final Map<UUID, Glow> GLOWS = new HashMap<>();
    private static Mode activeMode;

    private MpsqGameModeManager() { }

    public static synchronized void start(Mode mode) {
        activeMode = mode;
        STATUS.clear();
        GLOWS.clear();
    }

    public static synchronized void stop() {
        activeMode = null;
        STATUS.clear();
        GLOWS.clear();
    }

    public static synchronized Mode activeMode() { return activeMode; }
    public static synchronized void setZone(Mode mode, int index, BlockPos first, BlockPos second) {
        ZONES.computeIfAbsent(mode, ignored -> new HashMap<>()).put(index, new MpsqGameZone(first, second));
    }

    public static synchronized void clearZones(Mode mode) { ZONES.remove(mode); }
    public static synchronized MpsqGameZone zone(Mode mode, int index) {
        var zones = ZONES.get(mode);
        return zones == null ? null : zones.get(index);
    }

    public static synchronized void setStatus(UUID id, Status status) { if (id != null) STATUS.put(id, status); }
    public static synchronized Status status(UUID id) { return STATUS.getOrDefault(id, Status.ACTIVE); }
    public static synchronized void setGlow(UUID id, Glow glow) { if (id != null) GLOWS.put(id, glow); }
    public static synchronized Glow glow(UUID id) { return GLOWS.getOrDefault(id, Glow.NONE); }
    public static synchronized void resetPlayer(UUID id) { STATUS.remove(id); GLOWS.remove(id); }
    public static synchronized boolean isEliminated(UUID id) { return status(id) == Status.ELIMINATED; }

    /** Shared interpretation of the administrative elimination command. */
    public static synchronized void onPlayerKick(UUID id) { resetPlayer(id); }

    public static synchronized boolean eligible(Entity entity) {
        return entity != null && !(entity instanceof net.minecraft.world.entity.decoration.ArmorStand)
                && entity instanceof net.minecraft.world.entity.LivingEntity;
    }
}
