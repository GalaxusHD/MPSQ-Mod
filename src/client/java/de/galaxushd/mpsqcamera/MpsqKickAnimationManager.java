package de.galaxushd.mpsqcamera;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side adaptation of the supplied Blockbench fall keyframes. */
public final class MpsqKickAnimationManager {
    private static final long DURATION_MS=4_000;
    private static final Map<String,Long> ACTIVE=new ConcurrentHashMap<>();
    private MpsqKickAnimationManager(){}
    public static void initialize(){ClientTickEvents.END_CLIENT_TICK.register(client->ACTIVE.entrySet().removeIf(e->System.currentTimeMillis()-e.getValue()>DURATION_MS));}
    public static void start(String player){if(player!=null&&!player.isBlank())ACTIVE.put(player.toLowerCase(Locale.ROOT),System.currentTimeMillis());}
    public static float elapsedSeconds(String player){if(!TeamVisibilitySettings.visible()||player==null)return -1;Long start=ACTIVE.get(player.toLowerCase(Locale.ROOT));if(start==null)return -1;float elapsed=(System.currentTimeMillis()-start)/1000f;if(elapsed>DURATION_MS/1000f){ACTIVE.remove(player.toLowerCase(Locale.ROOT));return -1;}return elapsed;}
    public static float rootPitchDegrees(String player){float t=elapsedSeconds(player);if(t<0)return 0;return sample(t,new float[][]{{0,0,0,0},{.1667f,-30,0,0},{.3333f,-90,0,0},{.5417f,-90,0,0}})[0];}
    /** Rotation channels are the supplied model's degree keyframes mapped onto vanilla player parts. */
    public static float[] rotation(String player,String bone){float t=elapsedSeconds(player);if(t<0)return null;float[][] frames=switch(bone){
        case "head"->new float[][]{{0,0,0,0},{.1667f,42.5f,0,0},{.375f,12.5f,0,0},{.625f,27.5f,0,0},{.8333f,12.5f,0,0}};
        case "rightArm"->new float[][]{{0,0,0,0},{.1667f,-66.024f,-7.301f,15.948f},{.4583f,-178.622f,27.129f,-15.492f}};
        case "leftArm"->new float[][]{{0,0,0,0},{.1667f,-75.109f,9.298f,-31.282f},{.4583f,-173.504f,-31.892f,11.917f}};
        case "rightLeg"->new float[][]{{0,0,0,0},{.25f,-56.399f,10.156f,.317f},{.4583f,-25.837f,17.835f,2.187f},{.625f,3.967f,14.478f,15.504f},{.75f,-5.021f,19.001f,12.657f},{.9583f,0,17.5f,12.5f}};
        case "leftLeg"->new float[][]{{0,0,0,0},{.25f,-71.353f,-10.319f,-.328f},{.4583f,-25.837f,-17.835f,-2.187f},{.625f,3.284f,-12.068f,-15.347f},{.75f,-7.499f,-19.432f,-12.607f},{.9583f,0,-15,-15}};
        default->null;};
        if(frames==null||t>1.0f)return null;float[] r=sample(t,frames);float toRadians=(float)Math.PI/180f;return new float[]{r[0]*toRadians,r[1]*toRadians,r[2]*toRadians};
    }
    private static float[] sample(float time,float[][] frames){if(time<=frames[0][0])return new float[]{frames[0][1],frames[0][2],frames[0][3]};for(int i=1;i<frames.length;i++){float[] b=frames[i],a=frames[i-1];if(time<=b[0]){float p=(time-a[0])/(b[0]-a[0]);return new float[]{lerp(a[1],b[1],p),lerp(a[2],b[2],p),lerp(a[3],b[3],p)};}}float[] last=frames[frames.length-1];return new float[]{last[1],last[2],last[3]};}
    private static float lerp(float a,float b,float p){return a+(b-a)*p;}
}
