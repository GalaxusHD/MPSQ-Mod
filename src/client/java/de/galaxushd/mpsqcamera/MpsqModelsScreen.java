package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Officer model inventory and uploaded-asset catalogue. Both lists use wheel scrolling. */
public final class MpsqModelsScreen extends Screen {
    private static final int TOP=66,BOTTOM=48,ROW=24;
    private final Screen parent;
    private JsonArray assets=new JsonArray(),placed=new JsonArray(),npcs=new JsonArray();
    private int tab,scroll;private String status="Lade Modelle …",previewUrl="";private boolean requested,draggingScrollbar;
    public MpsqModelsScreen(Screen parent){super(Text.literal("Models"));this.parent=parent;}
    @Override protected void init(){if(requested)return;requested=true;
        MpsqApiClient.get("/models/catalog").whenComplete((d,e)->client.execute(()->{if(e==null&&d.isJsonArray()){assets=d.getAsJsonArray();status="";if(MpsqActionSync.server().isBlank())resolveLocalNpcUrls();}else status="Modellkatalog konnte nicht geladen werden.";clamp();}));
        if(MpsqActionSync.server().isBlank()){
            placed=MpsqLocalObjectStore.loadWorld(MpsqActionSync.world());
            npcs=MpsqLocalNpcStore.loadWorld(MpsqActionSync.world());
            status="Einzelspieler-Möbel werden nur lokal gespeichert.";
            return;
        }
        String scope="?server="+java.net.URLEncoder.encode(MpsqActionSync.server(),java.nio.charset.StandardCharsets.UTF_8)+"&world="+java.net.URLEncoder.encode(MpsqActionSync.world(),java.nio.charset.StandardCharsets.UTF_8);
        MpsqApiClient.get("/objects"+scope).whenComplete((d,e)->client.execute(()->{if(e==null&&d.isJsonArray())placed=d.getAsJsonArray();else if(status.isBlank())status="Platzierte Möbel konnten nicht geladen werden.";clamp();}));
        MpsqApiClient.get("/npcs"+scope).whenComplete((d,e)->client.execute(()->{if(e==null&&d.isJsonArray())npcs=d.getAsJsonArray();clamp();}));
    }
    private void resolveLocalNpcUrls(){for(JsonElement e:npcs){JsonObject npc=e.getAsJsonObject();String id=str(npc,"asset_id","");for(JsonElement a:assets){JsonObject asset=a.getAsJsonObject();if(id.equals(str(asset,"id",""))){if(asset.has("url"))npc.add("url",asset.get("url").deepCopy());npc.addProperty("name",str(asset,"name",id));break;}}}}
    @Override public void renderBackground(DrawContext c,int x,int y,float d){super.renderBackground(c,x,y,d);MpsqTheme.drawBackground(c,width,height);}
    @Override public void render(DrawContext c,int mx,int my,float delta){super.render(c,mx,my,delta);int center=width/2;
        c.drawCenteredTextWithShadow(textRenderer,title,center,22,MpsqTheme.TEXT_TITEL);c.fill(center-160,43,center+160,44,0x66FFFFFF);
        c.fill(center-154,49,center-3,65,tab==0?0xAA9C203C:0x55000000);c.fill(center+3,49,center+154,65,tab==1?0xAA9C203C:0x55000000);
        c.drawCenteredTextWithShadow(textRenderer,"Platziert",center-78,53,0xFFFFFFFF);c.drawCenteredTextWithShadow(textRenderer,"Katalog",center+78,53,0xFFFFFFFF);
        c.enableScissor(center-158,TOP,center+158,height-BOTTOM);int y=TOP-scroll;
        if(tab==0){y=section(c,center,y,"Möbel",placed,"model_id","name");y+=11;y=section(c,center,y,"NPC Modell",filterPlaced("npc_model"),"asset_id","name");y+=11;y=section(c,center,y,"NPC Normal Skin",filterPlaced("npc_skin_normal"),"asset_id","name");y+=11;section(c,center,y,"NPC Slim Skin",filterPlaced("npc_skin_slim"),"asset_id","name");}
        else {for(String cat:new String[]{"furniture","npc_model","npc_skin_normal","npc_skin_slim"}){String label=label(cat);JsonArray rows=filter(cat);if(rows.size()==0)continue;y=heading(c,center,y,label);for(JsonElement el:rows){JsonObject o=el.getAsJsonObject();if(y+ROW>=TOP&&y<height-BOTTOM){c.fill(center-153,y,center+185,y+ROW-2,0x44000000);String name=str(o,"display_name",str(o,"name",str(o,"id",str(o,"filename","Asset"))));String assetId=str(o,"id",str(o,"asset_id","?"));c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(name+"  ["+assetId+"]",184),center-145,y+6,0xFFFFFFFF);if(o.has("url")&&!o.get("url").isJsonNull())MpsqAccessoryRenderer.drawGuiPreview(c,o.get("url").getAsString(),center+166,y+11,1.35f);}y+=ROW;}}}
        c.disableScissor();drawScrollbar(c,center+155,TOP,height-BOTTOM,contentHeight());c.drawCenteredTextWithShadow(textRenderer,status,center,height-31,0xFFFFFFFF);c.drawTextWithShadow(textRenderer,Text.literal("Zurück"),center-18,height-19,0xFFFFFFFF);
    }
    private int section(DrawContext c,int center,int y,String name,JsonArray rows,String idKey,String nameKey){y=heading(c,center,y,name);if(rows.size()==0){c.drawTextWithShadow(textRenderer,Text.literal(name.contains("Skin")?"Noch keinem NPC zugewiesen":"Noch nichts platziert"),center-145,y+4,0xFFAAAAAA);return y+ROW;}for(JsonElement e:rows){JsonObject r=e.getAsJsonObject();if(y+ROW>=TOP&&y<height-BOTTOM){c.fill(center-153,y,center+153,y+ROW-2,0x44000000);String title=str(r,nameKey,str(r,"display_name",str(r,idKey,"Unbekannt")));String place=r.has("x")?"  ("+r.get("x").getAsInt()+", "+r.get("y").getAsInt()+", "+r.get("z").getAsInt()+")":"";c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(title+place,238),center-145,y+6,0xFFFFFFFF);if(r.has("url")&&!r.get("url").isJsonNull())MpsqAccessoryRenderer.drawGuiPreview(c,r.get("url").getAsString(),center+137,y+11,1.25f);}y+=ROW;}return y;}
    private int heading(DrawContext c,int center,int y,String name){if(y+18>=TOP&&y<height-BOTTOM){c.fill(center-153,y+15,center+153,y+16,0xFFB72B46);c.drawTextWithShadow(textRenderer,Text.literal(name),center-145,y+3,0xFFFF7777);}return y+21;}
    private JsonArray filter(String category){JsonArray out=new JsonArray();for(JsonElement e:assets)if(category.equals(str(e.getAsJsonObject(),"category","")))out.add(e);return out;}
    private JsonArray filterPlaced(String category){JsonArray out=new JsonArray();for(JsonElement e:npcs)if(category.equals(str(e.getAsJsonObject(),"category","")))out.add(e);return out;}
    private int contentHeight(){if(tab==0){int rows=Math.max(1,placed.size())+Math.max(1,filterPlaced("npc_model").size())+Math.max(1,filterPlaced("npc_skin_normal").size())+Math.max(1,filterPlaced("npc_skin_slim").size());return 4*21+3*11+rows*ROW;}int total=0;for(String cat:new String[]{"furniture","npc_model","npc_skin_normal","npc_skin_slim"}){int size=filter(cat).size();if(size>0)total+=21+size*ROW;}return total;}
    private int maxScroll(){return Math.max(0,contentHeight()-(height-TOP-BOTTOM));}
    private void clamp(){scroll=Math.max(0,Math.min(scroll,maxScroll()));}
    private void drawScrollbar(DrawContext c,int x,int top,int bottom,int contentHeight){int visible=bottom-top;if(contentHeight<=visible)return;int thumb=Math.max(18,visible*visible/contentHeight),travel=visible-thumb,y=top+(maxScroll()==0?0:travel*scroll/maxScroll());c.fill(x,top,x+4,bottom,0x66000000);c.fill(x,y,x+4,y+thumb,draggingScrollbar?0xFFFF7777:0xFFBBBBBB);}
    private void scrollTo(double mouseY){int visible=height-TOP-BOTTOM,content=contentHeight(),thumb=Math.max(18,visible*visible/content),travel=Math.max(1,visible-thumb);scroll=(int)Math.round(Math.max(0,Math.min(travel,mouseY-TOP-thumb/2))*maxScroll()/travel);}
    private static String str(JsonObject o,String key,String fallback){return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():fallback;}
    private String label(String c){return switch(c){case "furniture"->"Möbel";case "npc_model"->"NPC Modell";case "npc_skin_normal"->"NPC Normal Skin";case "npc_skin_slim"->"NPC Slim Skin";default->"Accessoires";};}
    @Override public boolean mouseClicked(double x,double y,int button){if(button==0&&x>=width/2+153&&x<=width/2+162&&y>=TOP&&y<height-BOTTOM&&maxScroll()>0){draggingScrollbar=true;scrollTo(y);return true;}if(button==0&&y>=49&&y<66){tab=x<width/2?0:1;scroll=0;return true;}if(button==0&&y>=height-25){close();return true;}return super.mouseClicked(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(draggingScrollbar&&button==0){scrollTo(y);return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){if(button==0)draggingScrollbar=false;return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double h,double v){scroll=Math.max(0,Math.min(maxScroll(),scroll-(int)(v*ROW*2)));return true;}
    @Override public void close(){client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
