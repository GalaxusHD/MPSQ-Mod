package de.galaxushd.mpsqcamera;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Versioned local replay, without Java object deserialization. */
public final class MpsqReplayData {
    public static final int MAX_CELLS=8_000_000, MAX_FRAMES=36_001, MAX_ACTORS=4096;
    public static final int MAX_STATES=1_000_000;
    public record Area(int x,int y,int z,int width,int height,int depth) {
        public int cells(){long n=(long)width*height*depth;if(width<1||height<1||depth<1||n>MAX_CELLS)throw new IllegalArgumentException("Aufnahmebereich zu groß");return (int)n;}
        public int index(int bx,int by,int bz){int a=bx-x,b=by-y,c=bz-z;return a<0||b<0||c<0||a>=width||b>=height||c>=depth?-1:(b*depth+c)*width+a;}
        public int bx(int i){return x+i%width;} public int by(int i){return y+i/(width*depth);} public int bz(int i){return z+i/width%depth;}
    }
    public record Actor(String uuid,String type,String name,String nbt,String texture,String signature){}
    public record State(int actor,double x,double y,double z,float yaw,float pitch,float bodyYaw,int pose,int glow,float fall){}
    public record PetState(String id,String variant,double x,double y,double z,float yaw){}
    public record DropState(String clip,double x,double y,double z,float yaw,float scale,float age){}
    public record EntityDetails(int actor,String nbt,int age,boolean grounded,String vehicle){}
    public record BlockEntityDetails(int cell,String nbt){}
    public record Frame(long time,List<State> actors,List<PetState> pets,List<DropState> drops,List<EntityDetails> details,List<BlockEntityDetails> blockEntities,String scene){
        public Frame(long time,List<State> actors){this(time,actors,List.of(),List.of());}
        public Frame(long time,List<State> actors,List<PetState> pets,List<DropState> drops){this(time,actors,pets,drops,List.of(),List.of());}
        public Frame(long time,List<State> actors,List<PetState> pets,List<DropState> drops,List<EntityDetails> details,List<BlockEntityDetails> blockEntities){this(time,actors,pets,drops,details,blockEntities,"{}");}
    }
    public record Change(long time,int cell,int before,int after){}
    public String game,world;
    public long created,duration;
    public Area area;
    public int[] blocks;
    public final List<String> palette=new ArrayList<>();
    public final List<Actor> actors=new ArrayList<>();
    public final List<Frame> frames=new ArrayList<>();
    public final List<Change> changes=new ArrayList<>();

