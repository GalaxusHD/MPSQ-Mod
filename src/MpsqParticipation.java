package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.storage.LevelResource;

/** Local event roster, scoped to a server or single-player save. */
public final class MpsqParticipation {
    private static String scope="";
    private static Path file;
    private static MpsqReturnPool pool=new MpsqReturnPool();
    private static long nextScan;
    private static String error="";
    public static boolean allowed(){return TeamStateStore.self().map(p->p.permissionRank().level()>=TeamRank.OFFICER.level()).orElse(false);}
    private static void load(){
        var c=Minecraft.getInstance();String key=c.level==null?"":MpsqActionSync.server();
        if(c.level!=null&&key.isEmpty()&&c.getSingleplayerServer()!=null)key=c.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().toString();
        if(key.equals(scope))return;scope=key;pool=new MpsqReturnPool();error="";nextScan=0;
        file=key.isEmpty()?null:FabricLoader.getInstance().getConfigDir().resolve("mpsq-participants").resolve(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8))+".json");
        if(file!=null&&Files.isRegularFile(file))try{
            var root=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for(JsonElement e:root.getAsJsonArray("waiting"))pool.exclude(e.getAsString());
            for(JsonElement e:root.getAsJsonArray("selected")){String n=e.getAsString();pool.selected.put(MpsqReturnPool.key(n),n);}
            for(JsonElement e:root.getAsJsonArray("returned"))pool.returned.add(e.getAsString());
        }catch(Exception e){error="Teilnehmerliste konnte nicht gelesen werden.";MpsqCameraClient.LOGGER.warn(error,e);}
    }
    private static void save(){if(file==null)return;try{
        JsonObject root=new JsonObject();JsonArray waiting=new JsonArray(),selected=new JsonArray(),returned=new JsonArray();
        pool.waiting.values().forEach(waiting::add);pool.selected.values().forEach(selected::add);pool.returned.forEach(returned::add);
        root.add("waiting",waiting);root.add("selected",selected);root.add("returned",returned);
        Files.createDirectories(file.getParent());Path temp=file.resolveSibling(file.getFileName()+".tmp");Files.writeString(temp,new GsonBuilder().setPrettyPrinting().create().toJson(root),StandardCharsets.UTF_8);
        try{Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
        error="";
    }catch(Exception e){error="Teilnehmerliste konnte nicht gespeichert werden.";MpsqCameraClient.LOGGER.warn(error,e);}}
    public static boolean inactive(String name){load();String k=MpsqReturnPool.key(name);return pool.waiting.containsKey(k);}
    public static boolean returned(String name){load();return pool.returned.contains(MpsqReturnPool.key(name));}
    public static void exclude(String name){load();if(error.isEmpty()&&name!=null&&!name.isBlank()&&pool.exclude(name))save();}
    public static void scan(){
        load();long now=System.currentTimeMillis();if(now<nextScan)return;nextScan=now+1000;
        var c=Minecraft.getInstance();if(c.getConnection()==null||!error.isEmpty())return;boolean changed=false;
        for(var info:c.getConnection().getOnlinePlayers()){
            String n=info.getProfile().name();
            if(TeamStateStore.byMinecraftName(n).map(TeamProfile::displayedRank).orElse(TeamRank.PLAYER)==TeamRank.VIP&&!pool.returned.contains(MpsqReturnPool.key(n)))changed|=pool.exclude(n);
        }
        if(changed)save();
    }
    public static List<String> selected(){scan();return List.copyOf(pool.selected.values());}
    public static int available(){scan();return pool.waiting.size()-pool.selected.size();}
    public static String draw(int count){if(!allowed())return "Nur Offizier oder höher.";scan();if(!error.isEmpty())return error;
        if(!MpsqSystemController.activeSystemId().isEmpty())return "Auslosen ist nur in der Spielpause möglich.";
        try{pool.draw(count,new java.security.SecureRandom());save();return error.isEmpty()?count+" Personen ausgelost.":error;}catch(IllegalArgumentException e){return e.getMessage();}}
    public static String confirm(String name){if(!allowed())return "Nur Offizier oder höher.";load();
        if(!MpsqSystemController.activeSystemId().isEmpty())return "Rückkehr nur in der Spielpause bestätigen.";
        if(!error.isEmpty())return error;
        if(!pool.confirm(name))return "Name nicht in der Auswahl.";
        MpsqGamesRuntime.restore(name);save();return error.isEmpty()?name+": Rückkehr bestätigt.":error;}
}
