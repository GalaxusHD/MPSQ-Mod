import de.galaxushd.mpsqcamera.MpsqMotionCapture;
import de.galaxushd.mpsqcamera.MpsqCinematicScene;
import java.util.*;
public class MotionCaptureCheck {
 static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
 static double number(Object pose,String name)throws Exception{var method=pose.getClass().getMethod(name);method.setAccessible(true);return ((Number)method.invoke(pose)).doubleValue();}
 public static void main(String[] args)throws Exception{
  var sample=MpsqMotionCapture.class.getDeclaredMethod("sample",List.class,double.class,boolean.class,String.class);sample.setAccessible(true);
  var validate=MpsqMotionCapture.class.getDeclaredMethod("validate",List.class);validate.setAccessible(true);
  var a=new MpsqMotionCapture.Frame();a.time=0;a.yaw=170;a.head=10;a.pitch=-30;
  var b=new MpsqMotionCapture.Frame();b.time=2;b.x=10;b.y=4;b.yaw=-170;b.head=30;b.pitch=30;
  var frames=List.of(a,b);validate.invoke(null,frames);
  Object pose=sample.invoke(null,frames,1d,false,"FORWARD");
  var type=pose.getClass();
  require(Math.abs(number(pose,"x")-5)<.0001,"interpolate position");
  require(Math.abs(number(pose,"yaw")-180)<.0001,"shortest yaw across 180 degrees");
  pose=sample.invoke(null,frames,0d,false,"REVERSE");require(number(pose,"x")==10,"reverse begins at end");
  pose=sample.invoke(null,frames,3d,true,"BOTH");require(number(pose,"x")==5,"ping-pong return leg");
  var n=new MpsqCinematicScene.Npc();n.recording=new ArrayList<>(frames);var gson=new com.google.gson.Gson();var copy=gson.fromJson(gson.toJson(n),MpsqCinematicScene.Npc.class);require(copy.recording.size()==2&&copy.recording.get(1).yaw==-170,"recording persistence");
  var clips=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/assets/mpsqcamera/custom_models/scenes/catalog.json"))).getAsJsonArray();
  a.clip=clips.get(0).getAsJsonObject().get("id").getAsString();a.age=.2f;validate.invoke(null,frames);
  pose=sample.invoke(null,frames,.5d,false,"FORWARD");var clipMethod=pose.getClass().getMethod("clip");clipMethod.setAccessible(true);
  require(a.clip.equals(clipMethod.invoke(pose))&&Math.abs(number(pose,"clipTime")-.7)<.0001,"Scenes clip and age survive recording interpolation");
  b.time=0;boolean rejected=false;try{validate.invoke(null,frames);}catch(java.lang.reflect.InvocationTargetException e){rejected=e.getCause() instanceof IllegalArgumentException;}require(rejected,"reject non-increasing frame timestamps");
  System.out.println("PASS: motion interpolation, angle boundary, reverse, ping-pong, persistence, invalid frames");
 }
}
