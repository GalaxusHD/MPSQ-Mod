import java.lang.reflect.*;
import java.util.*;
import com.google.gson.*;
import java.io.*;
public class ModularTimerCheck {
 static Class<?> clock;static Object timer;
 static Object call(String name,Class<?>[] types,Object...args)throws Exception{var m=clock.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(timer,args);}
 static long value(String name)throws Exception{var f=clock.getDeclaredField(name);f.setAccessible(true);return f.getLong(timer);}
 static void flag(String name,boolean b)throws Exception{var f=clock.getDeclaredField(name);f.setAccessible(true);f.setBoolean(timer,b);}
 static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
 static JsonObject model(String name)throws Exception{try(var in=ModularTimerCheck.class.getResourceAsStream("/assets/mpsqcamera/custom_models/"+name+".json")){return JsonParser.parseReader(new InputStreamReader(in)).getAsJsonObject();}}
 static void bones(JsonObject b,Set<String> names){names.add(b.get("name").getAsString());for(var c:b.getAsJsonArray("children"))bones(c.getAsJsonObject(),names);}
 public static void main(String[] args)throws Exception{
  clock=Class.forName("de.galaxushd.mpsqcamera.MpsqTimerClock");var constructor=clock.getDeclaredConstructor();constructor.setAccessible(true);timer=constructor.newInstance();
  check((long)call("parse",new Class[]{String.class},"2m30s")==150000,"Duration parsing");
  check((long)call("parse",new Class[]{String.class},"99h59m59s")==359999000,"Maximum duration");
  for(String invalid:List.of("","-1","1mblah","100h","99999999999999999999999s"))try{call("parse",new Class[]{String.class},invalid);throw new AssertionError("Accepted "+invalid);}catch(InvocationTargetException expected){}
  call("set",new Class[]{long.class},1500L);call("start",new Class[]{});check(!(boolean)call("advance",new Class[]{long.class},1000L),"Ended early");check((long)call("seconds",new Class[]{})==1,"Countdown must ceil fractional second");
  check((boolean)call("advance",new Class[]{long.class},600L),"End not fired");check(!(boolean)call("advance",new Class[]{long.class},500L),"End fired twice");
  call("reset",new Class[]{});call("advance",new Class[]{long.class},5000L);check(value("value")==1500,"Reset must pause");
  call("add",new Class[]{long.class},-9999L);check(value("value")==0,"Negative countdown");
  flag("up",true);call("start",new Class[]{});call("advance",new Class[]{long.class},1100L);check(value("value")==1100,"Stopwatch");
  check(call("digits",new Class[]{long.class,String.class},3600L,"auto").equals("0100"),"Hour format");check(call("digits",new Class[]{long.class,String.class},3599L,"auto").equals("5959"),"Minute format");
  JsonObject base=model("timer"),numbers=model("numbers");Set<String> names=new HashSet<>();for(var b:base.getAsJsonArray("bones"))bones(b.getAsJsonObject(),names);check(names.containsAll(Set.of("core","h_colon","h_min1","h_min2","h_sec1","h_sec2")),"Missing digit anchors");
  Set<String> digits=new HashSet<>();for(var b:numbers.getAsJsonArray("bones"))bones(b.getAsJsonObject(),digits);for(int i=0;i<10;i++)check(digits.contains(""+i),"Missing numeral "+i);
  for(String effect:List.of("size","spin","flip","bounce","bump"))for(String slot:List.of("sec1","sec2","min1","min2")){
   JsonObject found=null;for(var a:base.getAsJsonArray("animations"))if(a.getAsJsonObject().get("name").getAsString().equals(slot+"_"+effect))found=a.getAsJsonObject();
   check(found!=null&&found.getAsJsonObject("animators").has("h_"+slot),"Unbound digit animation "+slot+"_"+effect);
  }
  Class<?> manager=Class.forName("de.galaxushd.mpsqcamera.MpsqModularTimer");Class<?> state=Class.forName("de.galaxushd.mpsqcamera.MpsqModularTimer$Timer");var ctor=state.getDeclaredConstructor();ctor.setAccessible(true);Object t=ctor.newInstance();
  for(String key:List.of("name","world")){var f=state.getDeclaredField(key);f.setAccessible(true);f.set(t,key);}
  var encode=manager.getDeclaredMethod("encode",state);encode.setAccessible(true);var decode=manager.getDeclaredMethod("decode",JsonObject.class,boolean.class);decode.setAccessible(true);
  JsonObject json=(JsonObject)encode.invoke(null,t);json.addProperty("running",true);json.addProperty("value",123456);json.addProperty("age",9876);Object replay=decode.invoke(null,json,true);check(((JsonObject)encode.invoke(null,replay)).get("running").getAsBoolean(),"Replay must retain running state");
  Object loaded=decode.invoke(null,json,false);check(!((JsonObject)encode.invoke(null,loaded)).get("running").getAsBoolean(),"Reload must resume paused");check(((JsonObject)encode.invoke(null,loaded)).get("value").getAsLong()==123456,"Persistence lost time");
  var red=state.getDeclaredField("redEnding");red.setAccessible(true);red.set(t,true);var clockField=state.getDeclaredField("clock");clockField.setAccessible(true);Object localClock=clockField.get(t);var remaining=clock.getDeclaredField("value");remaining.setAccessible(true);var display=state.getDeclaredMethod("displayColor");display.setAccessible(true);var custom=state.getDeclaredField("color");custom.setAccessible(true);custom.set(t,0xFF123456);
  for(long ms:new long[]{10001,10000,1,0}){remaining.setLong(localClock,ms);check((int)display.invoke(t)==(ms>10000?0xFF123456:0xFFFF3030),"Warning color threshold "+ms);}
  var up=clock.getDeclaredField("up");up.setAccessible(true);up.setBoolean(localClock,true);check((int)display.invoke(t)==0xFF123456,"Stopwatch warning must be disabled");up.setBoolean(localClock,false);red.set(t,false);check((int)display.invoke(t)==0xFF123456,"Warning switch ignored");
  red.set(t,true);var input=state.getDeclaredField("endInput");input.setAccessible(true);input.set(t,"minecraft:entity.cat.ambient");Object restored=decode.invoke(null,encode.invoke(null,t),false);check(red.getBoolean(restored)&&input.get(restored).equals(input.get(t)),"Lost end action/warning settings");
  var renderer=Class.forName("de.galaxushd.mpsqcamera.MpsqAccessoryRenderer");var size=renderer.getDeclaredMethod("validateTextureSize",String.class,int.class,int.class);size.setAccessible(true);size.invoke(null,"builtin://mpsq/bobblehead/crate",2048,2048);try{size.invoke(null,"local://external",2048,2048);throw new AssertionError("External texture limit bypass");}catch(InvocationTargetException expected){}
  System.out.println("PASS: warning threshold 10/0 seconds, stopwatch and switch, persistence, crate texture limit");
  System.out.println("PASS: timer parsing, boundaries, pause/reset, one-shot end, stopwatch, formats, all digits/20 animations, replay and storage round-trip");
 }
}

