package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import java.util.UUID;

/** Furniture placements stored in the same per-world client data file. */
public final class MpsqLocalObjectStore {
    private MpsqLocalObjectStore(){}
    public static JsonArray loadWorld(String world){JsonArray out=new JsonArray();for(JsonElement e:MpsqLocalWorldStore.array("objects")){if(!e.isJsonObject())continue;JsonObject o=e.getAsJsonObject();if(world.equals(str(o,"world_id","minecraft:overworld")))out.add(o.deepCopy());}return out;}
    public static synchronized boolean set(String world,int x,int y,int z,String model,int rotation,boolean remove){return set(world,x,y,z,model,rotation,"",1.0f,"",remove);}
    public static synchronized boolean set(String world,int x,int y,int z,String model,int rotation,String displayName,float scale,String soundId,boolean remove){
        if(!MpsqLocalWorldStore.available())return false;
        JsonArray all=MpsqLocalWorldStore.array("objects"),next=new JsonArray();
        boolean found=false;
        for(JsonElement element:all){
            if(!element.isJsonObject()){next.add(element.deepCopy());continue;}
            JsonObject object=element.getAsJsonObject();
            boolean same=world.equals(str(object,"world_id","minecraft:overworld"))
                    &&hasBlockPosition(object,x,y,z);
            if(same)found=true;else next.add(object.deepCopy());
        }
        if(remove&&!found)return false;
        if(!remove){
            JsonObject object=new JsonObject();
            object.addProperty("id",UUID.randomUUID().toString());object.addProperty("world_id",world);
            object.addProperty("x",x);object.addProperty("y",y);object.addProperty("z",z);
            object.addProperty("model_id",model);object.addProperty("rotation",rotation);
            object.addProperty("display_name",displayName==null?"":displayName);
            object.addProperty("scale",Float.isFinite(scale)?Math.max(0.25f,Math.min(3.0f,scale)):1.0f);
            object.addProperty("sound_id",soundId==null?"":soundId);next.add(object);
        }
        return MpsqLocalWorldStore.setArray("objects",next);
    }
    private static boolean hasBlockPosition(JsonObject object,int x,int y,int z){
        try{return object.has("x")&&object.has("y")&&object.has("z")
                &&object.get("x").getAsDouble()==x&&object.get("y").getAsDouble()==y&&object.get("z").getAsDouble()==z;}
        catch(RuntimeException ignored){return false;}
    }
    private static String str(JsonObject o,String k,String fallback){return o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsString():fallback;}
}


