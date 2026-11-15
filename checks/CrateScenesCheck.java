import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
public class CrateScenesCheck {
 static boolean crateClipsLoop(JsonObject model){for(var a:model.getAsJsonArray("animations")){var clip=a.getAsJsonObject();if(clip.get("name").getAsString().equals("open idle"))return clip.get("loop").getAsString().equals("loop");}return false;}
 static void require(boolean b,String s){if(!b)throw new AssertionError(s);}
 static void collect(JsonArray list,Map<String,JsonObject> map){for(var v:list){var b=v.getAsJsonObject();map.put(b.get("name").getAsString(),b);collect(b.getAsJsonArray("children"),map);}}
 public static void main(String[] args)throws Exception{
  var crate=Class.forName("de.galaxushd.mpsqcamera.MpsqCrate");var pose=crate.getDeclaredMethod("pose",float.class);pose.setAccessible(true);
  float[] times={0,1.249f,1.25f,8.749f,8.75f,10.749f,10.75f};String[] names={"open","open","open idle","open idle","close","close","idle"};
  for(int i=0;i<times.length;i++){Object p=pose.invoke(null,times[i]);var name=p.getClass().getDeclaredMethod("clip");name.setAccessible(true);require(names[i].equals(name.invoke(p)),"crate boundary "+times[i]);}
  var renderer=Class.forName("de.galaxushd.mpsqcamera.MpsqAccessoryRenderer");var hide=renderer.getDeclaredMethod("hideBone",JsonObject.class,BitSet.class);hide.setAccessible(true);
  var scenes=Class.forName("de.galaxushd.mpsqcamera.MpsqScenesClips");var sample=scenes.getDeclaredMethod("sample",JsonObject.class,JsonObject.class,String.class,float.class);sample.setAccessible(true);
  int clips=0,benches=0;
  try(var paths=Files.list(Path.of("src/main/resources/assets/mpsqcamera/custom_models/scenes"))){for(var path:paths.filter(p->!p.getFileName().toString().equals("catalog.json")).toList()){
   var model=JsonParser.parseString(Files.readString(path)).getAsJsonObject();Map<String,JsonObject> bones=new HashMap<>();collect(model.getAsJsonArray("bones"),bones);
   for(var b:bones.values())if(b.get("name").getAsString().startsWith("scenes_bench")){BitSet hidden=new BitSet();hide.invoke(null,b,hidden);require(hidden.cardinality()>0,"bench not hidden");benches++;}
   for(var a:model.getAsJsonArray("animations")){clips++;var clip=a.getAsJsonObject();for(var target:clip.getAsJsonObject("animators").entrySet()){
    var bone=bones.get(target.getKey());require(bone!=null,"Missing bone: "+path+" "+target.getKey());
    for(var track:target.getValue().getAsJsonObject().entrySet()){float last=0;for(var key:track.getValue().getAsJsonArray()){
     var f=key.getAsJsonObject();float t=f.get("time").getAsFloat();require(t>=last,"unsorted track");
     for(float point:new float[]{last,(last+t)/2,t})for(float v:(float[])sample.invoke(null,clip,bone,track.getKey(),point))require(Float.isFinite(v),"nonfinite pose");last=t;
    }}
   }}
  }}
  require(clips==76,"Expected all 38 clips in normal/slim: "+clips);require(benches==4,"expected two bench models, normal/slim");
  var transform=scenes.getDeclaredMethod("transformSample",JsonObject.class,JsonObject.class,String.class,float.class);transform.setAccessible(true);
  JsonObject testBone=JsonParser.parseString("{\"name\":\"hand\"}").getAsJsonObject();
  JsonObject axes=JsonParser.parseString("{\"coordinate_system\":\"blockbench5\",\"animators\":{\"hand\":{\"rotation\":[{\"time\":0,\"values\":[120,20,50]}],\"position\":[{\"time\":0,\"values\":[2,3,4]}]}}}").getAsJsonObject();
  require(Arrays.equals((float[])transform.invoke(null,axes,testBone,"rotation",0f),new float[]{120,20,50}),"Blockbench 5 arm rotations must not be mirrored");
  axes.addProperty("coordinate_system","blockbench4");
  require(Arrays.equals((float[])transform.invoke(null,axes,testBone,"rotation",0f),new float[]{-120,-20,50}),"Legacy rotation axes");
  require(Arrays.equals((float[])transform.invoke(null,axes,testBone,"position",0f),new float[]{-2,3,4}),"Legacy translation axes");
  var model=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/mpsqcamera/custom_models/mpsq_crate.json"))).getAsJsonObject();
  Map<String,Float> lengths=new HashMap<>();for(var a:model.getAsJsonArray("animations")){var o=a.getAsJsonObject();lengths.put(o.get("name").getAsString(),o.get("length").getAsFloat());}
  require(lengths.equals(Map.of("idle",2f,"open",1.25f,"open idle",1.5f,"close",2f)),"crate source timing");
  require(crateClipsLoop(model),"Open idle must loop");
  var sampleCrate=renderer.getDeclaredMethod("animationTrack",JsonObject.class,JsonObject.class,String.class,float.class);sampleCrate.setAccessible(true);
  JsonObject lid=new JsonObject();lid.addProperty("name","oberteil");Map<String,JsonObject> crateClips=new HashMap<>();for(var a:model.getAsJsonArray("animations"))crateClips.put(a.getAsJsonObject().get("name").getAsString(),a.getAsJsonObject());
  float[] opened=(float[])sampleCrate.invoke(null,crateClips.get("open"),lid,"rotation",1.25f);float[] held=(float[])sampleCrate.invoke(null,crateClips.get("open idle"),lid,"rotation",0f);float[] closing=(float[])sampleCrate.invoke(null,crateClips.get("close"),lid,"rotation",0f);float[] closed=(float[])sampleCrate.invoke(null,crateClips.get("close"),lid,"rotation",2f);
  require(opened[0]==70&&Arrays.equals(opened,held)&&Arrays.equals(held,closing)&&closed[0]==0,"Lid angle/direction or phase continuity");
  require(-Math.sin(Math.toRadians(opened[0]))*(-19)>0,"Lid front must rise above its rear hinge");
  var catalog=Class.forName("de.galaxushd.mpsqcamera.MpsqBuiltinNpcCatalog");var field=catalog.getDeclaredField("SKINS");field.setAccessible(true);var skins=(List<?>)field.get(null);require(skins.size()==46&&skins.get(8).equals("Thanos")&&skins.get(9).equals("spieler_2e8f14af0c41d273"),"Skin count/order");
  for(var id:skins){var path=Path.of("src/main/resources/assets/mpsqcamera/textures/npc_skins/"+id.toString().toLowerCase(Locale.ROOT)+".png");var image=javax.imageio.ImageIO.read(path.toFile());require(image!=null&&image.getWidth()==64&&(image.getHeight()==64||image.getHeight()==32),"Invalid skin "+id);}
  System.out.println("PASS: crate lid rises +70 degrees, phase continuity, 37 new skins after Thanos, all PNG dimensions");
  System.out.println("PASS: crate phase boundaries, source durations, all 76 scene variants sampled, all bench props suppressed");
 }
}


