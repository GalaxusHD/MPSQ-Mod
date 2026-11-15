import de.galaxushd.mpsqcamera.MpsqCinematicScene;
import de.galaxushd.mpsqcamera.MpsqCinematicTriggerState;
import com.google.gson.Gson;
public class CinematicCheck {
    static void near(double actual,double expected){if(Math.abs(actual-expected)>0.0001)throw new AssertionError(actual+" != "+expected);}
    static void invalid(MpsqCinematicScene s){try{s.validate();throw new AssertionError("invalid scene accepted");}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){
        var s=new MpsqCinematicScene();var a=new MpsqCinematicScene.Point(0,0,10,0,170,20);var b=new MpsqCinematicScene.Point(10,10,20,30,-170,-20);
        s.points.add(b);s.points.add(a);s.validate();near(s.sample(5).x,5);near(s.sample(5).yaw,180);near(s.sample(5).pitch,0);
        near(s.sample(-1).x,0);near(s.sample(11).z,30);
        b.transition="CUT";near(s.sample(9.999).x,0);near(s.sample(10).x,10);
        b.transition="SMOOTH";near(s.sample(2.5).x,2.03125);near(s.sample(5).x,5);
        var copy=new Gson().fromJson(new Gson().toJson(s),MpsqCinematicScene.class);copy.validate();near(copy.sample(2.5).x,2.03125);
        b.time=0;invalid(s);b.time=10;b.x=Double.NaN;invalid(s);b.x=10;b.pitch=91;invalid(s);b.pitch=-20;b.transition="unknown";invalid(s);
        var single=new MpsqCinematicScene();single.points.add(a);single.validate();near(single.sample(100).y,10);
        single.name="Test";var names=java.util.List.of(single);
        for(String duplicate:new String[]{"Test","test"," TEST "}){
            try{MpsqCinematicScene.uniqueName(duplicate,names,null);throw new AssertionError("duplicate accepted");}catch(IllegalArgumentException expected){}
        }
        for(String allowed:new String[]{"Test1","Test 1"})if(!allowed.equals(MpsqCinematicScene.uniqueName(allowed,names,null)))throw new AssertionError("valid name changed");
        MpsqCinematicScene.uniqueName("test",names,single.id);
        single.triggerZone="Eingang";single.onMasterEnter=true;single.redstoneEnabled=true;single.once=false;
        var restored=new Gson().fromJson(new Gson().toJson(single),MpsqCinematicScene.class);
        if(!restored.triggerZone.equals("Eingang")||!restored.onMasterEnter||!restored.redstoneEnabled||restored.once)throw new AssertionError("triggers not persisted");
        var legacy=new Gson().fromJson("{\"name\":\"Alt\",\"points\":[]}",MpsqCinematicScene.class);
        if(!legacy.triggerZone.isEmpty()||legacy.onMasterEnter||legacy.redstoneEnabled||!legacy.once)throw new AssertionError("legacy defaults");
        var effects=new MpsqCinematicScene();
        for(String type:new String[]{"CHAT","ACTIONBAR","TITLE","COMMAND","SOUND","MEDIA","PARTICLE","FADE"}){
            var e=new MpsqCinematicScene.Event();e.type=type;e.time=10;e.duration=2;e.value=type.equals("MEDIA")?"asset_id":type.equals("COMMAND")?"say Test":"Test";effects.events.add(e);
        }
        effects.validate();near(effects.duration(),12);
        var effectsCopy=new Gson().fromJson(new Gson().toJson(effects),MpsqCinematicScene.class);effectsCopy.validate();near(effectsCopy.duration(),12);
        if(effectsCopy.events.size()!=8)throw new AssertionError("event persistence");
        effectsCopy.events.getFirst().time=Double.NaN;invalid(effectsCopy);effectsCopy.events.getFirst().time=10;
        effectsCopy.events.getFirst().volume=2;invalid(effectsCopy);effectsCopy.events.getFirst().volume=1;
        effectsCopy.events.get(3).value="say A\nsay B";invalid(effectsCopy);effectsCopy.events.get(3).value="say A";
        effectsCopy.events.add(effectsCopy.events.getFirst());invalid(effectsCopy);
        if(!legacy.events.isEmpty())throw new AssertionError("legacy events");
        System.out.println("PASS: event types, timing/duration, persistence, invalid volume/time/commands and duplicate IDs");
        var npcScene=new MpsqCinematicScene();var n=npcScene.npc;n.placed=true;n.movementStart=2;
        n.route[0]=new MpsqCinematicScene.Waypoint(0,10,0,1);n.route[1]=new MpsqCinematicScene.Waypoint(10,10,0,4);n.route[4]=new MpsqCinematicScene.Waypoint(10,10,10,2);
        npcScene.validate();near(n.position(1).x,0);near(n.position(4).x,125.0/24);near(n.position(7).z,125.0/24);near(n.position(99).z,10);near(npcScene.duration(),8);
        var action=new MpsqCinematicScene.NpcAction();action.clip="wave";action.time=10;action.duration=2;action.look="CAMERA";n.actions.add(action);npcScene.validate();near(npcScene.duration(),12);
        if(n.action(9)!=null||n.action(10)!=action||n.action(12)!=null)throw new AssertionError("NPC action boundaries");
        var actorRoundtrip=new Gson().fromJson(new Gson().toJson(npcScene),MpsqCinematicScene.class);actorRoundtrip.validate();near(actorRoundtrip.npc.position(7).z,125.0/24);
        n.route[2]=new MpsqCinematicScene.Waypoint(20,10,0,2);n.route[1]=null;invalid(npcScene);n.route[2]=null;n.route[4].duration=0;invalid(npcScene);n.route[4].duration=2;
        n.actions.add(action);invalid(npcScene);n.actions.removeLast();
        near(MpsqCinematicScene.snapRotation(22),15);near(MpsqCinematicScene.snapRotation(23),30);
        var dialogue=new MpsqCinematicScene.Event();dialogue.type="DIALOGUE";dialogue.value="Hallo!";dialogue.extra="Wumpus";dialogue.validate();
        System.out.println("PASS: NPC route interpolation, sequential slots, action timing/overlap, duration, persistence, 15-degree snapping and dialogue type");
        var directed=new MpsqCinematicScene.Npc();directed.movementStart=5;
        directed.route[0]=new MpsqCinematicScene.Waypoint(0,0,0,1);
        directed.route[1]=new MpsqCinematicScene.Waypoint(10,0,0,2);
        near(directed.position(0,false,"REVERSE").x,10);
        near(directed.position(6,false,"REVERSE").x,5);
        near(directed.position(99,false,"REVERSE").x,0);
        near(directed.position(7,false,"BOTH").x,10);
        near(directed.position(8,false,"BOTH").x,5);
        near(directed.position(99,false,"BOTH").x,0);
        near(directed.position(10,true,"BOTH").x,5);
        near(directed.position(10,true,"FORWARD").x,5);
        System.out.println("PASS: reverse start position, delayed start, complete round trip, terminal position and repeating routes");
        var curved=new MpsqCinematicScene();
        curved.points.add(new MpsqCinematicScene.Point(0,0,0,0,350,0));
        curved.points.add(new MpsqCinematicScene.Point(2,2,0,2,10,0));curved.points.getLast().transition="SMOOTH";
        curved.points.add(new MpsqCinematicScene.Point(5,5,0,3,30,0));curved.points.getLast().transition="SMOOTH";
        double epsilon=.0001;
        double incoming=(curved.sample(2).z-curved.sample(2-epsilon).z)/epsilon;
        double outgoing=(curved.sample(2+epsilon).z-curved.sample(2).z)/epsilon;
        if(Math.abs(incoming-outgoing)>.002)throw new AssertionError("Discontinuous curve tangent");
        var corner=new MpsqCinematicScene();
        corner.points.add(new MpsqCinematicScene.Point(0,0,0,0,0,0));
        corner.points.add(new MpsqCinematicScene.Point(2,4,0,0,0,0));corner.points.getLast().transition="SMOOTH";
        corner.points.add(new MpsqCinematicScene.Point(4,4,0,4,90,0));corner.points.getLast().transition="SMOOTH";
        near(corner.sample(2).x,4);near(corner.sample(2).z,0);
        if(Math.abs(corner.sample(1).z)<.01||Math.abs(corner.sample(3).x-4)<.01)throw new AssertionError("Right-angle path is still straight");
        double vx0=(corner.sample(2).x-corner.sample(2-epsilon).x)/epsilon,vx1=(corner.sample(2+epsilon).x-corner.sample(2).x)/epsilon;
        double vz0=(corner.sample(2).z-corner.sample(2-epsilon).z)/epsilon,vz1=(corner.sample(2+epsilon).z-corner.sample(2).z)/epsilon;
        if(Math.abs(vx0-vx1)>.002||Math.abs(vz0-vz1)>.002)throw new AssertionError("Corner changes direction abruptly");
        corner.points.get(1).transition="LINEAR";near(corner.sample(1).z,0);near(corner.sample(1).x,2);
        corner.points.get(1).transition="CUT";near(corner.sample(1).x,0);
        System.out.println("PASS: continuous smooth tangents, rounded right-angle paths, explicit LINEAR and CUT");
        try {
            var restore=MpsqCinematicScene.class.getDeclaredMethod("restoreFrom",MpsqCinematicScene.class);restore.setAccessible(true);
            var oldNpc=npcScene.npc;var saved=new Gson().fromJson(new Gson().toJson(npcScene),MpsqCinematicScene.class);
            npcScene.npc.name="Changed";npcScene.name="Changed";restore.invoke(npcScene,saved);
            if(npcScene.npc!=oldNpc||!npcScene.name.equals(saved.name)||!oldNpc.name.equals(saved.npc.name))throw new AssertionError("Rollback lost editor reference");
            oldNpc.actions.add(null);invalid(npcScene);oldNpc.actions.removeLast();
        } catch(ReflectiveOperationException e){throw new AssertionError(e);}
        System.out.println("PASS: editor rollback preserves scene/NPC references, null actions rejected");
        var extended=new MpsqCinematicScene.Npc();
        for(int i=0;i<21;i++)extended.route[i]=new MpsqCinematicScene.Waypoint(i,0,0,1);
        extended.validate();near(extended.duration(),20);near(extended.position(20).x,20);
        var legacyRoute=new MpsqCinematicScene.Npc();legacyRoute.route=java.util.Arrays.copyOf(extended.route,5);legacyRoute.validate();if(legacyRoute.route.length!=21)throw new AssertionError("Legacy path migration");near(legacyRoute.position(4).x,4);
        System.out.println("PASS: all 21 waypoints and legacy five-point route migration");
        var edges=new MpsqCinematicTriggerState();
        legacy.validate();
        if(legacy.npc==null||legacy.npc.placed)throw new AssertionError("legacy NPC defaults");
        single.npc.name="Actor";single.npc.category="npc_skin_slim";single.npc.type="self";
        single.npc.placed=true;single.npc.x=12.5;single.npc.equipment[4]="minecraft:iron_sword";single.bars=true;
        var actorCopy=new Gson().fromJson(new Gson().toJson(single),MpsqCinematicScene.class);actorCopy.validate();
        if(!actorCopy.npc.placed||!actorCopy.bars||!actorCopy.npc.category.equals("npc_skin_slim")||!actorCopy.npc.equipment[4].equals("minecraft:iron_sword"))throw new AssertionError("NPC/bars persistence");
        near(actorCopy.npc.x,12.5);
        actorCopy.npc.x=Double.NaN;invalid(actorCopy);
        System.out.println("PASS: NPC equipment/type/placement and cinema bars roundtrip, legacy NPC defaults, invalid NPC position");
        if(edges.entered("zone",true)||edges.entered("zone",true)||edges.entered("zone",false)||!edges.entered("zone",true)||edges.entered("zone",true))throw new AssertionError("entry detection");
        if(edges.entered("master",false)||!edges.entered("master",true))throw new AssertionError("independent master entry");
        edges.clear();if(edges.entered("zone",true))throw new AssertionError("world reset must seed silently");
        System.out.println("PASS: case-insensitive names, self rename, distinct names, trigger JSON/legacy defaults, independent area entry and world reset");
        System.out.println("PASS: sort, endpoints, shortest yaw crossing, linear/eased motion, exact cut boundary, JSON roundtrip, invalid values and single-point preview");
    }
}
