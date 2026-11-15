import de.galaxushd.mpsqcamera.MpsqWeakIdentityCache;
import java.lang.ref.Reference;
import java.util.Map;

public class NpcIdentityCheck {
    static final class Actor {
        int id;
        @Override public int hashCode(){throw new IllegalStateException("Tried to access entity ID before ID assignment");}
        @Override public boolean equals(Object ignored){throw new AssertionError("Entity equality must not be called");}
    }
    static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        var cache=new MpsqWeakIdentityCache<Actor,String>();Actor a=new Actor(),b=new Actor();
        check(cache.put(a,"initial")==null,"Unassigned actor can enter cache");a.id=-1900000002;
        check(cache.get(a).equals("initial"),"Assigning ID keeps old pose");
        cache.put(b,"other");check(cache.put(a,"next").equals("initial"),"Interpolation returns previous pose");
        check(cache.get(b).equals("other")&&cache.size()==2,"Distinct identities remain distinct");
        check(cache.getOrDefault(new Actor(),"missing").equals("missing"),"Absent actor fallback");
        var field=MpsqWeakIdentityCache.class.getDeclaredField("values");field.setAccessible(true);
        var map=(Map<?,?>)field.get(cache);for(Object key:map.keySet()){Reference<?> ref=(Reference<?>)key;if(ref.get()==a){ref.clear();ref.enqueue();break;}}
        check(cache.size()==1&&cache.get(a)==null&&cache.get(b).equals("other"),"Collected actor removed without retaining entity");
        System.out.println("PASS: no entity ID/hash/equals access before spawn; stable identity after ID assignment; weak cleanup.");
    }
}
