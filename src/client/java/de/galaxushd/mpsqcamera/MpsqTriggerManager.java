package de.galaxushd.mpsqcamera;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
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
    private static BlockPos lastClick;
    private static long nextPoll;
    private static String scope = "";
    private static boolean pending;
    private static int generation;

    private MpsqTriggerManager() { }

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
            lastClick = null;
            nextPoll = 0;
        }
        if (client.world == null || client.player == null || !TeamVisibilitySettings.visible()
                || (!MpsqActionSync.server().isBlank() && !MpsqActionSync.isMpsqServer())) {
            lastClick = null;
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
            if (kind.usesPowerEdge()) {
                if (rose(pos, MpsqTriggerBlockPolicy.isPowered(state))) dispatchLocal(row);
            }
        }
        fireClickedFullBlock(client, pos -> {
            JsonObject row = MpsqLocalActionStore.find(pos);
            if (row == null) return;
            var state = client.world.getBlockState(pos);
            var kind = MpsqTriggerBlockPolicy.classify(state, client.world, pos);
            if (kind == MpsqTriggerBlockPolicy.Kind.FULL_BLOCK
                    && row.get("blockId").getAsString().equals(Registries.BLOCK.getId(state.getBlock()).toString())) dispatchLocal(row);
        });
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
            if (kind.usesPowerEdge() && rose(trigger.position(), MpsqTriggerBlockPolicy.isPowered(state))) fire(trigger.id());
        }
        fireClickedFullBlock(client, pos -> TRIGGERS.stream()
                .filter(trigger -> trigger.worldId().equals(worldId) && trigger.position().equals(pos)
                        && ("FULL_BLOCK".equals(trigger.objectType()) || "TRIGGER".equals(trigger.objectType())))
                .findFirst().ifPresent(trigger -> fire(trigger.id())));
    }

    private static boolean rose(BlockPos pos, boolean powered) {
        Boolean previous = POWERED.put(pos.toImmutable(), powered);
        // The first observed state seeds the edge detector. Joining while a
        // lever is already on must not accidentally fire its saved action.
        return Boolean.FALSE.equals(previous) && powered;
    }

    private static void fireClickedFullBlock(MinecraftClient client, java.util.function.Consumer<BlockPos> fire) {
        if (!(client.crosshairTarget instanceof BlockHitResult hit) || !client.options.useKey.isPressed() || client.currentScreen != null) {
            lastClick = null;
            return;
        }
        BlockPos pos = hit.getBlockPos();
        if (lastClick != null && lastClick.equals(pos)) return;
        lastClick = pos;
        fire.accept(pos);
    }

    private static void dispatchLocal(JsonObject row) {
        JsonObject event = new JsonObject();
        event.addProperty("action_type", row.get("actionType").getAsString());
        event.add("action_data", row.getAsJsonObject("actionData"));
        event.addProperty("created_at", java.time.Instant.now().toString());
        MpsqActionSync.dispatch(event);
    }

    private static void fire(UUID id) {
        MpsqApiClient.fireTrigger(id).exceptionally(error -> {
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
