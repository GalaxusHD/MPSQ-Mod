package de.galaxushd.mpsqcamera;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Client-side trigger cache and redstone-state observer. */
public final class MpsqTriggerManager {
    private static final List<MpsqTrigger> TRIGGERS = new CopyOnWriteArrayList<>();
    private static final Map<BlockPos, Boolean> POWERED = new HashMap<>();
    private static final Set<UUID> DELETING = new HashSet<>();
    private static long nextPoll;
    private static String scope = "";
    private static boolean pending;
    private static int generation;

    private MpsqTriggerManager() { }

    public static BlockPos position(UUID triggerId) {
        if (triggerId == null) return null;
        return TRIGGERS.stream().filter(trigger -> trigger.id().equals(triggerId))
                .map(MpsqTrigger::position).findFirst().orElse(null);
    }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(MpsqTriggerManager::tick);
        refresh();
    }

    public static void refresh() {
        if (pending || !MpsqApiClient.isReady() || MpsqActionSync.server().isBlank()) return;
        pending = true;
        int epoch = generation;
        MpsqApiClient.loadTriggers().whenComplete((rows, error) -> MinecraftClient.getInstance().execute(() -> {
            if (epoch != generation) return;
            pending = false;
            if (error == null) {
                TRIGGERS.clear();
                TRIGGERS.addAll(rows);
            } else MpsqCameraClient.LOGGER.debug("Trigger konnten nicht geladen werden", error);
        }));
    }

    private static void tick(MinecraftClient client) {
        String current = MpsqActionSync.server() + "|" + MpsqActionSync.world();
        if (!scope.equals(current)) {
            scope = current;
            generation++;
            pending = false;
            TRIGGERS.clear();
            POWERED.clear();
            DELETING.clear();
            nextPoll = 0;
        }
        if (client.world == null || client.player == null || !TeamVisibilitySettings.visible()
                || (!MpsqActionSync.server().isBlank() && !MpsqActionSync.isMpsqServer())) {
            return;
        }

        boolean localWorld = MpsqActionSync.server().isBlank() && client.getServer() != null;
        if (!localWorld && MpsqApiClient.isReady()) {
            long now = System.currentTimeMillis();
            if (now >= nextPoll) {
                nextPoll = now + 15_000L;
                refresh();
            }
        }

        if (localWorld) {
            tickLocal(client);
            return;
        }
        tickOnline(client);
    }

    private static void tickLocal(MinecraftClient client) {
        var rows = MpsqLocalActionStore.load();
        for (var element : rows) {
            if (!element.isJsonObject()) continue;
            JsonObject row = element.getAsJsonObject();
            BlockPos pos = new BlockPos(row.get("x").getAsInt(), row.get("y").getAsInt(), row.get("z").getAsInt());
            if (!client.world.isChunkLoaded(pos)) continue;
            var state = client.world.getBlockState(pos);
            String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
            var kind = MpsqTriggerBlockPolicy.classify(state, client.world, pos);
            if (!blockId.equals(row.get("blockId").getAsString()) || kind == MpsqTriggerBlockPolicy.Kind.NONE) {
                MpsqLocalActionStore.remove(pos);
                POWERED.remove(pos);
                continue;
            }
            String savedType = row.has("objectType") ? row.get("objectType").getAsString() : kind.name();
            if (!savedType.equals(kind.name())) {
                MpsqLocalActionStore.remove(pos);
                POWERED.remove(pos);
                continue;
            }
            if (isClickActivatedSystem(row, kind)) continue;
            if (kind.usesPowerEdge()) {
                Boolean changedTo = stateTransition(pos, MpsqTriggerBlockPolicy.isActivated(state, kind));
                if (changedTo != null && (kind.followsPowerState() || changedTo)) dispatchLocal(row, kind, changedTo);
            }
        }
    }

    private static void tickOnline(MinecraftClient client) {
        if (!MpsqApiClient.isReady()) return;
        String worldId = MpsqActionSync.world();
        for (MpsqTrigger trigger : TRIGGERS) {
            if (!trigger.worldId().equals(worldId) || !client.world.isChunkLoaded(trigger.position())) continue;
            var state = client.world.getBlockState(trigger.position());
            String actualBlock = Registries.BLOCK.getId(state.getBlock()).toString();
            var kind = MpsqTriggerBlockPolicy.classify(state, client.world, trigger.position());
            String savedType = "TRIGGER".equals(trigger.objectType()) ? kind.name() : trigger.objectType();
            if (!actualBlock.equals(trigger.blockId()) || kind == MpsqTriggerBlockPolicy.Kind.NONE
                    || !kind.name().equals(savedType)) {
                disableStale(trigger);
                continue;
            }
            if (isClickActivatedSystem(trigger, kind)) continue;
            if (kind.usesPowerEdge()) {
                Boolean changedTo = stateTransition(trigger.position(), MpsqTriggerBlockPolicy.isActivated(state, kind));
                if (changedTo != null && (kind.followsPowerState() || changedTo)) {
                    if (kind.followsPowerState()) fire(trigger.id(), changedTo, false);
                    else fire(trigger.id(), true, true);
                }
            }
        }
    }

    private static Boolean stateTransition(BlockPos pos, boolean powered) {
        Boolean previous = POWERED.put(pos.toImmutable(), powered);
        // Seed without emitting an event on join; report subsequent state changes.
        return previous == null || previous == powered ? null : powered;
    }

    /** Button presses are short pulses; trigger system switches from the actual interaction. */
    public static void onRightClick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.currentScreen != null
                || !TeamVisibilitySettings.visible()
                || !(client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit)) return;
        BlockPos pos = hit.getBlockPos();
        var state = client.world.getBlockState(pos);
        if (MpsqTriggerBlockPolicy.classify(state, client.world, pos) != MpsqTriggerBlockPolicy.Kind.BUTTON) return;

        boolean localWorld = MpsqActionSync.server().isBlank() && client.getServer() != null;
        if (localWorld) {
            for (var element : MpsqLocalActionStore.load()) {
                if (!element.isJsonObject()) continue;
                JsonObject row = element.getAsJsonObject();
                if (!row.has("x") || !row.has("y") || !row.has("z")
                        || row.get("x").getAsInt() != pos.getX()
                        || row.get("y").getAsInt() != pos.getY()
                        || row.get("z").getAsInt() != pos.getZ()
                        || !isClickActivatedSystem(row, MpsqTriggerBlockPolicy.Kind.BUTTON)) continue;
                String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
                if (!blockId.equals(row.get("blockId").getAsString())) return;
                dispatchLocal(row, MpsqTriggerBlockPolicy.Kind.BUTTON, true);
                return;
            }
            return;
        }

        String worldId = MpsqActionSync.world();
        for (MpsqTrigger trigger : TRIGGERS) {
            if (!trigger.position().equals(pos) || !trigger.worldId().equals(worldId)
                    || !isButtonAction(trigger.actionType())) continue;
            fire(trigger.id(), null, true);
            return;
        }
    }

    private static boolean isClickActivatedSystem(JsonObject row, MpsqTriggerBlockPolicy.Kind kind) {
        return kind == MpsqTriggerBlockPolicy.Kind.BUTTON
                && row.has("actionType") && isButtonAction(row.get("actionType").getAsString());
    }

    private static boolean isClickActivatedSystem(MpsqTrigger trigger, MpsqTriggerBlockPolicy.Kind kind) {
        return kind == MpsqTriggerBlockPolicy.Kind.BUTTON && isButtonAction(trigger.actionType());
    }

    private static boolean isButtonAction(String action) {
        return "SWITCH_SYSTEM".equals(action) || action.startsWith("RLGL_");
    }

    private static void dispatchLocal(JsonObject row, MpsqTriggerBlockPolicy.Kind kind, boolean powered) {
        JsonObject event = new JsonObject();
        event.addProperty("action_type", row.get("actionType").getAsString());
        JsonObject triggerPosition = new JsonObject();
        triggerPosition.addProperty("x", row.get("x").getAsInt());
        triggerPosition.addProperty("y", row.get("y").getAsInt());
        triggerPosition.addProperty("z", row.get("z").getAsInt());
        event.add("trigger_position", triggerPosition);
        JsonObject data = row.getAsJsonObject("actionData").deepCopy();
        if (kind.followsPowerState()) data.addProperty("redstone_powered", powered);
        event.add("action_data", data);
        event.addProperty("created_at", java.time.Instant.now().toString());
        MpsqActionSync.dispatch(event);
    }

    private static void fire(UUID id) {
        fire(id, null, false);
    }

    private static void fire(UUID id, Boolean powered) {
        fire(id, powered, false);
    }

    private static void fire(UUID id, Boolean powered, boolean pulse) {
        MpsqApiClient.fireTrigger(id, powered, pulse).exceptionally(error -> {
            MpsqCameraClient.LOGGER.warn("MPSQ-Auslöser konnte nicht aktiviert werden", error);
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                if (client.player != null) client.player.sendMessage(Text.literal("§cMPSQ-System: Aktion fehlgeschlagen – " + rootMessage(error)), false);
            });
            return null;
        });
    }

    /** Disable the shared row (preserving event history) when its block is gone or replaced. */
    private static void disableStale(MpsqTrigger trigger) {
        TRIGGERS.remove(trigger);
        POWERED.remove(trigger.position());
        if (!DELETING.add(trigger.id())) return;
        String query = "?server=" + URLEncoder.encode(MpsqActionSync.server(), StandardCharsets.UTF_8)
                + "&world=" + URLEncoder.encode(MpsqActionSync.world(), StandardCharsets.UTF_8);
        MpsqApiClient.delete("/triggers/" + trigger.id() + query).whenComplete((result, error) -> {
            if (error != null) {
                DELETING.remove(trigger.id());
                MpsqCameraClient.LOGGER.warn("Veralteter MPSQ-Auslöser konnte nicht entfernt werden", error);
            }
        });
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        String message = cause.getMessage();
        return message == null || message.isBlank() ? "API nicht erreichbar" : message;
    }
}
