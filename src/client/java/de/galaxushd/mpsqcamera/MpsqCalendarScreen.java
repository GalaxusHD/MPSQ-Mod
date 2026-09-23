package de.galaxushd.mpsqcamera;
import com.google.gson.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;

public final class MpsqCalendarScreen extends Screen {
    private final Screen parent;
    private JsonArray rows=new JsonArray();
    private TextFieldWidget titleInput,dateInput;
    private String status="";
    private boolean requested,pending;
    private int page;
    private static final DateTimeFormatter FORMAT=DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm").withResolverStyle(java.time.format.ResolverStyle.STRICT);
    public MpsqCalendarScreen(Screen parent){super(Text.literal("Teamkalender"));this.parent=parent;}
    private boolean canEdit(){return TeamStateStore.self().map(p->p.permissionRank().level()>=TeamRank.OFFICER.level()).orElse(false);}
    private int count(){return Math.max(1,(height-180)/28);}
    @Override protected void init(){
        int n=count(),start=page*n;
        if(canEdit()){
            for(int i=start;i<Math.min(start+n,rows.size());i++){String id=rows.get(i).getAsJsonObject().get("id").getAsString();addDrawableChild(ButtonWidget.builder(Text.literal("×"),b->remove(id)).dimensions(width-38,58+(i-start)*28,22,20).build());}
            titleInput=addDrawableChild(new TextFieldWidget(textRenderer,16,height-104,Math.max(100,width/2-24),20,Text.literal("Titel")));titleInput.setMaxLength(120);titleInput.setPlaceholder(Text.literal("Titel"));
            dateInput=addDrawableChild(new TextFieldWidget(textRenderer,width/2,height-104,width/2-16,20,Text.literal("Datum")));dateInput.setText(LocalDateTime.now().plusDays(1).withSecond(0).format(FORMAT));
            addDrawableChild(ButtonWidget.builder(Text.literal("Termin speichern"),b->save()).dimensions(width/2-80,height-78,160,20).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("←"),b->{if(page>0){page--;clearAndInit();}}).dimensions(16,height-28,28,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("→"),b->{if((page+1)*count()<rows.size()){page++;clearAndInit();}}).dimensions(width-44,height-28,28,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Zurück"),b->close()).dimensions(width/2-60,height-28,120,20).build());
        if(!requested){requested=true;reload();}
    }
    private void reload(){MpsqApiClient.get("/calendar").whenComplete((data,error)->client.execute(()->{status=error==null?"":"Kalender konnte nicht geladen werden.";if(error==null){rows=data.getAsJsonArray();page=Math.min(page,Math.max(0,(rows.size()-1)/count()));clearAndInit();}}));}
    private void save(){
        if(pending)return;
        JsonObject body=new JsonObject();
        try{if(titleInput.getText().isBlank())throw new IllegalArgumentException();body.addProperty("startsAt",LocalDateTime.parse(dateInput.getText(),FORMAT).atZone(ZoneId.systemDefault()).toInstant().toString());}
        catch(Exception e){status="Titel und Datum: TT.MM.JJJJ HH:mm";return;}
        body.addProperty("title",titleInput.getText());pending=true;
        MpsqApiClient.post("/calendar",body).whenComplete((data,error)->client.execute(()->{pending=false;if(error==null)reload();else status="Speichern fehlgeschlagen.";}));
    }
    private void remove(String id){if(pending)return;pending=true;MpsqApiClient.delete("/calendar/"+id).whenComplete((data,error)->client.execute(()->{pending=false;if(error==null)reload();else status="Löschen fehlgeschlagen.";}));}
    @Override public void renderBackground(DrawContext c,int x,int y,float d){super.renderBackground(c,x,y,d);MpsqTheme.drawBackground(c,width,height);}
    @Override public void render(DrawContext c,int x,int y,float d){
        super.render(c,x,y,d);c.drawCenteredTextWithShadow(textRenderer,title,width/2,24,0xFFFFFFFF);c.fill(12,45,width-12,47,MpsqTheme.TEXT_GEDAEMPT);
        int start=page*count();
        for(int i=start;i<Math.min(start+count(),rows.size());i++){var row=rows.get(i).getAsJsonObject();String date=FORMAT.withZone(ZoneId.systemDefault()).format(Instant.parse(row.get("starts_at").getAsString()));c.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(date+" | "+row.get("title").getAsString(),width-70),16,64+(i-start)*28,0xFFFFFFFF);}
        c.drawCenteredTextWithShadow(textRenderer,status,width/2,height-47,0xFFFFFFFF);
    }
    @Override public void close(){client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
