package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import java.util.UUID;

/** Furniture placements stored in the same per-world client data file. */
public final class MpsqLocalObjectStore {
    private MpsqLocalObjectStore(){}
    public static JsonArray loadWorld(String world){JsonArray out=new JsonArray();for(JsonElement e:MpsqLocalWorldStore.array("objects")){if(!e.isJsonObject())continue;JsonObject o=e.getAsJsonObject();if(world.equals(str(o,"world_id","minecraft:overworld")))out.add(o.deepCopy());}return out;}
    public static synchronized boolean set(String world,int x,int y,int z,String model,int rotation,boolean remove){if(!MpsqLocalWorldStore.available())return false;JsonArray all=MpsqLocalWorldStore.array("objects"),next=new JsonArray();for(JsonElement e:all){if(!e.isJsonObject())continue;JsonObject o=e.getAsJsonObject();boolean same=world.equals(str(o,"world_id","minecraft:overworld"))&&o.has("x")&&o.get("x").getAsInt()==x&&o.get("y").getAsInt()==y&&o.get("z").getAsInt()==z;if(!same)next.add(o.deepCopy());}if(!remove){JsonObject o=new JsonObject();o.addProperty("id",UUID.randomUUID().toString());o.addProperty("world_id",world);o.addProperty("x",x);o.addProperty("y",y);o.addProperty("z",z);o.addProperty("model_id",model);o.addProperty("rotation",rotation);next.add(o);}return MpsqLocalWorldStore.setArray("objects",next);}
    private static String str(JsonObject o,String k,String fallback){return o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsString():fallback;}
}
