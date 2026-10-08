package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Two independently scrollable views: unlocked accessories and the full catalogue. */
public final class MpsqAccessoriesScreen extends Screen {
    private static final int TOP=64, BOTTOM=58, ROW=25;
    private final Screen parent;
    private JsonArray owned=new JsonArray(), catalog=new JsonArray();
    private int tab, scroll;
    private boolean draggingScrollbar;
    private String status="Lade Accessoires …";
    private boolean ownedLoaded, catalogLoaded, pending;
    private long points;
    public MpsqAccessoriesScreen(Screen parent){this(parent,false);}
    public MpsqAccessoriesScreen(Screen parent,boolean shopMode){super(Text.literal("Accessoires"));this.parent=parent;this.tab=shopMode?1:0;}
    private boolean localWorld(){return MpsqActionSync.server().isBlank()&&MpsqLocalWorldStore.available();}

    @Override protected void init(){
        if(localWorld()){points=MpsqLocalWorldStore.points();}
        if(!localWorld())MpsqApiClient.get("/me/points").whenComplete((d,e)->client.execute(()->{if(e==null&&d.isJsonObject()&&d.getAsJsonObject().has("points")){points=d.getAsJsonObject().get("points").getAsLong();}else points=-1;}));
        if(localWorld()){owned=localOwned();ownedLoaded=true;}
        else if(!ownedLoaded){ownedLoaded=true;MpsqApiClient.get("/me/accessories").whenComplete((d,e)->client.execute(()->{if(e==null&&d.isJsonArray()){owned=withoutLegacyDiscordHead(d.getAsJsonArray());status="";}else status="Besitzliste konnte nicht geladen werden.";clamp();}));}
        if(localWorld()){catalog=withoutLegacyDiscordHead(MpsqLocalWorldStore.array("accessory_catalog"));catalogLoaded=!catalog.isEmpty();}
        if(!catalogLoaded&&MpsqApiClient.isReady()){catalogLoaded=true;MpsqApiClient.get("/accessory-catalog").whenComplete((d,e)->client.execute(()->{if(e==null&&d.isJsonArray()){if(localWorld())MpsqLocalWorldStore.setArray("accessory_catalog",d.getAsJsonArray());catalog=withoutLegacyDiscordHead(d.getAsJsonArray());if(status.startsWith("Lade"))status="";if(localWorld())owned=localOwned();}else{catalogLoaded=false;if(status.startsWith("Lade"))status="Katalog konnte nicht geladen werden.";}clamp();}));}
    }
    private JsonArray localOwned(){JsonArray out=new JsonArray();String equipped=MpsqLocalWorldStore.equipped();JsonArray allCatalog=MpsqLocalWorldStore.array("accessory_catalog");for(JsonElement e:MpsqLocalWorldStore.array("accessories_owned")){JsonObject row=e.isJsonObject()?e.getAsJsonObject().deepCopy():new JsonObject();String id=e.isJsonObject()?str(row,"accessory_id",""):e.getAsString();if(id.isBlank()||"discord_hat".equalsIgnoreCase(id))continue;row.addProperty("accessory_id",id);row.addProperty("equipped",id.equals(equipped));for(JsonElement c:allCatalog){if(!c.isJsonObject())continue;JsonObject def=c.getAsJsonObject();if(id.equals(str(def,"accessory_id",""))){for(var entry:def.entrySet())if(!row.has(entry.getKey()))row.add(entry.getKey(),entry.getValue().deepCopy());break;}}if(!isLegacyDiscordHead(row))out.add(row);}return out;}
    private JsonArray rows(){return tab==0?owned:catalog;}
    private static JsonArray withoutLegacyDiscordHead(JsonArray input){JsonArray filtered=new JsonArray();for(JsonElement value:input)if(value.isJsonObject()&&!isLegacyDiscordHead(value.getAsJsonObject()))filtered.add(value.deepCopy());return filtered;}
    private static boolean isLegacyDiscordHead(JsonObject row){JsonObject def=row.has("mpsq_accessories")&&row.get("mpsq_accessories").isJsonObject()?row.getAsJsonObject("mpsq_accessories"):row;for(JsonObject source:new JsonObject[]{row,def})for(String key:new String[]{"filename","asset_id","model_id","accessory_key","accessory_id","id","display_name"}){String value=str(source,key,"").replace('\\','/').toLowerCase(java.util.Locale.ROOT);int slash=value.lastIndexOf('/');if(slash>=0)value=value.substring(slash+1);if(value.equals("discord_hat")||value.equals("discord_hat.json")||value.equals("discord_hat.bbmodel")||value.equals("discord head")||value.equals("discord hat"))return true;}return false;}
    private void clamp(){scroll=Math.max(0,Math.min(scroll,Math.max(0,rows().size()*ROW-(height-TOP-BOTTOM))));}
    @Override public void renderBackground(DrawContext c,int x,int y,float d){super.renderBackground(c,x,y,d);MpsqTheme.drawBackground(c,width,height);}
    @Override public void render(DrawContext c,int mx,int my,float delta){
        super.render(c,mx,my,delta);int center=width/2;
        c.drawCenteredTextWithShadow(textRenderer,title,center,22,MpsqTheme.TEXT_TITEL);
        c.drawTextWithShadow(textRenderer,Text.literal("MPSQ-Punkte: "+(points<0?"–":points)),center+82,27,0xFFFFA0AA);
        c.fill(center-154,42,center+154,43,0x66FFFFFF);
        c.fill(center-148,48,center-3,65,tab==0?0xAA9C203C:0x55000000);c.fill(center+3,48,center+148,65,tab==1?0xAA9C203C:0x55000000);
        c.drawCenteredTextWithShadow(textRenderer,"Meine Accessoires",center-75,52,0xFFFFFFFF);c.drawCenteredTextWithShadow(textRenderer,"Alle Accessoires",center+75,52,0xFFFFFFFF);
        int bottom=height-BOTTOM;c.enableScissor(center-150,TOP,center+150,bottom);
        JsonArray list=rows();for(int i=0;i<list.size();i++){int yy=TOP+i*ROW-scroll;if(yy+ROW<TOP||yy>bottom)continue;JsonObject r=list.get(i).getAsJsonObject();JsonObject def=r.has("mpsq_accessories")&&r.get("mpsq_accessories").isJsonObject()?r.getAsJsonObject("mpsq_accessories"):r;String name=str(r,"display_name",str(def,"display_name",str(r,"id",str(def,"accessory_key","Unbenannt"))));boolean equipped=r.has("equipped")&&r.get("equipped").getAsBoolean();
            c.fill(center-146,yy+1,center+146,yy+ROW-2,(i%2==0)?0x55000000:0x33000000);
            String label=(equipped?"✓ ":"")+name;if(tab==1&&staff()&&r.has("id"))label+="  ["+r.get("id").getAsString()+"]";
            if(tab==1){int price=r.has("price_points")?r.get("price_points").getAsInt():500;boolean has=r.has("owned")&&r.get("owned").getAsBoolean()||localWorld()&&MpsqLocalWorldStore.owns(str(r,"accessory_id",""));label+=(has?"  · Besitzt du":"  · "+price+" Punkte");}
            c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(label,width/2+82),center-137,yy+7,equipped?0xFFFF7777:0xFFFFFFFF);
            String preview=previewUrl(r);if(preview!=null&&!preview.isBlank())MpsqAccessoryRenderer.drawGuiPreview(c,preview,center+126,yy+11,1.35f);
        }c.disableScissor();drawScrollbar(c,center+151,TOP,bottom,list.size()*ROW);
        c.drawCenteredTextWithShadow(textRenderer,status,center,height-47,0xFFFFFFFF);
        if(list.size()*ROW>height-TOP-BOTTOM)c.drawCenteredTextWithShadow(textRenderer,"Mausrad zum Scrollen",center,height-34,0xFFBBBBBB);
        c.drawTextWithShadow(textRenderer,Text.literal("Zurück"),center-142,height-19,0xFFFFFFFF);
    }
    private boolean staff(){return TeamStateStore.self().map(p->p.permissionRank().level()>=TeamRank.OFFICER.level()).orElse(false);}
    private void drawScrollbar(DrawContext c,int x,int top,int bottom,int contentHeight){int visible=bottom-top;if(contentHeight<=visible)return;int thumb=Math.max(18,visible*visible/contentHeight),travel=visible-thumb,max=maxScroll(),y=top+(max==0?0:travel*scroll/max);c.fill(x,top,x+4,bottom,0x66000000);c.fill(x,y,x+4,y+thumb,draggingScrollbar?0xFFFF7777:0xFFBBBBBB);}
    private int maxScroll(){return Math.max(0,rows().size()*ROW-(height-TOP-BOTTOM));}
    private void scrollTo(double mouseY){int visible=height-TOP-BOTTOM,content=rows().size()*ROW,thumb=Math.max(18,visible*visible/content),travel=Math.max(1,visible-thumb);scroll=(int)Math.round(Math.max(0,Math.min(travel,mouseY-TOP-thumb/2))*maxScroll()/travel);clamp();}
    private static String str(JsonObject o,String k,String fallback){return o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsString():fallback;}
    private static String previewUrl(JsonObject row){String builtin=MpsqAccessoryRenderer.resolveBuiltinAccessory(row);return builtin!=null?builtin:str(row,"url","");}
    @Override public boolean mouseClicked(double x,double y,int button){int c=width/2;if(button==0){if(x>=c+149&&x<=c+158&&y>=TOP&&y<height-BOTTOM&&maxScroll()>0){draggingScrollbar=true;scrollTo(y);return true;}if(y>=48&&y<66){if(x>=c-150&&x<c){tab=0;scroll=0;return true;}if(x>=c&&x<=c+150){tab=1;scroll=0;return true;}}}
        if((button==0||button==1)&&y>=TOP&&y<height-BOTTOM&&x>=c-150&&x<=c+150){int index=(int)(y-TOP+scroll)/ROW;JsonArray list=rows();if(index>=0&&index<list.size()){JsonObject row=list.get(index).getAsJsonObject();String preview=previewUrl(row);if(button==1&&tab==1&&preview!=null&&!preview.isBlank()){MpsqAccessoryRenderer.tryOn(preview);client.setScreen(null);return true;}if(button==0&&tab==0&&row.has("accessory_id"))equip(row.get("equipped").getAsBoolean()?null:row.get("accessory_id").getAsString());else if(button==0&&tab==1)buy(row);return true;}}
        if(button==0&&y>=height-25&&x<c){close();return true;}
        return super.mouseClicked(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(draggingScrollbar&&button==0){scrollTo(y);return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){if(button==0)draggingScrollbar=false;return super.mouseReleased(x,y,button);}
    private void equip(String id){if(pending)return;if(localWorld()){boolean ok=MpsqLocalWorldStore.equip(id);pending=false;status=ok?"Auswahl in dieser Welt gespeichert.":"Accessoire konnte nicht ausgewählt werden.";owned=localOwned();MpsqAccessoryRenderer.refresh();return;}pending=true;JsonObject b=new JsonObject();if(id==null)b.add("id",JsonNull.INSTANCE);else b.addProperty("id",id);MpsqApiClient.post("/me/accessories/equip",b).whenComplete((d,e)->client.execute(()->{pending=false;status=e==null?"Auswahl gespeichert.":"Accessoire konnte nicht ausgewählt werden.";MpsqAccessoryRenderer.refresh();ownedLoaded=false;clearAndInit();}));}
    private void buy(JsonObject row){if(pending||!row.has("accessory_id")||row.get("accessory_id").isJsonNull())return;String id=row.get("accessory_id").getAsString();if(row.has("owned")&&row.get("owned").getAsBoolean()||localWorld()&&MpsqLocalWorldStore.owns(id)){status="Dieses Accessoire besitzt du bereits.";return;}if(localWorld()){int price=row.has("price_points")?row.get("price_points").getAsInt():500;if(!MpsqLocalWorldStore.spendPoints(price)){status="Nicht genug lokale Punkte (Preis: "+price+").";return;}if(!MpsqLocalWorldStore.ownAccessory(id)){MpsqLocalWorldStore.addPoints(price);status="Kauf konnte nicht lokal gespeichert werden.";return;}points=MpsqLocalWorldStore.points();owned=localOwned();status="Accessoire für diese Welt freigeschaltet.";return;}pending=true;JsonObject body=new JsonObject();body.addProperty("accessoryId",id);status="Kauf wird verarbeitet …";MpsqApiClient.post("/me/accessories/buy",body).whenComplete((d,e)->client.execute(()->{pending=false;if(e==null){points=d.isJsonObject()&&d.getAsJsonObject().has("points")?d.getAsJsonObject().get("points").getAsLong():points;status="Accessoire gekauft und freigeschaltet.";ownedLoaded=false;catalogLoaded=false;}else status="Kauf fehlgeschlagen: "+e.getMessage();clearAndInit();}));}
    @Override public boolean mouseScrolled(double x,double y,double h,double v){scroll-=(int)Math.signum(v)*ROW*3;clamp();return true;}
    @Override public void close(){client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
