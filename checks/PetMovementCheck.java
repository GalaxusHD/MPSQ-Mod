import java.util.*;
import net.minecraft.world.phys.AABB;
public class PetMovementCheck {
    static java.lang.reflect.Method step;
    static int count;
    static void check(String name, double expected, AABB... obstacles) throws Exception {
        List<AABB> boxes=new ArrayList<>();
        boxes.add(new AABB(-5,-1,-5,5,0,5));
        boxes.addAll(Arrays.asList(obstacles));
        double actual=(double)step.invoke(null,.75,0.,.5,.80,.5,.23,.68,boxes);
        if (Double.isNaN(expected) ? !Double.isNaN(actual) : Math.abs(expected-actual)>1e-6)
            throw new AssertionError(name+": expected "+expected+", got "+actual);
        count++;
    }
    public static void main(String[] args) throws Exception {
        Class<?> c=Class.forName("de.galaxushd.mpsqcamera.MpsqPetGrounding");
        step=c.getDeclaredMethod("stepY",double.class,double.class,double.class,double.class,double.class,double.class,double.class,List.class);
        step.setAccessible(true);
        check("flat",0);
        check("full block edge before centre",1,new AABB(1,0,0,2,1,1));
        check("slab",.5,new AABB(1,0,0,2,.5,1));
        check("trapdoor",.1875,new AABB(1,0,0,2,.1875,1));
        check("block and trapdoor too high",Double.NaN,new AABB(1,0,0,2,1,1),new AABB(1,1,0,2,1.1875,1));
        check("wall",Double.NaN,new AABB(1,0,0,2,2,1));
        check("ceiling prevents step",Double.NaN,new AABB(1,0,0,2,1,1),new AABB(0,1.5,0,2,2,1));
        double gap=(double)step.invoke(null,.75,0.,.5,.8,.5,.23,.68,List.of());
        if (!Double.isNaN(gap)) throw new AssertionError("gap");
        count++;
        var spawn=c.getDeclaredMethod("spawnY",double.class,double.class,double.class,double.class,double.class,List.class);spawn.setAccessible(true);
        for(double top:new double[]{.1875,.5,1,1.5}){
            double result=(double)spawn.invoke(null,.5,0.,.5,.2,.8,List.of(new AABB(0,0,0,1,top,1)));
            if(Math.abs(result-top)>1e-6)throw new AssertionError("spawn must rest on collision top "+top);count++;
        }
        double enclosed=(double)spawn.invoke(null,.5,0.,.5,.2,.8,List.of(new AABB(0,-1,0,1,0,1),new AABB(0,.5,0,1,8,1)));
        if(!Double.isNaN(enclosed))throw new AssertionError("spawn inside blocked ceiling");count++;
        double unsupported=(double)spawn.invoke(null,.5,0.,.5,.2,.8,List.of());
        if(!Double.isNaN(unsupported))throw new AssertionError("unsupported spawn");count++;
        System.out.println("PASS: "+count+" pet collision checks");
    }
}
