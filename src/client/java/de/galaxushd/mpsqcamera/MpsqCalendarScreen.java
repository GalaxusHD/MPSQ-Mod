package de.galaxushd.mpsqcamera;
import com.google.gson.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;

public final class MpsqCalendarScreen extends Screen {
    private final Screen parent;
    private JsonArray rows=new JsonArray();
    private EditBox titleInput,dateInput;
    private String status="";
    private boolean requested,pending;
    private int page;
    private static final DateTimeFormatter FORMAT=DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm").withResolverStyle(java.time.format.ResolverStyle.STRICT);
    public MpsqCalendarScreen(Screen parent){super(Component.literal("Teamkalender"));this.parent=parent;}
    private boolean canEdit(){return TeamStateStore.self().map(p->p.permissionRank().level()>=TeamRank.OFFICER.level()).orElse(false);}
    private int count(){return Math.max(1,(height-180)/28);}
    @Override protected void init(){
        int n=count(),start=page*n;
        if(canEdit()){
            for(int i=start;i<Math.min(start+n,rows.size());i++){String id=rows.get(i).getAsJsonObject().get("id").getAsString();addRenderableWidget(Button.builder(Component.literal("×"),b->remove(id)).bounds(width-38,58+(i-start)*28,22,20).build());}
            titleInput=addRenderableWidget(new EditBox(font,16,height-104,Math.max(100,width/2-24),20,Component.literal("Titel")));titleInput.setMaxLength(120);titleInput.setHint(Component.literal("Titel"));
            dateInput=addRenderableWidget(new EditBox(font,width/2,height-104,width/2-16,20,Component.literal("Datum")));dateInput.setValue(LocalDateTime.now().plusDays(1).withSecond(0).format(FORMAT));
            addRenderableWidget(Button.builder(Component.literal("Termin speichern"),b->save()).bounds(width/2-80,height-78,160,20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("←"),b->{if(page>0){page--;rebuildWidgets();}}).bounds(16,height-28,28,20).build());
        addRenderableWidget(Button.builder(Component.literal("→"),b->{if((page+1)*count()<rows.size()){page++;rebuildWidgets();}}).bounds(width-44,height-28,28,20).build());
        addRenderableWidget(Button.builder(Component.literal("Zurück"),b->onClose()).bounds(width/2-60,height-28,120,20).build());
        if(!requested){requested=true;reload();}
    }
    private void reload(){MpsqApiClient.get("/calendar").whenComplete((data,error)->minecraft.execute(()->{status=error==null?"":"Kalender konnte nicht geladen werden.";if(error==null){rows=data.getAsJsonArray();page=Math.min(page,Math.max(0,(rows.size()-1)/count()));rebuildWidgets();}}));}
    private void save(){
        if(pending)return;
        JsonObject body=new JsonObject();
        try{if(titleInput.getValue().isBlank())throw new IllegalArgumentException();body.addProperty("startsAt",LocalDateTime.parse(dateInput.getValue(),FORMAT).atZone(ZoneId.systemDefault()).toInstant().toString());}
        catch(Exception e){status="Titel und Datum: TT.MM.JJJJ HH:mm";return;}
        body.addProperty("title",titleInput.getValue());pending=true;
        MpsqApiClient.post("/calendar",body).whenComplete((data,error)->minecraft.execute(()->{pending=false;if(error==null)reload();else status="Speichern fehlgeschlagen.";}));
    }
    private void remove(String id){if(pending)return;pending=true;MpsqApiClient.delete("/calendar/"+id).whenComplete((data,error)->minecraft.execute(()->{pending=false;if(error==null)reload();else status="Löschen fehlgeschlagen.";}));}
    @Override public void extractBackground(GuiGraphicsExtractor c,int x,int y,float d){super.extractBackground(c,x,y,d);MpsqTheme.drawBackground(c,width,height);}
    @Override public void extractRenderState(GuiGraphicsExtractor c,int x,int y,float d){
        super.extractRenderState(c,x,y,d);c.centeredText(font,title,width/2,24,0xFFFFFFFF);c.fill(12,45,width-12,47,MpsqTheme.TEXT_GEDAEMPT);
        int start=page*count();
        for(int i=start;i<Math.min(start+count(),rows.size());i++){var row=rows.get(i).getAsJsonObject();String date=FORMAT.withZone(ZoneId.systemDefault()).format(Instant.parse(row.get("starts_at").getAsString()));c.text(font,font.plainSubstrByWidth(date+" | "+row.get("title").getAsString(),width-70),16,64+(i-start)*28,0xFFFFFFFF);}
        c.centeredText(font,status,width/2,height-47,0xFFFFFFFF);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
