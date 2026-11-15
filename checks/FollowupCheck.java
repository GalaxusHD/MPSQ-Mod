import de.galaxushd.mpsqcamera.*;
import java.util.*;
public class FollowupCheck {
 static void require(boolean ok,String text){if(!ok)throw new AssertionError(text);}
 public static void main(String[] args)throws Exception{
  UUID id=UUID.randomUUID();
  for(var rank:List.of(TeamRank.PLAYER,TeamRank.STREAMER,TeamRank.UNDERCOVER_001)){
   TeamStateStore.setSelf(new TeamProfile(id,"Tester",TeamRank.SENIOR_OFFICER,rank,true));
   TeamStateStore.setMembers(List.of(new TeamProfile(UUID.randomUUID(),"Tester",TeamRank.SENIOR_OFFICER,null,true)));
   var selected=TeamStateStore.byMinecraftName("tester").orElseThrow();
   require(selected.displayedRank()==rank,"stale member list must not replace active self role");
   require(selected.permissionRank()==TeamRank.SENIOR_OFFICER,"permissions preserved");
  }
  var method=Class.forName("de.galaxushd.mpsqcamera.MpsqPathSeconds").getDeclaredMethod("hasMovement",MpsqCinematicScene.Npc.class);method.setAccessible(true);
  var npc=new MpsqCinematicScene.Npc();var action=new MpsqCinematicScene.NpcAction();action.duration=20;npc.actions.add(action);
  require(!(boolean)method.invoke(null,npc),"animation alone must not draw ruler");
  npc.route[0]=new MpsqCinematicScene.Waypoint(0,0,0,1);npc.route[1]=new MpsqCinematicScene.Waypoint(0,0,0,5);
  require(!(boolean)method.invoke(null,npc),"stationary route must not draw ruler");npc.route[1].x=3;
  require((boolean)method.invoke(null,npc),"walking route needs ruler");
  System.out.println("PASS: active roles override stale entries, permissions preserved, rulers only on moving paths");
 }
}
