import de.galaxushd.mpsqcamera.MpsqReturnPool;
import java.util.*;
public class ReturnPoolCheck {
 public static void main(String[] args){
  var pool=new MpsqReturnPool();for(int i=0;i<150;i++)pool.exclude("Player"+i);
  pool.exclude("PLAYER0");if(pool.waiting.size()!=150)throw new AssertionError("duplicate name");
  var first=pool.draw(50,new Random(1));var second=pool.draw(50,new Random(2));
  var all=new HashSet<>(first);all.addAll(second);if(all.size()!=100)throw new AssertionError("duplicate winners");
  if(pool.waiting.size()!=150)throw new AssertionError("drawing returned people too early");
  String n=first.getFirst();if(!pool.confirm(n)||pool.waiting.containsKey(MpsqReturnPool.key(n))||!pool.returned.contains(MpsqReturnPool.key(n)))throw new AssertionError("confirmation");
  pool.exclude(n);if(pool.returned.contains(MpsqReturnPool.key(n)))throw new AssertionError("second elimination");
  for(int invalid:new int[]{0,51})try{pool.draw(invalid,new Random());throw new AssertionError("invalid count");}catch(IllegalArgumentException expected){}
  pool.draw(50,new Random(3));int before=pool.selected.size();try{pool.draw(2,new Random());throw new AssertionError("overdraw");}catch(IllegalArgumentException expected){}
  if(pool.selected.size()!=before)throw new AssertionError("partial draw");
  System.out.println("PASS: 150 candidates, deduplication, unique draws, confirmation, re-elimination, count limits and atomic overdraw rejection");
 }
}
