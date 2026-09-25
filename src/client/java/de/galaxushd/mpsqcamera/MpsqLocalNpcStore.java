package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/** Stores test NPC placements in the matching integrated-world file. */
public final class MpsqLocalNpcStore {
    private static final Path ROOT=FabricLoader.getInstance().getConfigDir().resolve("mpsq-local-npcs");
    private MpsqLocalNpcStore(){}
    private static Path file(){try{var server=MinecraftClient.getInstance().getServer();if(server==null)return null;Path save=server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(save.toString().getBytes(StandardCharsets.UTF_8)));return ROOT.resolve(hash+".json");}catch(Exception e){return null;}}
    public static JsonArray load(){Path p=file();if(p==null||!Files.isRegularFile(p))return new JsonArray();try{JsonElement e=JsonParser.parseString(Files.readString(p));return e.isJsonArray()?e.getAsJsonArray():new JsonArray();}catch(Exception e){return new JsonArray();}}
    public static JsonArray loadWorld(String world){JsonArray out=new JsonArray();for(JsonElement e:load()){JsonObject n=e.getAsJsonObject();if(world.equals(n.has("world_id")?n.get("world_id").getAsString():"minecraft:overworld"))out.add(n);}return out;}
    public static JsonObject set(String world,BlockPos pos,boolean remove,JsonObject data){JsonArray all=load(),next=new JsonArray();for(JsonElement e:all){JsonObject n=e.getAsJsonObject();boolean same=world.equals(n.has("world_id")?n.get("world_id").getAsString():"minecraft:overworld")&&n.get("x").getAsInt()==pos.getX()&&n.get("y").getAsInt()==pos.getY()&&n.get("z").getAsInt()==pos.getZ();if(!same)next.add(n);}JsonObject saved=null;if(!remove){saved=data.deepCopy();saved.addProperty("id",UUID.randomUUID().toString());saved.addProperty("world_id",world);saved.addProperty("x",pos.getX());saved.addProperty("y",pos.getY());saved.addProperty("z",pos.getZ());next.add(saved);}Path p=file();if(p==null)return null;try{Files.createDirectories(p.getParent());Files.writeString(p,next.toString(),StandardCharsets.UTF_8);return saved;}catch(Exception e){return null;}}
    public static boolean update(String world,String id,JsonObject patch){
        JsonArray all=load();boolean found=false;
        for(JsonElement e:all){if(!e.isJsonObject())continue;JsonObject npc=e.getAsJsonObject();
            if(!id.equals(npc.has("id")?npc.get("id").getAsString():"")||!world.equals(npc.has("world_id")?npc.get("world_id").getAsString():"minecraft:overworld"))continue;
            found=true;copy(patch,npc,"name","display_name");copy(patch,npc,"scale","scale");copy(patch,npc,"glowColor","glow_color");copy(patch,npc,"animation","animation");copy(patch,npc,"yaw","yaw");copy(patch,npc,"pitch","pitch");copy(patch,npc,"facePlayer","face_player");copy(patch,npc,"taskType","task_type");copy(patch,npc,"interactionData","interaction_data");break;
        }
        if(!found)return false;Path p=file();if(p==null)return false;
        try{Files.createDirectories(p.getParent());Files.writeString(p,all.toString(),StandardCharsets.UTF_8);return true;}catch(Exception e){return false;}
    }
    private static void copy(JsonObject source,JsonObject target,String from,String to){if(source.has(from))target.add(to,source.get(from).deepCopy());}
}
