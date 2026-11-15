package de.galaxushd.mpsqcamera;
import com.mojang.blaze3d.platform.NativeImage;
/** Vanilla-compatible legacy limb mirroring and skin alpha rules. */
final class MpsqSkinImages {
    static boolean slim(NativeImage image,boolean hint){
        if(image.getHeight()==32)return false;
        boolean transparent=true;
        for(int y=20;y<32;y++)for(int x=54;x<56;x++)if((image.getPixel(x,y)>>>24)!=0)transparent=false;
        for(int y=52;y<64;y++)for(int x=46;x<48;x++)if((image.getPixel(x,y)>>>24)!=0)transparent=false;
        return transparent||hint;
    }
    static NativeImage normalize(NativeImage source){
        int h=source.getHeight();if(source.getWidth()!=64||(h!=32&&h!=64))throw new IllegalArgumentException("Skin muss 64x32 oder 64x64 sein");
        int[] pixels=new int[4096];for(int y=0;y<h;y++)for(int x=0;x<64;x++)pixels[y*64+x]=source.getPixel(x,y);
        normalizePixels(pixels,h==32);
        NativeImage out=new NativeImage(64,64,true);for(int y=0;y<64;y++)for(int x=0;x<64;x++)out.setPixel(x,y,pixels[y*64+x]);source.close();return out;
    }
    static void normalizePixels(int[] p,boolean legacy){
        if(legacy){
            int[][] regions={{4,16,16,32,4,4},{8,16,16,32,4,4},{0,20,24,32,4,12},{4,20,16,32,4,12},{8,20,8,32,4,12},{12,20,16,32,4,12},{44,16,-8,32,4,4},{48,16,-8,32,4,4},{40,20,0,32,4,12},{44,20,-8,32,4,12},{48,20,-16,32,4,12},{52,20,-8,32,4,12}};
            for(int[] r:regions)for(int y=0;y<r[5];y++)for(int x=0;x<r[4];x++)p[(r[1]+r[3]+y)*64+r[0]+r[2]+r[4]-1-x]=p[(r[1]+y)*64+r[0]+x];
            boolean opaque=true;for(int y=0;y<32;y++)for(int x=32;x<64;x++)if((p[y*64+x]>>>24)<128)opaque=false;
            if(opaque)for(int y=0;y<32;y++)for(int x=32;x<64;x++)p[y*64+x]&=0xFFFFFF;
        }
        opaque(p,0,0,32,16);opaque(p,0,16,64,32);opaque(p,16,48,48,64);
    }
    private static void opaque(int[] p,int x0,int y0,int x1,int y1){for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++)p[y*64+x]|=0xFF000000;}
}
