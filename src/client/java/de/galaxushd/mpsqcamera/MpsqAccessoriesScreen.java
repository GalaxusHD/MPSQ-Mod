package de.galaxushd.mpsqcamera;
import com.google.gson.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class MpsqAccessoriesScreen extends Screen {
    private final Screen parent;
    private JsonArray entries=new JsonArray();
    private String status="";
    private int page;
    private boolean requested,pending;
    public MpsqAccessoriesScreen(Screen parent){super(Text.literal("Accessoires"));this.parent=parent;}
    @Override protected void init(){
        int perPage=Math.max(1,(height-140)/24),start=page*perPage;
        for(int i=start;i<Math.min(start+perPage,entries.size());i++){
            JsonObject row=entries.get(i).getAsJsonObject(),item=row.getAsJsonObject("mpsq_accessories");
            String name=item.get("display_name").getAsString();boolean equipped=row.get("equipped").getAsBoolean();
            addDrawableChild(ButtonWidget.builder(Text.literal((equipped?"✓ ":"")+name),b->equip(equipped?null:row.get("accessory_id").getAsString())).dimensions(width/2-120,60+(i-start)*24,240,20).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("←"),b->{if(page>0){page--;clearAndInit();}}).dimensions(width/2-120,height-72,30,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("→"),b->{if((page+1)*perPage<entries.size()){page++;clearAndInit();}}).dimensions(width/2+90,height-72,30,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Code einlösen"),b->client.setScreen(new MpsqRedeemScreen())).dimensions(width/2-80,height-72,160,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Zurück"),b->close()).dimensions(width/2-60,height-28,120,20).build());
        if(!requested){requested=true;reload();}
    }
    private void reload(){MpsqApiClient.get("/me/accessories").whenComplete((data,error)->client.execute(()->{if(error!=null){status="Accessoires konnten nicht geladen werden.";return;}entries=data.getAsJsonArray();status=entries.isEmpty()?"Noch keine Accessoires freigeschaltet.":"";clearAndInit();}));}
    private void equip(String id){
        if(pending)return;pending=true;JsonObject body=new JsonObject();if(id==null)body.add("id",JsonNull.INSTANCE);else body.addProperty("id",id);
        MpsqApiClient.post("/me/accessories/equip",body).whenComplete((data,error)->client.execute(()->{pending=false;if(error!=null){status="Auswahl konnte nicht gespeichert werden.";return;}MpsqAccessoryRenderer.refresh();reload();}));
    }
    @Override public void renderBackground(DrawContext c,int x,int y,float d){super.renderBackground(c,x,y,d);MpsqTheme.drawBackground(c,width,height);}
    @Override public void render(DrawContext c,int x,int y,float d){super.render(c,x,y,d);c.drawCenteredTextWithShadow(textRenderer,title,width/2,24,0xFFFFFFFF);c.fill(12,45,width-12,47,MpsqTheme.TEXT_GEDAEMPT);c.drawCenteredTextWithShadow(textRenderer,status,width/2,height-46,0xFFFFFFFF);}
    @Override public void close(){client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
