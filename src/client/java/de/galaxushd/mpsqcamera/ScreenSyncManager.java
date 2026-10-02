package de.galaxushd.mpsqcamera;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/** Fetches screens and their server-only metadata in one request. */
public final class ScreenSyncManager {
    private ScreenSyncManager() { }
    public static CompletableFuture<Void> refresh() {
        String server=MpsqActionSync.server(),world=MpsqActionSync.world();
        String path="/screens?server="+URLEncoder.encode(server,StandardCharsets.UTF_8)+"&world="+URLEncoder.encode(world,StandardCharsets.UTF_8);
        return MpsqApiClient.get(path).thenAccept(json -> {
            List<LocalScreenStore.LocalScreenData> screens = new ArrayList<>(); Map<UUID, String> codes = new HashMap<>(); Map<UUID, LocalScreenStore.LocalGroupData> groups = new HashMap<>(); Set<UUID> owned = new HashSet<>(),triggerLinkedOnly=new HashSet<>(); Map<UUID, CinemaPlaybackStore.PlaybackState> playbackStates = new HashMap<>();
            for (JsonElement item : json.getAsJsonArray()) {
                JsonObject row = item.getAsJsonObject(); UUID id = UUID.fromString(row.get("id").getAsString());
                BlockPos p1 = new BlockPos(row.get("pos1_x").getAsInt(), row.get("pos1_y").getAsInt(), row.get("pos1_z").getAsInt()); BlockPos p2 = new BlockPos(row.get("pos2_x").getAsInt(), row.get("pos2_y").getAsInt(), row.get("pos2_z").getAsInt());
                UUID groupId = row.has("group_id") && !row.get("group_id").isJsonNull() ? UUID.fromString(row.get("group_id").getAsString()) : null;
                codes.put(id, row.has("activation_code")&&!row.get("activation_code").isJsonNull()?row.get("activation_code").getAsString():"------"); if (row.has("is_owner") && row.get("is_owner").getAsBoolean()) owned.add(id);
                if(row.has("is_trigger_linked_only")&&row.get("is_trigger_linked_only").getAsBoolean())triggerLinkedOnly.add(id);
                if (row.has("front") && !row.get("front").isJsonNull()) ScreenAccessStore.setFront(id, row.get("front").getAsString());
                if (groupId != null && row.has("mpsq_screen_groups") && row.get("mpsq_screen_groups").isJsonObject()) { JsonObject group = row.getAsJsonObject("mpsq_screen_groups"); groups.put(id, new LocalScreenStore.LocalGroupData(groupId, group.get("activation_code").getAsString())); }
                String modeValue = row.get("mode").getAsString();
                LocalScreenStore.ScreenInputType mode = "CAMERA".equals(modeValue)
                        ? LocalScreenStore.ScreenInputType.CAMERA
                        : ("REDSTONE".equals(modeValue) ? LocalScreenStore.ScreenInputType.REDSTONE : LocalScreenStore.ScreenInputType.LINK);
                List<UUID> cameraIds = new ArrayList<>();
                if (row.has("mpsq_screen_cameras") && row.get("mpsq_screen_cameras").isJsonArray()) {
                    JsonArray assignments = row.getAsJsonArray("mpsq_screen_cameras");
                    List<JsonObject> orderedAssignments = new ArrayList<>();
                    for (JsonElement assignment : assignments) {
                        if (assignment.isJsonObject()) orderedAssignments.add(assignment.getAsJsonObject());
                    }
                    orderedAssignments.sort((left, right) -> Integer.compare(
                            left.has("sort_order") ? left.get("sort_order").getAsInt() : 0,
                            right.has("sort_order") ? right.get("sort_order").getAsInt() : 0
                    ));
                    for (JsonObject link : orderedAssignments) {
                        if (link.has("camera_id") && !link.get("camera_id").isJsonNull()) {
                            cameraIds.add(UUID.fromString(link.get("camera_id").getAsString()));
                        }
                    }
                }
                ScreenCameraStore.put(id, cameraIds);
                UUID firstCameraId = cameraIds.isEmpty() ? null : cameraIds.get(0);
                if (row.has("playback_state") && row.get("playback_state").isJsonObject()) {
                    JsonObject state = row.getAsJsonObject("playback_state");
                    boolean playing = state.has("playing") && state.get("playing").getAsBoolean();
                    long positionMs = state.has("positionMs") ? state.get("positionMs").getAsLong() : 0L;
                    long revision = state.has("revision") ? state.get("revision").getAsLong() : 0L;
                    long updatedAtMs = 0L;
                    try {
                        if (row.has("updated_at") && !row.get("updated_at").isJsonNull()) {
                            updatedAtMs = Instant.parse(row.get("updated_at").getAsString()).toEpochMilli();
                        }
                    } catch (RuntimeException ignored) { }
                    playbackStates.put(id, new CinemaPlaybackStore.PlaybackState(playing, Math.max(0L, positionMs), revision, updatedAtMs));
                }
                screens.add(new LocalScreenStore.LocalScreenData(id, p1, p2, row.get("name").getAsString(), new Vec3d(p1.getX(), p1.getY(), p1.getZ()), mode, row.get("cinema_url").getAsString(), firstCameraId, groupId));
            }
            MinecraftClient.getInstance().execute(() -> {
                LocalScreenStore.replaceAll(screens);
                ScreenAccessStore.replace(codes, groups, owned, triggerLinkedOnly);
                CinemaPlaybackStore.replace(playbackStates);
                CinemaBrowserManager.synchronize();
            });
        });
    }
}
