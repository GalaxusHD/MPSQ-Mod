package de.galaxushd.mpsqcamera;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side adaptation of the supplied Blockbench fall keyframes. */
public final class MpsqKickAnimationManager {
    private static final long DURATION_MS=4_000;
    private static final long CLONE_DURATION_MS=2_050;
    private static final float FLIGHT_SECONDS=1.55f;
    private static final Map<String,Long> ACTIVE=new ConcurrentHashMap<>();
    private static final Map<String,CloneRecord> CLONES=new ConcurrentHashMap<>();
    private static final Map<UUID,SkinTextures> CLONE_SKINS=new ConcurrentHashMap<>();
    private static final Map<UUID,UUID> CLONE_SOURCE_UUIDS=new ConcurrentHashMap<>();
    private static final Map<UUID,boolean[]> SOURCE_MODEL_PARTS=new ConcurrentHashMap<>();
    private static int nextCloneEntityId=-1_800_000_000;
    private MpsqKickAnimationManager(){}
    public static void initialize(){ClientTickEvents.END_CLIENT_TICK.register(client->{long now=System.currentTimeMillis();if(!TeamVisibilitySettings.visible()||client.world==null){for(CloneRecord record:CLONES.values())if(!record.playerCopy.isRemoved())record.playerCopy.discard();CLONES.clear();CLONE_SKINS.clear();CLONE_SOURCE_UUIDS.clear();SOURCE_MODEL_PARTS.clear();ACTIVE.clear();return;}ACTIVE.entrySet().removeIf(e->now-e.getValue()>DURATION_MS);CLONES.entrySet().removeIf(e->{CloneRecord record=e.getValue();long elapsed=now-record.startedAt;if(elapsed>CLONE_DURATION_MS){if(!record.playerCopy.isRemoved())record.playerCopy.discard();ACTIVE.remove(normalize(record.animationKey));CLONE_SKINS.remove(record.playerCopy.getUuid());CLONE_SOURCE_UUIDS.remove(record.playerCopy.getUuid());SOURCE_MODEL_PARTS.remove(record.playerCopy.getUuid());return true;}updateClonePosition(record,elapsed/1000.0);return false;});});}
    public static void start(String player){if(player!=null&&!player.isBlank())ACTIVE.put(player.toLowerCase(Locale.ROOT),System.currentTimeMillis());}
    /** Creates a short-lived client-side copy at the target's pre-teleport position. */
    public static void startClone(String targetName,JsonObject data,AbstractClientPlayerEntity localSource){
        if(!TeamVisibilitySettings.visible()||targetName==null||targetName.isBlank()||data==null||!data.has("cloneId"))return;
        String cloneId=data.get("cloneId").getAsString();if(cloneId.isBlank()||CLONES.containsKey(cloneId))return;
        if(!data.has("x")||!data.has("y")||!data.has("z")||!data.has("yaw")||!data.has("pitch"))return;
        MinecraftClient client=MinecraftClient.getInstance();if(client.world==null)return;
        double x=data.get("x").getAsDouble(),y=data.get("y").getAsDouble(),z=data.get("z").getAsDouble();
        float yaw=data.get("yaw").getAsFloat(),pitch=data.get("pitch").getAsFloat();
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Float.isFinite(yaw)||!Float.isFinite(pitch))return;
        AbstractClientPlayerEntity skinSource=localSource;
        if(skinSource==null)for(PlayerEntity player:client.world.getPlayers())if(player instanceof AbstractClientPlayerEntity candidate&&candidate.getGameProfile().getName().equalsIgnoreCase(targetName)){skinSource=candidate;break;}
        // A fresh UUID avoids colliding with the real player entity that was just teleported.
        String animationKey=normalize(targetName+"#mpsq-kick-"+cloneId);
        GameProfile profile=new GameProfile(UUID.randomUUID(),targetName);
        if(skinSource!=null)profile.getProperties().putAll(skinSource.getGameProfile().getProperties());
        OtherClientPlayerEntity clone=new OtherClientPlayerEntity((ClientWorld)client.world,profile);
        if(skinSource!=null){CLONE_SKINS.put(profile.getId(),skinSource.getSkinTextures());CLONE_SOURCE_UUIDS.put(profile.getId(),skinSource.getUuid());}
        clone.setId(nextCloneEntityId--);clone.setNoGravity(true);clone.refreshPositionAndAngles(x,y,z,yaw,pitch);
        client.world.addEntity(clone);
        double dx=0.0,dz=0.0;
        if(data.has("sourceX")&&data.has("sourceZ")){
            dx=x-data.get("sourceX").getAsDouble();dz=z-data.get("sourceZ").getAsDouble();
        }
        double length=Math.sqrt(dx*dx+dz*dz);
        if(!Double.isFinite(length)||length<0.001){double radians=Math.toRadians(yaw);dx=Math.sin(radians);dz=-Math.cos(radians);length=Math.sqrt(dx*dx+dz*dz);}
        dx/=length;dz/=length;
        long now=System.currentTimeMillis();CloneRecord record=new CloneRecord(clone,animationKey,now,x,y,z,yaw,pitch,dx,dz);
        CLONES.put(cloneId,record);ACTIVE.put(animationKey,now);
    }
    public static boolean isClone(AbstractClientPlayerEntity player){for(CloneRecord record:CLONES.values())if(record.playerCopy==player)return true;return false;}
    public static String animationKey(AbstractClientPlayerEntity player){for(CloneRecord record:CLONES.values())if(record.playerCopy==player)return record.animationKey;return player.getGameProfile().getName();}
    public static SkinTextures skinTextures(AbstractClientPlayerEntity player){return player==null?null:CLONE_SKINS.get(player.getUuid());}
    public static void rememberSourceModelParts(AbstractClientPlayerEntity player,PlayerEntityRenderState state){if(player==null||state==null||isClone(player))return;SOURCE_MODEL_PARTS.put(player.getUuid(),new boolean[]{state.hatVisible,state.jacketVisible,state.leftSleeveVisible,state.rightSleeveVisible,state.leftPantsLegVisible,state.rightPantsLegVisible});}
    public static boolean[] sourceModelParts(AbstractClientPlayerEntity player){UUID source=player==null?null:CLONE_SOURCE_UUIDS.get(player.getUuid());boolean[] parts=source==null?null:SOURCE_MODEL_PARTS.get(source);return parts==null?null:parts.clone();}
    private static String normalize(String value){return value==null?"":value.toLowerCase(Locale.ROOT);}
    public static float elapsedSeconds(String player){if(!TeamVisibilitySettings.visible()||player==null)return -1;String key=normalize(player);Long start=ACTIVE.get(key);if(start==null)return -1;float elapsed=(System.currentTimeMillis()-start)/1000f;if(elapsed>DURATION_MS/1000f){ACTIVE.remove(key);return -1;}return elapsed;}
    public static float rootPitchDegrees(String player){float t=elapsedSeconds(player);if(t<0)return 0;return sample(t,new float[][]{{0,0,0,0},{.1667f,30,0,0},{.3333f,90,0,0},{.5417f,90,0,0}})[0];}
    /** Shrinks the clone away after its backward flight has landed. */
    public static float despawnScale(String player){float t=elapsedSeconds(player);if(t<FLIGHT_SECONDS)return 1.0f;return Math.max(0.001f,1.0f-(t-FLIGHT_SECONDS)/0.45f);}
    /** Rotation channels are the supplied model's degree keyframes mapped onto vanilla player parts. */
    public static float[] rotation(String player,String bone){float t=elapsedSeconds(player);if(t<0)return null;float[][] frames=switch(bone){
        case "head"->new float[][]{{0,0,0,0},{.1667f,42.5f,0,0},{.375f,12.5f,0,0},{.625f,27.5f,0,0},{.8333f,12.5f,0,0}};
        case "rightArm"->new float[][]{{0,0,0,0},{.1667f,-66.024f,-7.301f,15.948f},{.4583f,-178.622f,27.129f,-15.492f}};
        case "leftArm"->new float[][]{{0,0,0,0},{.1667f,-75.109f,9.298f,-31.282f},{.4583f,-173.504f,-31.892f,11.917f}};
        case "rightLeg"->new float[][]{{0,0,0,0},{.25f,-56.399f,10.156f,.317f},{.4583f,-25.837f,17.835f,2.187f},{.625f,3.967f,14.478f,15.504f},{.75f,-5.021f,19.001f,12.657f},{.9583f,0,17.5f,12.5f}};
        case "leftLeg"->new float[][]{{0,0,0,0},{.25f,-71.353f,-10.319f,-.328f},{.4583f,-25.837f,-17.835f,-2.187f},{.625f,3.284f,-12.068f,-15.347f},{.75f,-7.499f,-19.432f,-12.607f},{.9583f,0,-15,-15}};
        default->null;};
        if(frames==null)return null;float[] r=sample(t,frames);float toRadians=(float)Math.PI/180f;return new float[]{r[0]*toRadians,r[1]*toRadians,r[2]*toRadians};
    }
    private static float[] sample(float time,float[][] frames){if(time<=frames[0][0])return new float[]{frames[0][1],frames[0][2],frames[0][3]};for(int i=1;i<frames.length;i++){float[] b=frames[i],a=frames[i-1];if(time<=b[0]){float p=(time-a[0])/(b[0]-a[0]);return new float[]{lerp(a[1],b[1],p),lerp(a[2],b[2],p),lerp(a[3],b[3],p)};}}float[] last=frames[frames.length-1];return new float[]{last[1],last[2],last[3]};}
    private static float lerp(float a,float b,float p){return a+(b-a)*p;}
    private static void updateClonePosition(CloneRecord record,double elapsedSeconds){
        double progress=Math.min(1.0,Math.max(0.0,elapsedSeconds/FLIGHT_SECONDS));
        double distance=2.8*progress;
        double arc=0.82*Math.sin(Math.PI*progress);
        double x=record.startX+record.directionX*distance;
        double z=record.startZ+record.directionZ*distance;
        double y=record.startY+arc;
        // Refresh interpolation endpoints too, so the copy glides instead of snapping.
        record.playerCopy.refreshPositionAndAngles(x,y,z,record.yaw,record.pitch);
    }
    private record CloneRecord(OtherClientPlayerEntity playerCopy,String animationKey,long startedAt,
                               double startX,double startY,double startZ,float yaw,float pitch,
                               double directionX,double directionZ){}
}
