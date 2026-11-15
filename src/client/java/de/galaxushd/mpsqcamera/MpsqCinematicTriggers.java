package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Local playback and per-save, per-player once history. No implicit server-wide broadcast. */
final class MpsqCinematicTriggers {
    private static final MpsqCinematicTriggerState edges=new MpsqCinematicTriggerState();
    private static Object world;
    private static boolean dirty=true, ready;
    private static List<MpsqCinematicScene> scenes=List.of();
    private static Set<String> played=new HashSet<>();
    private static final Set<String> pending=new LinkedHashSet<>();
    private static Path history;
    static void invalidate(){dirty=true;}
    static void initialize(){ClientTickEvents.END_CLIENT_TICK.register(MpsqCinematicTriggers::tick);}
    static void redstone(String id){pending.add(id);}
    static MpsqCinematicScene find(String name) throws Exception {
        return MpsqCinematicStore.load().stream().filter(s->s.name.equalsIgnoreCase(name.trim())).findFirst().orElseThrow(()->new IllegalArgumentException("Kamerafahrt nicht gefunden."));
    }
    private static void reload(Minecraft c){
        ready=false;edges.clear();pending.clear();
        try{
            scenes=MpsqCinematicStore.load();
            Path file=MpsqCinematicStore.file();
            history=file.resolveSibling(file.getFileName()+".played-"+c.player.getUUID()+".json");
            played=new HashSet<>();
            if(Files.exists(history)){
                JsonArray values=JsonParser.parseString(Files.readString(history)).getAsJsonArray();
                for(var value:values)played.add(value.getAsString());
            }
            ready=true;
        }catch(Exception e){message("Kamera-Auslöser nicht geladen: "+e.getMessage());}
        dirty=false;
    }
    private static void tick(Minecraft c){
        if(world!=c.level){world=c.level;dirty=true;ready=false;pending.clear();edges.clear();}
        if(c.level==null||c.player==null)return;
        if(dirty)reload(c);
        if(!ready)return;
        Set<String> requests=new LinkedHashSet<>();
        String dimension=c.level.dimension().identifier().toString();
        for(var scene:scenes){
            if(!dimension.equals(scene.dimension))continue;
            boolean master=edges.entered(scene.id+":master",MpsqZoneCatalog.inMaster(c.player));
            boolean zone=edges.entered(scene.id+":zone",!scene.triggerZone.isBlank()&&MpsqZoneCatalog.inCameraZone(scene.triggerZone,c.player));
            if((scene.onMasterEnter&&master)||(!scene.triggerZone.isBlank()&&zone)||(scene.redstoneEnabled&&pending.contains(scene.id)))requests.add(scene.id);
        }
        pending.clear();
        if(!TeamVisibilitySettings.inArea()||c.gui.screen()!=null||MpsqCinematicPreview.active()||ScreenCreationManager.isCameraViewActive()||!c.player.isAlive()||MpsqCinematicScreen.capturing())return;
        for(var scene:scenes){
            if(!requests.contains(scene.id)||scene.points.isEmpty()||(scene.once&&played.contains(scene.id)))continue;
            // Persist before starting: Esc does not bypass a one-time activation.
            if(scene.once&&!remember(scene.id))return;
            c.gui.setScreen(new MpsqCinematicPreview(null,scene,0));
            return;
        }
    }
    private static boolean remember(String id){
        Path temp=null;
        try{
            var next=new TreeSet<>(played);next.add(id);
            Files.createDirectories(history.getParent());temp=Files.createTempFile(history.getParent(),"played-",".tmp");
            Files.writeString(temp,new Gson().toJson(next));
            try{Files.move(temp,history,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,history,StandardCopyOption.REPLACE_EXISTING);}
            played=next;return true;
        }catch(Exception e){message("Einmaliger Kamera-Start nicht gespeichert: "+e.getMessage());return false;}
        finally{if(temp!=null)try{Files.deleteIfExists(temp);}catch(Exception ignored){}}
    }
    private static void message(String text){var p=Minecraft.getInstance().player;if(p!=null)de.galaxushd.mpsqcamera.compat.PlayerMessages.send(p,Component.literal(text),false);}
}