    public int frameAt(long time){int lo=0,hi=frames.size()-1;while(lo<hi){int m=(lo+hi+1)/2;if(frames.get(m).time()<=time)lo=m;else hi=m-1;}return lo;}
    public void save(Path path)throws IOException{
        Files.createDirectories(path.getParent());Path tmp=path.resolveSibling(path.getFileName()+".tmp");
        try(var o=new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp))))){
            o.writeInt(0x4d505251);o.writeInt(3);o.writeUTF(game);o.writeUTF(world);o.writeLong(created);o.writeLong(duration);
            o.writeInt(area.x());o.writeInt(area.y());o.writeInt(area.z());o.writeInt(area.width());o.writeInt(area.height());o.writeInt(area.depth());
            o.writeInt(palette.size());for(String s:palette)o.writeUTF(s);
            for(int b:blocks)o.writeInt(b);
            o.writeInt(actors.size());for(var a:actors){o.writeUTF(a.uuid());o.writeUTF(a.type());o.writeUTF(a.name());writeSnapshot(o,a.nbt());o.writeUTF(a.texture());o.writeUTF(a.signature());}
            o.writeInt(frames.size());for(var f:frames){o.writeLong(f.time());o.writeInt(f.actors().size());for(var a:f.actors()){o.writeInt(a.actor());o.writeDouble(a.x());o.writeDouble(a.y());o.writeDouble(a.z());o.writeFloat(a.yaw());o.writeFloat(a.pitch());o.writeFloat(a.bodyYaw());o.writeInt(a.pose());o.writeInt(a.glow());o.writeFloat(a.fall());}
                o.writeInt(f.pets().size());for(var p:f.pets()){o.writeUTF(p.id());o.writeUTF(p.variant());o.writeDouble(p.x());o.writeDouble(p.y());o.writeDouble(p.z());o.writeFloat(p.yaw());}
                o.writeInt(f.drops().size());for(var d:f.drops()){o.writeUTF(d.clip());o.writeDouble(d.x());o.writeDouble(d.y());o.writeDouble(d.z());o.writeFloat(d.yaw());o.writeFloat(d.scale());o.writeFloat(d.age());}
                o.writeInt(f.details().size());for(var e:f.details()){o.writeInt(e.actor());writeSnapshot(o,e.nbt());o.writeInt(e.age());o.writeBoolean(e.grounded());o.writeUTF(e.vehicle());}
                o.writeInt(f.blockEntities().size());for(var e:f.blockEntities()){o.writeInt(e.cell());writeSnapshot(o,e.nbt());}writeSnapshot(o,f.scene());}
            o.writeInt(changes.size());for(var c:changes){o.writeLong(c.time());o.writeInt(c.cell());o.writeInt(c.before());o.writeInt(c.after());}
        }
        try{Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}
    }
    public static MpsqReplayData load(Path path)throws IOException{
        try(var in=new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(path))))){
            if(in.readInt()!=0x4d505251)throw new IOException("Unbekanntes Replay-Format");int version=in.readInt();if(version<1||version>3)throw new IOException("Unbekanntes Replay-Format");
            var d=new MpsqReplayData();d.game=in.readUTF();d.world=in.readUTF();d.created=in.readLong();d.duration=in.readLong();
            if(d.duration<0||d.duration>3_601_000)throw new IOException("Ungültige Dauer");
            d.area=new Area(in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readInt());
            int n=count(in,65536);for(int i=0;i<n;i++)d.palette.add(in.readUTF());if(n==0)throw new IOException("Leere Palette");
            d.blocks=new int[d.area.cells()];for(int i=0;i<d.blocks.length;i++)d.blocks[i]=index(in,n);
            var snapshotPool=new HashMap<String,String>();long[] snapshotBudget={0};
            n=count(in,MAX_ACTORS);for(int i=0;i<n;i++)d.actors.add(new Actor(in.readUTF(),in.readUTF(),in.readUTF(),version>=3?readSnapshot(in,snapshotPool,snapshotBudget):in.readUTF(),in.readUTF(),in.readUTF()));
            n=count(in,MAX_FRAMES);long previous=-1;int totalStates=0;
            for(int i=0;i<n;i++){long t=in.readLong();if(t<0||t<previous||t>d.duration)throw new IOException("Ungültige Zeitleiste");previous=t;int size=count(in,512);totalStates+=size;if(totalStates>MAX_STATES)throw new IOException("Replay-Speicherlimit überschritten");List<State> states=new ArrayList<>(size);
                for(int j=0;j<size;j++){int id=index(in,d.actors.size());double x=in.readDouble(),y=in.readDouble(),z=in.readDouble();float yaw=in.readFloat(),pitch=in.readFloat(),body=in.readFloat();int pose=in.readInt(),glow=in.readInt();float fall=in.readFloat();if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Float.isFinite(yaw)||!Float.isFinite(pitch)||!Float.isFinite(body)||!Float.isFinite(fall))throw new IOException("Ungültige Position");states.add(new State(id,x,y,z,yaw,pitch,body,pose,glow,fall));}
                List<PetState> pets=new ArrayList<>();List<DropState> drops=new ArrayList<>();if(version>=2){int petCount=count(in,32);for(int j=0;j<petCount;j++){String id=in.readUTF(),variant=in.readUTF();double x=in.readDouble(),y=in.readDouble(),z=in.readDouble();float yaw=in.readFloat();if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Float.isFinite(yaw))throw new IOException("Ungültige Pet-Position");pets.add(new PetState(id,variant,x,y,z,yaw));}int dropCount=count(in,256);for(int j=0;j<dropCount;j++){String clip=in.readUTF();double x=in.readDouble(),y=in.readDouble(),z=in.readDouble();float yaw=in.readFloat(),scale=in.readFloat(),age=in.readFloat();if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Float.isFinite(yaw)||!Float.isFinite(scale)||!Float.isFinite(age))throw new IOException("Ungültige Abwurf-Position");drops.add(new DropState(clip,x,y,z,yaw,scale,age));}}
                List<EntityDetails> details=new ArrayList<>();List<BlockEntityDetails> blockEntities=new ArrayList<>();
                if(version>=3){int detailCount=count(in,512);for(int j=0;j<detailCount;j++)details.add(new EntityDetails(index(in,d.actors.size()),readSnapshot(in,snapshotPool,snapshotBudget),in.readInt(),in.readBoolean(),in.readUTF()));
                    int blockCount=count(in,16384);for(int j=0;j<blockCount;j++)blockEntities.add(new BlockEntityDetails(index(in,d.blocks.length),readSnapshot(in,snapshotPool,snapshotBudget)));}
                d.frames.add(new Frame(t,List.copyOf(states),List.copyOf(pets),List.copyOf(drops),List.copyOf(details),List.copyOf(blockEntities),version>=3?readSnapshot(in,snapshotPool,snapshotBudget):"{}"));
            }
            n=count(in,1_000_000);previous=-1;for(int i=0;i<n;i++){long t=in.readLong();if(t<previous||t>d.duration)throw new IOException("Ungültige Blockzeit");previous=t;d.changes.add(new Change(t,index(in,d.blocks.length),index(in,d.palette.size()),index(in,d.palette.size())));}
            if(d.frames.isEmpty())throw new IOException("Leeres Replay");return d;
        }catch(IllegalArgumentException e){throw new IOException("Ungültige Replay-Daten",e);}
    }
    private static int count(DataInputStream in,int max)throws IOException{int n=in.readInt();if(n<0||n>max)throw new IOException("Replay-Limit überschritten");return n;}
    private static void writeSnapshot(DataOutputStream out,String value)throws IOException{byte[] bytes=value.getBytes(java.nio.charset.StandardCharsets.UTF_8);if(bytes.length>2_000_000)throw new IOException("Entity-Daten zu groß");out.writeInt(bytes.length);out.write(bytes);}
    private static String readSnapshot(DataInputStream in,Map<String,String> pool,long[] budget)throws IOException{int n=count(in,2_000_000);byte[] bytes=in.readNBytes(n);if(bytes.length!=n)throw new EOFException();String value=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);String old=pool.get(value);if(old!=null)return old;budget[0]+=n;if(budget[0]>128_000_000)throw new IOException("Entity-Speicherlimit überschritten");pool.put(value,value);return value;}
    private static int index(DataInputStream in,int size)throws IOException{int n=in.readInt();if(n<0||n>=size)throw new IOException("Ungültiger Replay-Verweis");return n;}
}
