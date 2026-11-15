import java.lang.reflect.*;
import net.minecraft.client.model.geom.ModelPart;
public class IsolatedSkinCheck {
 public static void main(String[] args)throws Exception{
  Class<?> c=Class.forName("de.galaxushd.mpsqcamera.MpsqIsolatedSkinRenderer");
  Method factory=c.getDeclaredMethod("geometry",boolean.class,boolean.class);factory.setAccessible(true);
  for(boolean slim:new boolean[]{false,true}){
   Object npc=factory.invoke(null,slim,false),body=factory.invoke(null,slim,true);
   Method root=body.getClass().getDeclaredMethod("root"),model=body.getClass().getDeclaredMethod("model");root.setAccessible(true);model.setAccessible(true);
   if(root.invoke(npc)==root.invoke(body))throw new AssertionError("NPC and clone geometry shared");
   if(model.invoke(body)!=null)throw new AssertionError("Clone must have no animating PlayerModel");
   ModelPart r=(ModelPart)root.invoke(body);
   for(String[] parts:new String[][]{{"head","hat"},{"body","jacket"},{"left_arm","left_sleeve"},{"right_arm","right_sleeve"},{"left_leg","left_pants"},{"right_leg","right_pants"}}){
    ModelPart p=r.getChild(parts[0]);p.getChild(parts[1]);
    Field cubes=ModelPart.class.getDeclaredField("cubes");cubes.setAccessible(true);
    if(((java.util.List<?>)cubes.get(p)).isEmpty())throw new AssertionError("Missing base body geometry");
   }
  }
  System.out.println("PASS: isolated slim/wide NPC and clone geometry; six base and outer parts; no clone PlayerModel animator");
 }
}
