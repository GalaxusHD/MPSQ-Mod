package de.galaxushd.mpsqcamera;
import com.google.gson.*;
/** Smooth interpolation for the looping bobblehead tracks, including the loop seam. */
final class MpsqFigureAnimation {
    static float[] sample(JsonObject clip,JsonObject bone,String channel,float time){
        if(clip==null||!clip.has("animators"))return new float[3];
        var animators=clip.getAsJsonObject("animators");String name=bone.get("name").getAsString();
        if(!animators.has(name)||!animators.getAsJsonObject(name).has(channel))return new float[3];
        JsonArray frames=animators.getAsJsonObject(name).getAsJsonArray(channel);
        if(frames.isEmpty())return new float[3];
        int n=frames.size();
        if(n==1||time<=at(frames,0))return value(frames,0);
        if(time>=at(frames,n-1))return value(frames,n-1);
        boolean loop=clip.has("loop")&&"loop".equals(clip.get("loop").getAsString());
        for(int i=1;i<n;i++)if(time<=at(frames,i)){
            float t=(time-at(frames,i-1))/Math.max(.00001f,at(frames,i)-at(frames,i-1));
            float[] a=value(frames,i-1),b=value(frames,i);
            float[] prev=value(frames,i>1?i-2:loop&&n>2?n-2:0);
            float[] next=value(frames,i+1<n?i+1:loop&&n>2?1:n-1);
            float[] out=new float[3];
            for(int k=0;k<3;k++)out[k]=.5f*((2*a[k])+(-prev[k]+b[k])*t+(2*prev[k]-5*a[k]+4*b[k]-next[k])*t*t+(-prev[k]+3*a[k]-3*b[k]+next[k])*t*t*t);
            return out;
        }
        return value(frames,n-1);
    }
    private static float at(JsonArray f,int i){return f.get(i).getAsJsonObject().get("time").getAsFloat();}
    private static float[] value(JsonArray f,int i){var v=f.get(i).getAsJsonObject().getAsJsonArray("values");return new float[]{v.get(0).getAsFloat(),v.get(1).getAsFloat(),v.get(2).getAsFloat()};}
}
