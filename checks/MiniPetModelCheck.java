import com.google.gson.*;
import java.util.*;
import java.lang.reflect.*;
import java.io.*;
public class MiniPetModelCheck {
 static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static int cubes(Object group, Set<String> forbidden)throws Exception{
  if(forbidden.contains(field(group,"id")))throw new AssertionError("Helper group rendered");
  int count=((List<?>)field(group,"cubes")).size();
  for(Object child:(List<?>)field(group,"children"))count+=cubes(child,forbidden);
  return count;
 }
 static int expected(JsonObject group,Map<String,JsonObject> elements,Set<String> forbidden){
  String name=group.get("name").getAsString();
  if((group.has("visibility")&&!group.get("visibility").getAsBoolean())||Set.of("hitbox","tag_name","l_leash").contains(name)){
   forbidden.add(group.get("uuid").getAsString());return 0;
  }
  int count=0;
  for(JsonElement c:group.getAsJsonArray("children")){
   if(c.isJsonObject())count+=expected(c.getAsJsonObject(),elements,forbidden);
   else {JsonObject e=elements.get(c.getAsString());if(e!=null&&(!e.has("visibility")||e.get("visibility").getAsBoolean()))count++;}
  }return count;
 }
 public static void main(String[] args)throws Exception{
  Class<?> renderer=Class.forName("de.galaxushd.mpsqcamera.MpsqMiniYouModel");
  Method parse=renderer.getDeclaredMethod("parseGroup",JsonObject.class,Map.class);parse.setAccessible(true);
  Method load=renderer.getDeclaredMethod("model",String.class,boolean.class);load.setAccessible(true);
  Object enderman=load.invoke(null,"endermansdl",false);
  Map<?,?> animations=(Map<?,?>)field(enderman,"animations");
  if(!animations.containsKey("wave")||!animations.containsKey("teleport"))throw new AssertionError("Missing Endermansdl interactions");
  Object teleport=animations.get("teleport");String base=(String)field(field(enderman,"root"),"id");
  Method sample=teleport.getClass().getDeclaredMethod("value",String.class,String.class,double.class);sample.setAccessible(true);
  for(double time:new double[]{0,.75,1.5}){
   Object scale=sample.invoke(teleport,base,"scale",time);double expectedScale=time==.75?0:1;
   for(String axis:List.of("x","y","z"))if(Math.abs((double)field(scale,axis)-expectedScale)>1e-6)throw new AssertionError("Teleport scale at "+time);
  }
  Object untouched=sample.invoke(teleport,"missing-bone","scale",.75);
  if((double)field(untouched,"x")!=1)throw new AssertionError("Missing scale must be identity");
  Class<?> catalog=Class.forName("de.galaxushd.mpsqcamera.MpsqPetCatalog");Object pet=catalog.getMethod("byId",String.class).invoke(null,"endermansdl");
  if(!pet.getClass().getMethod("texture").invoke(pet).equals("endermansdl"))throw new AssertionError("Endermansdl skin changed");
  System.out.println("PASS: Endermansdl skin, wave/teleport and disappearing scale");
  Method waveYaw=renderer.getDeclaredMethod("waveRootYaw",String.class,boolean.class,double.class);waveYaw.setAccessible(true);
  float waving=(float)waveYaw.invoke(null,"my_skin",false,.6);
  if(!Float.isFinite(waving)||Math.abs(waving)<1)throw new AssertionError("Wave must compensate authored head yaw as well as body yaw");
  Class<?> follower=Class.forName("de.galaxushd.mpsqcamera.MpsqMiniYouPetRenderer");
  Method choose=follower.getDeclaredMethod("interactionFor",String.class,boolean.class);choose.setAccessible(true);
  if(!choose.invoke(null,"endermansdl",false).equals("wave")||!choose.invoke(null,"endermansdl",true).equals("teleport"))throw new AssertionError("Exclusive interaction branches");
  if(!choose.invoke(null,"my_skin",true).equals("wave"))throw new AssertionError("Other Mini You changed");
  JsonObject teleportSource=JsonParser.parseReader(new InputStreamReader(renderer.getResourceAsStream("/assets/mpsqcamera/models/pets/nm_mini_you_teleport.bbmodel"))).getAsJsonObject();
  for(JsonElement animation:teleportSource.getAsJsonArray("animations")){
   JsonObject a=animation.getAsJsonObject();if(!a.get("name").getAsString().equals("teleport"))continue;
   for(var entry:a.getAsJsonObject("animators").entrySet()){
    String name=entry.getValue().getAsJsonObject().get("name").getAsString();
    if(!Set.of("right_arm","left_arm").contains(name))continue;
    for(double time:new double[]{0,.2,.35,1.15,1.3,1.5}){
     Object rotation=sample.invoke(teleport,entry.getKey(),"rotation",time);
     for(String axis:List.of("x","y","z"))if(Math.abs((double)field(rotation,axis))>1e-6)throw new AssertionError("Teleport must not wave");
    }
   }
  }
  for(double time:new double[]{.2,.275,1.15,1.225}){
   Object scale=sample.invoke(teleport,base,"scale",time);double value=(double)field(scale,"x");
   if(time==.2&&Math.abs(value-1)>1e-6||time==1.15&&Math.abs(value)>1e-6||((time==.275||time==1.225)&&(value<=0||value>=1)))throw new AssertionError("Effect timing no longer matches scale keys");
  }
  System.out.println("PASS: exclusive wave/teleport, no teleport waving, effect/scale timing");
  Method speed=follower.getDeclaredMethod("followSpeed",boolean.class,double.class,double.class);speed.setAccessible(true);
  double walk=(double)speed.invoke(null,false,2.,.1);
  double run=(double)speed.invoke(null,true,5.,.28);
  if(Math.abs(run-walk*2)>1e-9)throw new AssertionError("Follow/run speed");
  for(int i=1;i<=3;i++){
   String path="/assets/mpsqcamera/models/pets/nm_mini_you_"+i+".bbmodel";
   JsonObject model=JsonParser.parseReader(new InputStreamReader(renderer.getResourceAsStream(path))).getAsJsonObject();
   Set<String> names=new HashSet<>();
   for(JsonElement a:model.getAsJsonArray("animations"))names.add(a.getAsJsonObject().get("name").getAsString());
   if(!names.containsAll(Set.of("wave","walk","alternate_walk","idle")))throw new AssertionError(names);
   Map<String,JsonObject> elements=new HashMap<>();
   for(JsonElement e:model.getAsJsonArray("elements"))elements.put(e.getAsJsonObject().get("uuid").getAsString(),e.getAsJsonObject());
   JsonObject root=model.getAsJsonArray("outliner").get(0).getAsJsonObject();
   Set<String> forbidden=new HashSet<>();int wanted=expected(root,elements,forbidden);
   int actual=cubes(parse.invoke(null,root,elements),forbidden);
   if(actual!=wanted||actual<10)throw new AssertionError("Body parts missing or hidden cubes rendered: "+actual+" / "+wanted);
   System.out.println("PASS: Mini You "+i+", "+actual+" visible body/layer cubes; helper groups excluded");
  }
 }
}
