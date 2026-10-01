package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.WorldSavePath;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Stores per-player NPC-task visits and manual glow preferences on the client. */
public final class MpsqNpcVisitStore {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE=FabricLoader.getInstance().getConfigDir().resolve("mpsq-npc-progress.json");
    private MpsqNpcVisitStore(){}

    public static synchronized boolean hasVisited(String task){
        if(!isTask(task))return false;
        return contains(scopeData(readRoot(),false).getAsJsonArray("visited_tasks"),task);
    }

    public static synchronized boolean markVisited(String task){
        if(!isTask(task))return false;
        JsonObject root=readRoot(),scope=scopeData(root,true);JsonArray visited=scope.getAsJsonArray("visited_tasks");
        if(contains(visited,task))return false;
        visited.add(task);scope.add("visited_tasks",visited);saveRoot(root);return true;
    }

    public static synchronized boolean isGlowDisabled(String npcId){
        if(npcId==null||npcId.isBlank())return false;
        return contains(scopeData(readRoot(),false).getAsJsonArray("disabled_npcs"),npcId);
    }

    public static synchronized void setGlowEnabled(String npcId,boolean enabled){
        if(npcId==null||npcId.isBlank())return;
        JsonObject root=readRoot(),scope=scopeData(root,true);JsonArray disabled=scope.getAsJsonArray("disabled_npcs"),next=new JsonArray();
        boolean found=false;
        for(JsonElement item:disabled){if(item.isJsonPrimitive()&&npcId.equals(item.getAsString()))found=true;else next.add(item.deepCopy());}
        if(!enabled&&!found)next.add(npcId);
        scope.add("disabled_npcs",next);saveRoot(root);
    }

    private static boolean isTask(String task){return "tutorial".equals(task)||"accessories".equals(task)||"quest".equals(task);}
    private static boolean contains(JsonArray array,String value){if(array==null)return false;for(JsonElement e:array)if(e.isJsonPrimitive()&&value.equals(e.getAsString()))return true;return false;}

    private static JsonObject scopeData(JsonObject root,boolean create){
        JsonObject scopes=root.has("scopes")&&root.get("scopes").isJsonObject()?root.getAsJsonObject("scopes"):new JsonObject();
        if(create&&(!root.has("scopes")||!root.get("scopes").isJsonObject()))root.add("scopes",scopes);
        JsonObject scope=scopes.has(scopeKey())&&scopes.get(scopeKey()).isJsonObject()?scopes.getAsJsonObject(scopeKey()):null;
        if(scope==null){scope=new JsonObject();scope.add("visited_tasks",new JsonArray());scope.add("disabled_npcs",new JsonArray());if(create)scopes.add(scopeKey(),scope);}
        if(!scope.has("visited_tasks")||!scope.get("visited_tasks").isJsonArray())scope.add("visited_tasks",new JsonArray());
        if(!scope.has("disabled_npcs")||!scope.get("disabled_npcs").isJsonArray())scope.add("disabled_npcs",new JsonArray());
        return scope;
    }

    private static String scopeKey(){
        MinecraftClient client=MinecraftClient.getInstance();String player=client.player==null?"unknown-player":client.player.getUuid().toString();
        String server=MpsqActionSync.server(),localWorld="";
        if(server.isBlank()&&client.getServer()!=null)try{localWorld=client.getServer().getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().toString();}catch(Exception ignored){}
        String identity=player+'\n'+server+'\n'+MpsqActionSync.world()+'\n'+localWorld;
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception ignored){return Integer.toHexString(identity.hashCode());}
    }

    private static JsonObject readRoot(){
        try{if(Files.isRegularFile(FILE)){JsonElement value=JsonParser.parseString(Files.readString(FILE,StandardCharsets.UTF_8));if(value.isJsonObject())return value.getAsJsonObject();}}catch(Exception ignored){}
        JsonObject root=new JsonObject();root.add("scopes",new JsonObject());return root;
    }

    private static void saveRoot(JsonObject root){
        try{Files.createDirectories(FILE.getParent());Path temp=FILE.resolveSibling(FILE.getFileName()+".tmp");Files.writeString(temp,GSON.toJson(root),StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);try{Files.move(temp,FILE,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ignored){Files.move(temp,FILE,StandardCopyOption.REPLACE_EXISTING);}}catch(Exception e){MpsqCameraClient.LOGGER.warn("NPC-Aufgabenstatus konnte nicht gespeichert werden",e);}
    }
}
