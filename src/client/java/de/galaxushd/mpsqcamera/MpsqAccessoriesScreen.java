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
    public MpsqAccessoriesScreen(Screen parent){super(Text.literal("Accessoires"));this.parent=parent;}

    @Override protected void init(){
        if(!ownedLoaded){ownedLoaded=true;MpsqApiClient.get("/me/accessories").whenComplete((d,e)->client.execute(()->{if(e==null&&d.isJsonArray()){owned=d.getAsJsonArray();status="";}else status="Besitzliste konnte nicht geladen werden.";clamp();}));}
        if(!catalogLoaded){catalogLoaded=true;MpsqApiClient.get("/accessory-catalog").whenComplete((d,e)->client.execute(()->{if(e==null&&d.isJsonArray()){catalog=d.getAsJsonArray();if(status.startsWith("Lade"))status="";}else status="Katalog konnte nicht geladen werden.";clamp();}));}
    }
    private JsonArray rows(){return tab==0?owned:catalog;}
    private void clamp(){scroll=Math.max(0,Math.min(scroll,Math.max(0,rows().size()*ROW-(height-TOP-BOTTOM))));}
    @Override public void renderBackground(DrawContext c,int x,int y,float d){super.renderBackground(c,x,y,d);MpsqTheme.drawBackground(c,width,height);}
    @Override public void render(DrawContext c,int mx,int my,float delta){
        super.render(c,mx,my,delta);int center=width/2;
        c.drawCenteredTextWithShadow(textRenderer,title,center,22,MpsqTheme.TEXT_TITEL);
        c.fill(center-154,42,center+154,43,0x66FFFFFF);
        c.fill(center-148,48,center-3,65,tab==0?0xAA9C203C:0x55000000);c.fill(center+3,48,center+148,65,tab==1?0xAA9C203C:0x55000000);
        c.drawCenteredTextWithShadow(textRenderer,"Meine Accessoires",center-75,52,0xFFFFFFFF);c.drawCenteredTextWithShadow(textRenderer,"Alle Accessoires",center+75,52,0xFFFFFFFF);
        int bottom=height-BOTTOM;c.enableScissor(center-150,TOP,center+150,bottom);
        JsonArray list=rows();for(int i=0;i<list.size();i++){int yy=TOP+i*ROW-scroll;if(yy+ROW<TOP||yy>bottom)continue;JsonObject r=list.get(i).getAsJsonObject();JsonObject def=r.has("mpsq_accessories")&&r.get("mpsq_accessories").isJsonObject()?r.getAsJsonObject("mpsq_accessories"):r;String name=str(r,"display_name",str(def,"display_name",str(r,"id",str(def,"accessory_key","Unbenannt"))));boolean equipped=r.has("equipped")&&r.get("equipped").getAsBoolean();
            c.fill(center-146,yy+1,center+146,yy+ROW-2,(i%2==0)?0x55000000:0x33000000);
            String label=(equipped?"✓ ":"")+name;if(tab==1&&staff()&&r.has("id"))label+="  ["+r.get("id").getAsString()+"]";
            c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(label,width/2+128),center-137,yy+7,equipped?0xFFFF7777:0xFFFFFFFF);
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
    @Override public boolean mouseClicked(double x,double y,int button){if(button==0){int c=width/2;if(x>=c+149&&x<=c+158&&y>=TOP&&y<height-BOTTOM&&maxScroll()>0){draggingScrollbar=true;scrollTo(y);return true;}if(y>=48&&y<66){if(x>=c-150&&x<c){tab=0;scroll=0;return true;}if(x>=c&&x<=c+150){tab=1;scroll=0;return true;}}
        if(y>=TOP&&y<height-BOTTOM&&x>=c-150&&x<=c+150){int index=(int)(y-TOP+scroll)/ROW;JsonArray list=rows();if(index>=0&&index<list.size()){JsonObject row=list.get(index).getAsJsonObject();if(tab==0&&row.has("accessory_id"))equip(row.get("equipped").getAsBoolean()?null:row.get("accessory_id").getAsString());return true;}}
        if(y>=height-25&&x<c){close();return true;}}
        return super.mouseClicked(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(draggingScrollbar&&button==0){scrollTo(y);return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){if(button==0)draggingScrollbar=false;return super.mouseReleased(x,y,button);}
    private void equip(String id){if(pending)return;pending=true;JsonObject b=new JsonObject();if(id==null)b.add("id",JsonNull.INSTANCE);else b.addProperty("id",id);MpsqApiClient.post("/me/accessories/equip",b).whenComplete((d,e)->client.execute(()->{pending=false;status=e==null?"Auswahl gespeichert.":"Accessoire konnte nicht ausgewählt werden.";MpsqAccessoryRenderer.refresh();ownedLoaded=false;clearAndInit();}));}
    @Override public boolean mouseScrolled(double x,double y,double h,double v){scroll-=(int)Math.signum(v)*ROW*3;clamp();return true;}
    @Override public void close(){client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
