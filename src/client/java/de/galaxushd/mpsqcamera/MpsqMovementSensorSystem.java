package de.galaxushd.mpsqcamera;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Client-side Red Light, Green Light system used by the MPSQ Redstone SWITCH_SYSTEM action. */
public final class MpsqMovementSensorSystem {
    public static final String SYSTEM_ID = "red_light_green_light";
    private static final long ACTIVITY_GLOW_MS = 900L;
    private static final int RESTING_GLOW = 0xFFFFFF;
    private static final int ACTIVE_GLOW = 0xFF5555;
    private static final Map<UUID, Sample> SAMPLES = new HashMap<>();
    private static final Map<UUID, Long> ACTIVE_UNTIL = new HashMap<>();
    private static volatile boolean active;
    private static BlockPos testVillagerAnchor;
    private static VillagerEntity testVillager;

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

    /** Selects a nearby manually placed villager as the visible test actor. */
    public static void setTestVillagerAnchor(BlockPos anchor) {
        testVillagerAnchor = anchor == null ? null : anchor.toImmutable();
    }

    public static boolean isTestVillager(net.minecraft.entity.Entity entity) {
        return testVillager != null && entity == testVillager;
    }

    public static int outlineColor(AbstractClientPlayerEntity player) {
        Long until = ACTIVE_UNTIL.get(player.getUuid());
        return until != null && System.currentTimeMillis() < until ? ACTIVE_GLOW : RESTING_GLOW;
    }

    public static int testVillagerOutlineColor() { return 0x55FF55; }

    private static synchronized void activate() {
        active = true;
        SAMPLES.clear();
        ACTIVE_UNTIL.clear();
        seedSamples(MinecraftClient.getInstance());
        findTestVillager(MinecraftClient.getInstance());
    }

    private static synchronized void deactivate() {
        active = false;
        SAMPLES.clear();
        ACTIVE_UNTIL.clear();
        removeTestVillager();
    }

    private static void tick(MinecraftClient client) {
        if (!active || !TeamVisibilitySettings.visible() || client.world == null || client.player == null) {
            if (active && (!TeamVisibilitySettings.visible() || client.world == null)) deactivate();
            return;
        }

        if (testVillager == null || testVillager.isRemoved() || testVillager.getWorld() != client.world) findTestVillager(client);

        Set<UUID> seen = new HashSet<>();
        for (PlayerEntity entity : client.world.getPlayers()) {
            if (!(entity instanceof AbstractClientPlayerEntity player)) continue;
            UUID id = player.getUuid();
            seen.add(id);
            Vec3d position = player.getPos();
            boolean sneaking = player.isSneaking();
            boolean swinging = player.handSwinging;
            Sample previous = SAMPLES.put(id, new Sample(position));
            if (previous == null) continue;

            if (position.squaredDistanceTo(previous.position) > 0.00001D
                    || sneaking || swinging || player.hurtTime > 0) {
                markActive(id);
            }
        }
        SAMPLES.keySet().removeIf(id -> !seen.contains(id));
        ACTIVE_UNTIL.keySet().removeIf(id -> !seen.contains(id));

        boolean attackPressed = client.options.attackKey.isPressed();
        boolean usePressed = client.options.useKey.isPressed();
        if (attackPressed || usePressed) {
            markActive(client.player.getUuid());
        }
    }

    private static void seedSamples(MinecraftClient client) {
        if (client.world == null) return;
        for (PlayerEntity entity : client.world.getPlayers()) {
            if (!(entity instanceof AbstractClientPlayerEntity player)) continue;
            SAMPLES.put(player.getUuid(), new Sample(player.getPos()));
        }
    }

    private static synchronized void markActive(UUID playerId) {
        ACTIVE_UNTIL.put(playerId, System.currentTimeMillis() + ACTIVITY_GLOW_MS);
    }

    private static void findTestVillager(MinecraftClient client) {
        testVillager = null;
        BlockPos anchor = testVillagerAnchor;
        if (!active || client.world == null || anchor == null) return;
        Box search = new Box(anchor).expand(24.0D);
        testVillager = client.world.getEntitiesByClass(VillagerEntity.class, search, entity -> !entity.isRemoved())
                .stream().min(java.util.Comparator.comparingDouble(entity -> entity.squaredDistanceTo(anchor.toCenterPos())))
                .orElse(null);
    }

    private static void removeTestVillager() {
        testVillager = null;
        testVillagerAnchor = null;
    }

    private record Sample(Vec3d position) { }
}
