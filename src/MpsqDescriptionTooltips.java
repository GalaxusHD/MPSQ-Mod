package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Optional, locally authored descriptions shared by the three collection menus. */
public final class MpsqDescriptionTooltips {
    private static final Path FILE=FabricLoader.getInstance().getConfigDir().resolve("mpsq-descriptions.json");
    private static JsonObject entries=new JsonObject();
    private static long next,modified=Long.MIN_VALUE;
    private static void reload(){
        long now=System.currentTimeMillis();if(now<next)return;next=now+1000;
        try{
            long stamp=Files.isRegularFile(FILE)?Files.getLastModifiedTime(FILE).toMillis():-1;
            if(stamp==modified)return;modified=stamp;
            if(stamp<0){entries=new JsonObject();return;}
            if(Files.size(FILE)>1048576)throw new IllegalArgumentException("Beschreibungen maximal 1 MB");
            var parsed=JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
            entries=parsed.has("entries")?parsed.getAsJsonObject("entries"):new JsonObject();
        }catch(Exception e){MpsqCameraClient.LOGGER.warn("Beschreibungen nicht geladen; letzte gültige Version bleibt erhalten",e);}
    }
    private static String str(JsonObject o,String key,String fallback){return o.has(key)&&o.get(key).isJsonPrimitive()?o.get(key).getAsString():fallback;}
    private static int color(String text,int fallback){try{return (int)(Long.parseLong(text.replace("#",""),16)|0xFF000000L);}catch(Exception e){return fallback;}}
    private static void image(GuiGraphicsExtractor g,String value,int x,int y,int w,int h){
        if(value.isBlank())return;try{var id=Identifier.parse(value);if(Minecraft.getInstance().getResourceManager().getResource(id).isPresent())g.blit(RenderPipelines.GUI_TEXTURED,id,x,y,0,0,w,h,w,h,w,h);}catch(Exception ignored){}
    }
    public static boolean draw(GuiGraphicsExtractor g,String category,String id,int mx,int my,int screenWidth,int screenHeight){
        reload();var entry=entries.get(category+":"+id);if(entry==null||!entry.isJsonObject())return false;
        try{
            var data=entry.getAsJsonObject();var font=Minecraft.getInstance().font;
            int width=Math.clamp(data.has("width")?data.get("width").getAsInt():240,120,480);
            var rows=new ArrayList<JsonObject>();String title=str(data,"title","");
            if(!title.isBlank()){var row=new JsonObject();row.addProperty("text",title);row.addProperty("color",str(data,"titleColor","#FFD45B"));rows.add(row);}
            if(data.has("lines"))for(var e:data.getAsJsonArray("lines")){
                if(rows.size()>=64)break;
                if(e.isJsonObject())rows.add(e.getAsJsonObject());else if(e.isJsonPrimitive()){var row=new JsonObject();row.addProperty("text",e.getAsString());rows.add(row);}
            }
            int height=16;for(var row:rows)height+=Math.max(str(row,"icon","").isBlank()?0:16,font.split(MpsqRichText.parse(str(row,"text","").substring(0,Math.min(2000,str(row,"text","").length()))),width-24-(str(row,"icon","").isBlank()?0:20)).size()*font.lineHeight)+4;
            float scale=Math.min(1f,Math.min((screenWidth-8f)/width,(screenHeight-8f)/height));
            int x=Math.max(4,Math.min(mx+12,screenWidth-4-Math.round(width*scale))),y=Math.max(4,Math.min(my+12,screenHeight-4-Math.round(height*scale)));
            g.pose().pushMatrix();
            try{
                g.pose().translate(x,y);g.pose().scale(scale);
                g.fill(0,0,width,height,color(str(data,"backgroundColor","#20242E"),0xFF20242E));
                image(g,str(data,"background",""),0,0,width,height);
                image(g,str(data,"frame",""),0,0,width,height);
                int lineY=8;
                for(var row:rows){String icon=str(row,"icon","");int indent=icon.isBlank()?0:20;if(indent>0)MpsqGuiIcon.draw(g,icon,10,lineY,16);
                    String text=str(row,"text","");var lines=font.split(MpsqRichText.parse(text.substring(0,Math.min(2000,text.length()))),width-24-indent);
                    for(int i=0;i<lines.size();i++)g.text(font,lines.get(i),12+indent,lineY+i*font.lineHeight,color(str(row,"color",str(data,"textColor","#FFFFFF")),0xFFFFFFFF));
                    lineY+=Math.max(indent>0?16:0,lines.size()*font.lineHeight)+4;
                }
            }finally{g.pose().popMatrix();}
            return true;
        }catch(Exception ignored){return false;}
    }
}
