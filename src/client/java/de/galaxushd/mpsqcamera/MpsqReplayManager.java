package de.galaxushd.mpsqcamera;
import com.google.gson.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Local position/event recordings. Playback never sends actions to a server. */
public final class MpsqReplayManager {
    public record Event(long time,String type,String data){}
    private static final List<Event> EVENTS=new ArrayList<>();
    private static JsonArray frames=new JsonArray();
    public static final Path DIRECTORY=FabricLoader.getInstance().getConfigDir().resolve("mpsq/replays");
    private static boolean recording;
    private static long started,next;
    private static String scope="";
    public static void initialize(){ClientTickEvents.END_CLIENT_TICK.register(client->{
        if(!recording)return;
        if(client.world==null||client.player==null||!scope.equals(MpsqActionSync.server()+"|"+MpsqActionSync.world())){stop();return;}
        if(System.currentTimeMillis()<next)return;next=System.currentTimeMillis()+200;
        if(frames.size()>=9000){stop();return;}
        JsonObject frame=new JsonObject();frame.addProperty("time",System.currentTimeMillis()-started);JsonArray players=new JsonArray();
        for(var p:client.world.getPlayers()){
            if(players.size()>=32)break;
            if(p.isInvisible()||(!client.player.canSee(p)&&p!=client.player))continue;
            JsonObject point=new JsonObject();point.addProperty("name",p.getName().getString());point.addProperty("x",p.getX());point.addProperty("y",p.getY());point.addProperty("z",p.getZ());players.add(point);
        }
        frame.add("players",players);frames.add(frame);
    });}
    public static void start(){EVENTS.clear();frames=new JsonArray();started=System.currentTimeMillis();next=0;scope=MpsqActionSync.server()+"|"+MpsqActionSync.world();recording=true;}
    public static void stop(){recording=false;}
    public static void record(String type,String data){if(recording&&EVENTS.size()<10000)EVENTS.add(new Event(System.currentTimeMillis()-started,type,data==null?"":data));}
    public static List<Event> snapshot(){return List.copyOf(EVENTS);}
    public static boolean recording(){return recording;}
    public static JsonArray frames(){return frames.deepCopy();}
    public static Path save() throws java.io.IOException {
        stop();Files.createDirectories(DIRECTORY);Path file=DIRECTORY.resolve("replay-"+Instant.now().toString().replace(':','-')+".json");
        JsonObject root=new JsonObject();root.addProperty("format",1);root.addProperty("scope",scope);root.add("frames",frames);root.add("events",new Gson().toJsonTree(EVENTS));
        Files.writeString(file,new Gson().toJson(root),StandardOpenOption.CREATE_NEW);return file;
    }
}
