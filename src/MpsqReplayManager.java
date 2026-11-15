package de.galaxushd.mpsqcamera;

import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.block.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Automatic, local recordings. The last completed recording is replaced atomically. */
public final class MpsqReplayManager {
    public static final Path DIRECTORY=FabricLoader.getInstance().getConfigDir().resolve("mpsq/replays");
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"MPSQ replay files");t.setDaemon(true);return t;});
    private static MpsqReplayData recording;
    private static net.minecraft.client.multiplayer.ClientLevel sourceWorld;
    private static final Map<BlockState,Integer> palette=new HashMap<>();
    private static final Map<UUID,Integer> actors=new HashMap<>();
    private static final Map<String,String> snapshots=new HashMap<>();
    private static long snapshotBytes;
    private static int[] currentBlocks;
    private static long started,next;
    private static int stateCount;
    public static volatile String status="Noch keine neue Aufnahme.";
    private static final java.util.concurrent.atomic.AtomicInteger saving=new java.util.concurrent.atomic.AtomicInteger();
    public static void initialize(){
        MpsqReplayPlayback.initialize();
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            if(MpsqExternalReplay.active()&&(c.level==null||c.player==null||!TeamVisibilitySettings.visible()))MpsqExternalReplay.finish();
            if(recording==null)return;
            if(c.level!=sourceWorld||c.player==null||!TeamVisibilitySettings.visible()){finish();return;}
            long now=System.nanoTime();if(now<next)return;next=now+100_000_000L;
            if(recording.frames.size()>=MpsqReplayData.MAX_FRAMES-1||elapsed()>=3_600_000){notice("Replay: 60-Minuten-Limit erreicht; Aufnahme wird gespeichert.");finish();return;}
            capture();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h,c)->finish());
    }
    public static boolean isRecording(){return recording!=null||MpsqExternalReplay.active();}
    public static boolean isSaving(){return saving.get()>0;}
    public static Path file(String game){if(!game.matches("[a-z0-9_]{1,64}"))throw new IllegalArgumentException();return DIRECTORY.resolve(game+".mpsqr");}
    public static void transition(String id){
        finish();if(id.isBlank()||MpsqReplayPlayback.active())return;
        Minecraft c=Minecraft.getInstance();if(c.level==null||c.player==null)return;
        if(MpsqExternalReplay.start(id))return;
        try{
            var d=new MpsqReplayData();d.game=id;d.created=System.currentTimeMillis();d.world=MpsqActionSync.server()+" / "+c.level.dimension().identifier();
            int radius=Math.min(96,Math.max(16,c.options.renderDistance().get()*16));
            int y=Math.max(-64,c.player.getBlockY()-48),top=Math.min(320,y+112);
            d.area=new MpsqReplayData.Area(c.player.getBlockX()-radius,y,c.player.getBlockZ()-radius,radius*2,Math.max(1,top-y),radius*2);
            d.blocks=new int[d.area.cells()];recording=d;sourceWorld=c.level;palette.clear();actors.clear();palette(Blocks.AIR.defaultBlockState());
            var pos=new BlockPos.MutableBlockPos();
            for(int i=0;i<d.blocks.length;i++){
                pos.set(d.area.bx(i),d.area.by(i),d.area.bz(i));
                if(c.level.hasChunkAt(pos))d.blocks[i]=palette(c.level.getBlockState(pos));
            }
            snapshots.clear();snapshotBytes=0;currentBlocks=d.blocks.clone();started=System.nanoTime();next=started;stateCount=0;capture();
            status="Aufnahme: "+MpsqZoneCatalog.label(id);notice(status+" – geladener Bereich, maximal "+(radius*2)+" × "+(radius*2)+" Blöcke.");
        }catch(Exception e){recording=null;sourceWorld=null;currentBlocks=null;status="Replay-Aufnahme konnte nicht starten.";MpsqCameraClient.LOGGER.error(status,e);notice(status);}
    }
    private static long elapsed(){return Math.min(3_600_000,Math.max(0,(System.nanoTime()-started)/1_000_000));}
    private static int palette(BlockState state){return palette.computeIfAbsent(state,s->{int id=recording.palette.size();recording.palette.add(BlockState.CODEC.encodeStart(JsonOps.INSTANCE,s).getOrThrow().toString());return id;});}
    public static void blockUpdate(BlockPos pos,BlockState state){
        if(recording==null||currentBlocks==null||sourceWorld!=Minecraft.getInstance().level)return;
        int cell=recording.area.index(pos.getX(),pos.getY(),pos.getZ());if(cell<0)return;
        int after=palette(state),before=currentBlocks[cell];if(before==after)return;
        if(recording.changes.size()>=1_000_000){notice("Replay: Blockänderungslimit erreicht; Aufnahme gespeichert.");finish();return;}
        recording.changes.add(new MpsqReplayData.Change(elapsed(),cell,before,after));currentBlocks[cell]=after;
    }
    private static void capture(){
        List<MpsqReplayData.State> states=new ArrayList<>();
        List<MpsqReplayData.EntityDetails> details=new ArrayList<>();
        for(Entity e:sourceWorld.entitiesForRendering()){
            if(e.isRemoved())continue;
            if(states.size()==512||actors.size()>=MpsqReplayData.MAX_ACTORS){notice("Replay: Entity-Limit erreicht; Aufnahme beendet.");finishWithoutFrame();return;}
            Integer id=actors.get(e.getUUID());
            if(id==null){
                String nbt="{}",texture="",signature="",name=e.getName().getString();
                try{nbt=entitySnapshot(e);}catch(Exception failure){recordingFailure(failure);return;}
                if(e instanceof AbstractClientPlayer p){name=p.getGameProfile().name();var props=p.getGameProfile().properties().get("textures");if(!props.isEmpty()){var prop=props.iterator().next();texture=prop.value();signature=prop.signature()==null?"":prop.signature();}}
                id=recording.actors.size();actors.put(e.getUUID(),id);recording.actors.add(new MpsqReplayData.Actor(e.getStringUUID(),BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString(),name,nbt,texture,signature));
            }
            int glow=-1;if(MpsqGamesRuntime.outline(e))glow=MpsqGamesRuntime.color(e);if(MpsqMovementSensorSystem.shouldOutlineEntity(e))glow=MpsqMovementSensorSystem.entityOutlineColor(e);
            float fall=e instanceof AbstractClientPlayer p?MpsqKickAnimationManager.elapsedSeconds(MpsqKickAnimationManager.animationKey(p)):-1;
            states.add(new MpsqReplayData.State(id,e.getX(),e.getY(),e.getZ(),e.getYRot(),e.getXRot(),e.getVisualRotationYInDegrees(),e.getPose().ordinal(),glow,fall));
            try{details.add(new MpsqReplayData.EntityDetails(id,entitySnapshot(e),e.tickCount,e.onGround(),e.getVehicle()==null?"":e.getVehicle().getStringUUID()));}catch(Exception failure){recordingFailure(failure);return;}
        }
        if(stateCount+states.size()>MpsqReplayData.MAX_STATES){notice("Replay: Speicherlimit erreicht; Aufnahme wird gespeichert.");finishWithoutFrame();return;}
        stateCount+=states.size();
        List<MpsqReplayData.PetState> pets=new ArrayList<>();var pet=MpsqPetPresenceClient.localSnapshot();if(pet!=null)pets.add(new MpsqReplayData.PetState(pet.petId(),pet.variant(),pet.x(),pet.y(),pet.z(),pet.yaw()));
        MpsqPetPresenceClient.remote().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{var p=entry.getValue();if(System.currentTimeMillis()-p.seenAt()<=10000)pets.add(new MpsqReplayData.PetState(p.petId(),p.variant(),p.x(),p.y(),p.z(),p.yaw()));});
        if(pets.size()>32){recordingFailure(new IllegalStateException("Pet-Aufnahmelimit erreicht"));return;}
        List<MpsqReplayData.BlockEntityDetails> blockEntities=new ArrayList<>();
        try{
            var area=recording.area;
            for(int cx=area.x()>>4;cx<=(area.x()+area.width()-1)>>4;cx++)for(int cz=area.z()>>4;cz<=(area.z()+area.depth()-1)>>4;cz++){
                var chunk=sourceWorld.getChunkSource().getChunk(cx,cz,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false);if(chunk==null)continue;
                for(var be:chunk.getBlockEntities().values()){var p=be.getBlockPos();int cell=area.index(p.getX(),p.getY(),p.getZ());if(cell>=0)blockEntities.add(new MpsqReplayData.BlockEntityDetails(cell,internSnapshot(be.saveWithFullMetadata(sourceWorld.registryAccess()).toString())));}
            }
            if(blockEntities.size()>16384)throw new IllegalStateException("Zu viele Block-Entities");
        }catch(Exception failure){recordingFailure(failure);return;}
        try{recording.frames.add(new MpsqReplayData.Frame(elapsed(),List.copyOf(states),List.copyOf(pets),MpsqSeagullDrops.replaySnapshot(),List.copyOf(details),List.copyOf(blockEntities),internSnapshot(MpsqAccessoryRenderer.replayScene())));}catch(Exception failure){recordingFailure(failure);}
    }
    private static String entitySnapshot(Entity entity){var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,sourceWorld.registryAccess());entity.saveWithoutId(output);var tag=output.buildResult();for(String key:List.of("Pos","Motion","Rotation","UUID","Passengers"))tag.remove(key);return internSnapshot(tag.toString());}
    private static String internSnapshot(String value){String existing=snapshots.get(value);if(existing!=null)return existing;int size=value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;if(size>2_000_000||snapshotBytes+size>128_000_000)throw new IllegalStateException("Entity-Speicherlimit erreicht");snapshotBytes+=size;snapshots.put(value,value);return value;}
    private static void recordingFailure(Exception failure){MpsqCameraClient.LOGGER.error("Replay-Zustand konnte nicht vollständig gespeichert werden",failure);notice("Replay: Aufnahme wegen unvollständiger Entity-Daten beendet; Details im Log.");finishWithoutFrame();}
    public static void finish(){MpsqExternalReplay.finish();if(recording==null)return;if(sourceWorld==Minecraft.getInstance().level&&recording.frames.size()<MpsqReplayData.MAX_FRAMES)capture();finishWithoutFrame();}
    private static void finishWithoutFrame(){
        if(recording==null)return;var data=recording;data.duration=Math.min(3_601_000,elapsed());recording=null;sourceWorld=null;currentBlocks=null;palette.clear();actors.clear();snapshots.clear();snapshotBytes=0;
        if(data.frames.isEmpty())return;saving.incrementAndGet();status="Replay wird gespeichert …";
        IO.submit(()->{try{data.save(file(data.game));status="Gespeichert: "+MpsqZoneCatalog.label(data.game);}catch(Exception e){status="Replay konnte nicht gespeichert werden; vorherige Aufnahme bleibt erhalten.";MpsqCameraClient.LOGGER.error(status,e);Minecraft.getInstance().execute(()->notice(status));}finally{saving.decrementAndGet();}});
    }
    public static CompletableFuture<MpsqReplayData> load(String id){return CompletableFuture.supplyAsync(()->{try{return MpsqReplayData.load(file(id));}catch(Exception e){throw new CompletionException(e);}},IO);}
    private static void notice(String s){var c=Minecraft.getInstance();if(c.player!=null)de.galaxushd.mpsqcamera.compat.PlayerMessages.send(c.player, Component.literal(s),false);}
}
