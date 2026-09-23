package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Read-only permanent rank history, authorized again by the API. */
public final class TeamLogsScreen extends Screen {
    private final Screen parent;
    private final List<JsonObject> rows = new ArrayList<>();
    private int page, scroll;
    private boolean loading;
    private String status = "";
    public TeamLogsScreen(Screen parent) { super(Text.literal("Logs")); this.parent=parent; }
    @Override protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("Zurück"), b->close()).dimensions(width/2-60,height-28,120,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("←"), b->{if(page>0&&!loading){page--;load();}}).dimensions(12,height-28,30,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("→"), b->{if(!loading){page++;load();}}).dimensions(width-42,height-28,30,20).build());
        load();
    }
    private void load() {
        if(loading)return;
        loading=true; status="Wird geladen…";
        MpsqApiClient.get("/team/rank-log?offset="+(page*100)).whenComplete((data,error)->client.execute(()->{
            loading=false; rows.clear(); scroll=0;
            if(error!=null){status="Logs konnten nicht geladen werden.";return;}
            for(JsonElement entry:data.getAsJsonArray())rows.add(entry.getAsJsonObject());
            status=rows.isEmpty()?"Keine Einträge auf dieser Seite.":"";
        }));
    }
    private static String text(JsonObject row,String key){return row.has(key)&&!row.get(key).isJsonNull()?row.get(key).getAsString():"?";}
    @Override public void renderBackground(DrawContext c,int x,int y,float d){super.renderBackground(c,x,y,d);MpsqTheme.drawBackground(c,width,height);}
    @Override public void render(DrawContext c,int x,int y,float d){
        super.render(c,x,y,d);
        c.drawCenteredTextWithShadow(textRenderer,title,width/2,24,MpsqTheme.TEXT_TITEL);
        c.fill(12,45,width-12,47,MpsqTheme.TEXT_GEDAEMPT);
        c.enableScissor(12,52,width-12,height-36);
        int rowY=56-scroll;
        for(JsonObject row:rows){
            String date=text(row,"created_at");
            try{date=DateTimeFormatter.ofPattern("dd.MM.yy HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(date));}catch(Exception ignored){}
            c.drawTextWithShadow(textRenderer,date+" | "+text(row,"target_name"),16,rowY,MpsqTheme.TEXT_NORMAL);
            TeamRank old=TeamRank.fromId(text(row,"old_base_rank")), next=TeamRank.fromId(text(row,"new_base_rank"));
            old.draw(c,16,rowY+13,10);
            int arrow=22+old.widthForHeight(10);
            c.drawTextWithShadow(textRenderer,"→",arrow,rowY+14,MpsqTheme.TEXT_NORMAL);
            next.draw(c,arrow+16,rowY+13,10);
            c.drawTextWithShadow(textRenderer,"Zuständiger Moderator: "+text(row,"actor_name"),16,rowY+28,MpsqTheme.TEXT_GEDAEMPT);
            if(row.has("actor_rank")&&!row.get("actor_rank").isJsonNull()){
                int rankX=22+textRenderer.getWidth("Zuständiger Moderator: "+text(row,"actor_name"));
                TeamRank.fromId(text(row,"actor_rank")).draw(c,rankX,rowY+27,9);
            }
            rowY+=46;
        }
        c.disableScissor();
        if(!status.isEmpty())c.drawCenteredTextWithShadow(textRenderer,status,width/2,55,MpsqTheme.TEXT_NORMAL);
    }
    @Override public boolean mouseScrolled(double x,double y,double h,double v){scroll=Math.max(0,Math.min(Math.max(0,rows.size()*46-(height-92)),scroll-(int)(v*23)));return true;}
    @Override public void close(){client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
