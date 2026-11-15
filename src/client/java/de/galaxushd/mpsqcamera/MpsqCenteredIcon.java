package de.galaxushd.mpsqcamera;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;
import java.util.*;

/** Centre the visible pixels, rather than the transparent canvas. Source assets stay unchanged. */
final class MpsqCenteredIcon {
 private record Bounds(int x,int y,int w,int h,int tw,int th){}
 private static final Map<Identifier,Optional<Bounds>> cache=new HashMap<>();
 static boolean draw(GuiGraphicsExtractor g,Identifier id,int x,int y,int size){
  var bounds=cache.computeIfAbsent(id,key->{try(var in=Minecraft.getInstance().getResourceManager().getResourceOrThrow(key).open();var image=NativeImage.read(in)){
   int minX=image.getWidth(),minY=image.getHeight(),maxX=-1,maxY=-1;
   for(int j=0;j<image.getHeight();j++)for(int i=0;i<image.getWidth();i++)if((image.getPixel(i,j)>>>24)>16){minX=Math.min(minX,i);maxX=Math.max(maxX,i);minY=Math.min(minY,j);maxY=Math.max(maxY,j);}
   return maxX<0?Optional.empty():Optional.of(new Bounds(minX,minY,maxX-minX+1,maxY-minY+1,image.getWidth(),image.getHeight()));
  }catch(Exception e){return Optional.empty();}});
  if(bounds.isEmpty())return false;var b=bounds.get();float fit=size/(float)Math.max(b.w,b.h);int w=Math.round(b.w*fit),h=Math.round(b.h*fit);
  g.blit(RenderPipelines.GUI_TEXTURED,id,x+(size-w)/2,y+(size-h)/2,b.x,b.y,w,h,b.w,b.h,b.tw,b.th);return true;
 }
 static void clear(){cache.clear();}
}
