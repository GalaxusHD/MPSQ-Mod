package de.galaxushd.mpsqcamera;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/** HTTP access to the public MPSQ Edge Function. No Supabase secret is stored in the mod. */
public final class MpsqApiClient {
    public static final String API_URL = "local://mpsq";
    private static final Path TOKEN_FILE = FabricLoader.getInstance().getConfigDir().resolve("mpsqcamera-token.txt");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final Gson GSON = new Gson();
    // R2 write links are issued by the authenticated API but the actual PNG
    // bytes bypass Supabase entirely.  This cache limits URL requests to one
    // per camera per minute instead of one Edge Function call per frame.
    private static final Map<UUID, String> FRAME_UPLOAD_URLS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> FRAME_UPLOAD_URL_EXPIRY_MS = new ConcurrentHashMap<>();
    private static String token;
    private static volatile long quotaRetryAt;
    private static volatile String teamProfileError = "";

    public static String teamProfileError() { return teamProfileError; }

    private MpsqApiClient() { }

    public static CompletableFuture<Void> initialize() {
        return LocalMpsqBackend.initialize().thenRun(() -> token = "local-only");
    }

    public static CompletableFuture<JsonElement> get(String path) { return request("GET", path, null, true); }
    public static CompletableFuture<JsonElement> post(String path, JsonObject body) { return request("POST", path, body, true); }
    public static CompletableFuture<JsonElement> patch(String path, JsonObject body) { return request("PATCH", path, body, true); }
    public static CompletableFuture<JsonElement> delete(String path) { return request("DELETE", path, null, true); }

