package de.galaxushd.mpsqcamera;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.*;
import net.minecraft.nbt.TagParser;
import net.minecraft.core.registries.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.*;
import net.minecraft.core.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** Playback only touches a newly created local spectator world, never the source world. */
public final class MpsqReplayPlayback {
    private static volatile MpsqReplayData data;
    private static String folder;
    private static volatile boolean ready,playing;
    private static volatile long position;
    private static long lastClock;
    private static long lastGhostPosition=-1;
    private static double speed=1;
    private static int buildCell,changeCursor;
    private static List<BlockState> states;
    private static boolean configured;
    private static final Map<Integer,Entity> ghosts=new HashMap<>();
    private static final Map<Integer,String> appliedDetails=new HashMap<>();
    private static final Map<Integer,String> appliedBlockDetails=new HashMap<>();
    private static final Map<Entity,Integer> colors=new IdentityHashMap<>();
    private static final Map<String,Float> falls=new HashMap<>();
    private static int nextEntity=-1_700_000_000;
    private static KeyMapping controls;
    private static String sceneText="";
    private static com.google.gson.JsonObject scene=new com.google.gson.JsonObject();
    static com.google.gson.JsonObject sceneSnapshot(){var d=data;if(d==null||d.frames.isEmpty())return new com.google.gson.JsonObject();String text=d.frames.get(d.frameAt(position)).scene();if(!text.equals(sceneText)){scene=JsonParser.parseString(text).getAsJsonObject();sceneText=text;}return scene;}
    public static String error="";
    public static boolean active(){return data!=null;}
    public static boolean ready(){return ready;}
    public static boolean playing(){return playing;}
    public static long position(){return position;}
    public static long duration(){return data==null?0:data.duration;}
    public static double speed(){return speed;}
    public static String label(){return data==null?"Replay":MpsqZoneCatalog.label(data.game);}
    public static Integer color(Entity e){return colors.get(e);}
    public static Float fall(String key){return falls.get(key);}
    public static boolean ghost(Entity e){return ghosts.containsValue(e);}
    public static String key(Entity e){return "mpsq-replay-"+e.getId();}
    public static void pause(){playing=!playing;lastClock=System.nanoTime();}
    public static void seek(long time){position=Math.max(0,Math.min(duration(),time));lastClock=System.nanoTime();}
    public static void speedNext(){speed=speed==.25?.5:speed==.5?1:speed==1?2:speed==2?4:.25;}
    public static void initialize(){
        de.galaxushd.mpsqcamera.compat.WorldRenderEvents.AFTER_ENTITIES.register(MpsqReplayPlayback::renderReplayCompanions);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->reset());
        controls=KeyMappingHelper.registerKeyMapping(new KeyMapping("Replay-Steuerung",com.mojang.blaze3d.platform.InputConstants.KEY_F8,MpsqKeyCategories.REPLAY));
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(data==null||!server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString().equals(folder))return;
            try{serverTick(server);}catch(Exception e){MpsqCameraClient.LOGGER.error("Replay-Welt konnte nicht aufgebaut werden",e);error="Replay-Welt konnte nicht aufgebaut werden.";playing=false;}
        });
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            if(data==null)return;
            if(c.level==null||c.player==null)return;
            if(c.getSingleplayerServer()==null||!c.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString().equals(folder)){reset();return;}
            while(controls.consumeClick())c.gui.setScreen(new MpsqReplayControlsScreen());
            long now=System.nanoTime();if(ready&&playing){position=Math.min(data.duration,position+Math.max(0,(long)((now-lastClock)/1_000_000.0*speed)));if(position==data.duration)playing=false;}lastClock=now;
            if(ready)updateGhosts(c);
        });
    }
    private static void renderReplayCompanions(de.galaxushd.mpsqcamera.compat.WorldRenderContext ctx){
        var d=data;var c=Minecraft.getInstance();if(d==null||!ready||c.level==null||ctx.matrixStack()==null||ctx.consumers()==null||d.frames.isEmpty())return;
        int frameIndex=d.frameAt(position);var frame=d.frames.get(frameIndex);var nextFrame=d.frames.get(Math.min(frameIndex+1,d.frames.size()-1));var cam=ctx.camera().getPos();
        float mix=nextFrame.time()==frame.time()?0:(float)Mth.clamp((position-frame.time())/(double)(nextFrame.time()-frame.time()),0,1);
        for(var original:frame.pets()){
            var nextPet=nextFrame.pets().stream().filter(q->q.id().equals(original.id())&&q.variant().equals(original.variant())).min(Comparator.comparingDouble(q->petDistance(original,q))).orElse(original);
            double distance=petDistance(original,nextPet);boolean moving=distance>0.00001&&distance<256;float blend=distance<256?mix:0;
            var p=new MpsqReplayData.PetState(original.id(),original.variant(),Mth.lerp(blend,original.x(),nextPet.x()),Mth.lerp(blend,original.y(),nextPet.y()),Mth.lerp(blend,original.z(),nextPet.z()),Mth.rotLerp(blend,original.yaw(),nextPet.yaw()));String groundClip=moving?"walk":"idle";
            if(cam.distanceToSqr(p.x(),p.y(),p.z())>MpsqPetGrounding.viewDistanceSquared())continue;float px=(float)(p.x()-cam.x),py=(float)(p.y()-cam.y),pz=(float)(p.z()-cam.z);
            switch(p.id()){
                case "mpsq_tiger","mpsq_tiger_baby","mpsq_crab","mpsq_penguin","mpsq_baby_happy_ghast","mpsq_colin_the_creeper","mpsq_exo_the_enderman","mpsq_slak_the_skeleton","mpsq_zekk_the_zombie"->MpsqAccessoryRenderer.drawCinematicModel(ctx,MpsqCustomPetRenderer.url(p.id()),p.x(),p.y(),p.z(),p.yaw(),groundClip,(position%100000L)/1000f,0,0,.72f);
                case "nogs_budgie"->{double floor=MpsqPetGrounding.groundY(c,p.x(),p.y(),p.z());String pose=!Double.isFinite(floor)||p.y()>floor+.12?"fly":"walk";MpsqBudgieModel.render(ctx.matrixStack(),ctx.consumers(),net.minecraft.util.LightCoordsUtil.getLightCoords(c.level,net.minecraft.core.BlockPos.containing(p.x(),p.y(),p.z())),px,py,pz,p.yaw(),.58f,p.variant(),pose,position/1000.0);}
                case "nogs_owl_barn","nogs_owl_horned","nogs_owl_long_eared","nogs_owl_snowy"->{MpsqOwlModel.select(p.id());MpsqOwlModel.render(ctx.matrixStack(),ctx.consumers(),net.minecraft.util.LightCoordsUtil.getLightCoords(c.level,net.minecraft.core.BlockPos.containing(p.x(),p.y(),p.z())),px,py,pz,p.yaw(),1f,MpsqOwlPetRenderer.animation(c,p.x(),p.y(),p.z()),position/1000.0);}
                case "nocsy_otter"->MpsqOtterModel.render(ctx.matrixStack(),ctx.consumers(),net.minecraft.util.LightCoordsUtil.getLightCoords(c.level,net.minecraft.core.BlockPos.containing(p.x(),p.y(),p.z())),px,py,pz,p.yaw(),.62f,groundClip,position/1000.0);
                case "nogs_hedgehog"->MpsqHedgehogModel.render(ctx.matrixStack(),ctx.consumers(),net.minecraft.util.LightCoordsUtil.getLightCoords(c.level,net.minecraft.core.BlockPos.containing(p.x(),p.y(),p.z())),px,py,pz,p.yaw(),1f,groundClip,position/1000.0);
                default->{var pet=MpsqPetCatalog.byId(p.id());if(pet!=null&&pet.miniModel()!=null){var skin=MpsqPetRenderer.skinFor(pet);if(skin!=null)MpsqMiniYouModel.render(ctx.matrixStack(),ctx.consumers(),0xF000F0,p.id(),skin.texture(),skin.slim(),px,py,pz,p.yaw(),.7f,groundClip,position/1000.0);}}
            }
        }
        for(var drop:frame.drops())if(cam.distanceToSqr(drop.x(),drop.y(),drop.z())<22500)MpsqAccessoryRenderer.drawCinematicModel(ctx,MpsqSeagullDrops.URL,drop.x(),drop.y(),drop.z(),drop.yaw(),drop.clip(),drop.age(),0,0,drop.scale());
    }
    private static double petDistance(MpsqReplayData.PetState a,MpsqReplayData.PetState b){return Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2);}
    public static void open(MpsqReplayData replay){
        Minecraft c=Minecraft.getInstance();MpsqReplayManager.finish();MpsqSystemController.stopActive();
        if(c.level!=null)c.disconnectWithSavingScreen();reset();
        states=replay.palette.stream().map(s->BlockState.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(s)).getOrThrow()).toList();
        folder="mpsq-replay-"+UUID.randomUUID();data=replay;
        lastClock=System.nanoTime();error="";
        var rules=new GameRules(net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS);
        var info=new LevelSettings("MPSQ Replay – "+MpsqZoneCatalog.label(replay.game),GameType.SPECTATOR,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,true),false,WorldDataConfiguration.DEFAULT);
        c.createWorldOpenFlows().createFreshLevel(folder,info,new WorldOptions(0,false,false),lookup->{
            var flat=new FlatLevelGeneratorSettings(Optional.empty(),lookup.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS),List.of());
            return WorldPresets.createNormalWorldDimensions(lookup).replaceOverworldGenerator(lookup,new FlatLevelSource(flat));
        },new TitleScreen());
    }
    private static void serverTick(net.minecraft.server.MinecraftServer server){
        var d=data;var world=server.overworld();if(world==null||!error.isEmpty())return;
        if(!configured){
            world.getGameRules().set(GameRules.SPAWN_MOBS,false,server);
            world.getGameRules().set(GameRules.ADVANCE_TIME,false,server);
            world.getGameRules().set(GameRules.ADVANCE_WEATHER,false,server);
            world.getGameRules().set(GameRules.RANDOM_TICK_SPEED,0,server);
            world.dimensionType().defaultClock().ifPresent(clock -> server.clockManager().setTotalTicks(clock, 6000L));server.tickRateManager().setFrozen(true);configured=true;
        }
        if(!ready){
            var pos=new BlockPos.MutableBlockPos();int end=Math.min(d.blocks.length,buildCell+100000);
            for(;buildCell<end;buildCell++)if(!states.get(d.blocks[buildCell]).isAir()){
                pos.set(d.area.bx(buildCell),d.area.by(buildCell),d.area.bz(buildCell));world.setBlock(pos,states.get(d.blocks[buildCell]),18);
            }
            if(buildCell<d.blocks.length||server.getPlayerList().getPlayers().isEmpty())return;
            for(var p:server.getPlayerList().getPlayers()){p.setGameMode(GameType.SPECTATOR);p.teleportTo(d.area.x()+d.area.width()/2.0,d.area.y()+d.area.height()/2.0+8,d.area.z()+d.area.depth()/2.0);}
            ready=true;Minecraft.getInstance().execute(()->Minecraft.getInstance().gui.setScreen(new MpsqReplayControlsScreen()));
        }
        int budget=20000;long target=position;
        while(changeCursor<d.changes.size()&&d.changes.get(changeCursor).time()<=target&&budget-->0){var ch=d.changes.get(changeCursor++);appliedBlockDetails.remove(ch.cell());world.setBlock(new BlockPos(d.area.bx(ch.cell()),d.area.by(ch.cell()),d.area.bz(ch.cell())),states.get(ch.after()),18);}
        while(changeCursor>0&&d.changes.get(changeCursor-1).time()>target&&budget-->0){var ch=d.changes.get(--changeCursor);appliedBlockDetails.remove(ch.cell());world.setBlock(new BlockPos(d.area.bx(ch.cell()),d.area.by(ch.cell()),d.area.bz(ch.cell())),states.get(ch.before()),18);}
        if(!d.frames.isEmpty())for(var detail:d.frames.get(d.frameAt(target)).blockEntities()){
            int cell=detail.cell();var pos=new BlockPos(d.area.bx(cell),d.area.by(cell),d.area.bz(cell));var be=world.getBlockEntity(pos);
            if(be!=null&&!detail.nbt().equals(appliedBlockDetails.get(cell))){
                try{be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,world.registryAccess(),TagParser.parseCompoundFully(detail.nbt())));be.setChanged();world.sendBlockUpdated(pos,world.getBlockState(pos),world.getBlockState(pos),3);appliedBlockDetails.put(cell,detail.nbt());}
                catch(Exception failure){throw new IllegalStateException("Replay-Block-Entity bei "+pos,failure);}
            }
        }
    }
    private static void updateGhosts(Minecraft c){
        var d=data;int frame=d.frameAt(position);var a=d.frames.get(frame);var b=d.frames.get(Math.min(frame+1,d.frames.size()-1));
        double f=b.time()==a.time()?0:Mth.clamp((position-a.time())/(double)(b.time()-a.time()),0,1);
        Map<Integer,MpsqReplayData.State> next=new HashMap<>();for(var s:b.actors())next.put(s.actor(),s);
        Map<Integer,MpsqReplayData.EntityDetails> details=new HashMap<>();for(var detail:a.details())details.put(detail.actor(),detail);
        Set<Integer> visible=new HashSet<>();colors.clear();falls.clear();
        for(var s:a.actors()){
            visible.add(s.actor());Entity e=ghosts.get(s.actor());if(e==null){e=createGhost(c,d.actors.get(s.actor()));if(e==null)continue;ghosts.put(s.actor(),e);}
            var detail=details.get(s.actor());
            if(detail!=null){
                if(!detail.nbt().equals(appliedDetails.get(s.actor()))){
                    try{var uuid=e.getUUID();int id=e.getId();e.load(TagValueInput.create(ProblemReporter.DISCARDING,c.level.registryAccess(),TagParser.parseCompoundFully(detail.nbt())));e.setUUID(uuid);e.setId(id);if(e instanceof Mob mob)mob.setNoAi(true);appliedDetails.put(s.actor(),detail.nbt());}
                    catch(Exception failure){error="Replay-Entity-Daten konnten nicht wiederhergestellt werden.";playing=false;MpsqCameraClient.LOGGER.error(error,failure);return;}
                }
                e.tickCount=detail.age();e.setOnGround(detail.grounded());
            }
            var n=next.getOrDefault(s.actor(),s);double mix=squaredDistance(s,n)>256?0:f;
            e.snapTo(Mth.lerp(mix,s.x(),n.x()),Mth.lerp(mix,s.y(),n.y()),Mth.lerp(mix,s.z(),n.z()),Mth.rotLerp((float)mix,s.yaw(),n.yaw()),Mth.lerp((float)mix,s.pitch(),n.pitch()));
            e.setDeltaMovement(Vec3.ZERO);e.setNoGravity(true);e.setPose(Pose.values()[Math.max(0,Math.min(Pose.values().length-1,s.pose()))]);
            if(e instanceof LivingEntity l){l.setYBodyRot(s.bodyYaw());l.yBodyRotO=s.bodyYaw();l.setYHeadRot(e.getYRot());l.yHeadRotO=e.getYRot();if(position!=lastGhostPosition)l.walkAnimation.update((float)Math.min(1,Math.sqrt(squaredDistance(s,n))*4),1f,1f);}
            if(s.glow()>=0)colors.put(e,s.glow());if(s.fall()>=0)falls.put(key(e),s.fall());
        }
        ghosts.entrySet().removeIf(entry->{if(visible.contains(entry.getKey()))return false;entry.getValue().discard();appliedDetails.remove(entry.getKey());return true;});
        Map<String,Entity> byUuid=new HashMap<>();for(var entry:ghosts.entrySet())byUuid.put(d.actors.get(entry.getKey()).uuid(),entry.getValue());
        for(var detail:a.details()){Entity e=ghosts.get(detail.actor());if(e==null)continue;Entity vehicle=byUuid.get(detail.vehicle());if(e.getVehicle()!=vehicle){e.stopRiding();if(vehicle!=null)e.startRiding(vehicle,true,false);}}
        lastGhostPosition=position;
    }
    private static double squaredDistance(MpsqReplayData.State a,MpsqReplayData.State b){return Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2);}
    private static Entity createGhost(Minecraft c,MpsqReplayData.Actor actor){
        try{
            Entity e;
            if(actor.type().equals("minecraft:player")){
                var properties=com.google.common.collect.ArrayListMultimap.<String,Property>create();
                if(!actor.texture().isEmpty())properties.put("textures",new Property("textures",actor.texture(),actor.signature().isEmpty()?null:actor.signature()));
                GameProfile profile=new GameProfile(UUID.fromString(actor.uuid()),actor.name(),new com.mojang.authlib.properties.PropertyMap(properties));
                var skin=c.getSkinManager().createLookup(profile,false);
                e=new RemotePlayer(c.level,profile){@Override public net.minecraft.world.entity.player.PlayerSkin getSkin(){return skin.get();}};
            }else{
                var type=BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(actor.type()));e=type.create(c.level,EntitySpawnReason.LOAD);if(e==null)return null;
            }
            try{e.load(TagValueInput.create(ProblemReporter.DISCARDING,c.level.registryAccess(),TagParser.parseCompoundFully(actor.nbt())));}catch(Exception ignored){}
            e.setUUID(UUID.randomUUID());e.setId(nextEntity--);e.setNoGravity(true);if(e instanceof Mob m)m.setNoAi(true);
            c.level.addEntity(e);return e;
        }catch(Exception ex){MpsqCameraClient.LOGGER.debug("Replay-Entity konnte nicht geladen werden: {}",actor.type(),ex);return null;}
    }
    public static void close(){Minecraft c=Minecraft.getInstance();if(active()&&c.getSingleplayerServer()!=null&&c.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString().equals(folder))c.disconnectWithSavingScreen();reset();c.gui.setScreen(new TitleScreen());}
    public static void reset(){for(var e:ghosts.values())e.discard();ghosts.clear();appliedDetails.clear();appliedBlockDetails.clear();colors.clear();falls.clear();data=null;states=null;ready=false;playing=false;configured=false;position=0;buildCell=0;changeCursor=0;speed=1;}
}
