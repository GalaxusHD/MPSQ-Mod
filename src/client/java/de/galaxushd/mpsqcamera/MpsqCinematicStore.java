package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;
import net.fabricmc.loader.api.FabricLoader;

/** Scene files are scoped to a server or single-player save, never to the shared menu. */
final class MpsqCinematicStore {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    static Path file(){
        var c=Minecraft.getInstance();if(c.level==null)throw new IllegalStateException("Bitte zuerst eine Welt betreten.");
        String key=MpsqActionSync.server();
        if(key.isEmpty()&&c.getSingleplayerServer()!=null)key=c.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().toString();
        if(key.isEmpty())throw new IllegalStateException("Die Welt konnte nicht zugeordnet werden.");
        return FabricLoader.getInstance().getConfigDir().resolve("mpsq-cinematics").resolve(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8))+".json");
    }
    static List<MpsqCinematicScene> load() throws Exception {
        Path file=file();if(!Files.exists(file))return new ArrayList<>();
        var array=GSON.fromJson(Files.readString(file,StandardCharsets.UTF_8),MpsqCinematicScene[].class);
        if(array==null)throw new IllegalArgumentException("Leere Szenendatei.");
        var raw=JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8)).getAsJsonArray();
        var result=new ArrayList<>(Arrays.asList(array));
        for(int i=0;i<result.size();i++){var s=result.get(i);
            if(!raw.get(i).getAsJsonObject().has("pathVersion")){for(var p:s.points)if(!"CUT".equals(p.transition))p.transition="SMOOTH";s.pathVersion=1;}
            s.validate();
        }return result;
    }
    static void save(List<MpsqCinematicScene> scenes) throws Exception {
        for(var s:scenes)s.validate();Path file=file();Files.createDirectories(file.getParent());
        Path temp=Files.createTempFile(file.getParent(),"scenes-",".tmp");
        try{Files.writeString(temp,GSON.toJson(scenes),StandardCharsets.UTF_8);
            try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
        MpsqCinematicTriggers.invalidate();MpsqSceneWorldNpcs.invalidate();
    }
}



