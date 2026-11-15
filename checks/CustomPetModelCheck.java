import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
public class CustomPetModelCheck {
    public static void main(String[] args)throws Exception{
        Class<?> renderer=Class.forName("de.galaxushd.mpsqcamera.MpsqAccessoryRenderer");
        var vec=renderer.getDeclaredMethod("vec",JsonObject.class,String.class);vec.setAccessible(true);
        var normalize=renderer.getDeclaredMethod("normalizeBones",JsonArray.class);normalize.setAccessible(true);
        var validate=renderer.getDeclaredMethod("validateBones",JsonArray.class,int.class);validate.setAccessible(true);
        var sample=renderer.getDeclaredMethod("animationTrack",JsonObject.class,JsonObject.class,String.class,float.class);sample.setAccessible(true);
        for(String stem:List.of("bs_vm_crab","bs_vm_penguin","baby_happy_ghast_mount","nm_colin_the_creeper","nm_exo_the_enderman","nm_slak_the_skeleton","nm_zekk_the_zombie","nm_tiger","nm_tiger_baby")){
            var model=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/mpsqcamera/custom_models/"+stem+".json"))).getAsJsonObject();
            for(var el:model.getAsJsonArray("elements"))for(String key:List.of("from","to","origin","rotation")){
                float[] values=(float[])vec.invoke(null,el.getAsJsonObject(),key);
                if(values.length!=3)throw new AssertionError(stem+" invalid "+key);
                for(float value:values)if(!Float.isFinite(value))throw new AssertionError(stem+" nonfinite "+key);
            }
            var bones=model.getAsJsonArray("bones");normalize.invoke(null,bones);validate.invoke(null,bones,0);
            for(var element:model.getAsJsonArray("elements"))for(var face:element.getAsJsonObject().getAsJsonObject("faces").entrySet())
                for(var uv:face.getValue().getAsJsonObject().getAsJsonArray("uv"))if(!Float.isFinite(uv.getAsFloat()))throw new AssertionError(stem+" invalid UV coordinate");
            Map<String,JsonObject> byName=new HashMap<>();collect(bones,byName);
            for(var el:model.getAsJsonArray("animations")){
                var clip=el.getAsJsonObject();boolean moved=false;
                for(var animator:clip.getAsJsonObject("animators").entrySet()){
                    var bone=byName.get(animator.getKey());if(bone==null)throw new AssertionError(stem+" missing bone "+animator.getKey());
                    for(var channel:animator.getValue().getAsJsonObject().entrySet()){
                        float previous=-1;float[] first=null;
                        for(var frame:channel.getValue().getAsJsonArray()){
                            float time=frame.getAsJsonObject().get("time").getAsFloat();if(time<previous)throw new AssertionError(stem+" unsorted keyframes");previous=time;
                            float[] value=(float[])sample.invoke(null,clip,bone,channel.getKey(),time);
                            if(first==null)first=value;else if(!Arrays.equals(first,value))moved=true;
                        }
                    }
                }
                if(!moved)throw new AssertionError(stem+" static clip "+clip.get("name"));
            }
            System.out.println("PASS: runtime vectors, bone validation and animated tracks: "+stem);
        }
    }
    static void collect(JsonArray bones,Map<String,JsonObject> output){for(var el:bones){var b=el.getAsJsonObject();output.put(b.get("name").getAsString(),b);collect(b.getAsJsonArray("children"),output);}}
}
