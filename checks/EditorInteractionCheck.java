import java.lang.reflect.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import com.google.gson.*;
import java.nio.file.*;

public class EditorInteractionCheck {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static Method method(String type,String name,Class<?>...args)throws Exception {Method m=Class.forName("de.galaxushd.mpsqcamera."+type).getDeclaredMethod(name,args);m.setAccessible(true);return m;}
    static String clip(Object choice)throws Exception {if(choice==null)return "";Method m=choice.getClass().getDeclaredMethod("clip");m.setAccessible(true);return (String)m.invoke(choice);}
    static int heads;
    static void bones(JsonArray bones,boolean inherited,Method tracks)throws Exception{for(var e:bones){var b=e.getAsJsonObject();String name=b.get("name").getAsString();boolean applied=(boolean)tracks.invoke(null,name,inherited);if(applied)heads++;if(inherited)require(!applied,"nested head was rotated twice: "+name);if(b.has("children"))bones(b.getAsJsonArray("children"),inherited||applied,tracks);}}
    public static void main(String[]args)throws Exception {
        Method range=method("MpsqNpcManager","withinLookRange",Vec3.class,Vec3.class);
        require((boolean)range.invoke(null,Vec3.ZERO,new Vec3(20,0,0)),"20 inclusive");
        require(!(boolean)range.invoke(null,Vec3.ZERO,new Vec3(20.001,0,0)),"beyond 20");
        require(!(boolean)range.invoke(null,Vec3.ZERO,new Vec3(0,20.001,0)),"vertical range");
        var type=Class.forName("de.galaxushd.mpsqcamera.MpsqNpcReactionClock");var ctor=type.getDeclaredConstructor();ctor.setAccessible(true);Object clock=ctor.newInstance();
        Method step=type.getDeclaredMethod("advance",String.class,boolean.class,boolean.class,double.class,double.class,double.class);step.setAccessible(true);
        String idle="scenes/scene_townsfolk_1/idle",idle2="scenes/scene_townsfolk_2/idle",see="scenes/scene_townsfolk_2/see";
        require(step.invoke(clock,idle,false,false,0d,2d,3d)==null,"idle before gaze");
        require(clip(step.invoke(clock,idle,true,false,1d,2d,3d)).equals(see),"gaze starts reaction");
        require(clip(step.invoke(clock,idle,true,false,2d,2d,3d)).equals(see),"reaction runs");
        require(step.invoke(clock,idle,true,false,3d,2d,3d)==null,"one cycle ends");
        require(step.invoke(clock,idle,true,false,4d,2d,3d)==null,"holding gaze does not restart");
        step.invoke(clock,idle,false,false,5d,2d,3d);
        require(clip(step.invoke(clock,idle,true,false,6d,2d,3d)).equals(see),"new eye contact re-arms");
        require(clip(step.invoke(clock,idle,true,true,7d,2d,3d)).endsWith("scene_townsfolk_2/interact"),"idle1 menu interaction");
        require(step.invoke(clock,idle,true,false,8d,2d,3d)==null,"closing menu restores idle");
        require(clip(step.invoke(clock,idle2,false,true,9d,2d,3d)).endsWith("scene_townsfolk_5_pose_7/idle"),"idle2 menu pose");
        Object second=ctor.newInstance();require(step.invoke(second,idle2,true,false,0d,2d,3d)==null,"idle2 does not react");
        Method tracks=method("MpsqAccessoryRenderer","tracksHead",String.class,boolean.class);
        require((boolean)tracks.invoke(null,"h_head",false),"head rotation");require(!(boolean)tracks.invoke(null,"ih_b_item_head",true),"overlay inherits once");
        try(var files=Files.list(Path.of("src/main/resources/assets/mpsqcamera/custom_models/scenes"))){for(var file:files.filter(f->!f.getFileName().toString().equals("catalog.json")).toList())bones(JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonArray("bones"),false,tracks);}
        require(heads>0,"scene heads checked");
        var cavity=Class.forName("de.galaxushd.mpsqcamera.MpsqCameraCavity");var field=cavity.getDeclaredField("hidden");field.setAccessible(true);var pos=new BlockPos(-1,5,7);field.set(null,pos);
        require((boolean)cavity.getMethod("hides",BlockPos.class).invoke(null,pos),"camera cell hidden");require(!(boolean)cavity.getMethod("hides",BlockPos.class).invoke(null,pos.above()),"neighbor stays visible");field.set(null,null);
        require(!(boolean)cavity.getMethod("hides",BlockPos.class).invoke(null,pos),"camera exit restores cell");
        System.out.println("PASS: 20-block boundaries; gaze single cycle and re-arm; both menu mappings; "+heads+" head hierarchies; cavity cell/neighbor/reset");
    }
}
