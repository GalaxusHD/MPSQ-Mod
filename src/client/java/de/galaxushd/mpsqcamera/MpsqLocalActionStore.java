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

/** Stores test-world button actions per integrated save. */
public final class MpsqLocalActionStore {
    private static final Path ROOT=FabricLoader.getInstance().getConfigDir().resolve("mpsq-local-actions");
    private MpsqLocalActionStore(){}
    private static Path file(){try{var server=MinecraftClient.getInstance().getServer();if(server==null)return null;Path save=server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(save.toString().getBytes(StandardCharsets.UTF_8)));return ROOT.resolve(hash+".json");}catch(Exception e){return null;}}
    public static JsonArray load(){Path p=file();if(p==null||!Files.isRegularFile(p))return new JsonArray();try{JsonElement e=JsonParser.parseString(Files.readString(p));return e.isJsonArray()?e.getAsJsonArray():new JsonArray();}catch(Exception e){return new JsonArray();}}
    public static boolean set(BlockPos pos,String block,String type,JsonObject data){JsonArray old=load(),next=new JsonArray();for(JsonElement e:old){if(!e.isJsonObject())continue;JsonObject r=e.getAsJsonObject();if(r.get("x").getAsInt()==pos.getX()&&r.get("y").getAsInt()==pos.getY()&&r.get("z").getAsInt()==pos.getZ())continue;next.add(r);}JsonObject row=new JsonObject();row.addProperty("x",pos.getX());row.addProperty("y",pos.getY());row.addProperty("z",pos.getZ());row.addProperty("blockId",block);row.addProperty("actionType",type);row.add("actionData",data.deepCopy());next.add(row);Path p=file();if(p==null)return false;try{Files.createDirectories(p.getParent());Files.writeString(p,next.toString(),StandardCharsets.UTF_8);return true;}catch(Exception e){return false;}}
    public static JsonObject find(BlockPos pos){for(JsonElement e:load()){JsonObject r=e.getAsJsonObject();if(r.get("x").getAsInt()==pos.getX()&&r.get("y").getAsInt()==pos.getY()&&r.get("z").getAsInt()==pos.getZ())return r;}return null;}
}
