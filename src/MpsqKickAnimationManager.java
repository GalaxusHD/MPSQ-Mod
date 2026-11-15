package de.galaxushd.mpsqcamera;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.entity.player.Player;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side adaptation of the supplied Blockbench fall keyframes. */
public final class MpsqKickAnimationManager {
    private static final float ANIMATION_SPEED=1.25f;
    private static final float FINAL_POSE_SECONDS=.9583333f;
    private static final Map<String,Long> ACTIVE=new ConcurrentHashMap<>();
    private static final java.util.Set<String> IMPACT_PLAYED=ConcurrentHashMap.newKeySet();
    private static final Map<String,CloneRecord> CLONES=new ConcurrentHashMap<>();
    private static final Map<UUID,PlayerSkin> CLONE_SKINS=new ConcurrentHashMap<>();
    private static final Map<UUID,UUID> CLONE_SOURCE_UUIDS=new ConcurrentHashMap<>();
    private static final Map<UUID,boolean[]> SOURCE_MODEL_PARTS=new ConcurrentHashMap<>();
    private static int nextCloneEntityId=-1_800_000_000;
    private MpsqKickAnimationManager(){}
    public static void initialize(){
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(client.level==null || !TeamVisibilitySettings.inArea() || !TeamCommandManager.kickEffectsEnabled()) { clearClones(); return; }
            long now=System.currentTimeMillis();
            CLONES.entrySet().removeIf(e->{
                CloneRecord record=e.getValue();
                if(record.playerCopy.level()!=client.level) { discard(record); return true; }
                updateClonePosition(record,animationTime((now-record.startedAt)/1000.0));
                return false;
            });
        });
    }
    static float animationTime(double elapsed){
        return (float)Math.min(FINAL_POSE_SECONDS,Math.max(0,elapsed)*ANIMATION_SPEED);
    }
    public static void clearClones(){for(CloneRecord record:CLONES.values())discard(record);CLONES.clear();CLONE_SKINS.clear();CLONE_SOURCE_UUIDS.clear();SOURCE_MODEL_PARTS.clear();ACTIVE.clear();IMPACT_PLAYED.clear();}
    private static void discard(CloneRecord record){if(!record.playerCopy.isRemoved())record.playerCopy.discard();ACTIVE.remove(normalize(record.animationKey));IMPACT_PLAYED.remove(normalize(record.animationKey));CLONE_SKINS.remove(record.playerCopy.getUUID());CLONE_SOURCE_UUIDS.remove(record.playerCopy.getUUID());SOURCE_MODEL_PARTS.remove(record.playerCopy.getUUID());}
    public static void start(String player){if(player!=null&&!player.isBlank()){String key=player.toLowerCase(Locale.ROOT);IMPACT_PLAYED.remove(key);ACTIVE.put(key,System.currentTimeMillis());}}
    /** Keeps a client-side body at the target position until the game is stopped. */
    public static void startClone(String targetName,JsonObject data,AbstractClientPlayer localSource){
        if(!TeamVisibilitySettings.inArea()||targetName==null||targetName.isBlank()||data==null||!data.has("cloneId"))return;
        String cloneId=data.get("cloneId").getAsString();if(cloneId.isBlank()||CLONES.containsKey(cloneId))return;
        if(!data.has("x")||!data.has("y")||!data.has("z")||!data.has("yaw")||!data.has("pitch"))return;
        Minecraft client=Minecraft.getInstance();if(client.level==null)return;
        double x=data.get("x").getAsDouble(),y=data.get("y").getAsDouble(),z=data.get("z").getAsDouble();
        float yaw=data.has("bodyYaw")?data.get("bodyYaw").getAsFloat():data.get("yaw").getAsFloat(),pitch=data.get("pitch").getAsFloat();
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Float.isFinite(yaw)||!Float.isFinite(pitch))return;
        AbstractClientPlayer skinSource=localSource;
        if(skinSource==null)for(Player player:client.level.players())if(player instanceof AbstractClientPlayer candidate&&candidate.getGameProfile().name().equalsIgnoreCase(targetName)){skinSource=candidate;break;}
        // A fresh UUID avoids colliding with the real player entity that was just teleported.
        String animationKey=normalize(targetName+"#mpsq-kick-"+cloneId);
        GameProfile profile=new GameProfile(UUID.randomUUID(),targetName,skinSource==null?com.mojang.authlib.properties.PropertyMap.EMPTY:skinSource.getGameProfile().properties());
        // The dispatcher chooses slim/wide geometry BEFORE updateRenderState.
        // Override the entity skin too; replacing only state.skinTextures is too late.
        PlayerSkin capturedSkin=skinSource==null?null:skinSource.getSkin();
        if(capturedSkin==null&&client.getConnection()!=null){
            var entry=client.getConnection().getPlayerInfo(targetName);
            if(entry!=null)capturedSkin=entry.getSkin();
        }
        final PlayerSkin skin=capturedSkin;
        RemotePlayer clone=new RemotePlayer((ClientLevel)client.level,profile){
            @Override public PlayerSkin getSkin(){return skin!=null?skin:super.getSkin();}
        };
        if(skin!=null)CLONE_SKINS.put(profile.id(),skin);
        if(skinSource!=null){
            var parts=net.minecraft.world.entity.player.PlayerModelPart.values();
            SOURCE_MODEL_PARTS.put(profile.id(),new boolean[]{
                skinSource.isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.HAT),
                skinSource.isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.JACKET),
                skinSource.isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.LEFT_SLEEVE),
                skinSource.isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.RIGHT_SLEEVE),
                skinSource.isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.LEFT_PANTS_LEG),
                skinSource.isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.RIGHT_PANTS_LEG)});
        }
        clone.setId(nextCloneEntityId--);clone.setNoGravity(true);clone.snapTo(x,y,z,yaw,pitch);
        client.level.addEntity(clone);
        // Fall backwards in the target's own coordinate system, not away from
        // the command sender (who can be anywhere, including above the target).
        double radians=Math.toRadians(yaw),dx=Math.sin(radians),dz=-Math.cos(radians);
        clone.setYBodyRot(yaw);clone.setYHeadRot(yaw);clone.yBodyRotO=yaw;clone.yHeadRotO=yaw;
        long now=System.currentTimeMillis();CloneRecord record=new CloneRecord(clone,animationKey,now,x,y,z,yaw,pitch,dx,dz);
        CLONES.put(cloneId,record);ACTIVE.put(animationKey,now);
    }
    public static boolean isClone(AbstractClientPlayer player){for(CloneRecord record:CLONES.values())if(record.playerCopy==player)return true;return false;}
    public static String animationKey(AbstractClientPlayer player){for(CloneRecord record:CLONES.values())if(record.playerCopy==player)return record.animationKey;return player.getGameProfile().name();}
    public static PlayerSkin skinTextures(AbstractClientPlayer player){return player==null?null:CLONE_SKINS.get(player.getUUID());}
    public static void rememberSourceModelParts(AbstractClientPlayer player,AvatarRenderState state){if(player==null||state==null||isClone(player))return;SOURCE_MODEL_PARTS.put(player.getUUID(),new boolean[]{state.showHat,state.showJacket,state.showLeftSleeve,state.showRightSleeve,state.showLeftPants,state.showRightPants});}
    public static boolean[] sourceModelParts(AbstractClientPlayer player){boolean[] parts=player==null?null:SOURCE_MODEL_PARTS.get(player.getUUID());return parts==null?null:parts.clone();}
    private static String normalize(String value){return value==null?"":value.toLowerCase(Locale.ROOT);}
    public static float elapsedSeconds(String player){
        Float replay=MpsqReplayPlayback.fall(player);if(replay!=null)return replay;
        if(!TeamCommandManager.kickEffectsEnabled()||player==null)return -1;
        Long start=ACTIVE.get(normalize(player));
        return start==null?-1:animationTime((System.currentTimeMillis()-start)/1000.0);
    }
    public static float rootPitchDegrees(String player){float t=elapsedSeconds(player);if(t<0)return 0;return -sample(t,new float[][]{{0,0,0,0},{.1667f,-30,0,0},{.3333f,-90,0,0},{.5417f,-90,0,0}})[0];}
    /** Bodies keep their full size, including the final resting pose. */
    public static float despawnScale(String player){return 1.0f;}
    /** Rotation channels are the supplied model's degree keyframes mapped onto vanilla player parts. */
    public static float[] rotation(String player,String bone){float t=elapsedSeconds(player);if(t<0)return null;float[][] frames=switch(bone){
        case "head"->new float[][]{{0,0,0,0},{.1667f,42.5f,0,0},{.375f,12.5f,0,0},{.625f,27.5f,0,0},{.8333f,12.5f,0,0}};
        case "rightArm"->new float[][]{{0,0,0,0},{.1667f,-66.024f,-7.301f,15.948f},{.4583f,-178.622f,27.129f,-15.492f}};
        case "leftArm"->new float[][]{{0,0,0,0},{.1667f,-75.109f,9.298f,-31.282f},{.4583f,-173.504f,-31.892f,11.917f}};
        case "rightLeg"->new float[][]{{0,0,0,0},{.25f,-56.399f,10.156f,.317f},{.4583f,-25.837f,17.835f,2.187f},{.625f,3.967f,14.478f,15.504f},{.75f,-5.021f,19.001f,12.657f},{.9583f,0,17.5f,12.5f}};
        case "leftLeg"->new float[][]{{0,0,0,0},{.25f,-71.353f,-10.319f,-.328f},{.4583f,-25.837f,-17.835f,-2.187f},{.625f,3.284f,-12.068f,-15.347f},{.75f,-7.499f,-19.432f,-12.607f},{.9583f,0,-15,-15}};
        default->null;};
        if(frames==null)return null;float[] r=sample(t,frames);float toRadians=(float)Math.PI/180f;return new float[]{-r[0]*toRadians,-r[1]*toRadians,r[2]*toRadians};
    }
    public static float[] position(String player,String bone){
        float t=elapsedSeconds(player);if(t<0)return null;
        if(bone.endsWith("Arm"))return new float[]{0,-3*Math.min(1,t/.4583333f),0};
        if(bone.endsWith("Leg"))return new float[]{0,0,(float)sampleScalar(t,new double[][]{{0,0},{.4583333,-2},{.625,0},{.9583333,0}})};
        return new float[]{0,0,0};
    }
    private static float[] sample(float time,float[][] frames){if(time<=frames[0][0])return new float[]{frames[0][1],frames[0][2],frames[0][3]};for(int i=1;i<frames.length;i++){float[] b=frames[i],a=frames[i-1];if(time<=b[0]){float p=(time-a[0])/(b[0]-a[0]);return new float[]{lerp(a[1],b[1],p),lerp(a[2],b[2],p),lerp(a[3],b[3],p)};}}float[] last=frames[frames.length-1];return new float[]{last[1],last[2],last[3]};}
    private static float lerp(float a,float b,float p){return a+(b-a)*p;}
    private static void updateClonePosition(CloneRecord record,double elapsedSeconds){
        // Positions are taken from the supplied Blockbench root animation and
        // converted from model pixels to blocks. The original moves away from
        // the kicker while dropping; using its keyframes avoids the old fast,
        // upward arc that made the copy appear to fly in the wrong direction.
        double t=Math.min(FINAL_POSE_SECONDS,Math.max(0.0,elapsedSeconds));
        double distance=fallDistance(t);
        double drop=fallDrop(t);
        double x=record.startX+record.directionX*distance;
        double z=record.startZ+record.directionZ*distance;
        double y=record.startY+drop;
        // Refresh interpolation endpoints too, so the copy glides instead of snapping.
        record.playerCopy.snapTo(x,y,z,record.yaw,record.pitch);
        record.playerCopy.setYBodyRot(record.yaw);record.playerCopy.setYHeadRot(record.yaw);
        record.playerCopy.yBodyRotO=record.yaw;record.playerCopy.yHeadRotO=record.yaw;
    }
    static double fallDistance(double time){
        return sampleScalar(time,new double[][]{{0,0},{.1667,.743},{.3333,1.5625},{.5417,2.168},{.75,2.4375}});
    }
    static double fallDrop(double time){
        // End at the authored landing height; the later burial keyframe is intentionally omitted.
        return sampleScalar(time,new double[][]{{0,0},{.1667,-.276},{.3333,-1.3125},{.5417,-1.294},{.75,-1.294}});
    }
    private static double sampleScalar(double time,double[][] frames){
        if(time<=frames[0][0])return frames[0][1];
        for(int i=1;i<frames.length;i++){double[] b=frames[i],a=frames[i-1];if(time<=b[0]){double p=(time-a[0])/(b[0]-a[0]);return a[1]+(b[1]-a[1])*p;}}
        return frames[frames.length-1][1];
    }
    private record CloneRecord(RemotePlayer playerCopy,String animationKey,long startedAt,
                               double startX,double startY,double startZ,float yaw,float pitch,
                               double directionX,double directionZ){}
}
