package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.WorldSavePath;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Persistent client-owned state scoped to one integrated (private) world. */
public final class MpsqLocalWorldStore {
    private static final Path ROOT=FabricLoader.getInstance().getConfigDir().resolve("mpsq-world-data");
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private MpsqLocalWorldStore(){}
    public static boolean available(){return file()!=null;}
    private static Path file(){try{var server=MinecraftClient.getInstance().getServer();if(server==null)return null;Path save=server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(save.toString().getBytes(StandardCharsets.UTF_8)));return ROOT.resolve(hash+".json");}catch(Exception e){return null;}}
    private static synchronized JsonObject read(){Path p=file();if(p==null)return new JsonObject();try{if(!Files.isRegularFile(p))return fresh();JsonElement e=JsonParser.parseString(Files.readString(p));if(!e.isJsonObject())return fresh();JsonObject o=e.getAsJsonObject();JsonObject defaults=fresh();for(var entry:defaults.entrySet())if(!o.has(entry.getKey()))o.add(entry.getKey(),entry.getValue().deepCopy());return o;}catch(Exception e){return fresh();}}
    private static JsonObject fresh(){JsonObject o=new JsonObject();o.addProperty("schema",1);o.addProperty("points",0);o.add("npcs",new JsonArray());o.add("objects",new JsonArray());o.add("quests",new JsonArray());o.add("accessories_owned",new JsonArray());o.add("accessories_equipped",JsonNull.INSTANCE);return o;}
    private static synchronized boolean write(JsonObject data){Path p=file();if(p==null)return false;try{Files.createDirectories(p.getParent());Path tmp=p.resolveSibling(p.getFileName()+".tmp");Files.writeString(tmp,GSON.toJson(data),StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);try{Files.move(tmp,p,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,p,StandardCopyOption.REPLACE_EXISTING);}return true;}catch(Exception e){return false;}}
    private static Path assetFile(String url){try{Path p=file();if(p==null)return null;String key=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8)));return p.resolveSibling(p.getFileName().toString().replaceFirst("\\.json$","-assets")).resolve(key+".bin");}catch(Exception e){return null;}}
    public static byte[] readAsset(String url,int maxBytes){Path p=assetFile(url);if(p==null||!Files.isRegularFile(p))return null;try{if(Files.size(p)>maxBytes)return null;return Files.readAllBytes(p);}catch(Exception e){return null;}}
    public static void writeAsset(String url,byte[] bytes){Path p=assetFile(url);if(p==null||bytes==null)return;try{Files.createDirectories(p.getParent());Path tmp=p.resolveSibling(p.getFileName()+".tmp");Files.write(tmp,bytes,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);try{Files.move(tmp,p,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,p,StandardCopyOption.REPLACE_EXISTING);}}catch(Exception ignored){}}
    public static synchronized JsonArray array(String key){JsonObject d=read();JsonElement e=d.get(key);return e!=null&&e.isJsonArray()?e.getAsJsonArray().deepCopy():new JsonArray();}
    public static synchronized boolean setArray(String key,JsonArray value){JsonObject d=read();d.add(key,value==null?new JsonArray():value.deepCopy());return write(d);}
    public static synchronized long points(){return Math.max(0,read().get("points").getAsLong());}
    public static synchronized boolean addPoints(long amount){if(amount<0)return false;JsonObject d=read();long old=d.has("points")?d.get("points").getAsLong():0;if(Long.MAX_VALUE-old<amount)return false;d.addProperty("points",old+amount);return write(d);}
    public static synchronized boolean spendPoints(long amount){if(amount<0)return false;JsonObject d=read();long old=d.has("points")?d.get("points").getAsLong():0;if(old<amount)return false;d.addProperty("points",old-amount);return write(d);}
    public static synchronized boolean owns(String id){for(JsonElement e:array("accessories_owned"))if(e.isJsonPrimitive()&&id.equals(e.getAsString()))return true;return false;}
    public static synchronized boolean ownAccessory(String id){if(id==null||id.isBlank())return false;if(owns(id))return true;JsonArray a=array("accessories_owned");a.add(id);return setArray("accessories_owned",a);}
    public static synchronized String equipped(){JsonElement e=read().get("accessories_equipped");return e==null||e.isJsonNull()?null:e.getAsString();}
    public static synchronized boolean equip(String id){JsonObject d=read();if(id!=null&&!owns(id))return false;d.add("accessories_equipped",id==null?JsonNull.INSTANCE:new JsonPrimitive(id));return write(d);}
    public static synchronized JsonObject accessory(String id){for(JsonElement e:array("accessories_owned"))if(e.isJsonObject()&&id.equals(e.getAsJsonObject().get("accessory_id").getAsString()))return e.getAsJsonObject().deepCopy();return null;}
}
