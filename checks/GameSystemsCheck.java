import de.galaxushd.mpsqcamera.*;
import net.minecraft.core.BlockPos;
import java.lang.reflect.*;
import java.util.*;

public class GameSystemsCheck {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    @SuppressWarnings("unchecked") static <T>T field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return (T)f.get(null);}
    public static void main(String[] args)throws Exception{
        // Standalone checks do not boot Fabric; provide its local config path.
        var loader=net.fabricmc.loader.api.FabricLoader.getInstance();
        Field configDir=loader.getClass().getDeclaredField("configDir");configDir.setAccessible(true);
        configDir.set(loader,java.nio.file.Path.of("build/game-check-config").toAbsolutePath());
        boolean wasEnabled=ModConfig.mpsqEnabled;ModConfig.mpsqEnabled=true;
        Field activeSystem=MpsqSystemController.class.getDeclaredField("activeSystemId");activeSystem.setAccessible(true);
        Object previousSystem=activeSystem.get(null);activeSystem.set(null,MpsqMovementSensorSystem.SYSTEM_ID);
        Map<String,Long> animations=field(MpsqKickAnimationManager.class,"ACTIVE");
        animations.put("check-fall",System.currentTimeMillis()-1100);
        check(Math.abs(MpsqKickAnimationManager.rootPitchDegrees("check-fall")-90)<.01,"Fall rotates the face upward, not into the floor");
        check(MpsqKickAnimationManager.rotation("check-fall","head")[0]<0,"Blockbench head pitch converted to vanilla coordinates");
        check(MpsqKickAnimationManager.position("check-fall","rightArm")[1]==-3,"Arms include the source shoulder translation");
        animations.put("check-fall",System.currentTimeMillis()-2800);
        check(MpsqKickAnimationManager.despawnScale("check-fall")==1f,"Body retains full size after landing");
        activeSystem.set(null,"");
        check(MpsqKickAnimationManager.elapsedSeconds("check-fall")<0,"Animation disabled outside Red Light Green Light");
        activeSystem.set(null,previousSystem);
        animations.clear();ModConfig.mpsqEnabled=wasEnabled;
        var sky=new MpsqSkyRound();
        UUID s1=UUID.randomUUID(),s2=UUID.randomUUID(),s3=UUID.randomUUID(),s4=UUID.randomUUID(),late=UUID.randomUUID();
        Set<UUID> roster=Set.of(s1,s2,s3,s4);
        check(!sky.press(1,roster,0)&&!sky.press(0,Set.of(),0),"Wrong tower and empty starts rejected");
        sky.begin(roster);
        check(sky.press(0,roster,100)&&sky.initialCount()==4,"Activation roster starts at first button");
        check(!sky.press(0,Set.of(late),200)&&sky.remaining(200)==899900,"Repeated press cannot extend timer or admit late entrant");
        sky.tick(Set.of(late),300);check(sky.running(),"Nonparticipant exit cannot finish round");
        sky.tick(Set.of(s1),400);check(!sky.running()&&sky.tower()==1&&sky.survivors().equals(Set.of(s2,s3,s4)),"First loss releases survivors");
        check(!sky.press(1,Set.of(s2,s3),500),"Next tower waits for surviving roster");
        check(sky.press(1,Set.of(s2,s3,s4,late),600)&&sky.initialCount()==4&&!sky.survivors().contains(late),"Second tower does not recapture participants");
        sky.tick(Set.of(s2),700);check(sky.tower()==2&&!sky.running(),"Second tower releases after one exit");
        check(sky.press(2,Set.of(s3,s4,late),800),"Third button starts its own countdown");
        sky.tick(Set.of(s3),900);check(sky.tower()==3&&sky.survivors().equals(Set.of(s4)),"Final survivor passes");
        check(!sky.press(2,roster,1000),"Finished game cannot restart from tower button");
        sky.reset();check(sky.initialCount()==0&&sky.eliminated().isEmpty(),"Stop clears roster");
        sky.begin(roster);sky.press(0,roster,0);sky.tick(Set.of(),900000);
        check(sky.eliminated().equals(roster)&&!sky.running(),"Timeout eliminates entire remaining roster");
        sky.reset();sky.begin(roster);sky.press(0,roster,0);sky.kick(s1);
        check(sky.tower()==1&&!sky.survivors().contains(s1),"Elimination command resolves running tower");
        sky.fall(Set.of(s2));check(sky.tower()==1&&!sky.survivors().contains(s2),"Y fall during green transit remains fatal without skipping tower");
        check(sky.press(1,Set.of(s3,s4),1),"Reduced surviving roster can start next tower");
        sky.fall(Set.of(s3,s4));check(sky.tower()==3&&sky.survivors().isEmpty(),"Simultaneous falls eliminate everyone");
        sky.begin(roster);check(!sky.press(0,Set.of(s1,s2,s3,late),0),"Late entrant cannot replace missing activation participant");
        sky.press(0,roster,0);sky.tick(Set.of(s1),1);sky.tick(Set.of(s2,s3,s4),2);
        check(sky.survivors().equals(Set.of(s2,s3,s4)),"Green transit outside previous tower is safe");
        sky.press(1,Set.of(s2,s3,s4),3);sky.tick(Set.of(s2),4);sky.press(2,Set.of(s3,s4),5);sky.tick(Set.of(s3),6);
        sky.fall(Set.of(s4));check(sky.survivors().isEmpty(),"Y fall is fatal even after third tower passed");
        var box=new MpsqGameZone(new BlockPos(4,8,6),new BlockPos(-2,3,-1));
        check(box.minX()==-2&&box.maxX()==5&&box.minY()==3&&box.maxY()==9,"Reversed corners and inclusive block bounds");
        var checkpoint=new MpsqGameZone(new BlockPos(0,1,0),new BlockPos(0,1,0));
        check(checkpoint.containsFeet(.5,1,.5),"Standing feet enter a one-block checkpoint");
        check(!checkpoint.containsFeet(1,1,.5)&&!checkpoint.containsFeet(.5,2,.5),"Adjacent and above-checkpoint positions excluded");
        List<MpsqZoneCatalog.Entry> zones=field(MpsqZoneCatalog.class,"zones");
        check(!MpsqGamesRuntime.configurationError("red_light_green_light").isEmpty(),"Master area required for RLGL too");
        zones.add(new MpsqZoneCatalog.Entry(MpsqZoneCatalog.MASTER,box));
        check(MpsqZoneCatalog.inMaster(new BlockPos(0,5,0)),"Position inside master accepted");
        check(!MpsqZoneCatalog.inMaster(new BlockPos(20,5,0)),"Position outside master rejected");
        check(MpsqZoneCatalog.inMaster(checkpoint)==false,"Entire child volume must lie within master");
        check(!MpsqGamesRuntime.configurationError("dalgona").isEmpty(),"Cannot start empty Dalgona");
        for(int k=1;k<=4;k++)zones.add(new MpsqZoneCatalog.Entry(k,box));
        check(MpsqGamesRuntime.configurationError("dalgona").isEmpty(),"Four Dalgona teams accepted");
        check(!MpsqGamesRuntime.configurationError("six_leg").isEmpty(),"Missing checkpoints rejected");
        for(int k=10;k<=14;k++)for(int n=0;n<6;n++)zones.add(new MpsqZoneCatalog.Entry(k,box));
        check(!MpsqGamesRuntime.configurationError("six_leg").isEmpty(),"A alone cannot start two-team game");
        for(int k=19;k<=23;k++)for(int n=0;n<6;n++)zones.add(new MpsqZoneCatalog.Entry(k,box));
        check(MpsqGamesRuntime.configurationError("six_leg").isEmpty(),"Both complete team courses accepted");
        check(MpsqZoneCatalog.nextKind(10)==13&&MpsqZoneCatalog.nextKind(13)==11&&MpsqZoneCatalog.nextKind(19)==20,"Yellow follows red in both teams without migrating stored IDs");
        check(MpsqZoneCatalog.KINDS.get(7).limit()==2,"Two neutral tug zones");
        check(MpsqZoneCatalog.KINDS.get(16).limit()==3,"Three sky towers");
        try{MpsqGamesRuntime.height(Double.NaN);throw new AssertionError("NaN accepted");}catch(IllegalArgumentException expected){checks++;}
        Map<UUID,Integer> glows=field(MpsqGamesRuntime.class,"glows");
        Set<UUID> finished=field(MpsqGamesRuntime.class,"finished"), eliminated=field(MpsqGamesRuntime.class,"eliminated");
        Method eliminate=MpsqGamesRuntime.class.getDeclaredMethod("eliminate",UUID.class);eliminate.setAccessible(true);
        UUID winner=UUID.randomUUID(),loser=UUID.randomUUID();finished.add(winner);glows.put(winner,0xFFAA00);
        eliminate.invoke(null,winner);eliminate.invoke(null,loser);
        check(glows.get(winner)==0xFFAA00&&!eliminated.contains(winner),"Winner cannot be eliminated later");
        check(glows.get(loser)==0xFF5555&&eliminated.contains(loser),"Elimination uses light red");
        MpsqGamesRuntime.stop();check(glows.isEmpty()&&finished.isEmpty()&&eliminated.isEmpty(),"Stop clears round state");
        check(zones.size()==65,"Round reset preserves zone configuration including master");
        Method pair=MpsqGamesRuntime.class.getDeclaredMethod("pairMarbles",UUID.class,UUID.class);pair.setAccessible(true);
        Map<UUID,UUID> partners=field(MpsqGamesRuntime.class,"partners");
        Set<Integer> assigned=new HashSet<>();UUID first=null,second=null;
        for(int i=0;i<36;i++){
            UUID a=UUID.randomUUID(),b=UUID.randomUUID();if(i==0){first=a;second=b;}
            check((boolean)pair.invoke(null,a,b),"Free color creates pair");
            check(glows.get(a).equals(glows.get(b))&&assigned.add(glows.get(a)),"Same duo color, distinct from every other active duo");
        }
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        check(!(boolean)pair.invoke(null,a,b)&&!partners.containsKey(a),"Exhausted palette never duplicates a color");
        int released=glows.get(first);partners.remove(first);partners.remove(second);glows.remove(first);glows.remove(second);
        check((boolean)pair.invoke(null,a,b)&&glows.get(a)==released,"Released color can be reused");
        MpsqGamesRuntime.stop();check(partners.isEmpty()&&glows.isEmpty(),"Round stop releases all colors");
        zones.clear();System.out.println("PASS: "+checks+" game-system checks");
    }
}
