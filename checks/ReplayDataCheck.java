import de.galaxushd.mpsqcamera.MpsqReplayData;
import java.nio.file.*;
import java.util.*;

public class ReplayDataCheck {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        var folder=Files.createTempDirectory("mpsq-replay-check-");
        var file=folder.resolve("dalgona.mpsqr");
        var d=new MpsqReplayData();d.game="dalgona";d.world="Testwelt";d.created=1;d.duration=1000;
        d.area=new MpsqReplayData.Area(-3,-64,5,2,2,2);d.blocks=new int[]{0,1,0,0,1,0,0,0};
        d.palette.addAll(List.of("air","stone"));
        d.actors.add(new MpsqReplayData.Actor(UUID.randomUUID().toString(),"minecraft:villager","Test","{}","",""));
        var state=new MpsqReplayData.State(0,-3,-64,5,90,0,90,0,0xff0000,-1);
        d.frames.add(new MpsqReplayData.Frame(0,List.of(state)));
        d.frames.add(new MpsqReplayData.Frame(500,List.of(state),List.of(),List.of(),
                List.of(new MpsqReplayData.EntityDetails(0,"{CustomName:\""+"x".repeat(70000)+"\"}",77,true,"vehicle-uuid")),
                List.of(new MpsqReplayData.BlockEntityDetails(1,"{id:\"minecraft:chest\",Items:[]}")),"{\"objects\":[],\"npcs\":[{\"world_x\":3.5}]}"));
        d.frames.add(new MpsqReplayData.Frame(1000,List.of()));
        d.changes.add(new MpsqReplayData.Change(400,0,0,1));
        d.save(file);var read=MpsqReplayData.load(file);
        check(Arrays.equals(read.blocks,d.blocks),"Block snapshot roundtrip");
        check(read.frames.equals(d.frames)&&read.actors.equals(d.actors),"Entities, glow and poses roundtrip");
        check(read.changes.equals(d.changes),"Reversible block changes roundtrip");
        check(read.frames.get(1).details().get(0).nbt().length()>65535,"Large entity state preserved instead of discarded");
        check(read.frames.get(1).details().get(0).grounded()&&read.frames.get(1).details().get(0).age()==77,"Entity ground and animation age preserved");
        check(read.frames.get(1).blockEntities().equals(d.frames.get(1).blockEntities()),"Block entities survive save/load and seeking");
        check(read.frames.get(1).scene().equals(d.frames.get(1).scene()),"NPC and furniture scene preserved");
        check(read.frameAt(0)==0&&read.frameAt(499)==0&&read.frameAt(500)==1&&read.frameAt(999)==1&&read.frameAt(1000)==2,"Seek boundaries");
        check(read.frameAt(-1)==0&&read.frameAt(100000)==2,"Seek beyond endpoints");
        for(int i=0;i<8;i++)check(d.area.index(d.area.bx(i),d.area.by(i),d.area.bz(i))==i,"Area coordinate roundtrip");
        check(d.area.index(-4,-64,5)==-1&&d.area.index(-3,-62,5)==-1,"Area outside boundaries");
        d.world="x".repeat(70000);
        try{d.save(file);throw new AssertionError("Oversize string accepted");}catch(java.io.UTFDataFormatException expected){}
        check(MpsqReplayData.load(file).world.equals("Testwelt"),"Failed replacement preserves previous replay");
        d.world="Testwelt";d.blocks[0]=99;d.save(file);
        try{MpsqReplayData.load(file);throw new AssertionError("Invalid palette reference accepted");}catch(java.io.IOException expected){checks++;}
        d.blocks[0]=0;d.frames.set(1,new MpsqReplayData.Frame(-1,List.of(state)));d.save(file);
        try{MpsqReplayData.load(file);throw new AssertionError("Invalid timeline accepted");}catch(java.io.IOException expected){checks++;}
        for(int version=1;version<=2;version++){
            var legacy=folder.resolve("legacy.mpsqr");
            try(var out=new java.io.DataOutputStream(new java.util.zip.GZIPOutputStream(Files.newOutputStream(legacy)))){
                out.writeInt(0x4d505251);out.writeInt(version);out.writeUTF("dalgona");out.writeUTF("old");out.writeLong(1);out.writeLong(0);
                out.writeInt(0);out.writeInt(0);out.writeInt(0);out.writeInt(1);out.writeInt(1);out.writeInt(1);
                out.writeInt(1);out.writeUTF("air");out.writeInt(0);out.writeInt(0);out.writeInt(1);out.writeLong(0);out.writeInt(0);
                if(version==2){out.writeInt(0);out.writeInt(0);}out.writeInt(0);
            }
            var old=MpsqReplayData.load(legacy);check(old.frames.size()==1&&old.frames.get(0).details().isEmpty()&&old.frames.get(0).scene().equals("{}"),"Legacy replay version "+version);
            Files.delete(legacy);
        }
        Files.deleteIfExists(file);Files.deleteIfExists(folder.resolve("dalgona.mpsqr.tmp"));Files.delete(folder);
        System.out.println(checks+" Replay-Prüfungen erfolgreich");
    }
}
