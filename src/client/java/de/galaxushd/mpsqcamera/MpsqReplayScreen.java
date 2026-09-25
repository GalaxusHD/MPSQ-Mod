package de.galaxushd.mpsqcamera;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Seekable tactical player replay. */
public final class MpsqReplayScreen extends Screen {
    private final Screen parent;
    private JsonArray frames=new JsonArray();
    private int position;
    private boolean playing;
    private long next;
    private String status="";
    private double minX,minZ,maxX,maxZ;
    public MpsqReplayScreen(Screen parent){super(Text.literal("Spieler-Replay"));this.parent=parent;}
    @Override protected void init(){
        addDrawableChild(ButtonWidget.builder(Text.literal("Aufnehmen"),b->{if(client.world!=null){MpsqReplayManager.start();status="Aufnahme läuft (max. 30 Minuten).";}}).dimensions(12,50,95,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Stop / Speichern"),b->{try{Path p=MpsqReplayManager.save();install(MpsqReplayManager.frames());status="Gespeichert: "+p.getFileName();}catch(Exception e){status="Speichern fehlgeschlagen.";}}).dimensions(113,50,110,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Letzte laden"),b->load()).dimensions(229,50,95,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("▶ / Pause"),b->{playing=!playing;next=System.currentTimeMillis();}).dimensions(width/2-50,height-56,100,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Zurück"),b->close()).dimensions(width/2-50,height-28,100,20).build());
    }
    private void install(JsonArray data){
        if(data==null||data.size()>9000)throw new IllegalArgumentException();
        double a=Double.POSITIVE_INFINITY,b=a,c=Double.NEGATIVE_INFINITY,d=c;
        for(var value:data){var frame=value.getAsJsonObject();frame.get("time").getAsLong();if(frame.getAsJsonArray("players").size()>32)throw new IllegalArgumentException();
            for(var player:frame.getAsJsonArray("players")){var p=player.getAsJsonObject();p.get("name").getAsString();double x=p.get("x").getAsDouble(),z=p.get("z").getAsDouble();if(!Double.isFinite(x)||!Double.isFinite(z))throw new IllegalArgumentException();a=Math.min(a,x);b=Math.min(b,z);c=Math.max(c,x);d=Math.max(d,z);}}
        frames=data;minX=a;minZ=b;maxX=c;maxZ=d;position=0;playing=false;
    }
    private void load(){
        try(var files=Files.list(MpsqReplayManager.DIRECTORY)){
            var file=files.filter(p->p.getFileName().toString().endsWith(".json")).max(Comparator.comparing(p->p.getFileName().toString()));
            if(file.isEmpty()){status="Keine Aufnahme gefunden.";return;}
            if(Files.size(file.get())>64000000)throw new java.io.IOException();
            install(JsonParser.parseString(Files.readString(file.get())).getAsJsonObject().getAsJsonArray("frames"));status=file.get().getFileName().toString();
        }catch(Exception e){status="Keine lesbare Aufnahme vorhanden.";}
    }
    @Override public void tick(){if(playing&&position+1<frames.size()&&System.currentTimeMillis()>=next){long a=frames.get(position).getAsJsonObject().get("time").getAsLong();position++;long b=frames.get(position).getAsJsonObject().get("time").getAsLong();next=System.currentTimeMillis()+Math.max(1,b-a);}else if(position+1>=frames.size())playing=false;}
    @Override public boolean mouseClicked(double x,double y,int button){if(y>=height-78&&y<=height-66&&x>=16&&x<=width-16&&!frames.isEmpty()){position=(int)Math.round((x-16)/(width-32)*(frames.size()-1));return true;}return super.mouseClicked(x,y,button);}
    @Override public void renderBackground(DrawContext c,int x,int y,float d){super.renderBackground(c,x,y,d);MpsqTheme.drawBackground(c,width,height);}
    @Override public void render(DrawContext c,int x,int y,float d){
        super.render(c,x,y,d);c.drawCenteredTextWithShadow(textRenderer,title,width/2,24,0xFFFFFFFF);
        c.fill(16,88,width-16,Math.max(89,height-92),0xDD101820);
        if(!frames.isEmpty()){
            JsonObject frame=frames.get(position).getAsJsonObject();var players=frame.getAsJsonArray("players");
            c.enableScissor(16,88,width-16,Math.max(89,height-92));
            for(var p:players){var q=p.getAsJsonObject();int px=30+(int)((q.get("x").getAsDouble()-minX)/Math.max(1,maxX-minX)*(width-90));int py=100+(int)((q.get("z").getAsDouble()-minZ)/Math.max(1,maxZ-minZ)*Math.max(1,height-216));c.fill(px-2,py-2,px+3,py+3,0xFF55FFFF);c.drawTextWithShadow(textRenderer,q.get("name").getAsString(),px+5,py-3,0xFFFFFFFF);}
            c.disableScissor();c.drawTextWithShadow(textRenderer,(frame.get("time").getAsLong()/1000)+" s",18,height-89,0xFFFFFFFF);
        }
        c.fill(16,height-78,width-16,height-68,0xFF333B44);int seek=16+(int)((width-32)*(frames.size()<2?0:position/(double)(frames.size()-1)));c.fill(16,height-78,seek,height-68,0xFF55AAAA);
        c.drawCenteredTextWithShadow(textRenderer,textRenderer.trimToWidth(status,width-24),width/2,75,0xFFFFFFFF);
    }
    @Override public void close(){client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
