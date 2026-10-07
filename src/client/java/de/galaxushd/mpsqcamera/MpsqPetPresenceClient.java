package de.galaxushd.mpsqcamera;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Low-rate authenticated presence exchange so modded clients can render each other's companion pets. */
final class MpsqPetPresenceClient {
    record Snapshot(UUID playerId,String petId,String variant,double x,double y,double z,float yaw,long seenAt) { }
    private static final Map<UUID,Snapshot> REMOTE=new ConcurrentHashMap<>();
    private static String scope="";
    private static long nextSync;
    private static volatile boolean posting;
    private MpsqPetPresenceClient() { }
    static void initialize(){ClientTickEvents.END_CLIENT_TICK.register(MpsqPetPresenceClient::tick);}
    static Snapshot localSnapshot(){
        String selected=MpsqPetSelectionStore.selectedId(); if(selected==null)return null;
        Snapshot sample=MpsqMiniYouPetRenderer.snapshot(); if(sample!=null)return sample;
        sample=MpsqBudgiePetRenderer.snapshot(); if(sample!=null)return sample;
        sample=MpsqOtterPetRenderer.snapshot(); if(sample!=null)return sample;
        sample=MpsqHedgehogPetRenderer.snapshot(); if(sample!=null)return sample;
        return MpsqPetRenderer.snapshot();
    }
    static Map<UUID,Snapshot> remote(){return REMOTE;}
    private static void tick(MinecraftClient client){
        if(client.player==null||client.world==null||!TeamVisibilitySettings.visible()){REMOTE.clear();return;}
        String server=MpsqActionSync.server(),world=MpsqActionSync.world();
        if(server.isBlank()){REMOTE.clear();scope="";return;}
        String current=server.toLowerCase(java.util.Locale.ROOT)+"|"+world;
        if(!current.equals(scope)){scope=current;REMOTE.clear();nextSync=0;}
        long now=System.currentTimeMillis();if(now<nextSync||posting)return;nextSync=now+1200;
        Snapshot local=localSnapshot();
        JsonObject body=new JsonObject();body.addProperty("server",server);body.addProperty("world",world);body.addProperty("petId",local==null?"":local.petId());
        if(local!=null){body.addProperty("playerUuid",client.player.getUuid().toString());body.addProperty("variant",local.variant());body.addProperty("x",local.x());body.addProperty("y",local.y());body.addProperty("z",local.z());body.addProperty("yaw",local.yaw());}
        posting=true;MpsqApiClient.post("/pets/presence",body).whenComplete((data,error)->{
            posting=false;if(error!=null||data==null||!data.isJsonArray())return;long seen=System.currentTimeMillis();REMOTE.clear();
            for(JsonElement el:data.getAsJsonArray())try{JsonObject row=el.getAsJsonObject();UUID id=UUID.fromString(row.get("player_uuid").getAsString());if(id.equals(client.player==null?null:client.player.getUuid()))continue;
                Snapshot s=new Snapshot(id,row.get("pet_id").getAsString(),row.has("variant")?row.get("variant").getAsString():"",row.get("x").getAsDouble(),row.get("y").getAsDouble(),row.get("z").getAsDouble(),row.get("yaw").getAsFloat(),seen);REMOTE.put(id,s);
            }catch(Exception ignored){}
            REMOTE.entrySet().removeIf(entry->seen-entry.getValue().seenAt()>10000);
        });
    }
}
