package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Catalog of the purchased model animations. No Scenes server code is bundled. */
final class MpsqScenesClips {
    record Clip(String id,String model,String clip,double length,int volume) {
        String label(){
            String s=clip.replace("eat_sandwich_stand","Sandwich essen").replace("eat_soup_stand","Suppe essen")
                .replace("eat_marshmallow_sit","Marshmallow essen").replace("fence_sit_","Zaun sitzen ")
                .replace("dance","Tanz ").replace("cry","Weinen ").replace("sleep","Schlafen ")
                .replace("wakeup","Aufwachen").replace("interact","Interaktion").replace("see","Reaktion").replace("idle","Ruhe");
            return volume==5?s:"V1 · "+model.replace("scene_townsfolk_","")+" · "+s;
        }
    }
    private static final List<Clip> ALL=load();
    static List<Clip> all(){return ALL;}
    static Clip get(String id){return ALL.stream().filter(c->c.id.equals(id)).findFirst().orElse(null);}
    static String resource(String url){
        if(url==null||!url.startsWith("mpsq-scenes:"))return null;
        String model=url.substring(12);
        return ALL.stream().anyMatch(c->c.model.equals(model)|| (c.model+"_slim").equals(model))?"/assets/mpsqcamera/custom_models/scenes/"+model+".json":null;
    }
    static String url(Clip clip){return "mpsq-scenes:"+clip.model;}
    private static List<Clip> load(){
        try(var in=MpsqScenesClips.class.getResourceAsStream("/assets/mpsqcamera/custom_models/scenes/catalog.json")){
            if(in==null)throw new IOException("Scenes-Animationskatalog fehlt");
            return Arrays.stream(new Gson().fromJson(new InputStreamReader(in,StandardCharsets.UTF_8),Clip[].class))
                .sorted(Comparator.comparingInt((Clip c)->-c.volume)).toList();
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
    }
    /** Respect each source keyframe's interpolation, including scale-based prop hiding. */
    static float[] transformSample(JsonObject clip,JsonObject bone,String channel,float time){
        float[] value=sample(clip,bone,channel,time);
        boolean modern=clip!=null&&clip.has("coordinate_system")&&clip.get("coordinate_system").getAsString().equals("blockbench5");
        if(!modern){if(channel.equals("rotation")){value[0]=-value[0];value[1]=-value[1];}else if(channel.equals("position"))value[0]=-value[0];}
        return value;
    }
    static float[] sample(JsonObject clip,JsonObject bone,String channel,float time){
        float fallback=channel.equals("scale")?1:0;
        if(clip==null||!clip.has("animators"))return new float[]{fallback,fallback,fallback};
        JsonObject tracks=clip.getAsJsonObject("animators");String name=bone.get("name").getAsString();
        if(!tracks.has(name)||!tracks.getAsJsonObject(name).has(channel))return new float[]{fallback,fallback,fallback};
        JsonArray f=tracks.getAsJsonObject(name).getAsJsonArray(channel);if(f.isEmpty())return new float[]{fallback,fallback,fallback};
        int next=0;while(next<f.size()&&f.get(next).getAsJsonObject().get("time").getAsFloat()<time)next++;
        if(next==0)return values(f,0);if(next==f.size())return values(f,f.size()-1);
        var a=f.get(next-1).getAsJsonObject();var b=f.get(next).getAsJsonObject();
        float t=(time-a.get("time").getAsFloat())/Math.max(.000001f,b.get("time").getAsFloat()-a.get("time").getAsFloat());
        String interpolation=a.has("interpolation")?a.get("interpolation").getAsString():"linear";
        if(t>=1)return values(f,next);if(interpolation.equals("step"))return values(f,next-1);
        float[] av=values(f,next-1),bv=values(f,next),out=new float[3];
        boolean loop=clip.has("loop")&&clip.get("loop").getAsString().equals("loop")&&f.size()>=3;
        float[] p=values(f,next<2&&loop?f.size()-2:Math.max(0,next-2)),n=values(f,next+1>=f.size()&&loop?1:Math.min(f.size()-1,next+1));
        boolean curved=interpolation.equals("catmullrom")||(b.has("interpolation")&&b.get("interpolation").getAsString().equals("catmullrom"));
        for(int i=0;i<3;i++)out[i]=curved?
            .5f*(2*av[i]+(-p[i]+bv[i])*t+(2*p[i]-5*av[i]+4*bv[i]-n[i])*t*t+(-p[i]+3*av[i]-3*bv[i]+n[i])*t*t*t):av[i]+(bv[i]-av[i])*t;
        return out;
    }
    private static float[] values(JsonArray f,int i){var v=f.get(i).getAsJsonObject().getAsJsonArray("values");return new float[]{v.get(0).getAsFloat(),v.get(1).getAsFloat(),v.get(2).getAsFloat()};}
}

