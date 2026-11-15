package de.galaxushd.mpsqcamera;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import com.google.gson.JsonObject;
import java.util.*;
/** Small authored texture strips, independent of a statue's one-shot skeletal animation. */
final class MpsqAnimatedTextures {
    private record Strip(NativeImage source,NativeImage frame,DynamicTexture texture,int ticks,boolean interpolate){}
    private static final Map<Identifier,Strip> STRIPS=new HashMap<>();
    static DynamicTexture create(Identifier id,NativeImage image,JsonObject animation){
        if(animation==null)return new DynamicTexture(()->"MPSQ Accessoire",image);
        int size=image.getWidth();if(image.getHeight()%size!=0)throw new IllegalArgumentException("Ungültiger Animationsstreifen");
        NativeImage frame=new NativeImage(size,size,true);
        for(int y=0;y<size;y++)for(int x=0;x<size;x++)frame.setPixel(x,y,image.getPixel(x,y));
        DynamicTexture texture=new DynamicTexture(()->"MPSQ Glitzern",frame);
        STRIPS.put(id,new Strip(image,frame,texture,Math.max(1,animation.get("frametime").getAsInt()),animation.has("interpolate")&&animation.get("interpolate").getAsBoolean()));
        return texture;
    }
    static void update(){
        long millis=System.nanoTime()/1_000_000L;
        for(Strip s:STRIPS.values()){
            int size=s.frame.getWidth(),count=s.source.getHeight()/size;long duration=s.ticks*50L;
            int index=(int)((millis/duration)%count),next=(index+1)%count;float t=s.interpolate?(millis%duration)/(float)duration:0;
            for(int y=0;y<size;y++)for(int x=0;x<size;x++)s.frame.setPixel(x,y,blend(s.source.getPixel(x,index*size+y),s.source.getPixel(x,next*size+y),t));
            s.texture.upload();
        }
    }
    static int blend(int a,int b,float t){int out=0;for(int shift=0;shift<=24;shift+=8){int ca=(a>>>shift)&255,cb=(b>>>shift)&255;out|=Math.round(ca+(cb-ca)*t)<<shift;}return out;}
    static void remove(Identifier id){Strip s=STRIPS.remove(id);if(s!=null)s.source.close();}
    static void clear(){for(Strip s:STRIPS.values())s.source.close();STRIPS.clear();}
}
