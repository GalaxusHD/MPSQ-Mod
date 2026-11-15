package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;

/** Shared, world-scoped ambient routes. Generated actors never modify server entities. */
final class MpsqSharedPaths {
    static final class Route {
        public String id=UUID.randomUUID().toString(),name="Neuer Pfad",dimension="",direction="FORWARD";
        public boolean enabled,closedRoute;
        public double width=1,spawnDistance=48;
        public int forwardPercent=50,dayCount=3,nightCount=1,rainPercent=50;
        public List<MpsqCinematicScene.Waypoint> points=new ArrayList<>();
        public List<MpsqScenes.Profile> choices=new ArrayList<>();
        public List<MpsqMotionCapture.Frame> recording=new ArrayList<>();
        void validate(){
            if(recording==null)recording=new ArrayList<>();MpsqMotionCapture.validate(recording);
            if(name==null||name.isBlank()||name.length()>64||dimension==null||!Set.of("FORWARD","REVERSE","BOTH").contains(direction)
                ||!Double.isFinite(width)||width<0||width>8||!Double.isFinite(spawnDistance)||spawnDistance<1||spawnDistance>128
                ||forwardPercent<0||forwardPercent>100||dayCount<0||dayCount>64||nightCount<0||nightCount>64||rainPercent<0||rainPercent>100
                ||points==null||points.size()<2||points.size()>21||choices==null||choices.size()>64)throw new IllegalArgumentException("Ungültige Pfadeinstellungen (2–21 Wegpunkte).");
            for(var p:points)if(p==null||!Double.isFinite(p.x)||!Double.isFinite(p.y)||!Double.isFinite(p.z)||!Double.isFinite(p.duration)||p.duration<.1||p.duration>3600)throw new IllegalArgumentException("Ungültiger Wegpunkt.");
            for(var p:choices)p.validate();
        }
    }
    private static final Map<String,Route> routes=new LinkedHashMap<>();
    private static final Map<String,MpsqScenes.Profile> actors=new LinkedHashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static String error;
    private static long refresh;
    private static Path file(){var f=MpsqCinematicStore.file();return f.resolveSibling(f.getFileName().toString().replace(".json","-shared-paths.json"));}
    private static void load(){
        var w=Minecraft.getInstance().level;if(w==world)return;world=w;routes.clear();actors.clear();error=null;refresh=0;
        if(w==null)return;
        try{if(Files.exists(file()))for(var e:JsonParser.parseString(Files.readString(file())).getAsJsonArray()){var r=MpsqScenes.JSON.fromJson(e,Route.class);r.validate();routes.put(r.id,r);}}
        catch(Exception e){routes.clear();error="Pfade konnten nicht geladen werden: "+e.getMessage();}
    }
    static List<Route> all(){load();if(error!=null)throw new IllegalStateException(error);return routes.values().stream().map(r->MpsqScenes.JSON.fromJson(MpsqScenes.JSON.toJson(r),Route.class)).toList();}
    static Route get(String id){load();return routes.get(id);}
    static void save(Route r){
        if(!MpsqScenes.allowed())throw new IllegalStateException("Keine Bearbeitungsrechte.");all();r.validate();
        if(routes.values().stream().anyMatch(v->!v.id.equals(r.id)&&v.name.equalsIgnoreCase(r.name)))throw new IllegalArgumentException("Pfadname bereits vergeben.");
        for(var p:r.points){TeamVisibilitySettings.requirePlacement(p.x-r.width/2,p.y,p.z-r.width/2);TeamVisibilitySettings.requirePlacement(p.x+r.width/2,p.y,p.z+r.width/2);}
        var next=new LinkedHashMap<>(routes);next.put(r.id,MpsqScenes.JSON.fromJson(MpsqScenes.JSON.toJson(r),Route.class));write(next);
    }
    static void delete(String id){if(!MpsqScenes.allowed())throw new IllegalStateException("Keine Bearbeitungsrechte.");all();var next=new LinkedHashMap<>(routes);next.remove(id);write(next);}
    private static void write(Map<String,Route> next){
        try{Path f=file();Files.createDirectories(f.getParent());Path tmp=Files.createTempFile(f.getParent(),"shared-paths-",".tmp");try{Files.writeString(tmp,MpsqScenes.JSON.toJson(next.values()));try{Files.move(tmp,f,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,f,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(tmp);}routes.clear();routes.putAll(next);actors.clear();refresh=0;}
        catch(Exception e){throw new IllegalStateException("Pfad nicht gespeichert: "+e.getMessage(),e);}
    }
    static int population(Route r,long time,boolean rain){int n=Math.floorMod(time,24000)<13000?r.dayCount:r.nightCount;return rain?(int)Math.round(n*r.rainPercent/100.0):n;}
    static String direction(Route r,int seed){return r.direction.equals("BOTH")?(Math.floorMod(seed,100)<r.forwardPercent?"FORWARD":"REVERSE"):r.direction;}
    static MpsqScenes.Profile actor(String id){load();return actors.get(id);}
    static JsonArray append(JsonArray rows){
        load();var c=Minecraft.getInstance();if(c.level==null||c.player==null||!TeamVisibilitySettings.inArea())return rows;
        long now=System.nanoTime();if(now-refresh>500_000_000L){refresh=now;var next=new LinkedHashMap<String,MpsqScenes.Profile>();
            for(var r:routes.values())if(r.enabled&&r.dimension.equals(c.level.dimension().identifier().toString())&&!r.choices.isEmpty()){
                boolean near=r.points.stream().anyMatch(p->c.player.distanceToSqr(p.x,p.y,p.z)<=r.spawnDistance*r.spawnDistance);if(!near)continue;
                for(int i=0;i<population(r,c.level.getOverworldClockTime(),c.level.isRaining());i++){
                    String id="path:"+r.id+":"+i;var p=actors.get(id);if(p==null){p=MpsqScenes.copy(r.choices.get(Math.floorMod(Objects.hash(r.id,i),r.choices.size())));p.runtimeId="npc:"+id;p.sharedPath=r.id;p.pathSlot=i;p.deleted=false;p.enabled=true;p.loop=true;p.task="none";p.dialogue="";p.command="";p.sound="";}next.put(id,p);
                }
            }actors.clear();actors.putAll(next);
        }
        for(var entry:actors.entrySet()){var p=entry.getValue();var row=new JsonObject();row.addProperty("id",entry.getKey());row.addProperty("world_id",MpsqActionSync.world());row.addProperty("asset_id",p.npc.type);row.addProperty("category",p.npc.category);row.addProperty("url",p.npc.url);row.addProperty("display_name",p.npc.name);row.addProperty("x",p.npc.x);row.addProperty("y",p.npc.y);row.addProperty("z",p.npc.z);row.addProperty("world_x",p.npc.x);row.addProperty("world_y",p.npc.y);row.addProperty("world_z",p.npc.z);rows.add(row);}return rows;
    }
    static MpsqCinematicActors.Pose recordedPose(MpsqScenes.Profile p,double time){Route r=get(p.sharedPath);return r==null||!r.enabled?null:MpsqMotionCapture.sample(r.recording,time,true,direction(r,Objects.hash(p.runtimeId,p.pathSlot)));}
    static MpsqCinematicScene.Waypoint position(MpsqScenes.Profile p,double time){
        var capture=recordedPose(p,time);if(capture!=null)return new MpsqCinematicScene.Waypoint(capture.x(),capture.y(),capture.z(),1);
        Route r=get(p.sharedPath);if(r==null||!r.enabled||r.points.size()<2)return new MpsqCinematicScene.Waypoint(p.npc.x,p.npc.y,p.npc.z,1);
        // Each actor chooses one direction per route, never ping-pongs.
        int seed=Objects.hash(p.runtimeId,p.pathSlot);var n=new MpsqCinematicScene.Npc();n.closedRoute=r.closedRoute;n.route=r.points.toArray(new MpsqCinematicScene.Waypoint[21]);
        double length=r.points.stream().skip(1).mapToDouble(w->w.duration).sum(),t=time+Math.floorMod(seed,1000)/1000.0*length;
        var pos=n.position(t,true,direction(r,seed));var ahead=n.position(t+.01,true,direction(r,seed));double dx=ahead.x-pos.x,dz=ahead.z-pos.z,len=Math.hypot(dx,dz),offset=(Math.floorMod(seed/101,1000)/999.0-.5)*r.width;
        return new MpsqCinematicScene.Waypoint(pos.x+(len>1e-6?-dz/len*offset:0),pos.y,pos.z+(len>1e-6?dx/len*offset:0),1);
    }
}