    /** Loads enabled redstone triggers for the currently joined server. */
    private static Map<String, Object> triggerActionData(JsonObject row) {
        Map<String, Object> data = new java.util.HashMap<>();
        if (row.has("action_data") && row.get("action_data").isJsonObject()) {
            for (var entry : row.getAsJsonObject("action_data").entrySet()) {
                if (entry.getValue().isJsonPrimitive()) data.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return data;
    }
    public static CompletableFuture<List<MpsqTrigger>> loadTriggers() {
        String server = java.net.URLEncoder.encode(MpsqActionSync.server(), StandardCharsets.UTF_8);
        return get("/triggers?server=" + server).thenApply(json -> {
            List<MpsqTrigger> result = new ArrayList<>();
            if (!json.isJsonArray()) return result;
            for (JsonElement value : json.getAsJsonArray()) {
                JsonObject row = value.getAsJsonObject();
                result.add(new MpsqTrigger(UUID.fromString(row.get("id").getAsString()), row.get("world_id").getAsString(),
                        new BlockPos(row.get("pos_x").getAsInt(), row.get("pos_y").getAsInt(), row.get("pos_z").getAsInt()),
                        row.get("block_id").getAsString(), row.has("object_type") ? row.get("object_type").getAsString() : "TRIGGER",
                        row.get("action_type").getAsString(), triggerActionData(row)));
            }
            return result;
        });
    }

    public static CompletableFuture<JsonElement> fireTrigger(UUID triggerId) {
        return fireTrigger(triggerId, null, false);
    }

    public static CompletableFuture<JsonElement> fireTrigger(UUID triggerId, Boolean powered) {
        return fireTrigger(triggerId, powered, false);
    }

    public static CompletableFuture<JsonElement> fireTrigger(UUID triggerId, Boolean powered, boolean pulse) {
        JsonObject body = new JsonObject();
        body.addProperty("serverId", MpsqActionSync.server());
        body.addProperty("worldId", MpsqActionSync.world());
        if (powered != null) {
            body.addProperty("powered", powered);
            body.addProperty("pulse", pulse);
        }
        return post("/triggers/" + triggerId + "/fire", body);
    }

    public static CompletableFuture<JsonElement> redeemCode(String code) {
        JsonObject body = new JsonObject();
        body.addProperty("code", code);
        return post("/redeem", body);
    }

    /** Uploads one frame directly to R2 using a short-lived, camera-scoped URL. */
    public static CompletableFuture<JsonElement> postCameraFrame(UUID cameraId, byte[] png) {
        return LocalCameraFrameStore.replace(cameraId, png).thenApply(ignored -> JsonNull.INSTANCE);
    }

    /** Cameras whose wearer is this client. Their wearer publishes a bodycam frame. */
    public static CompletableFuture<List<UUID>> loadMyBodycamIds() {
        return get("/bodycams/mine").thenApply(json -> {
            List<UUID> ids = new ArrayList<>();
            if (!json.isJsonArray()) return ids;
            for (JsonElement row : json.getAsJsonArray()) {
                if (row.isJsonObject() && row.getAsJsonObject().has("id")) {
                    ids.add(UUID.fromString(row.getAsJsonObject().get("id").getAsString()));
                }
            }
            return ids;
        });
    }

    /** True once this client has a local API token and can safely poll shared screen state. */
    public static boolean isReady() {
        return token != null && !token.isBlank();
    }

    public static CompletableFuture<List<LocalCameraStore.CameraData>> loadCameras() {
        // Includes own cameras and cameras attached to screens this client may
        // view. A nearby mod user can therefore become a static-camera source.
        return get("/cameras/accessible").thenApply(json -> {
            List<LocalCameraStore.CameraData> cameras = new ArrayList<>();
            for (JsonElement element : json.getAsJsonArray()) {
                JsonObject row = element.getAsJsonObject();
                LocalCameraStore.CameraKind kind = "BODYCAM".equals(row.get("kind").getAsString())
                        ? LocalCameraStore.CameraKind.BODYCAM : LocalCameraStore.CameraKind.STATIC;
                Vec3 position = row.get("x").isJsonNull() ? null : new Vec3(row.get("x").getAsDouble(), row.get("y").getAsDouble(), row.get("z").getAsDouble());
                UUID wearer = row.has("body_owner_id") && !row.get("body_owner_id").isJsonNull()
                        ? UUID.fromString(row.get("body_owner_id").getAsString()) : null;
                String wearerName = row.has("body_owner_name") && !row.get("body_owner_name").isJsonNull()
                        ? row.get("body_owner_name").getAsString() : null;
                cameras.add(new LocalCameraStore.CameraData(UUID.fromString(row.get("id").getAsString()), row.get("name").getAsString(), kind,
                        row.get("dimension").getAsString(), position, row.get("yaw").getAsFloat(), row.get("pitch").getAsFloat(), wearer, wearerName));
            }
            return cameras;
        });
    }

    /** Refreshes the local camera cache and its client-only holograms from the shared API. */
    public static CompletableFuture<Void> refreshCameras() {
        return loadCameras().thenAccept(cameras -> Minecraft.getInstance().execute(() -> {
            LocalCameraStore.getAll().forEach(camera -> CameraHologramManager.remove(camera.id()));
            LocalCameraStore.replaceAll(cameras);
            cameras.forEach(CameraHologramManager::show);
        }));
    }

    public static CompletableFuture<List<LocalScreenStore.LocalScreenData>> loadScreens() {
        return get("/screens").thenApply(json -> {
            List<LocalScreenStore.LocalScreenData> screens = new ArrayList<>();
            for (JsonElement element : json.getAsJsonArray()) {
                JsonObject row = element.getAsJsonObject();
                BlockPos pos1 = new BlockPos(row.get("pos1_x").getAsInt(), row.get("pos1_y").getAsInt(), row.get("pos1_z").getAsInt());
                BlockPos pos2 = new BlockPos(row.get("pos2_x").getAsInt(), row.get("pos2_y").getAsInt(), row.get("pos2_z").getAsInt());
                String modeValue = row.get("mode").getAsString();
                LocalScreenStore.ScreenInputType mode = "CAMERA".equals(modeValue)
                        ? LocalScreenStore.ScreenInputType.CAMERA
                        : ("REDSTONE".equals(modeValue) || "MPSQ_REDSTONE".equals(modeValue)) ? LocalScreenStore.ScreenInputType.MPSQ_REDSTONE
                        : LocalScreenStore.ScreenInputType.LINK;
                UUID cameraId = null;
                if (row.has("mpsq_screen_cameras") && row.get("mpsq_screen_cameras").isJsonArray()) {
                    JsonArray cameras = row.getAsJsonArray("mpsq_screen_cameras");
                    List<UUID> cameraIds = new ArrayList<>();
                    for (JsonElement camera : cameras) cameraIds.add(UUID.fromString(camera.getAsJsonObject().get("camera_id").getAsString()));
                    ScreenCameraStore.put(UUID.fromString(row.get("id").getAsString()), cameraIds);
                    if (!cameraIds.isEmpty()) cameraId = cameraIds.get(0);
                }
                UUID groupId = row.has("group_id") && !row.get("group_id").isJsonNull() ? UUID.fromString(row.get("group_id").getAsString()) : null;
                screens.add(new LocalScreenStore.LocalScreenData(UUID.fromString(row.get("id").getAsString()), pos1, pos2,
                        row.get("name").getAsString(), new Vec3(pos1.getX(), pos1.getY(), pos1.getZ()), mode,
                        row.has("cinema_url") && !row.get("cinema_url").isJsonNull() ? row.get("cinema_url").getAsString() : "", cameraId, groupId));
            }
            return screens;
        });
    }

    /** Loads the signed-in user's shared MPSQ Team role. */
    public static CompletableFuture<TeamProfile> refreshTeamProfile() {
        return get("/team/me").thenApply(MpsqApiClient::parseTeamProfile).thenApply(profile -> {
            TeamStateStore.setSelf(profile);
            teamProfileError = "";
            return profile;
        }).whenComplete((profile, error) -> {
            if (error != null) teamProfileError = quotaRetryAt > System.currentTimeMillis()
                    ? "Rang nicht abrufbar: Supabase-Datenlimit erreicht."
                    : "Rang nicht abrufbar: Verbindung zum Teamserver fehlgeschlagen.";
        });
    }

    /** Stores whether this member's normal name is shown below the rank image. */
    public static CompletableFuture<Void> setOwnNameVisible(boolean visible) {
        JsonObject body = new JsonObject();
        body.addProperty("visible", visible);
        return post("/team/me/name-visibility", body)
                .thenCompose(ignored -> refreshTeamProfile()).thenApply(ignored -> null);
    }

    /** The API only returns members the signed-in user is allowed to see. */
    public static CompletableFuture<List<TeamProfile>> refreshTeamMembers() {
        return get("/team/members").thenApply(json -> {
            List<TeamProfile> members = new ArrayList<>();
            if (!json.isJsonArray()) return members;
            for (JsonElement element : json.getAsJsonArray()) members.add(parseTeamProfile(element));
            TeamStateStore.setMembers(members);
            return members;
        });
    }

    public static CompletableFuture<Void> changeTeamRank(UUID memberId, TeamRank rank) {
        JsonObject body = new JsonObject();
        body.addProperty("rank", rank.id());
        // Only Offizier and Frontman are confirmation-protected. All other
        // changes are still checked authoritatively by the API.
        boolean removesLeadership = TeamStateStore.members().stream()
                .filter(member -> member.id().equals(memberId))
                .map(TeamProfile::baseRank)
                .anyMatch(current -> current == TeamRank.OFFICER || current == TeamRank.FRONTMAN);
        // The Sr Offizier is the trusted root role. Its rank changes are applied
        // immediately; every other role must still create an approval request
        // for an Offizier/Frontman promotion or demotion.
        boolean isSeniorOfficer = TeamStateStore.self()
                .map(TeamProfile::permissionRank)
                .map(current -> current == TeamRank.SENIOR_OFFICER)
                .orElse(false);
        if (!isSeniorOfficer && (rank == TeamRank.OFFICER || rank == TeamRank.FRONTMAN || removesLeadership)) {
            body.addProperty("targetId", memberId.toString());
            return post("/team/rank-requests", body).thenApply(ignored -> (Void) null);
        }
        return post("/team/members/" + memberId + "/rank", body).thenApply(ignored -> (Void) null);
    }

    public static CompletableFuture<Void> clearOwnUndercoverRank() {
        return delete("/team/me/event-rank").thenApply(ignored -> (Void) null);
    }

    /**
     * Removes a temporary 001 rank.  The API permits this for the member
     * itself, or for the Sr Offizier when another member is selected.
     */
    public static CompletableFuture<Void> clearUndercoverRank(UUID memberId) {
        return delete("/team/members/" + memberId + "/event-rank").thenApply(ignored -> (Void) null);
    }

    /** Assigns an allowed temporary event rank (normally 001) through the API. */
    public static CompletableFuture<Void> setTemporaryTeamRank(UUID memberId, TeamRank rank) {
        JsonObject body = new JsonObject();
        body.addProperty("rank", rank.id());
        return post("/team/members/" + memberId + "/event-rank", body).thenApply(ignored -> (Void) null);
    }

    /** Sends one message to the private MPSQ Team chat. */
    public static CompletableFuture<Void> sendTeamMessage(String message) {
        String prepared = TeamChatPolicy.prepare(message);
        if (TeamChatPolicy.containsForbiddenContent(prepared)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("FILTERED"));
        }
        JsonObject body = new JsonObject();
        body.addProperty("message", prepared);
        return post("/team/chat", body).thenApply(ignored -> (Void) null);
    }

    public static CompletableFuture<List<TeamChatMessage>> loadTeamMessages() {
        return get("/team/chat").thenApply(json -> {
            List<TeamChatMessage> messages = new ArrayList<>();
            if (!json.isJsonArray()) return messages;
            for (JsonElement element : json.getAsJsonArray()) {
                JsonObject row = element.getAsJsonObject();
                String senderName = row.has("sender_name") ? row.get("sender_name").getAsString() : "MPSQ Team";
                String senderRank = row.has("sender_rank") ? row.get("sender_rank").getAsString() : "spieler";
                String message = row.has("message") ? row.get("message").getAsString() : "";
                // Older deployed API versions do not yet include the database ID.
                // created_at is stable, unlike a generated UUID on every poll.
                String stableId = row.has("id") ? row.get("id").getAsString()
                        : row.has("created_at") ? row.get("created_at").getAsString()
                        : senderName + "\\u0000" + senderRank + "\\u0000" + message;
                messages.add(new TeamChatMessage(
                        stableId,
                        senderName,
                        TeamRank.fromId(senderRank),
                        message));
            }
            return messages;
        });
    }

    public static CompletableFuture<List<CameraUsage>> loadCameraUsage() {
        return get("/team/camera-events").thenApply(json -> {
            List<CameraUsage> usages = new ArrayList<>();
            if (!json.isJsonArray()) return usages;
            for (JsonElement element : json.getAsJsonArray()) {
                JsonObject row = element.getAsJsonObject();
                usages.add(new CameraUsage(
                        row.has("viewer_name") ? row.get("viewer_name").getAsString() : "Unbekannt",
                        row.has("camera_name") ? row.get("camera_name").getAsString() : "Kamera"));
            }
            return usages;
        });
    }

    public static CompletableFuture<List<TeamTodo>> loadTeamTodos() {
        return get("/team/todos").thenApply(json -> {
            List<TeamTodo> todos = new ArrayList<>();
            if (!json.isJsonArray()) return todos;
            for (JsonElement element : json.getAsJsonArray()) {
                JsonObject row = element.getAsJsonObject();
                String listKey = row.has("list_key") && !row.get("list_key").isJsonNull()
                        ? row.get("list_key").getAsString() : "arbeiter";
                // Completion checkboxes are intentionally local-only. The
                // backend stores the shared task and its list, never a check.
                todos.add(new TeamTodo(UUID.fromString(row.get("id").getAsString()), row.get("text").getAsString(),
                        TeamTodoList.fromId(listKey), false));
            }
            return todos;
        });
    }

    public static CompletableFuture<Void> addTeamTodo(String text, TeamTodoList list) {
        JsonObject body = new JsonObject();
        body.addProperty("text", text);
        body.addProperty("listKey", list.id());
        return post("/team/todos", body).thenApply(ignored -> (Void) null);
    }

    /** Compatibility helper for older call sites. */
    public static CompletableFuture<Void> addTeamTodo(String text) {
        return addTeamTodo(text, TeamTodoList.WORKER);
    }

    public static CompletableFuture<Void> updateTeamTodo(UUID id, String text, TeamTodoList list) {
        JsonObject body = new JsonObject();
        body.addProperty("text", text);
        body.addProperty("listKey", list.id());
        return patch("/team/todos/" + id, body).thenApply(ignored -> (Void) null);
    }

    public static CompletableFuture<Void> deleteTeamTodo(UUID id) {
        return delete("/team/todos/" + id).thenApply(ignored -> (Void) null);
    }

    public static CompletableFuture<Void> toggleTeamTodo(UUID id, boolean done) {
        // Checkmarks are session-local by design and therefore must not cause
        // a server request. Screens maintain their own checked-ID set.
        return CompletableFuture.completedFuture(null);
    }

    public static CompletableFuture<TeamTimerState> loadTeamTimer() {
        return get("/team/timer").thenApply(json -> {
            JsonObject row = json.getAsJsonObject();
            return new TeamTimerState(row.has("running") && row.get("running").getAsBoolean(),
                    row.has("ends_at") && !row.get("ends_at").isJsonNull() ? row.get("ends_at").getAsString() : null,
                    row.has("label") ? row.get("label").getAsString() : "");
        });
    }

    public static CompletableFuture<Void> updateTeamTimer(boolean running, long durationSeconds, String label) {
        JsonObject body = new JsonObject();
        body.addProperty("running", running); body.addProperty("durationSeconds", durationSeconds); body.addProperty("label", label);
        return post("/team/timer", body).thenApply(ignored -> (Void) null);
    }

    public static CompletableFuture<List<TeamTemplate>> loadTeamTemplates() {
        return get("/team/templates").thenApply(json -> {
            List<TeamTemplate> templates = new ArrayList<>();
            if (!json.isJsonArray()) return templates;
            for (JsonElement element : json.getAsJsonArray()) {
                JsonObject row = element.getAsJsonObject();
                TeamTemplate.Speaker speaker = TeamTemplate.Speaker.fromId(
                        row.has("speaker") && !row.get("speaker").isJsonNull() ? row.get("speaker").getAsString() : "offizier");
                templates.add(new TeamTemplate(UUID.fromString(row.get("id").getAsString()), row.get("text").getAsString(), speaker));
            }
            return templates;
        });
    }

    public static CompletableFuture<Void> addTeamTemplate(String text) {
        return addTeamTemplate(text, TeamTemplate.Speaker.OFFICER);
    }

    public static CompletableFuture<Void> addTeamTemplate(String text, TeamTemplate.Speaker speaker) {
        JsonObject body = new JsonObject();
        body.addProperty("text", text);
        body.addProperty("speaker", speaker.id());
        return post("/team/templates", body).thenApply(ignored -> (Void) null);
    }

    public static CompletableFuture<Void> updateTeamTemplate(UUID id, String text, TeamTemplate.Speaker speaker) {
        JsonObject body = new JsonObject();
        body.addProperty("text", text);
        body.addProperty("speaker", speaker.id());
        return patch("/team/templates/" + id, body).thenApply(ignored -> (Void) null);
    }

    public static CompletableFuture<Void> deleteTeamTemplate(UUID id) {
        return delete("/team/templates/" + id).thenApply(ignored -> (Void) null);
    }

    private static TeamProfile parseTeamProfile(JsonElement json) {
        JsonObject row = json.getAsJsonObject();
        UUID id = UUID.fromString(row.get("id").getAsString());
        String name = row.has("display_name") ? row.get("display_name").getAsString() : "Minecraft Spieler";
        TeamRank base = TeamRank.fromId(row.has("base_rank") ? row.get("base_rank").getAsString() : "spieler");
        TeamRank active = row.has("active_rank") && !row.get("active_rank").isJsonNull()
                ? TeamRank.fromId(row.get("active_rank").getAsString()) : null;
        boolean nameVisible = !row.has("name_visible") || row.get("name_visible").isJsonNull()
                || row.get("name_visible").getAsBoolean();
        return new TeamProfile(id, name, base, active, nameVisible);
    }

    private static CompletableFuture<JsonElement> request(String method, String path, JsonObject body, boolean authenticated) {
        return LocalMpsqBackend.request(method, path, body);
    }



    private static String currentDisplayName() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.getUser() != null && !client.getUser().getName().isBlank()) {
            return client.getUser().getName();
        }
        return "Minecraft Client";
    }
}
