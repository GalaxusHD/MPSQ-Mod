package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

/** TV placements share real screen IDs, cinema playback and camera assignments. */
public final class MpsqTvFurniture {
    public static final String MODEL="mpsq_tv";
    private static final Map<UUID,JsonObject> placements=new HashMap<>();
    private static final Set<UUID> videoLinks=new HashSet<>();
    public static boolean hasScreenLink(UUID id){return placements.containsKey(id)&&videoLinks.contains(id);}
    private static Object level;
    private static int ticks;
    private static Hit pendingEditor;
    public record Hit(UUID screen,int button,BlockPos anchor){}
    private static String key(){return "tv_links_"+MpsqActionSync.world();}
    public static void initialize(){
        ClientTickEvents.END_CLIENT_TICK.register(c->{if(level!=c.level){level=c.level;placements.clear();videoLinks.clear();pendingEditor=null;ticks=0;}
            if(c.level==null)return;
            if(++ticks%20==0)reload();
            if(pendingEditor!=null&&c.gui.screen()==null){Hit hit=pendingEditor;pendingEditor=null;c.gui.setScreen(new MpsqTvButtonScreen(hit));}});
    }
    public static boolean handleRightClick(){return Minecraft.getInstance().gui.screen()==null&&click()==InteractionResult.SUCCESS;}
    private static void reload(){
        placements.clear();videoLinks.clear();if(!MpsqLocalWorldStore.available())return;
        JsonArray objects=MpsqLocalObjectStore.loadWorld(MpsqActionSync.world());
        for(JsonElement e:MpsqLocalWorldStore.array(key())){
            JsonObject link=e.getAsJsonObject();
            if(link.has("buttons"))for(var button:link.getAsJsonObject("buttons").entrySet())if("OPEN_LINK".equals(button.getValue().getAsJsonObject().get("type").getAsString()))videoLinks.add(UUID.fromString(link.get("screen").getAsString()));
            for(JsonElement item:objects){JsonObject row=item.getAsJsonObject();
                if(MODEL.equals(row.get("model_id").getAsString())&&same(row,link))placements.put(UUID.fromString(link.get("screen").getAsString()),row);
            }
        }
    }
    private static boolean same(JsonObject a,JsonObject b){for(String axis:List.of("x","y","z"))if(a.get(axis).getAsInt()!=b.get(axis).getAsInt())return false;return true;}
    public static JsonObject placement(UUID screen){var row=placements.get(screen);return row==null?null:MpsqFurniturePositions.apply(row);}
    private static JsonObject point(BlockPos p){JsonObject o=new JsonObject();o.addProperty("x",p.getX());o.addProperty("y",p.getY());o.addProperty("z",p.getZ());return o;}
    public static void saved(BlockPos pos,boolean television){
        if(!MpsqLocalWorldStore.available())return;
        JsonObject anchor=point(pos);JsonArray links=MpsqLocalWorldStore.array(key());
        for(JsonElement e:links)if(same(e.getAsJsonObject(),anchor)){
            if(television){reload();return;}
            UUID id=UUID.fromString(e.getAsJsonObject().get("screen").getAsString());
            MpsqApiClient.delete("/screens/"+id).thenCompose(v->ScreenSyncManager.refresh());
            links.remove(e);MpsqLocalWorldStore.setArray(key(),links);placements.remove(id);return;
        }
        if(!television)return;
        JsonObject body=new JsonObject();body.addProperty("name","MPSQ TV "+pos.toShortString());body.addProperty("mode","REDSTONE");
        body.addProperty("dimension",MpsqActionSync.world());body.add("pos1",anchor.deepCopy());body.add("pos2",anchor.deepCopy());body.addProperty("front","NORTH");body.addProperty("cinemaUrl","");
        Object ownerLevel=Minecraft.getInstance().level;String ownerKey=key();
        MpsqApiClient.post("/screens",body).whenComplete((result,error)->Minecraft.getInstance().execute(()->{
            if(Minecraft.getInstance().level!=ownerLevel)return;
            if(error!=null){message("TV-Bildschirm konnte nicht gespeichert werden: "+error.getMessage());return;}
            JsonObject screen=result.isJsonArray()?result.getAsJsonArray().get(0).getAsJsonObject():result.getAsJsonObject();
            JsonArray current=MpsqLocalWorldStore.array(ownerKey);anchor.addProperty("screen",screen.get("id").getAsString());anchor.add("buttons",new JsonObject());current.add(anchor);
            if(!MpsqLocalWorldStore.setArray(ownerKey,current)){message("TV-Verknüpfung konnte nicht gespeichert werden.");return;}
            reload();ScreenSyncManager.refresh();
        }));
    }
    /** Same center, scale and Y rotation as the furniture renderer, in original BB units. */
    public static Vec3 worldPoint(JsonObject row,double x,double y,double z){
        row=MpsqFurniturePositions.apply(row);double scale=row.get("scale").getAsDouble()*1.5/16,angle=Math.toRadians(row.get("rotation").getAsDouble());
        double dx=(x-8)*scale,dz=(z-8.375)*scale;
        return new Vec3(row.get("x").getAsDouble()+.5+dx*Math.cos(angle)+dz*Math.sin(angle),row.get("y").getAsDouble()+y*scale,row.get("z").getAsDouble()+.5-dx*Math.sin(angle)+dz*Math.cos(angle));
    }
    private static Vec3 localPoint(JsonObject row,Vec3 v){
        row=MpsqFurniturePositions.apply(row);double scale=row.get("scale").getAsDouble()*1.5/16,angle=Math.toRadians(row.get("rotation").getAsDouble());
        double dx=v.x-row.get("x").getAsDouble()-.5,dz=v.z-row.get("z").getAsDouble()-.5;
        return new Vec3(8+(dx*Math.cos(angle)-dz*Math.sin(angle))/scale,(v.y-row.get("y").getAsDouble())/scale,8.375+(dx*Math.sin(angle)+dz*Math.cos(angle))/scale);
    }
    /** Logical IDs stay stable in saved actions: pink 1/2/3, then back/pause/forward.
     * Positions follow the visible symbols in the current TV texture atlas. */
    static AABB buttonBounds(int button){
        double[] left={22.25,21.25,20.25,-5.25,-6,-6.75};
        if(button<0||button>=left.length)throw new IllegalArgumentException("TV button");
        return new AABB(left[button]-.08,.17,6.67,left[button]+.58,.83,7.33);
    }
    public static Hit target(){
        var c=Minecraft.getInstance();if(c.player==null||!TeamVisibilitySettings.inArea())return null;
        Vec3 eye=c.player.getEyePosition(),end=eye.add(c.player.getViewVector(1).scale(c.player.blockInteractionRange()));
        double best=c.hitResult!=null&&c.hitResult.getType()!=HitResult.Type.MISS?eye.distanceToSqr(c.hitResult.getLocation())+.0001:eye.distanceToSqr(end);
        Hit found=null;
        for(var entry:placements.entrySet()){
            JsonObject row=entry.getValue();Vec3 from=localPoint(row,eye),to=localPoint(row,end);
            for(int i=0;i<6;i++){
                var hit=buttonBounds(i).clip(from,to);
                if(hit.isEmpty())continue;Vec3 p=hit.get();double distance=eye.distanceToSqr(worldPoint(row,p.x,p.y,p.z));
                if(distance<best){best=distance;found=new Hit(entry.getKey(),i,BlockPos.containing(row.get("x").getAsDouble(),row.get("y").getAsDouble(),row.get("z").getAsDouble()));}
            }
        }return found;
    }
    public static boolean configureTarget(){reload();Hit hit=target();if(hit==null||hit.button()>2)return false;
        if(!MpsqFinishZoneTool.staff()){message("Nur Teammitglieder können TV-Knöpfe bearbeiten.");return true;}
        pendingEditor=hit;return true;
    }
    static boolean saveButton(Hit hit,String type,JsonObject data){
        JsonArray links=MpsqLocalWorldStore.array(key());
        for(JsonElement e:links){JsonObject row=e.getAsJsonObject();if(!row.get("screen").getAsString().equals(hit.screen().toString()))continue;
            JsonObject config=new JsonObject();config.addProperty("type",type);config.add("data",data.deepCopy());
            if(!row.has("buttons"))row.add("buttons",new JsonObject());row.getAsJsonObject("buttons").add(Integer.toString(hit.button()),config);
            boolean saved=MpsqLocalWorldStore.setArray(key(),links);if(saved)reload();return saved;
        }return false;
    }
    private static InteractionResult click(){
        Hit hit=target();if(hit==null)return InteractionResult.PASS;
        if(hit.button()<3){
            for(JsonElement e:MpsqLocalWorldStore.array(key())){JsonObject row=e.getAsJsonObject();if(!row.get("screen").getAsString().equals(hit.screen().toString()))continue;
                JsonObject buttons=row.getAsJsonObject("buttons");String slot=Integer.toString(hit.button());
                if(buttons==null||!buttons.has(slot)){message("Diesen TV-Knopf zuerst mit /mpsq-redstone einrichten.");break;}
                JsonObject config=buttons.getAsJsonObject(slot),data=config.getAsJsonObject("data").deepCopy();String type=config.get("type").getAsString();
                if(type.equals("TV_CAMERA")){
                    UUID camera=UUID.fromString(data.get("cameraId").getAsString());List<UUID> cameras=new ArrayList<>();cameras.add(camera);
                    for(var other:buttons.entrySet()){JsonObject cfg=other.getValue().getAsJsonObject();if("TV_CAMERA".equals(cfg.get("type").getAsString())){UUID id=UUID.fromString(cfg.getAsJsonObject("data").get("cameraId").getAsString());if(!cameras.contains(id))cameras.add(id);}}
                    for(UUID id:ScreenCameraStore.cameras(hit.screen()))if(!cameras.contains(id))cameras.add(id);
                    LocalScreenStore.updateConfig(hit.screen(),LocalScreenStore.ScreenInputType.CAMERA,"",camera);ScreenCameraStore.put(hit.screen(),cameras);
                    while(!camera.equals(ScreenCameraStore.active(hit.screen())))ScreenCameraStore.next(hit.screen(),1);
                    java.util.concurrent.CompletableFuture<?> chain=java.util.concurrent.CompletableFuture.completedFuture(null);
                    for(int i=0;i<cameras.size();i++){JsonObject assignment=new JsonObject();assignment.addProperty("cameraId",cameras.get(i).toString());assignment.addProperty("sortOrder",i);chain=chain.thenCompose(v->MpsqApiClient.post("/screens/"+hit.screen()+"/cameras",assignment));}
                }
                else {if(type.equals("OPEN_LINK")){data.addProperty("screenId",hit.screen().toString());String oldUrl=LocalScreenStore.findById(hit.screen()).map(LocalScreenStore.LocalScreenData::url).orElse("");LocalScreenStore.updateConfig(hit.screen(),LocalScreenStore.ScreenInputType.MPSQ_REDSTONE,oldUrl,null);
                        JsonObject update=new JsonObject();update.addProperty("mode","REDSTONE");update.addProperty("cinemaUrl",data.get("url").getAsString());MpsqApiClient.patch("/screens/"+hit.screen(),update);
                    }
                    JsonObject event=new JsonObject();event.addProperty("action_type",type);event.add("action_data",data);event.add("trigger_position",point(hit.anchor()));event.addProperty("created_at",java.time.Instant.now().toString());MpsqActionSync.dispatch(event);}
                break;
            }
        }else control(hit);
        return InteractionResult.SUCCESS;
    }
    private static void control(Hit hit){
        var screen=LocalScreenStore.findById(hit.screen()).orElse(null);if(screen==null)return;
        if(screen.inputType()==LocalScreenStore.ScreenInputType.CAMERA){
            if(!TeamStateStore.self().map(TeamProfile::canViewCameras).orElse(false))return;
            if(hit.button()==4){List<UUID> cameras=new ArrayList<>(ScreenCameraStore.cameras(hit.screen()));UUID active=ScreenCameraStore.active(hit.screen());if(active!=null){cameras.remove(active);cameras.add(0,active);}ScreenCreationManager.enterMobileView(cameras);}
            else ScreenCameraStore.next(hit.screen(),hit.button()==3?-1:1);
        }else{
            var old=CinemaPlaybackStore.get(hit.screen());long now=System.currentTimeMillis();long position=old.positionMs()+(old.playing()&&old.updatedAtMs()>0?Math.max(0,now-old.updatedAtMs()):0);
            if(hit.button()!=4)position=Math.max(0,position+(hit.button()==3?-10000:10000));
            CinemaPlaybackStore.set(hit.screen(),new CinemaPlaybackStore.PlaybackState(hit.button()==4?!old.playing():old.playing(),position,Math.max(now,old.revision()+1),now));
            CinemaBrowserManager.synchronize();
        }
    }
    static void message(String text){var p=Minecraft.getInstance().player;if(p!=null)de.galaxushd.mpsqcamera.compat.PlayerMessages.send(p,net.minecraft.network.chat.Component.literal(text),true);}
}
