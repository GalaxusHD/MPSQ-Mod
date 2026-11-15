import java.lang.reflect.*;
public class PetMotionCheck {
    static void close(double a,double b){if(Math.abs(a-b)>1e-6)throw new AssertionError(a+" != "+b);}
    public static void main(String[] args)throws Exception{
        Class<?> c=Class.forName("de.galaxushd.mpsqcamera.MpsqPetMotion");
        var ctor=c.getDeclaredConstructor();ctor.setAccessible(true);Object motion=ctor.newInstance();
        var tick=c.getDeclaredMethod("tick",double.class,double.class,double.class,float.class,boolean.class);tick.setAccessible(true);
        var frame=c.getDeclaredMethod("frame",double.class,double.class,double.class,float.class,float.class);frame.setAccessible(true);
        tick.invoke(motion,0.,0.,0.,179f,true);
        double[] middle=(double[])frame.invoke(motion,2.,1.,0.,-179f,.5f);
        close(middle[0],1);close(middle[1],.5);close(middle[3],180);
        double[] teleport=(double[])frame.invoke(motion,100.,0.,0.,20f,.5f);close(teleport[0],100);
        tick.invoke(motion,0.,0.,0.,0f,false);
        double[] initial=(double[])frame.invoke(motion,2.,1.,0.,20f,.5f);close(initial[0],2);
        System.out.println("PASS: pet interpolation, shortest rotation, teleport and initial placement");
    }
}
