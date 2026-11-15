import java.lang.reflect.*;
public class SharedPathsCheck {
 static void require(boolean b,String s){if(!b)throw new AssertionError(s);}
 static void field(Object o,String k,Object v)throws Exception {var f=o.getClass().getDeclaredField(k);f.setAccessible(true);f.set(o,v);}
 public static void main(String[] args)throws Exception{
  var c=Class.forName("de.galaxushd.mpsqcamera.MpsqSharedPaths");var r=Class.forName(c.getName()+"$Route");var ctor=r.getDeclaredConstructor();ctor.setAccessible(true);Object route=ctor.newInstance();
  var pop=c.getDeclaredMethod("population",r,long.class,boolean.class);pop.setAccessible(true);var dir=c.getDeclaredMethod("direction",r,int.class);dir.setAccessible(true);
  field(route,"dayCount",10);field(route,"nightCount",4);field(route,"rainPercent",50);
  require((int)pop.invoke(null,route,0L,false)==10,"day");require((int)pop.invoke(null,route,12999L,false)==10,"day boundary");require((int)pop.invoke(null,route,13000L,false)==4,"night boundary");require((int)pop.invoke(null,route,24000L,true)==5,"day wrap and rain");require((int)pop.invoke(null,route,18000L,true)==2,"night rain");
  field(route,"direction","BOTH");field(route,"forwardPercent",37);int forwards=0;for(int i=-100;i<100;i++)if(dir.invoke(null,route,i).equals("FORWARD"))forwards++;require(forwards==74,"weighted split including negative seeds");
  field(route,"forwardPercent",0);require(dir.invoke(null,route,0).equals("REVERSE"),"0 percent");field(route,"forwardPercent",100);require(dir.invoke(null,route,99).equals("FORWARD"),"100 percent");field(route,"direction","REVERSE");require(dir.invoke(null,route,0).equals("REVERSE"),"one direction stays fixed");
  var enabled=r.getDeclaredField("enabled");enabled.setAccessible(true);require(!(boolean)enabled.get(route),"new paths disabled");
  var validate=r.getDeclaredMethod("validate");validate.setAccessible(true);boolean failed=false;try{validate.invoke(route);}catch(InvocationTargetException e){failed=e.getCause() instanceof IllegalArgumentException;}require(failed,"reject routes with fewer than two points");
  var npc=new de.galaxushd.mpsqcamera.MpsqCinematicScene.Npc();npc.cosmeticModel="nm_hat_wumpus";npc.cosmeticBone="h_head";npc.cosmeticX=1.5;npc.cosmeticRY=45;npc.validate();
  var gson=new com.google.gson.Gson();var copy=gson.fromJson(gson.toJson(npc),de.galaxushd.mpsqcamera.MpsqCinematicScene.Npc.class);require(copy.cosmeticModel.equals("nm_hat_wumpus")&&copy.cosmeticX==1.5&&copy.cosmeticRY==45,"cosmetic persistence");
  copy.cosmeticScale=Float.NaN;boolean invalid=false;try{copy.validate();}catch(IllegalArgumentException e){invalid=true;}require(invalid,"reject invalid cosmetic scale");
  System.out.println("PASS: day/night boundaries, rain scaling, weighted direction, new paths disabled, route validation");
 }
}
