package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import java.util.*;

/** Offline Specials supplied by the project owner; numbered tracks are deliberately excluded. */
final class MpsqBuiltinMusic {
    static final List<String> IDS=List.of("fly_me_to_the_moon","squidgamebereit","squidgameessen","squidgamelobbystart");
    static final List<String> TITLES=List.of("Fly Me to the Moon","Squid Game · Bereit","Squid Game · Essen","Squid Game · Lobby-Start");
    static String sound(String id){
        if(id==null)return null;String key=id.toLowerCase(Locale.ROOT).replaceFirst("\\.mp3$","");
        if(key.startsWith("mpsqcamera:music."))key=key.substring("mpsqcamera:music.".length());
        return IDS.contains(key)?"mpsqcamera:music."+key:null;
    }
    static JsonArray playlists(JsonArray saved){
        JsonArray result=saved.deepCopy();for(var e:result)if(e.isJsonObject()&&"mpsq_specials".equals(MpsqScenes.str(e.getAsJsonObject(),"id","")))return result;
        var row=new JsonObject();row.addProperty("id","mpsq_specials");row.addProperty("name","Squid Game · Specials");var tracks=new JsonArray();var titles=new JsonArray();IDS.forEach(tracks::add);TITLES.forEach(titles::add);row.add("tracks",tracks);row.add("trackNames",titles);result.add(row);return result;
    }
}
