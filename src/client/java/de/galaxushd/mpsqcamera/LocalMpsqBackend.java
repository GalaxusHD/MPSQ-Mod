package de.galaxushd.mpsqcamera;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Persistent, single-computer replacement for the former Supabase/R2 backend. */
public final class LocalMpsqBackend {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("mpsq-local-test");
    private static Path file;
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static JsonObject data;

    private LocalMpsqBackend() { }

    public static CompletableFuture<Void> initialize() {
        synchronized (LocalMpsqBackend.class) {
            load();
            JsonObject profile = object("profile");
            if (!profile.has("id")) profile.addProperty("id", UUID.randomUUID().toString());
            profile.addProperty("display_name", displayName());
            profile.addProperty("base_rank", "sr_offizier");
            if (!profile.has("name_visible")) profile.addProperty("name_visible", true);
            save();
        }
        return CompletableFuture.completedFuture(null);
    }

    public static CompletableFuture<JsonElement> request(String method, String path, JsonObject body) {
        try {
            synchronized (LocalMpsqBackend.class) {
                load();
                JsonElement result = route(method.toUpperCase(Locale.ROOT), path, body == null ? new JsonObject() : body);
                if (!"GET".equalsIgnoreCase(method)) save();
                return CompletableFuture.completedFuture(result);
            }
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private static JsonElement route(String method, String path, JsonObject body) {
        path=path.split("\\?",2)[0];
        if(path.equals("/me/points")){JsonObject out=new JsonObject();out.addProperty("points",MpsqLocalWorldStore.points());return out;}
        if(path.equals("/models/catalog"))return MpsqBuiltinNpcCatalog.catalog();
        if(path.equals("/furniture/catalog"))return MpsqLocalWorldStore.array("furniture_catalog");
        if(path.equals("/accessory-catalog"))return accessoryCatalog();
        if(path.equals("/objects"))return MpsqLocalWorldStore.array("objects");
        if(path.equals("/npcs"))return MpsqLocalWorldStore.array("npcs");
        if(path.equals("/me/accessories"))return MpsqLocalWorldStore.array("accessories_owned");
        if(path.equals("/accessory-wearers")||path.equals("/pets/presence"))return new JsonArray();
        if(path.equals("/playlists"))return MpsqBuiltinMusic.playlists(array("playlists"));
        if(path.equals("/team/rank-log"))return array("rank_log").deepCopy();
        if(path.equals("/triggers")&&method.equals("GET"))return array("triggers").deepCopy();
        if(path.equals("/actions")&&method.equals("POST")){
            JsonObject event=new JsonObject();event.addProperty("action_type",string(body,"actionType",""));event.add("action_data",body.has("actionData")?body.get("actionData").deepCopy():new JsonObject());event.addProperty("created_at",Instant.now().toString());
            Minecraft.getInstance().execute(()->MpsqActionSync.dispatch(event));return event;
        }
        if(path.equals("/action-events")){JsonObject out=new JsonObject();out.add("events",new JsonArray());out.addProperty("cursor",Instant.now().toString());return out;}
        if(path.equals("/redeem"))throw new IllegalStateException("Server-Codes sind in der lokalen Testversion nicht verfügbar.");
        if (path.equals("/register")) { JsonObject out = profile(); out.addProperty("token", "offline"); return out; }
        if (path.equals("/me")) { if (body.has("displayName")) object("profile").addProperty("display_name", body.get("displayName").getAsString()); return profile(); }
        if (path.equals("/join")) {
            String wanted=string(body,"code","");
            for(JsonElement e:array("screens"))if(wanted.equals(string(e.getAsJsonObject(),"activation_code","")))return e.deepCopy();
            throw new IllegalStateException("Kein lokaler Bildschirm mit diesem Code gefunden.");
        }

        if (path.equals("/cameras") && method.equals("POST")) return createCamera(body);
        if (path.equals("/cameras/accessible") && method.equals("GET")) return array("cameras").deepCopy();
        if (path.equals("/bodycams/mine") && method.equals("GET")) return myBodycams();
        if (path.startsWith("/cameras/") && !path.endsWith("/frame") && !path.endsWith("/frame-upload-url")) {
            UUID id = uuidSegment(path, 2);
            if (method.equals("PATCH")) return updateById("cameras", id, body, true);
            if (method.equals("DELETE")) { removeCamera(id); return JsonNull.INSTANCE; }
        }
        if (path.endsWith("/frame")) return JsonNull.INSTANCE;

        if (path.equals("/screens") && method.equals("GET")) return array("screens").deepCopy();
        if (path.equals("/screens") && method.equals("POST")) return createScreen(body);
        if (path.startsWith("/screens/")) return screenRoute(method, path, body);
        if (path.equals("/groups") && method.equals("POST")) return createGroup(body);

        if (path.equals("/bodycam-requests") && method.equals("GET")) return array("bodycam_requests").deepCopy();
        if (path.equals("/bodycam-requests") && method.equals("POST")) return createBodycamRequest(body);
        if (path.startsWith("/bodycam-requests/") && path.endsWith("/respond")) return respondBodycam(path, body);

        if (path.equals("/team/me")) return profile();
        if (path.equals("/team/me/name-visibility") && method.equals("POST")) {
            object("profile").addProperty("name_visible", body.get("visible").getAsBoolean());
            return profile();
        }
        if (path.equals("/team/members") && method.equals("GET")) { JsonArray out = new JsonArray(); out.add(profile()); return out; }
        if (path.contains("/event-rank")) return eventRankRoute(method, path, body);
        if (path.startsWith("/team/members/") && path.endsWith("/rank") && method.equals("POST")) {
            throw new IllegalStateException("Der lokale Grundrang bleibt SR-Offizier; nutze eine temporäre Rolle.");
        }
        if (path.equals("/team/rank-requests")) return new JsonObject();
        if (path.equals("/team/chat")) return listRoute(method, "chat", body, true);
        if (path.equals("/team/camera-events")) return listRoute(method, "camera_events", body, true);
        if (path.equals("/team/todos") || path.startsWith("/team/todos/")) return entityRoute(method, path, body, "todos");
        if (path.equals("/team/templates") || path.startsWith("/team/templates/")) return entityRoute(method, path, body, "templates");
        if (path.equals("/team/timer")) { if (method.equals("POST")) data.add("timer", normalizeTimer(body)); return object("timer").deepCopy(); }
        if (path.equals("/team/disqualify")) return new JsonObject();

        throw new IllegalStateException("Lokaler MPSQ-Endpunkt fehlt: " + method + " " + path);
    }

    private static JsonElement createCamera(JsonObject body) {
        JsonObject row = new JsonObject();
        row.addProperty("id", UUID.randomUUID().toString());
        row.addProperty("name", string(body, "name", "Kamera"));
        row.addProperty("kind", string(body, "kind", "STATIC"));
        row.addProperty("dimension", string(body, "dimension", "minecraft:overworld"));
        copyNumberOrNull(body, row, "x"); copyNumberOrNull(body, row, "y"); copyNumberOrNull(body, row, "z");
        row.addProperty("yaw", number(body, "yaw", 0)); row.addProperty("pitch", number(body, "pitch", 0));
        if (body.has("bodyOwnerId")) row.addProperty("body_owner_id", body.get("bodyOwnerId").getAsString()); else row.add("body_owner_id", JsonNull.INSTANCE);
        if (body.has("bodyOwnerName")) row.addProperty("body_owner_name", body.get("bodyOwnerName").getAsString()); else row.add("body_owner_name", JsonNull.INSTANCE);
        array("cameras").add(row); return row.deepCopy();
    }

    public static JsonArray accessoryCatalog(){
        return withBuiltinAccessories(MpsqLocalWorldStore.array("accessory_catalog"));
    }
    public static JsonArray withBuiltinAccessories(JsonArray input){
        JsonArray result=input.deepCopy();
        String[] keys={"nm_hat_budgie","nm_hat_hedgehog","nm_hat_wumpus","nm_hat_owl_long_eared","nm_hat_seagull"};
        String[] names={"Wellensittich","Igel","Wumpus","Eule","Möwe"};
        for(int i=0;i<keys.length;i++){
            String key=keys[i];boolean exists=false;
            for(JsonElement e:result)if(e.isJsonObject()&&("builtin://mpsq/"+key).equals(MpsqAccessoryRenderer.resolveBuiltinAccessory(e.getAsJsonObject()))){exists=true;break;}
            if(exists)continue;
            JsonObject row=new JsonObject();String id=UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
            row.addProperty("id",id);row.addProperty("accessory_id",id);row.addProperty("asset_id",id);row.addProperty("accessory_key",key);row.addProperty("client_builtin",true);row.addProperty("owned",true);
            row.addProperty("display_name",names[i]);row.addProperty("filename",key+".json");row.addProperty("url","builtin://mpsq/"+key);row.addProperty("category","accessory");row.addProperty("price_points",0);
            result.add(row);
        }
        return MpsqAccessoryRenderer.uniqueAccessories(result);
    }

    private static JsonElement createScreen(JsonObject body) {
        JsonObject row = new JsonObject(); UUID id = UUID.randomUUID();
        row.addProperty("id", id.toString()); row.addProperty("name", string(body, "name", "Bildschirm"));
        String mode=string(body,"mode","KINO");row.addProperty("mode",mode.equals("MPSQ_REDSTONE")?"REDSTONE":mode);
        row.addProperty("dimension", string(body, "dimension", "minecraft:overworld"));
        copyPos(body.getAsJsonObject("pos1"), row, "pos1"); copyPos(body.getAsJsonObject("pos2"), row, "pos2");
        row.addProperty("front", string(body, "front", "NORTH")); row.addProperty("cinema_url", string(body, "cinemaUrl", ""));
        row.addProperty("activation_code", code(6)); row.addProperty("is_owner", true); row.add("group_id", JsonNull.INSTANCE);
        row.add("mpsq_screen_cameras", new JsonArray()); row.add("members", new JsonArray());
        row.add("playback_state", defaultPlayback()); row.addProperty("updated_at", Instant.now().toString());
        array("screens").add(row); return row.deepCopy();
    }

    private static JsonElement screenRoute(String method, String path, JsonObject body) {
        String[] p = path.split("/"); UUID screenId = UUID.fromString(p[2]); JsonObject screen = find("screens", screenId);
        if (screen == null) throw new IllegalStateException("Bildschirm nicht gefunden");
        if (p.length == 3) {
            if (method.equals("PATCH")) { mergeScreen(screen, body); return screen.deepCopy(); }
            if (method.equals("DELETE")) { removeById("screens", screenId); return JsonNull.INSTANCE; }
        }
        if (p.length >= 4 && p[3].equals("cameras")) {
            JsonArray links = screen.getAsJsonArray("mpsq_screen_cameras");
            if (method.equals("POST")) { UUID cameraId = UUID.fromString(body.get("cameraId").getAsString()); removeLink(links, cameraId); JsonObject link = new JsonObject(); link.addProperty("camera_id", cameraId.toString()); link.addProperty("sort_order", body.has("sortOrder") ? body.get("sortOrder").getAsInt() : links.size()); links.add(link); screen.addProperty("mode", "CAMERA"); return link; }
            if (method.equals("DELETE") && p.length >= 5) { removeLink(links, UUID.fromString(p[4])); return JsonNull.INSTANCE; }
        }
        if (p.length >= 4 && p[3].equals("members")) {
            JsonArray members = screen.getAsJsonArray("members");
            if (method.equals("GET")) return members.deepCopy();
            if (method.equals("POST")) { JsonObject member = new JsonObject(); member.addProperty("client_id", UUID.randomUUID().toString()); JsonObject c = new JsonObject(); c.addProperty("display_name", string(body, "displayName", "Spieler")); member.add("mpsq_clients", c); members.add(member); return member; }
            if (method.equals("DELETE") && p.length >= 5) { removeMember(members, p[4]); return JsonNull.INSTANCE; }
        }
        if (p.length >= 4 && p[3].equals("remove-from-group")) { screen.add("group_id", JsonNull.INSTANCE); screen.remove("mpsq_screen_groups"); return screen; }
        throw new IllegalStateException("Lokale Bildschirmaktion fehlt: " + method + " " + path);
    }

    private static JsonElement createGroup(JsonObject body) {
        UUID groupId = UUID.randomUUID(); String activation = code(5); JsonObject group = new JsonObject();
        group.addProperty("id", groupId.toString()); group.addProperty("activation_code", activation); array("groups").add(group);
        for (JsonElement id : body.getAsJsonArray("screenIds")) { JsonObject screen = find("screens", UUID.fromString(id.getAsString())); if (screen != null) { screen.addProperty("group_id", groupId.toString()); JsonObject embedded = new JsonObject(); embedded.addProperty("activation_code", activation); screen.add("mpsq_screen_groups", embedded); } }
        return group;
    }

    private static JsonElement createBodycamRequest(JsonObject body) {
        JsonObject req = new JsonObject(); req.addProperty("id", UUID.randomUUID().toString()); req.addProperty("requesterName", displayName()); req.addProperty("targetDisplayName", string(body, "targetDisplayName", displayName())); array("bodycam_requests").add(req); return req;
    }

    private static JsonElement respondBodycam(String path, JsonObject body) {
        UUID requestId = uuidSegment(path, 2); JsonObject req = find("bodycam_requests", requestId);
        if (req != null && body.has("accepted") && body.get("accepted").getAsBoolean()) { JsonObject camera = new JsonObject(); camera.addProperty("name", req.get("targetDisplayName").getAsString() + "'s Bodycam"); camera.addProperty("kind", "BODYCAM"); camera.addProperty("dimension", "minecraft:overworld"); camera.addProperty("bodyOwnerId", object("profile").get("id").getAsString()); camera.addProperty("bodyOwnerName", req.get("targetDisplayName").getAsString()); createCamera(camera); }
        removeById("bodycam_requests", requestId); return new JsonObject();
    }

    private static JsonElement entityRoute(String method, String path, JsonObject body, String key) {
        if (path.equals("/team/" + key)) {
            if (method.equals("GET")) return array(key).deepCopy();
            if (method.equals("POST")) { JsonObject row = body.deepCopy(); row.addProperty("id", UUID.randomUUID().toString()); if (key.equals("todos") && body.has("listKey")) { row.addProperty("list_key", body.get("listKey").getAsString()); row.remove("listKey"); } array(key).add(row); return row; }
        }
        UUID id = uuidSegment(path, 3);
        if (method.equals("PATCH")) { JsonObject row = find(key, id); if (row == null) throw new IllegalStateException("Eintrag nicht gefunden"); for (var e : body.entrySet()) row.add(e.getKey().equals("listKey") ? "list_key" : e.getKey(), e.getValue().deepCopy()); return row; }
        if (method.equals("DELETE")) { removeById(key, id); return JsonNull.INSTANCE; }
        throw new IllegalStateException("Lokale Listenaktion fehlt");
    }

    private static JsonElement listRoute(String method, String key, JsonObject body, boolean enrich) {
        if (method.equals("GET")) return array(key).deepCopy();
        JsonObject row = body.deepCopy(); row.addProperty("id", UUID.randomUUID().toString()); row.addProperty("created_at", Instant.now().toString());
        if (key.equals("chat")) { row.addProperty("sender_name", displayName()); row.addProperty("sender_rank", profile().get("active_rank").getAsString()); }
        if (key.equals("camera_events")) { if (!row.has("viewer_name")) row.addProperty("viewer_name", displayName()); }
        array(key).add(row); while (array(key).size() > 100) array(key).remove(0); return row;
    }

    private static JsonElement eventRankRoute(String method, String path, JsonObject body) {
        JsonObject profile = object("profile");
        if (method.equals("DELETE")) profile.remove("event_rank"); else profile.addProperty("event_rank", body.get("rank").getAsString());
        return profile();
    }

    private static JsonObject profile() {
        JsonObject stored = object("profile"); JsonObject out = stored.deepCopy();
        String base = string(stored, "base_rank", "sr_offizier"); String active = stored.has("event_rank") ? stored.get("event_rank").getAsString() : base;
        out.addProperty("base_rank", base); out.addProperty("active_rank", active); return out;
    }

    private static JsonArray myBodycams() { JsonArray out = new JsonArray(); String self = object("profile").get("id").getAsString(); for (JsonElement e : array("cameras")) { JsonObject c = e.getAsJsonObject(); if (c.has("body_owner_id") && !c.get("body_owner_id").isJsonNull() && self.equals(c.get("body_owner_id").getAsString())) out.add(c.deepCopy()); } return out; }
    private static void removeCamera(UUID id) { removeById("cameras", id); for (JsonElement e : array("screens")) removeLink(e.getAsJsonObject().getAsJsonArray("mpsq_screen_cameras"), id); }
    private static JsonElement updateById(String key, UUID id, JsonObject changes, boolean camera) { JsonObject row = find(key, id); if (row == null) throw new IllegalStateException("Eintrag nicht gefunden"); for (var e : changes.entrySet()) row.add(e.getKey(), e.getValue().deepCopy()); return row.deepCopy(); }
    private static void mergeScreen(JsonObject row, JsonObject body) { for (var e : body.entrySet()) { String key = switch (e.getKey()) { case "cinemaUrl" -> "cinema_url"; case "playbackState" -> "playback_state"; default -> e.getKey(); }; row.add(key, e.getValue().deepCopy()); } row.addProperty("updated_at", Instant.now().toString()); }
    private static void removeLink(JsonArray links, UUID id) { for (int i = links.size() - 1; i >= 0; i--) if (id.toString().equals(links.get(i).getAsJsonObject().get("camera_id").getAsString())) links.remove(i); }
    private static void removeMember(JsonArray members, String id) { for (int i = members.size() - 1; i >= 0; i--) if (id.equals(members.get(i).getAsJsonObject().get("client_id").getAsString())) members.remove(i); }
    private static JsonObject normalizeTimer(JsonObject body) { JsonObject timer = new JsonObject(); boolean running = body.has("running") && body.get("running").getAsBoolean(); timer.addProperty("running", running); timer.addProperty("label", string(body, "label", "")); if (running) timer.addProperty("ends_at", Instant.now().plusSeconds((long) number(body, "durationSeconds", 0)).toString()); else timer.add("ends_at", JsonNull.INSTANCE); return timer; }
    private static JsonObject defaultPlayback() { JsonObject p = new JsonObject(); p.addProperty("playing", false); p.addProperty("positionMs", 0); p.addProperty("revision", 0); return p; }
    private static void copyPos(JsonObject from, JsonObject to, String prefix) { to.addProperty(prefix + "_x", from.get("x").getAsInt()); to.addProperty(prefix + "_y", from.get("y").getAsInt()); to.addProperty(prefix + "_z", from.get("z").getAsInt()); }
    private static void copyNumberOrNull(JsonObject from, JsonObject to, String key) { if (from.has(key) && !from.get(key).isJsonNull()) to.add(key, from.get(key).deepCopy()); else to.add(key, JsonNull.INSTANCE); }
    private static String string(JsonObject o, String key, String fallback) { return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : fallback; }
    private static double number(JsonObject o, String key, double fallback) { return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsDouble() : fallback; }
    private static String code(int length) { StringBuilder out = new StringBuilder(); for (int i = 0; i < length; i++) out.append(CODE_CHARS.charAt((int) (Math.random() * CODE_CHARS.length()))); return out.toString(); }
    private static UUID uuidSegment(String path, int index) { return UUID.fromString(path.split("/")[index]); }
    private static JsonObject find(String key, UUID id) { for (JsonElement e : array(key)) if (e.getAsJsonObject().has("id") && id.toString().equals(e.getAsJsonObject().get("id").getAsString())) return e.getAsJsonObject(); return null; }
    private static void removeById(String key, UUID id) { JsonArray a = array(key); for (int i = a.size() - 1; i >= 0; i--) if (id.toString().equals(a.get(i).getAsJsonObject().get("id").getAsString())) a.remove(i); }
    private static JsonArray array(String key) { if (!data.has(key) || !data.get(key).isJsonArray()) data.add(key, new JsonArray()); return data.getAsJsonArray(key); }
    private static JsonObject object(String key) { if (!data.has(key) || !data.get(key).isJsonObject()) data.add(key, new JsonObject()); return data.getAsJsonObject(key); }
    private static String displayName() { Minecraft client = Minecraft.getInstance(); return client != null && client.getUser() != null ? client.getUser().getName() : "Minecraft Client"; }

    private static void load() {
        Path current=scopeFile();
        if (data != null && current.equals(file)) return;
        JsonObject loaded;
        try { loaded = Files.exists(current) ? JsonParser.parseString(Files.readString(current, StandardCharsets.UTF_8)).getAsJsonObject() : new JsonObject(); }
        catch (Exception exception) { throw new IllegalStateException("Lokale Daten nicht lesbar; Datei bleibt unverändert: "+current,exception); }
        data=loaded;file=current;
        array("cameras"); array("screens"); array("groups"); array("bodycam_requests"); array("chat"); array("camera_events"); array("todos"); array("templates"); object("profile");
        if (!data.has("timer")) data.add("timer", normalizeTimer(new JsonObject()));
        var p=object("profile");if(!p.has("id"))p.addProperty("id",UUID.nameUUIDFromBytes(("local:"+displayName()).getBytes(StandardCharsets.UTF_8)).toString());
        p.addProperty("display_name",displayName());p.addProperty("base_rank","sr_offizier");if(!p.has("name_visible"))p.addProperty("name_visible",true);
    }

    private static Path scopeFile(){
        var c=Minecraft.getInstance();
        // Multiplayer still uses local test data, isolated from singleplayer and other servers.
        if(c!=null&&c.getCurrentServer()!=null){
            String serverScope="multiplayer:"+c.getCurrentServer().ip.trim().toLowerCase(java.util.Locale.ROOT);
            return ROOT.resolve(UUID.nameUUIDFromBytes(serverScope.getBytes(StandardCharsets.UTF_8))+".json");
        }
        String scope=c!=null&&c.getSingleplayerServer()!=null?c.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize().toString():"menu";
        return ROOT.resolve(UUID.nameUUIDFromBytes(scope.getBytes(StandardCharsets.UTF_8))+".json");
    }

    private static void save() {
        try { Files.createDirectories(file.getParent()); Path temp = file.resolveSibling(file.getFileName() + ".tmp"); Files.writeString(temp, GSON.toJson(data), StandardCharsets.UTF_8); try{Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);}catch(java.nio.file.AtomicMoveNotSupportedException e){Files.move(temp,file,java.nio.file.StandardCopyOption.REPLACE_EXISTING);} }
        catch (IOException exception) { throw new IllegalStateException("Lokale MPSQ-Daten konnten nicht gespeichert werden", exception); }
    }
}
