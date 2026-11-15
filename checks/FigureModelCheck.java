import com.google.gson.*;
import java.nio.file.*;
import java.lang.reflect.*;
public class FigureModelCheck {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        var sample=Class.forName("de.galaxushd.mpsqcamera.MpsqFigureAnimation").getDeclaredMethod("sample",JsonObject.class,JsonObject.class,String.class,float.class);sample.setAccessible(true);
        var valid=Class.forName("de.galaxushd.mpsqcamera.MpsqFigureSkins").getDeclaredMethod("valid",String.class);valid.setAccessible(true);
        check((boolean)valid.invoke(null,"Galaxus_HD"),"Valid Java name");
        check(!(boolean)valid.invoke(null,"../skin"),"Reject path as name");
        try(var paths=Files.list(Path.of("src/client/resources/assets/mpsqcamera/custom_models/furniture"))){
            for(Path path:paths.filter(p->p.toString().contains("bobblehead")).toList()){
                var model=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                String skinKey=model.get("skin_texture_key").getAsString();
                check(model.getAsJsonObject("textures").has(skinKey),"Figure texture exists "+path);
                check(model.getAsJsonObject("textures").size()>1,"Separate stand texture "+path);
                var clip=model.getAsJsonArray("animations").get(0).getAsJsonObject();float length=clip.get("length").getAsFloat();
                for(var entry:clip.getAsJsonObject("animators").entrySet()){
                    var bone=new JsonObject();bone.addProperty("name",entry.getKey());
                    for(var channel:entry.getValue().getAsJsonObject().entrySet()){
                        var frames=channel.getValue().getAsJsonArray();
                        for(var frame:frames){float t=frame.getAsJsonObject().get("time").getAsFloat();var v=(float[])sample.invoke(null,clip,bone,channel.getKey(),t);var expected=frame.getAsJsonObject().getAsJsonArray("values");for(int k=0;k<3;k++)check(Math.abs(v[k]-expected.get(k).getAsFloat())<.001,"Authored keyframe preserved");}
                        for(float t=0;t<=length;t+=.037f){var v=(float[])sample.invoke(null,clip,bone,channel.getKey(),t);for(float f:v)check(Float.isFinite(f),"Finite animation");}
                    }
                }
            }
        }
        System.out.println("PASS: "+checks+" figure model / animation checks");
    }
}
