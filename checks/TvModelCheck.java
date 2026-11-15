import com.google.gson.*;
import de.galaxushd.mpsqcamera.MpsqTvFurniture;
import net.minecraft.world.phys.Vec3;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
public class TvModelCheck {
    static int checks;
    static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        JsonObject model=JsonParser.parseString(Files.readString(Path.of("src/client/resources/assets/mpsqcamera/custom_models/furniture/mpsq_tv.json"))).getAsJsonObject();
        check(model.getAsJsonArray("elements").size()==12,"All TV parts including six controls preserved");
        double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        for(JsonElement e:model.getAsJsonArray("elements")){JsonObject cube=e.getAsJsonObject();min=Math.min(min,cube.getAsJsonArray("from").get(0).getAsDouble());max=Math.max(max,cube.getAsJsonArray("to").get(0).getAsDouble());}
        check(max-min==48,"Baseline width is exactly three blocks");
        JsonArray frameUv=model.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("faces").getAsJsonObject("north").getAsJsonArray("uv");
        check(Math.abs(frameUv.get(0).getAsDouble()-.0625)<1e-8&&Math.abs(frameUv.get(3).getAsDouble()-.5)<1e-8,"Java-block UVs use the project 8x8 canvas, not stale per-texture 128x128 metadata");
        JsonObject back=model.getAsJsonArray("elements").get(2).getAsJsonObject().getAsJsonObject("faces").getAsJsonObject("south");
        check(Math.abs(back.getAsJsonArray("uv").get(0).getAsDouble()-.234375)<1e-8,"Back panel samples its gray atlas rectangle");
        Class<?> renderer=Class.forName("de.galaxushd.mpsqcamera.MpsqAccessoryRenderer");
        Method bake=renderer.getDeclaredMethod("bakeFurnitureGeometry",JsonArray.class,JsonArray.class,JsonArray.class,Map.class);bake.setAccessible(true);
        Map<String,net.minecraft.resources.Identifier> textures=new HashMap<>();for(String key:model.getAsJsonObject("textures").keySet())textures.put(key,net.minecraft.resources.Identifier.fromNamespaceAndPath("mpsqcamera","check/"+key));
        List<?> geometry=(List<?>)bake.invoke(null,model.getAsJsonArray("elements"),new JsonArray(),new JsonArray(),textures);
        check(geometry.size()==72,"Every frame, button and screen face bakes");
        Method hitbox=MpsqTvFurniture.class.getDeclaredMethod("buttonBounds",int.class);hitbox.setAccessible(true);
        // Element indices selected from the visible atlas glyphs: pink 1,2,3 and gray back,pause,forward.
        int[] buttonElements={5,6,10,8,9,7};
        for(int i=0;i<6;i++){
            JsonObject cube=model.getAsJsonArray("elements").get(buttonElements[i]).getAsJsonObject();
            double x=(cube.getAsJsonArray("from").get(0).getAsDouble()+cube.getAsJsonArray("to").get(0).getAsDouble())/3;
            var bounds=(net.minecraft.world.phys.AABB)hitbox.invoke(null,i);
            check(bounds.contains(x,.5,7),"Action hits the rendered numbered/transport button "+i);
            for(int j=0;j<6;j++)if(i!=j)check(!((net.minecraft.world.phys.AABB)hitbox.invoke(null,j)).contains(x,.5,7),"No crossed button action");
            check(cube.getAsJsonObject("faces").getAsJsonObject("north").get("texture").getAsString().equals(i<3?"1":"3"),"Pink configurable; gray transport");
        }
        Class<?> status=Class.forName("de.galaxushd.mpsqcamera.ScreenStatusTextures");
        Method brighten=status.getDeclaredMethod("brighten",int.class);brighten.setAccessible(true);
        check((int)brighten.invoke(null,0x40FFFFFF)==0,"Transparent residue stays invisible");
        check((int)brighten.invoke(null,0xFF808080)==0xFFCDCDCD,"Status titles are brighter with unchanged opacity");
        Method inverse=MpsqTvFurniture.class.getDeclaredMethod("localPoint",JsonObject.class,Vec3.class);inverse.setAccessible(true);
        for(int width=1;width<=9;width++)for(int rotation:new int[]{0,90,180,270,45}){
            JsonObject row=new JsonObject();row.addProperty("x",112345);row.addProperty("y",64);row.addProperty("z",12345);row.addProperty("rotation",rotation);row.addProperty("scale",width/3.0);
            Vec3 left=MpsqTvFurniture.worldPoint(row,-8,0,7),right=MpsqTvFurniture.worldPoint(row,24,0,7);
            check(Math.abs(left.distanceTo(right)-width)<1e-8,"Exact block width at every rotation and size");
            for(double x:new double[]{-6.5,-5.75,-5,20.5,21.5,22.5}){
                Vec3 world=MpsqTvFurniture.worldPoint(row,x,.5,7),local=(Vec3)inverse.invoke(null,row,world);
                check(local.distanceTo(new Vec3(x,.5,7))<1e-8,"Ray and rendered button agree at distant coordinates");
            }
        }
        System.out.println("PASS: "+checks+" TV geometry / scale / control checks");
    }
}
