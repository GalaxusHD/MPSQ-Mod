import java.lang.reflect.*;
import java.util.*;
public class PetNavigationCheck {
    static Method x,y,z,find;static Constructor<?> point;static Class<?> ground;
    static double value(Method m,Object p){try{return (double)m.invoke(p);}catch(Exception e){throw new RuntimeException(e);}}
    static double height(double x,double z){if(Math.abs(x)<=2&&Math.abs(z)<=2)return 2;if(Math.abs(x)<=2&&z>=-3&&z< -2)return 1;return 0;}
    public static void main(String[] args)throws Exception{
        Class<?> path=Class.forName("de.galaxushd.mpsqcamera.MpsqPetPath"),p=Class.forName(path.getName()+"$Point");ground=Class.forName(path.getName()+"$Ground");
        point=p.getDeclaredConstructor(double.class,double.class,double.class);point.setAccessible(true);
        x=p.getDeclaredMethod("x");y=p.getDeclaredMethod("y");z=p.getDeclaredMethod("z");x.setAccessible(true);y.setAccessible(true);z.setAccessible(true);
        find=path.getDeclaredMethod("find",p,double.class,double.class,ground);find.setAccessible(true);
        Object terrain=Proxy.newProxyInstance(ground.getClassLoader(),new Class[]{ground},(o,m,a)->{
            double ny=height((double)a[1],(double)a[2]),py=value(y,a[0]);return ny-py>1.0001||py-ny>1.1?Double.NaN:ny;
        });
        List<?> route=(List<?>)find.invoke(null,point.newInstance(0.,2.,0.),5.,0.,terrain);
        if(route.isEmpty())throw new AssertionError("Platform route not found");
        if(route.stream().noneMatch(n->value(z,n)<=-3&&value(y,n)==1))throw new AssertionError("Did not use stairs behind platform");
        double previous=2;for(Object n:route){double py=value(y,n);if(Math.abs(py-previous)>1.001)throw new AssertionError("Unsafe height step");previous=py;}
        Object blocked=Proxy.newProxyInstance(ground.getClassLoader(),new Class[]{ground},(o,m,a)->Double.NaN);
        if(!((List<?>)find.invoke(null,point.newInstance(0.,2.,0.),5.,0.,blocked)).isEmpty())throw new AssertionError("Route through solid wall");
        Object flat=Proxy.newProxyInstance(ground.getClassLoader(),new Class[]{ground},(o,m,a)->0.);
        List<?> diagonal=(List<?>)find.invoke(null,point.newInstance(0.,0.,0.),5.,5.,flat);
        if(diagonal.size()!=5||diagonal.stream().anyMatch(n->Math.abs(value(x,n)-value(z,n))>.001))throw new AssertionError("Diagonal path zigzags");
        Object corner=Proxy.newProxyInstance(ground.getClassLoader(),new Class[]{ground},(o,m,a)->(double)a[1]==1.&&(double)a[2]==1.?0.:Double.NaN);
        if(!((List<?>)find.invoke(null,point.newInstance(0.,0.,0.),1.,1.,corner)).isEmpty())throw new AssertionError("Diagonal cuts solid corner");
        System.out.println("PASS: stairs, safe steps, unreachable destination, diagonals and blocked corners");
    }
}
