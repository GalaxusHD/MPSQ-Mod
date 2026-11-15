package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.stats.Stats;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** World-scoped accepted quests. Counts new gains, independently of the menu. */
public final class MpsqQuestProgress {
    private static final Map<String,JsonObject> quests=new LinkedHashMap<>();
    private static final Map<String,Integer> baselines=new HashMap<>();
    private static final Map<String,Long> bars=new HashMap<>();
    private static final Set<String> pending=new HashSet<>();
    private static final Map<String,Integer> unsaved=new HashMap<>();
    private static final Map<String,Long> retryAt=new HashMap<>();
    private static Object world;private static Path file;private static long next,statsNext,localNext;private static boolean statsReady;
    private static String str(JsonObject q,String key,String fallback){return q.has(key)&&!q.get(key).isJsonNull()?q.get(key).getAsString():fallback;}
    private static int num(JsonObject q,String key,int fallback){try{return q.get(key).getAsInt();}catch(Exception e){return fallback;}}
    private static boolean flag(JsonObject q,String key){return q.has(key)&&q.get(key).getAsBoolean();}
    private static boolean active(JsonObject q){return flag(q,"accepted")&&!flag(q,"claimed");}
    private static boolean local(){return MpsqActionSync.server().isBlank()&&MpsqLocalWorldStore.available();}
    public static void initialize(){ClientTickEvents.END_CLIENT_TICK.register(c->tick());}
    public static void statsReceived(){statsReady=true;}
    private static void ensureWorld(){
        var c=Minecraft.getInstance();if(world==c.level)return;
        for(String id:bars.keySet())MpsqBossbarManager.remove("quest-"+id);
        quests.clear();baselines.clear();bars.clear();pending.clear();unsaved.clear();retryAt.clear();statsReady=false;next=statsNext=localNext=0;world=c.level;file=null;
        if(world==null)return;
        try{var scene=MpsqCinematicStore.file();file=scene.resolveSibling(scene.getFileName().toString().replace(".json","-quest-progress.json"));if(Files.exists(file))for(var e:JsonParser.parseString(Files.readString(file)).getAsJsonArray()){var q=e.getAsJsonObject();String id=str(q,"id","");quests.put(id,q);if(active(q)&&q.has("client_pending_progress"))unsaved.put(id,num(q,"client_pending_progress",0));q.remove("client_pending_progress");}}catch(Exception e){MpsqCameraClient.LOGGER.warn("Quest-Zwischenspeicher nicht lesbar",e);}
    }
    public static void observe(JsonArray rows){ensureWorld();for(var e:rows)if(e.isJsonObject()){
        var q=e.getAsJsonObject();String id=str(q,"id","");if(id.isBlank())continue;
        var old=quests.put(id,q.deepCopy());
        if(!active(q)){baselines.remove(id);bars.remove(id);unsaved.remove(id);MpsqBossbarManager.remove("quest-"+id);}else if(old==null||!active(old))baseline(id,q);
    }save();}
    public static void updateView(JsonObject q){var latest=quests.get(str(q,"id",""));if(latest!=null)q.addProperty("progress",num(latest,"progress",0));}
    private static void save(){if(file==null)return;try{Files.createDirectories(file.getParent());JsonArray a=new JsonArray();quests.forEach((id,q)->{var copy=q.deepCopy();if(unsaved.containsKey(id))copy.addProperty("client_pending_progress",unsaved.get(id));a.add(copy);});Path tmp=Files.createTempFile(file.getParent(),"quests-",".tmp");Files.writeString(tmp,a.toString());try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}}catch(Exception e){MpsqCameraClient.LOGGER.warn("Quest-Zwischenspeicher nicht gespeichert",e);}}
    private static int count(JsonObject q){
        var c=Minecraft.getInstance();String id=str(q,"objective_item","minecraft:air");
        if(id.startsWith("kill:")){if(!statsReady)return -1;var type=BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id.substring(5)));return c.player.getStats().getValue(Stats.ENTITY_KILLED.get(type));}
        var item=BuiltInRegistries.ITEM.getValue(Identifier.parse(id));int total=0;var inv=c.player.getInventory();for(int i=0;i<inv.getContainerSize();i++){var stack=inv.getItem(i);if(stack.is(item))total+=stack.getCount();}return total;
    }
    private static void baseline(String id,JsonObject q){try{int n=count(q);if(n>=0)baselines.put(id,n);}catch(Exception ignored){}}
    public static String objective(JsonObject q){String id=str(q,"objective_item","minecraft:stone");String name;try{name=id.startsWith("kill:")?BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id.substring(5))).getDescription().getString():MpsqGuiIcon.itemName(id).getString();}catch(Exception e){name=id;}return (id.startsWith("kill:")?"Töte ":"Sammle ")+num(q,"target_count",1)+" × "+name;}
    private static void tick(){
        ensureWorld();var c=Minecraft.getInstance();if(c.player==null)return;long now=System.currentTimeMillis();
        bars.entrySet().removeIf(e->{if(now>=e.getValue()){MpsqBossbarManager.remove("quest-"+e.getKey());return true;}return false;});
        if(now<next)return;next=now+250;
        if(local()&&now>=localNext){
            localNext=now+2000;
            var rows=MpsqLocalWorldStore.array("quests");Set<String> ids=new HashSet<>();for(var e:rows)if(e.isJsonObject()){var q=e.getAsJsonObject();String id=str(q,"id","");ids.add(id);quests.put(id,q);}
            quests.keySet().removeIf(id->{if(ids.contains(id))return false;baselines.remove(id);bars.remove(id);unsaved.remove(id);MpsqBossbarManager.remove("quest-"+id);return true;});
        }
        boolean kills=quests.values().stream().anyMatch(q->active(q)&&str(q,"objective_item","").startsWith("kill:"));
        if(kills&&now>=statsNext){statsNext=now+2000;c.player.connection.send(new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.REQUEST_STATS));}
        for(var q:new ArrayList<>(quests.values())){
            String id=str(q,"id","");if(!active(q)){baselines.remove(id);bars.remove(id);unsaved.remove(id);MpsqBossbarManager.remove("quest-"+id);continue;}if(pending.contains(id))continue;
            if(unsaved.containsKey(id)){if(now>=retryAt.getOrDefault(id,0L))progress(q,unsaved.get(id));continue;}
            try{int n=count(q);if(n<0)continue;Integer old=baselines.put(id,n);if(old==null||n<=old)continue;int before=num(q,"progress",0),after=Math.min(num(q,"target_count",1),before+n-old);if(after>before)progress(q,after);}catch(Exception e){MpsqCameraClient.LOGGER.debug("Questziel nicht verfügbar",e);}
        }
    }
    private static void progress(JsonObject q,int value){
        String id=str(q,"id","");unsaved.put(id,value);save();retryAt.put(id,System.currentTimeMillis()+2000);if(local()){
            var rows=MpsqLocalWorldStore.array("quests");boolean found=false;for(var e:rows)if(e.isJsonObject()&&id.equals(str(e.getAsJsonObject(),"id",""))&&active(e.getAsJsonObject())){e.getAsJsonObject().addProperty("progress",value);found=true;}
            if(found&&MpsqLocalWorldStore.setArray("quests",rows))committed(q,value);return;
        }
        Object scope=world;pending.add(id);JsonObject body=new JsonObject();body.addProperty("progress",value);
        MpsqApiClient.post("/quests/"+id+"/progress",body).whenComplete((d,e)->Minecraft.getInstance().execute(()->{if(world!=scope)return;pending.remove(id);if(e==null)committed(q,value);else MpsqCameraClient.LOGGER.warn("Questfortschritt nicht gespeichert",e);}));
    }
    private static void committed(JsonObject q,int value){String id=str(q,"id","");unsaved.remove(id);retryAt.remove(id);q=quests.getOrDefault(id,q);if(!active(q)){save();return;}q.addProperty("progress",value);quests.put(id,q);bars.put(id,System.currentTimeMillis()+10000);MpsqBossbarManager.apply(new MpsqBossbarState("quest-"+id,objective(q),"purple",Math.min(1f,(float)value/Math.max(1,num(q,"target_count",1))),true));save();}
}
