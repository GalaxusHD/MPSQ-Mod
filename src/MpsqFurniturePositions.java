package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import java.nio.file.*;
import net.minecraft.world.phys.Vec3;

/** Local fine positioning preserves the server/block anchor and its TV links. */
final class MpsqFurniturePositions {
    private static Path loaded;
    private static JsonObject positions=new JsonObject();
    private static Path load(){
        Path base=MpsqCinematicStore.file(),file=base.resolveSibling(base.getFileName().toString().replace(".json","-furniture-positions.json"));
        if(!file.equals(loaded))try{positions=Files.exists(file)?JsonParser.parseString(Files.readString(file)).getAsJsonObject():new JsonObject();loaded=file;}catch(Exception e){throw new IllegalStateException("Möbelpositionen konnten nicht gelesen werden",e);}
        return file;
    }
    static double anchor(JsonObject row,String axis){return row.get(row.has("anchor_"+axis)?"anchor_"+axis:axis).getAsDouble();}
    private static String key(JsonObject row){return MpsqActionSync.world()+":"+anchor(row,"x")+":"+anchor(row,"y")+":"+anchor(row,"z");}
    static JsonObject apply(JsonObject row){
        if(row.has("anchor_x"))return row;
        try{load();var value=positions.get(key(row));if(value==null)return row;
            JsonObject result=row.deepCopy();for(String axis:new String[]{"x","y","z"}){result.addProperty("anchor_"+axis,anchor(row,axis));result.add(axis,value.getAsJsonObject().get(axis));}return result;
        }catch(Exception e){return row;}
    }
    static void save(JsonObject row,Vec3 point){
        TeamVisibilitySettings.requirePlacement(point.x,point.y,point.z);Path file=load();JsonObject next=positions.deepCopy(),value=new JsonObject();
        value.addProperty("x",point.x-.5);value.addProperty("y",point.y);value.addProperty("z",point.z-.5);next.add(key(row),value);
        write(file,next);
    }
    static void remove(JsonObject row){Path file=load();JsonObject next=positions.deepCopy();next.remove(key(row));write(file,next);}
    private static void write(Path file,JsonObject next){
        try{Files.createDirectories(file.getParent());Path tmp=Files.createTempFile(file.getParent(),"furniture-",".tmp");
            try{Files.writeString(tmp,new GsonBuilder().setPrettyPrinting().create().toJson(next));try{Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(tmp);}positions=next;
        }catch(Exception e){throw new IllegalStateException("Möbelposition konnte nicht gespeichert werden",e);}
    }
}
