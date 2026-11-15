package de.galaxushd.mpsqcamera;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Native Scenes overview and shared route settings. */
final class MpsqScenesHub extends Screen {
    private Screen parent; private String section="",status=""; private int page;
    private MpsqSharedPaths.Route route;
    private final net.minecraft.client.multiplayer.ClientLevel world=Minecraft.getInstance().level;
    private static Screen queued;
    MpsqScenesHub(Screen parent){super(Component.literal("Scenes · NPCs & Pfade"));this.parent=parent;}
    static void initialize(){
        MpsqScenesTool.initialize();
        de.galaxushd.mpsqcamera.compat.WorldRenderEvents.AFTER_ENTITIES.register(ctx->{
            var c=Minecraft.getInstance();
            if(!(c.gui.screen() instanceof MpsqScenesHub hub)||hub.route==null||!hub.section.equals("ROUTE")||c.level!=hub.world||!MpsqScenes.allowed()||ctx.matrixStack()==null||ctx.consumers()==null)return;
            var r=hub.route;if(!r.dimension.equals(c.level.dimension().identifier().toString()))return;
            var npc=new MpsqCinematicScene.Npc();npc.route=r.points.toArray(new MpsqCinematicScene.Waypoint[21]);npc.closedRoute=r.closedRoute;npc.recording=r.recording;
            MpsqPathSeconds.draw(ctx,npc);
            var lines=ctx.consumers().getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.lines());var cam=ctx.camera().getPos();
            double end=npc.duration();int count=Math.max(1,Math.min(512,(int)Math.ceil(end*20)));var previous=npc.position(0,false,"FORWARD");
            for(int i=1;i<=count;i++){var next=npc.position(end*i/count,false,"FORWARD");MpsqCinematicMarkers.segment(lines,ctx.matrixStack(),cam,previous.x,previous.y+.08,previous.z,next.x,next.y+.08,next.z,0xFF33FFCC);previous=next;}
        });
        ClientCommandRegistrationCallback.EVENT.register((d,c)->d.register(ClientCommands.literal("mpsq-scenes").executes(ctx->{if(MpsqScenes.allowed())queued=new MpsqScenesHub(null);else ctx.getSource().sendError(Component.literal("Nur ab Offizier innerhalb der Gesamtfläche."));return 1;}).then(ClientCommands.literal("pfadname").then(ClientCommands.argument("name",com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(ctx->{MpsqScenesTool.finishPath(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx,"name"));return 1;})))));
        ClientTickEvents.END_CLIENT_TICK.register(c->{MpsqScenesTool.tick();if(queued!=null){var s=queued;queued=null;if(c.level!=null&&MpsqScenes.allowed())c.gui.setScreen(s);}});
    }
    static void showLater(Screen s){queued=s;}
    private void act(Runnable r){try{if(minecraft.level!=world||!MpsqScenes.allowed())throw new IllegalStateException("Welt/Bearbeitungsrechte geändert.");r.run();}catch(Exception e){status=e.getMessage();}}
    private void button(int col,int row,String label,Runnable run){addRenderableWidget(new MpsqScenesWidget(width/2-154+col*156,48+row*25,152,23,label,null,()->act(run)));}
    private void tab(String s){section=s;page=0;rebuildWidgets();}
    private void edit(String title,String value,Consumer<String> save){minecraft.gui.setScreen(new Text(this,title,value,v->act(()->{save.accept(v);status="Gespeichert";rebuildWidgets();})));}
    private void save(){MpsqSharedPaths.save(route);rebuildWidgets();}
    private int integer(String s,int min,int max){int n=Integer.parseInt(s);if(n<min||n>max)throw new IllegalArgumentException("Bereich: "+min+"–"+max);return n;}
    @Override protected void init(){
        try{switch(section){case "NPCS"->npcs();case "PATHS"->paths();case "ROUTE"->settings();case "POOL","ADD_POOL"->pool();default->{
            button(0,0,"NPC-Produkte / Erstellen",()->{minecraft.gui.setScreen(new MpsqScenesProducts(this));});
            button(1,0,"Platzierte NPCs",()->tab("NPCS"));button(0,1,"Gemeinsame Pfade",()->tab("PATHS"));button(1,1,"Pfad zeichnen",()->MpsqScenesTool.beginPath(this));button(0,2,"Pfad aufnehmen",()->MpsqMotionCapture.start(this,frames->MpsqScenesTool.recordedPath(this,frames)));
        }}}catch(Exception e){status=e.getMessage();}
        addRenderableWidget(new MpsqScenesWidget(width/2-76,height-28,152,22,"Zurück",null,this::onClose));
    }
    private void npcs(){
        var rows=MpsqAccessoryRenderer.npcsSnapshot();var list=new ArrayList<com.google.gson.JsonObject>();for(var e:rows){var o=e.getAsJsonObject();if(!MpsqScenes.str(o,"id","").startsWith("path:"))list.add(o);}
        page=Math.min(page,Math.max(0,(list.size()-1)/10));for(int i=page*10;i<Math.min(list.size(),page*10+10);i++){var o=list.get(i);int n=i-page*10;button(n%2,n/2,MpsqScenes.str(o,"display_name","NPC"),()->minecraft.gui.setScreen(new MpsqScenesScreen(MpsqScenes.fromNpc(o),this,null)));}pages();
    }
    private void paths(){var all=MpsqSharedPaths.all();page=Math.min(page,Math.max(0,(all.size()-1)/10));for(int i=page*10;i<Math.min(all.size(),page*10+10);i++){var p=all.get(i);int n=i-page*10;button(n%2,n/2,(p.enabled?"● ":"○ ")+p.name,()->{route=p;tab("ROUTE");});}button(0,5,"+ Neuer Pfad",()->MpsqScenesTool.beginPath(this));pages();}
    private void pages(){button(0,6,"←",()->{page=Math.max(0,page-1);rebuildWidgets();});button(1,6,"→",()->{page++;rebuildWidgets();});}
    private void settings(){
        button(0,0,route.name,()->edit("Pfadname",route.name,v->{route.name=v.strip();save();}));button(1,0,route.enabled?"Aktiv: AN":"Aktiv: AUS",()->{route.enabled=!route.enabled;save();});
        button(0,1,"Breite: "+route.width,()->edit("Pfadbreite 0–8 Blöcke",""+route.width,v->{route.width=Double.parseDouble(v.replace(',','.'));save();}));
        button(1,1,"Richtung: "+route.direction,()->{route.direction=switch(route.direction){case "FORWARD"->"REVERSE";case "REVERSE"->"BOTH";default->"FORWARD";};save();});
        button(0,2,"Vorwärts: "+route.forwardPercent+" %",()->edit("Anteil Vorwärts bei BOTH (0–100)",""+route.forwardPercent,v->{route.forwardPercent=integer(v,0,100);save();}));
        button(1,2,"Spawn-Radius: "+route.spawnDistance,()->edit("Spielerabstand zu Wegpunkten (1–128)",""+route.spawnDistance,v->{route.spawnDistance=Double.parseDouble(v.replace(',','.'));save();}));
        button(0,3,"Tag: "+route.dayCount,()->edit("NPC-Anzahl bei Tag (0–64)",""+route.dayCount,v->{route.dayCount=integer(v,0,64);save();}));button(1,3,"Nacht: "+route.nightCount,()->edit("NPC-Anzahl bei Nacht (0–64)",""+route.nightCount,v->{route.nightCount=integer(v,0,64);save();}));
        button(0,4,"Bei Regen: "+route.rainPercent+" %",()->edit("Anteil der Tages-/Nachtanzahl bei Regen",""+route.rainPercent,v->{route.rainPercent=integer(v,0,100);save();}));button(1,4,"NPC- / Skin-Pool: "+route.choices.size(),()->tab("POOL"));
        button(0,5,"Start/Ende verbinden: "+(route.closedRoute?"AN":"AUS"),()->{route.closedRoute=!route.closedRoute;save();});button(1,5,"Pfad löschen",()->minecraft.gui.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(ok->{if(ok)act(()->MpsqSharedPaths.delete(route.id));tab("PATHS");minecraft.gui.setScreen(this);},Component.literal("Pfad löschen?"),Component.literal(route.name))));
    }
    private void pool(){
        button(0,0,"+ Platzierter NPC als Vorlage",()->{section="ADD_POOL";minecraft.gui.setScreen(new Pick(this,o->{route.choices.add(MpsqScenes.copy(MpsqScenes.fromNpc(o).profile()));save();section="POOL";}));});
        int start=page*8;for(int i=start;i<Math.min(route.choices.size(),start+8);i++){int index=i,n=i-start;button(n%2,1+n/2,"Entfernen: "+route.choices.get(i).npc.name,()->{route.choices.remove(index);save();});}pages();
    }
    @Override public void tick(){if(minecraft.level!=world)minecraft.gui.setScreen(null);}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){g.fill(0,0,width,height,0x40102638);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){super.extractRenderState(g,x,y,d);g.centeredText(font,title,width/2,18,0xFFFFFFFF);g.centeredText(font,status,width/2,height-42,0xFFFFFFFF);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){if(section.isEmpty())minecraft.gui.setScreen(parent);else if(section.equals("POOL")||section.equals("ADD_POOL"))tab("ROUTE");else if(section.equals("ROUTE"))tab("PATHS");else tab("");}
    static final class Text extends Screen {
        private final Screen back;private final String value;private final Consumer<String> save;private EditBox field;
        Text(Screen back,String title,String value,Consumer<String> save){super(Component.literal(title));this.back=back;this.value=value;this.save=save;}
        @Override protected void init(){field=new EditBox(font,width/2-150,height/2-16,300,22,Component.literal("Wert"));field.setMaxLength(64);field.setValue(value);addRenderableWidget(field);setInitialFocus(field);addRenderableWidget(new MpsqScenesWidget(width/2-75,height/2+14,150,22,"Übernehmen",null,()->{save.accept(field.getValue());minecraft.gui.setScreen(back);}));}
        @Override public void onClose(){minecraft.gui.setScreen(back);}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){super.extractRenderState(g,x,y,d);g.centeredText(font,title,width/2,height/2-40,0xFFFFFFFF);}
        @Override public boolean isPauseScreen(){return false;}
    }
    static final class Pick extends Screen {
        private final Screen back;private final Consumer<com.google.gson.JsonObject> choose;private int page;
        Pick(Screen back,Consumer<com.google.gson.JsonObject> choose){super(Component.literal("NPC / Skin auswählen"));this.back=back;this.choose=choose;}
        @Override protected void init(){var list=new ArrayList<com.google.gson.JsonObject>();for(var e:MpsqAccessoryRenderer.npcsSnapshot())if(!MpsqScenes.str(e.getAsJsonObject(),"id","").startsWith("path:"))list.add(e.getAsJsonObject());page=Math.min(page,Math.max(0,(list.size()-1)/10));for(int i=page*10;i<Math.min(list.size(),page*10+10);i++){var o=list.get(i);int n=i-page*10;addRenderableWidget(new MpsqScenesWidget(width/2-150+(n%2)*152,45+(n/2)*26,150,24,MpsqScenes.str(o,"display_name","NPC"),null,()->{choose.accept(o);minecraft.gui.setScreen(back);}));}addRenderableWidget(new MpsqScenesWidget(width/2-150,height-60,148,22,"←",null,()->{page=Math.max(0,page-1);rebuildWidgets();}));addRenderableWidget(new MpsqScenesWidget(width/2+2,height-60,148,22,"→",null,()->{page++;rebuildWidgets();}));addRenderableWidget(new MpsqScenesWidget(width/2-75,height-32,150,22,"Zurück",null,this::onClose));}
        @Override public void onClose(){minecraft.gui.setScreen(back);}
        @Override public boolean isPauseScreen(){return false;}
    }
}
