package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Officer model inventory and uploaded-asset catalogue. Both lists use wheel scrolling. */
public final class MpsqModelsScreen extends Screen {
    private static final int TOP=66,BOTTOM=48,ROW=24;
    private final Screen parent;
    private JsonArray assets=new JsonArray(),placed=new JsonArray(),npcs=new JsonArray();
    private int tab,scroll;private String status="Lade Modelle …",previewUrl="";private boolean requested,draggingScrollbar;
    public MpsqModelsScreen(Screen parent){super(Component.literal("Models"));this.parent=parent;}
    @Override protected void init(){if(requested)return;requested=true;
        MpsqApiClient.get("/models/catalog").whenComplete((d,e)->minecraft.execute(()->{if(e==null&&d.isJsonArray()){assets=d.getAsJsonArray();ensureBuiltinWumpus();status="";if(MpsqActionSync.server().isBlank())resolveLocalNpcUrls();}else{ensureBuiltinWumpus();status="Modellkatalog konnte nicht geladen werden.";}clamp();}));
        if(MpsqActionSync.server().isBlank()){
            placed=activeFurniture(MpsqLocalObjectStore.loadWorld(MpsqActionSync.world()));
            npcs=MpsqLocalNpcStore.loadWorld(MpsqActionSync.world());
            status="Einzelspieler-Möbel werden nur lokal gespeichert.";
            return;
        }
        String scope="?server="+java.net.URLEncoder.encode(MpsqActionSync.server(),java.nio.charset.StandardCharsets.UTF_8)+"&world="+java.net.URLEncoder.encode(MpsqActionSync.world(),java.nio.charset.StandardCharsets.UTF_8);
        MpsqApiClient.get("/objects"+scope).whenComplete((d,e)->minecraft.execute(()->{if(e==null&&d.isJsonArray())placed=activeFurniture(d.getAsJsonArray());else if(status.isBlank())status="Platzierte Möbel konnten nicht geladen werden.";clamp();}));
        MpsqApiClient.get("/npcs"+scope).whenComplete((d,e)->minecraft.execute(()->{if(e==null&&d.isJsonArray())npcs=currentWorld(d.getAsJsonArray());clamp();}));
    }
    private void resolveLocalNpcUrls(){for(JsonElement e:npcs){JsonObject npc=e.getAsJsonObject();String id=str(npc,"asset_id","");for(JsonElement a:assets){JsonObject asset=a.getAsJsonObject();if(id.equals(str(asset,"id",""))){if(asset.has("url"))npc.add("url",asset.get("url").deepCopy());npc.addProperty("name",str(asset,"name",id));break;}}}}
    private void ensureBuiltinWumpus(){ensureBuiltin("mpsq_wumpus","npc_model","Wumpus","builtin://mpsq/wumpus");for(var item:MpsqBuiltinFurnitureCatalog.all())ensureBuiltin(item.id(),"furniture",item.name(),item.url());}
    private void ensureBuiltin(String id,String category,String name,String url){for(JsonElement e:assets)if(id.equals(str(e.getAsJsonObject(),"id","")))return;JsonObject item=new JsonObject();item.addProperty("id",id);item.addProperty("kind","model");item.addProperty("category",category);item.addProperty("display_name",name);item.addProperty("filename",name);item.addProperty("url",url);assets.add(item);}
    @Override public void extractBackground(GuiGraphicsExtractor c,int x,int y,float d){super.extractBackground(c,x,y,d);MpsqTheme.drawBackground(c,width,height);}
    @Override public void extractRenderState(GuiGraphicsExtractor c,int mx,int my,float delta){super.extractRenderState(c,mx,my,delta);int center=width/2;
        c.centeredText(font,title,center,22,MpsqTheme.TEXT_TITEL);c.fill(center-160,43,center+160,44,0x66FFFFFF);
        c.fill(center-154,49,center-3,65,tab==0?0xAA9C203C:0x55000000);c.fill(center+3,49,center+154,65,tab==1?0xAA9C203C:0x55000000);
        c.centeredText(font,"Platziert",center-78,53,0xFFFFFFFF);c.centeredText(font,"Katalog",center+78,53,0xFFFFFFFF);
        c.enableScissor(center-158,TOP,center+158,height-BOTTOM);int y=TOP-scroll;
        if(tab==0){y=section(c,center,y,"Möbel",placed,"model_id","name");y+=11;y=section(c,center,y,"Spieler-NPCs",filterPlaced("npc_skin_normal"),"asset_id","name");y+=11;section(c,center,y,"NPC Modelle",filterPlaced("npc_model"),"asset_id","name");}
        else {for(String cat:new String[]{"furniture","npc_model","npc_skin_normal"}){String label=label(cat);JsonArray rows=filter(cat);if(rows.size()==0)continue;y=heading(c,center,y,label);for(JsonElement el:rows){JsonObject o=el.getAsJsonObject();if(y+ROW>=TOP&&y<height-BOTTOM){c.fill(center-153,y,center+153,y+ROW-2,0xFF20242E);String name=str(o,"display_name",str(o,"name",str(o,"id",str(o,"filename","Asset"))));String assetId=str(o,"id",str(o,"asset_id","?"));c.text(font,font.plainSubstrByWidth(name+"  ["+assetId+"]",264),center-145,y+6,0xFFFFFFFF);drawPreview(c,o,cat,center+138,y+10,0.8f);}y+=ROW;}}}
        c.disableScissor();drawScrollbar(c,center+155,TOP,height-BOTTOM,contentHeight());c.centeredText(font,status,center,height-31,0xFFFFFFFF);c.text(font,Component.literal("Zurück"),center-18,height-19,0xFFFFFFFF);
    }
    private int section(GuiGraphicsExtractor c,int center,int y,String name,JsonArray rows,String idKey,String nameKey){y=heading(c,center,y,name);if(rows.size()==0){c.text(font,Component.literal(name.contains("Skin")?"Noch keinem NPC zugewiesen":"Noch nichts platziert"),center-145,y+4,0xFFAAAAAA);return y+ROW;}for(JsonElement e:rows){JsonObject r=e.getAsJsonObject();if(y+ROW>=TOP&&y<height-BOTTOM){c.fill(center-153,y,center+153,y+ROW-2,0x44000000);String title=str(r,nameKey,str(r,"display_name",""));if(title.isBlank()){String id=str(r,idKey,"");title=assetName(id);if(title.isBlank())title=id.isBlank()?"Unbekannt":id;}String place=r.has("x")?"  ("+r.get("x").getAsInt()+", "+r.get("y").getAsInt()+", "+r.get("z").getAsInt()+")":"";c.text(font,font.plainSubstrByWidth(title+place,286),center-145,y+6,0xFFFFFFFF);}y+=ROW;}return y;}
    private String assetName(String id){var builtin=MpsqBuiltinFurnitureCatalog.byId(id);if(builtin!=null)return builtin.name();for(JsonElement e:assets){JsonObject row=e.getAsJsonObject();if(id.equals(str(row,"id",str(row,"asset_id",""))))return str(row,"display_name",str(row,"name",str(row,"filename","")));}return "";}
    private void drawPreview(GuiGraphicsExtractor c,JsonObject row,String category,int x,int y,float size){category=str(row,"category",category);if(!row.has("url")||row.get("url").isJsonNull())return;String url=row.get("url").getAsString();if(category.startsWith("npc_skin"))MpsqAccessoryRenderer.drawGuiSkinPreview(c,url,x,y,size,category.endsWith("slim"));else MpsqAccessoryRenderer.drawGuiPreview(c,url,x,y,size);}
    private int heading(GuiGraphicsExtractor c,int center,int y,String name){if(y+18>=TOP&&y<height-BOTTOM){c.fill(center-153,y+15,center+153,y+16,0xFFB72B46);c.text(font,Component.literal(name),center-145,y+3,0xFFFF7777);}return y+21;}
    private JsonArray filter(String category){JsonArray out=new JsonArray();for(JsonElement e:assets){if(!e.isJsonObject())continue;JsonObject row=e.getAsJsonObject();if(!available(row))continue;if((category.equals("npc_skin_normal")?str(row,"category","").startsWith("npc_skin"):category.equals(str(row,"category",""))))out.add(e);}return out;}
    private static boolean available(JsonObject row){if(row.has("deleted")&&row.get("deleted").getAsBoolean())return false;if(row.has("is_deleted")&&row.get("is_deleted").getAsBoolean())return false;String status=str(row,"status","active");return !status.equalsIgnoreCase("deleted")&&!status.equalsIgnoreCase("removed")&&!status.equalsIgnoreCase("archived");}
    private JsonArray filterPlaced(String category){JsonArray out=new JsonArray();for(JsonElement e:npcs)if((category.equals("npc_skin_normal")?str(e.getAsJsonObject(),"category","").startsWith("npc_skin"):category.equals(str(e.getAsJsonObject(),"category",""))))out.add(e);return out;}
    private static JsonArray activeFurniture(JsonArray source){JsonArray out=new JsonArray();for(JsonElement e:source)if(e.isJsonObject()&&available(e.getAsJsonObject())&&inCurrentWorld(e.getAsJsonObject())&&!MpsqBuiltinFurnitureCatalog.isRetired(str(e.getAsJsonObject(),"model_id","")))out.add(e.deepCopy());return out;}
    private static boolean inCurrentWorld(JsonObject row){return MpsqActionSync.world().equals(str(row,"world_id",str(row,"world",MpsqActionSync.world())))&&MpsqActionSync.server().equals(str(row,"server_id",str(row,"server",MpsqActionSync.server())));}
    private static JsonArray currentWorld(JsonArray source){JsonArray out=new JsonArray();for(var e:source)if(e.isJsonObject()&&available(e.getAsJsonObject())&&inCurrentWorld(e.getAsJsonObject()))out.add(e);return out;}
    private int contentHeight(){if(tab==0){int rows=Math.max(1,placed.size())+Math.max(1,filterPlaced("npc_skin_normal").size())+Math.max(1,filterPlaced("npc_model").size());return 3*21+2*11+rows*ROW;}int total=0;for(String cat:new String[]{"furniture","npc_model","npc_skin_normal"}){int size=filter(cat).size();if(size>0)total+=21+size*ROW;}return total;}
    private int maxScroll(){return Math.max(0,contentHeight()-(height-TOP-BOTTOM));}
    private void clamp(){scroll=Math.max(0,Math.min(scroll,maxScroll()));}
    private void drawScrollbar(GuiGraphicsExtractor c,int x,int top,int bottom,int contentHeight){int visible=bottom-top;if(contentHeight<=visible)return;int thumb=Math.max(18,visible*visible/contentHeight),travel=visible-thumb,y=top+(maxScroll()==0?0:travel*scroll/maxScroll());c.fill(x,top,x+4,bottom,0x66000000);c.fill(x,y,x+4,y+thumb,draggingScrollbar?0xFFFF7777:0xFFBBBBBB);}
    private void scrollTo(double mouseY){int visible=height-TOP-BOTTOM,content=contentHeight(),thumb=Math.max(18,visible*visible/content),travel=Math.max(1,visible-thumb);scroll=(int)Math.round(Math.max(0,Math.min(travel,mouseY-TOP-thumb/2))*maxScroll()/travel);}
    private static String str(JsonObject o,String key,String fallback){return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():fallback;}
    private String label(String c){return switch(c){case "furniture"->"Möbel";case "npc_model"->"NPC-Modelle";case "npc_skin_normal"->"Spieler-NPCs";case "npc_skin_slim"->"NPC Slim Skin";default->"Accessoires";};}
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        double x = event.x(), y = event.y();
        int button = event.button();
if(button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT&&x>=width/2+153&&x<=width/2+162&&y>=TOP&&y<height-BOTTOM&&maxScroll()>0){draggingScrollbar=true;scrollTo(y);return true;}if(button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT&&y>=49&&y<66){tab=x<width/2?0:1;scroll=0;return true;}if(button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT&&tab==1&&x>=width/2-158&&x<=width/2+153&&y>=TOP&&y<height-BOTTOM){int center=width/2,rowY=TOP-scroll;for(String cat:new String[]{"furniture","npc_model","npc_skin_normal"}){JsonArray rows=filter(cat);if(rows.isEmpty())continue;rowY+=21;for(JsonElement e:rows){if(y>=rowY&&y<rowY+ROW){JsonObject item=e.getAsJsonObject();String id=str(item,"id",str(item,"asset_id",""));if(!id.isBlank()){minecraft.keyboardHandler.setClipboard(id);status="ID kopiert – im Möbelmenü einfügen: "+id;}return true;}rowY+=ROW;}}}if(button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT&&y>=height-25){onClose();return true;}return super.mouseClicked(event, doubled);}
    @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        double x = event.x(), y = event.y();
        int button = event.button();
if(draggingScrollbar&&button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT){scrollTo(y);return true;}return super.mouseDragged(event, dx,dy);}
    @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        double x = event.x(), y = event.y();
        int button = event.button();
if(button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT)draggingScrollbar=false;return super.mouseReleased(event);}
    @Override public boolean mouseScrolled(double x,double y,double h,double v){scroll=Math.max(0,Math.min(maxScroll(),scroll-(int)(v*ROW*2)));return true;}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}

