import java.lang.reflect.Method;
public class KickAnimationCheck {
 static Class<?> c;
 static double call(String name,double t)throws Exception{Method m=c.getDeclaredMethod(name,double.class);m.setAccessible(true);return ((Number)m.invoke(null,t)).doubleValue();}
 static void same(double a,double b){if(Math.abs(a-b)>.00001)throw new AssertionError(a+" != "+b);}
 public static void main(String[] args)throws Exception{
  c=Class.forName("de.galaxushd.mpsqcamera.MpsqKickAnimationManager");
  same(call("animationTime",.2),.25);
  same(call("animationTime",-.1),0);
  same(call("animationTime",1),call("animationTime",3600));
  for(double t:new double[]{.9583333,2,3.333,4,60,3600}){
   same(call("fallDrop",t),-1.294);
   same(call("fallDistance",t),2.4375);
  }
  same(((Number)c.getMethod("despawnScale",String.class).invoke(null,"test")).doubleValue(),1);
  System.out.println("PASS: 16 kick animation checks (speed, final pose, no burial or shrink)");
 }
}
