package de.galaxushd.mpsqcamera;

import java.util.*;
import java.util.function.Consumer;
import com.google.gson.Gson;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.SimpleParticleType;

final class MpsqCinematicEventScreen extends Screen {
    private final Screen parent;private final MpsqCinematicScene scene;private final Consumer<Runnable> save;
    private String type;private int page;private String error="";
    MpsqCinematicEventScreen(Screen parent,MpsqCinematicScene scene,String type,Consumer<Runnable> save){super(Component.literal("Zeitgesteuerte Ereignisse"));this.parent=parent;this.scene=scene;this.type=type;this.save=save;}
    static String label(String type){return switch(type){case "DIALOGUE"->"Dialoge";case "CHAT"->"Chatnachricht";case "ACTIONBAR"->"Aktionsbar";case "TITLE"->"Titel / Untertitel";case "COMMAND"->"Befehl als Spieler";case "SOUND"->"Minecraft-Sound";case "MEDIA"->"Eigene Musik / Sound-ID";case "PARTICLE"->"Partikel";case "FADE"->"Schwarzblende";default->type;};}
    @Override protected void init(){
        clearWidgets();var list=scene.events.stream().filter(e->e.type.equals(type)).sorted(Comparator.comparingDouble(e->e.time)).toList();int count=Math.max(1,(height-105)/24);page=Math.max(0,Math.min(page,Math.max(0,(list.size()-1)/count)));
        addRenderableWidget(Button.builder(Component.literal("+ "+label(type)),b->{var e=new MpsqCinematicScene.Event();e.type=type;if(minecraft.player!=null){e.x=minecraft.player.getX();e.y=minecraft.player.getY();e.z=minecraft.player.getZ();}minecraft.gui.setScreen(new Edit(this,e,true));}).bounds(width/2-155,35,type.equals("DIALOGUE")?150:310,20).build());
        if(type.equals("DIALOGUE"))addRenderableWidget(Button.builder(Component.literal("NPC-Text übernehmen"),b->minecraft.gui.setScreen(new DialogueChoices(this))).bounds(width/2+5,35,150,20).build());
        for(int i=page*count;i<Math.min(list.size(),(page+1)*count);i++){var e=list.get(i);int y=61+(i-page*count)*24;
            addRenderableWidget(Button.builder(Component.literal(String.format(Locale.ROOT,"%.2f s · %s",e.time,e.value.isBlank()?label(type):e.value)),b->minecraft.gui.setScreen(new Edit(this,e,false))).bounds(width/2-155,y,264,20).build());
            addRenderableWidget(Button.builder(Component.literal("X"),b->minecraft.gui.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(ok->{if(ok)try{save.accept(()->scene.events.remove(e));}catch(Exception ex){error=ex.getMessage();}minecraft.gui.setScreen(this);},Component.literal("Ereignis löschen?"),Component.literal(label(type))))).bounds(width/2+114,y,41,20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Zurück"),b->onClose()).bounds(width/2-155,height-27,100,20).build());
        addRenderableWidget(Button.builder(Component.literal("<"),b->{page--;init();}).bounds(width/2-50,height-27,100,20).build());
        addRenderableWidget(Button.builder(Component.literal(">"),b->{page++;init();}).bounds(width/2+55,height-27,100,20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){super.extractRenderState(g,x,y,d);g.centeredText(font,label(type),width/2,15,0xFFFFFFFF);if(!error.isBlank())g.centeredText(font,error,width/2,height-40,0xFFFF7777);}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    private static final class DialogueChoices extends Screen {
        private final MpsqCinematicEventScreen parent;private int page;
        private record Line(String speaker,String text){}
        DialogueChoices(MpsqCinematicEventScreen parent){super(Component.literal("Vorhandene NPC-Dialoge"));this.parent=parent;}
        @Override protected void init(){
            var lines=new ArrayList<Line>();for(var value:MpsqAccessoryRenderer.npcsSnapshot()){var npc=value.getAsJsonObject();if(!npc.has("interaction_data")||!npc.get("interaction_data").isJsonObject())continue;var data=npc.getAsJsonObject("interaction_data");if(!data.has("pages")||!data.get("pages").isJsonArray())continue;String speaker=npc.has("display_name")?npc.get("display_name").getAsString():npc.has("name")?npc.get("name").getAsString():"NPC";
                for(var text:data.getAsJsonArray("pages"))if(text.isJsonPrimitive())lines.add(new Line(speaker,text.getAsString()));
            }
            int count=Math.max(1,(height-80)/24);page=Math.max(0,Math.min(page,Math.max(0,(lines.size()-1)/count)));
            for(int i=page*count;i<Math.min(lines.size(),(page+1)*count);i++){var line=lines.get(i);addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(line.speaker+": "+line.text,290)),b->{var e=new MpsqCinematicScene.Event();e.type="DIALOGUE";e.value=line.text;e.extra=line.speaker;minecraft.gui.setScreen(new Edit(parent,e,true));}).bounds(width/2-150,35+(i-page*count)*24,300,20).build());}
            addRenderableWidget(Button.builder(Component.literal("Zurück"),b->onClose()).bounds(width/2-150,height-28,100,20).build());
            addRenderableWidget(Button.builder(Component.literal("<"),b->{page--;rebuildWidgets();}).bounds(width/2-45,height-28,90,20).build());
            addRenderableWidget(Button.builder(Component.literal(">"),b->{page++;rebuildWidgets();}).bounds(width/2+50,height-28,100,20).build());
        }
        @Override public void onClose(){minecraft.gui.setScreen(parent);}
        @Override public boolean isPauseScreen(){return false;}
    }
    private static final class Edit extends Screen {
        private final MpsqCinematicEventScreen parent;private final MpsqCinematicScene.Event event;private final boolean fresh;
        private final EditBox[] fields=new EditBox[6];private String error="";
        Edit(MpsqCinematicEventScreen parent,MpsqCinematicScene.Event source,boolean fresh){super(Component.literal(label(source.type)));this.parent=parent;event=new Gson().fromJson(new Gson().toJson(source),MpsqCinematicScene.Event.class);this.fresh=fresh;}
        @Override protected void init(){
            String[] values={""+event.time,""+event.duration,event.value,event.extra,""+event.volume,""+event.pitch};
            for(int i=0;i<6;i++){fields[i]=new EditBox(font,width/2-10,38,165,20,Component.literal(labels()[i]));fields[i].setMaxLength(i==2||i==3?2048:24);fields[i].setValue(values[i]);} int row=0;for(int i:rows()){fields[i].setY(38+row++*24);addRenderableWidget(fields[i]);}
            if(event.type.equals("MEDIA"))addRenderableWidget(Button.builder(Component.literal("Lokale Musik auswählen"),b->{int i=MpsqBuiltinMusic.IDS.indexOf(fields[2].getValue());i=(i+1)%MpsqBuiltinMusic.IDS.size();fields[2].setValue(MpsqBuiltinMusic.IDS.get(i));b.setMessage(Component.literal(MpsqBuiltinMusic.TITLES.get(i)));}).bounds(width/2-155,38+rows().length*24,310,20).build());
            if(event.type.equals("PARTICLE"))addRenderableWidget(Button.builder(Component.literal("Partikelposition: hier setzen"),b->{if(minecraft.player!=null){event.x=minecraft.player.getX();event.y=minecraft.player.getY();event.z=minecraft.player.getZ();}}).bounds(width/2-155,38+rows().length*24,310,20).build());
            addRenderableWidget(Button.builder(Component.literal("Speichern"),b->{try{
                event.time=Double.parseDouble(fields[0].getValue().replace(',','.'));event.duration=Double.parseDouble(fields[1].getValue().replace(',','.'));event.value=fields[2].getValue().strip();event.extra=fields[3].getValue();event.volume=Float.parseFloat(fields[4].getValue().replace(',','.'));event.pitch=Float.parseFloat(fields[5].getValue().replace(',','.'));event.validate();
                if(event.type.equals("SOUND")){var id=Identifier.tryParse(event.value);if(id==null||minecraft.getSoundManager().getSoundEvent(id)==null)throw new IllegalArgumentException("Sound-ID nicht gefunden.");}
                if(event.type.equals("PARTICLE")){var id=Identifier.tryParse(event.value);if(id==null||!(BuiltInRegistries.PARTICLE_TYPE.getValue(id) instanceof SimpleParticleType))throw new IllegalArgumentException("Einfache Partikel-ID erforderlich.");int count=event.extra.isBlank()?20:Integer.parseInt(event.extra);if(count<1||count>200)throw new IllegalArgumentException("Partikelanzahl: 1–200.");TeamVisibilitySettings.requirePlacement(event.x,event.y,event.z);}
                parent.save.accept(()->{if(fresh)parent.scene.events.add(event);else{int index=-1;for(int i=0;i<parent.scene.events.size();i++)if(parent.scene.events.get(i).id.equals(event.id))index=i;if(index<0)throw new IllegalArgumentException("Ereignis fehlt.");parent.scene.events.set(index,event);}});onClose();
            }catch(Exception e){error=e instanceof NumberFormatException?"Bitte gültige Zahlen eingeben.":e.getMessage();}}).bounds(width/2-155,height-28,150,20).build());
            addRenderableWidget(Button.builder(Component.literal("Abbrechen"),b->onClose()).bounds(width/2+5,height-28,150,20).build());
        }
        private int[] rows(){return switch(event.type){case "FADE"->new int[]{0,1};case "SOUND","MEDIA"->new int[]{0,1,2,4,5};case "TITLE","PARTICLE","DIALOGUE"->new int[]{0,1,2,3};case "CHAT","COMMAND"->new int[]{0,2};default->new int[]{0,1,2};};} private String[] labels(){return new String[]{"Start (Sekunden)","Dauer (Sekunden)",switch(event.type){case "SOUND","PARTICLE"->"Minecraft-ID";case "MEDIA"->"MPSQ-Sound-ID";case "COMMAND"->"Befehl";default->"Text";},event.type.equals("PARTICLE")?"Anzahl (1–200)":event.type.equals("DIALOGUE")?"Sprecher (optional)":"Untertitel (Titel)","Lautstärke (0–1)","Tonhöhe (0.25–4)"};}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){super.extractRenderState(g,x,y,d);g.centeredText(font,title,width/2,15,0xFFFFFFFF);int row=0;for(int i:rows())g.text(font,labels()[i],width/2-155,44+row++*24,0xFFFFFFFF);if(!error.isBlank())g.centeredText(font,font.plainSubstrByWidth(error,width-20),width/2,height-42,0xFFFF7777);}
        @Override public void onClose(){minecraft.gui.setScreen(parent);}
        @Override public boolean isPauseScreen(){return false;}
    }
}
