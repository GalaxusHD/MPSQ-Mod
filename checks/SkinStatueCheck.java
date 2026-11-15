import java.lang.reflect.*;
import java.util.*;
import java.nio.file.*;
import com.google.gson.*;
public class SkinStatueCheck {
    static int checks;
    static void check(boolean result,String message){checks++;if(!result)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        var normalize=Class.forName("de.galaxushd.mpsqcamera.MpsqSkinImages").getDeclaredMethod("normalizePixels",int[].class,boolean.class);normalize.setAccessible(true);
        int[] p=new int[4096];for(int i=0;i<2048;i++)p[i]=0xFF000000|i;
        normalize.invoke(null,p,true);
        check((p[52*64+20]&0xFFFFFF)==20*64+7,"Legacy left leg is mirrored from right leg");
        check((p[52*64+36]&0xFFFFFF)==20*64+47,"Legacy left arm is mirrored from right arm");
        check((p[8*64+40]>>>24)==0,"Opaque legacy overlay cleared");
        check((p[8*64+8]>>>24)==255,"Head base stays opaque");
        p=new int[4096];p[8*64+40]=0x80AABBCC;normalize.invoke(null,p,false);
        check(p[8*64+40]==0x80AABBCC,"Modern head overlay preserved");
        check((p[8*64+8]>>>24)==255,"Modern base alpha normalized");
        p=new int[4096];p[8*64+8]=0xFFAABBCC;p[8*64+40]=0xFF112233;
        normalize.invoke(null,p,false);
        check(p[8*64+8]==0xFFAABBCC,"Base head pixels retained under opaque overlay");
        check(p[8*64+40]==0xFF112233,"Opaque outer layer retained without changing source skin");
        var blend=Class.forName("de.galaxushd.mpsqcamera.MpsqAnimatedTextures").getDeclaredMethod("blend",int.class,int.class,float.class);blend.setAccessible(true);
        check((int)blend.invoke(null,0xFF000000,0xFFFFFFFF,0f)==0xFF000000,"Frame start");
        check((int)blend.invoke(null,0xFF000000,0xFFFFFFFF,.5f)==0xFF808080,"Frame interpolation");
        check((int)blend.invoke(null,0xFF000000,0xFFFFFFFF,1f)==0xFFFFFFFF,"Frame end");
        try(var paths=Files.list(Path.of("src/client/resources/assets/mpsqcamera/custom_models/furniture"))){for(Path path:paths.toList()){
            var json=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            for(var texture:json.getAsJsonObject("textures").entrySet())if(texture.getKey().contains("sparkle")){
                byte[] png=Base64.getDecoder().decode(texture.getValue().getAsString().split(",")[1]);
                int w=java.nio.ByteBuffer.wrap(png).getInt(16),h=java.nio.ByteBuffer.wrap(png).getInt(20);
                check(h/w==9,"Nine authored sparkle frames");
                check(json.getAsJsonObject("texture_animations").getAsJsonObject(texture.getKey()).get("interpolate").getAsBoolean(),"Animation metadata retained");
            }
        }}
        System.out.println("PASS: "+checks+" skin conversion and animated texture checks");
    }
}
