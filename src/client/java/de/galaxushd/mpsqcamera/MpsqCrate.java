package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import java.util.*;

/** One click opens, plays five open-idle loops, then closes. */
final class MpsqCrate {
    static final String ID="mpsq_crate";
    record Pose(String clip,float time){}
    private static final Map<String,Long> starts=new HashMap<>();
    private static String key(JsonObject o){return MpsqActionSync.world()+":"+o.get("x")+":"+o.get("y")+":"+o.get("z");}
    static boolean isCrate(JsonObject o){return o.has("model_id")&&ID.equals(o.get("model_id").getAsString());}
    static void open(JsonObject o){if(MpsqReplayPlayback.active())return;String key=key(o);long now=System.nanoTime();Long old=starts.get(key);if(old==null||now-old>=10_750_000_000L)starts.put(key,now);}
    static float elapsed(JsonObject o){if(MpsqReplayPlayback.active())return o.has("crate_elapsed")?o.get("crate_elapsed").getAsFloat():-1;long now=System.nanoTime();long start=starts.computeIfAbsent(key(o),unused->now-10_750_000_000L);return (now-start)/1e9f;}
    static Pose pose(float time){if(time<0)return new Pose("idle",0);if(time<1.25f)return new Pose("open",time);if(time<8.75f)return new Pose("open idle",time-1.25f);if(time<10.75f)return new Pose("close",time-8.75f);return new Pose("idle",(time-10.75f)%2f);}
    static void record(JsonArray objects){for(var e:objects){JsonObject o=e.getAsJsonObject();if(isCrate(o))o.addProperty("crate_elapsed",elapsed(o));}}
    static void clear(){starts.clear();}
}


