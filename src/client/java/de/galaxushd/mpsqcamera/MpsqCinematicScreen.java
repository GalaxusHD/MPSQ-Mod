package de.galaxushd.mpsqcamera;

import java.util.*;
import java.util.function.Consumer;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Vanilla chest-style authoring UI; its slots never manipulate the real inventory. */
public final class MpsqCinematicScreen extends Screen {
    private static final Identifier CUSTOM=Identifier.fromNamespaceAndPath("mpsqcamera","textures/gui/cinematic.png");
    private static final Identifier PLUS=Identifier.fromNamespaceAndPath("mpsqcamera","textures/gui/cinematic_add.png");
    private static final Identifier CLOCK_ICON=Identifier.fromNamespaceAndPath("mpsqcamera","textures/gui/cinematic_clock.png");
    private static final Identifier CAMERA_ICON=Identifier.fromNamespaceAndPath("mpsqcamera","textures/gui/cinematic_camera.png");
    private static final Identifier SOUNDS=Identifier.fromNamespaceAndPath("mpsqcamera","textures/gui/cinematic_sounds.png");
    private static final Identifier CHEST=Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static MpsqCinematicScreen capture, openNext;
    private static MpsqCinematicScreen pendingNpc;
    private static MpsqCinematicScreen queuedNpc;
    static int openPendingNpc(){
        var c=Minecraft.getInstance();
        if(pendingNpc==null||c.level!=pendingNpc.originWorld||!allowed()){
            if(c.player!=null)de.galaxushd.mpsqcamera.compat.PlayerMessages.send(c.player,Component.literal("Zuerst in einer Kamerafahrt die NPC-Kategorie auswählen."),false);
            pendingNpc=null;return 0;
        }
        queuedNpc=pendingNpc;pendingNpc=null;return 1;
    }
    private static net.minecraft.client.multiplayer.ClientLevel captureWorld;
    private static boolean captureReady;
    static boolean capturing(){return capture!=null;}
    private final net.minecraft.client.multiplayer.ClientLevel originWorld=Minecraft.getInstance().level;
    private final Screen parent;
    private List<MpsqCinematicScene> scenes=new ArrayList<>();
    private MpsqCinematicScene scene;
    private String section="",error="";
    private boolean loadFailed;
    private int page,left,top;
    private final Map<Integer,Cell> cells=new HashMap<>();
    private record Cell(Item item,List<Component> lines,Consumer<Integer> click){}
    public MpsqCinematicScreen(Screen parent){super(Component.literal("Kamerafahrten"));this.parent=parent;try{scenes=MpsqCinematicStore.load();}catch(Exception e){error=e.getMessage();loadFailed=true;}}
    public static void initialize(){
        MpsqCinematicMarkers.initialize();MpsqCinematicActors.initialize();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,context)->dispatcher.register(ClientCommands.literal("mpsq-cinematic")

            .executes(ctx->{openNext=new MpsqCinematicScreen(null);return 1;})
            .then(ClientCommands.literal("keyframe").executes(ctx->{if(capture!=null)captureReady=true;else ctx.getSource().sendError(Component.literal("Zuerst im Editor einen Kamerapunkt auswählen."));return 1;}))
            .then(ClientCommands.literal("abbrechen").executes(ctx->{MpsqCinematicNpcScreen.cancelCapture();openNext=capture;capture=null;captureWorld=null;return 1;}))));
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            MpsqCinematicNpcScreen.captureTick();
            if(queuedNpc!=null){var editor=queuedNpc;queuedNpc=null;if(c.level==editor.originWorld&&allowed()){if(!editor.scene.npc.placed)editor.mutate(()->{var n=editor.scene.npc;n.x=c.player.getX();n.y=c.player.getY();n.z=c.player.getZ();n.yaw=MpsqCinematicScene.snapRotation(c.player.getYRot());n.placed=true;});c.gui.setScreen(MpsqScenesScreen.cinematic(editor,editor.scene,change->editor.mutate(change)));}}
            if(pendingNpc!=null&&(c.level!=pendingNpc.originWorld||!allowed()))pendingNpc=null;
            if(capture!=null&&(c.level!=captureWorld||!allowed())){capture=null;captureWorld=null;captureReady=false;}
            if(captureReady&&capture!=null){captureReady=false;var target=capture;capture=null;captureWorld=null;try{target.addPoint();}catch(Exception e){target.error=e.getMessage();}openNext=target;}
            if(openNext!=null){var s=openNext;openNext=null;if(c.level!=null&&allowed())c.gui.setScreen(s);}
        });
    }
    private static boolean allowed(){return TeamVisibilitySettings.visible()&&MpsqParticipation.allowed();}
    @Override protected void init(){refresh();}
    private void cell(int index,Item icon,String title,String details,Consumer<Integer> action){
        var lines=new ArrayList<Component>();lines.add(Component.literal(title));if(!details.isEmpty())for(String line:details.split("\n"))lines.add(Component.literal(line));cells.put(index,new Cell(icon,lines,action));
    }
    private void event(int slot,Item icon,String type){cell(slot,icon,MpsqCinematicEventScreen.label(type),"Zeitgesteuerte Einträge hinzufügen und bearbeiten",b->minecraft.gui.setScreen(new MpsqCinematicEventScreen(this,scene,type,change->mutate(change))));}
    private void disabled(int slot,Item icon,String label){cell(slot,icon,label,"Noch nicht angeschlossen\nFür einen späteren Ausbau vorgesehen",b->{});}
    private boolean soundMenu(){return scene!=null&&section.equals("Sounds");}
    private int panelWidth(){return soundMenu()?121:176;}
    private int panelHeight(){return soundMenu()?41:custom()?131:222;}
    private boolean custom(){return scene!=null&&section.isEmpty();}
    private void refresh(){
        if(!loadFailed)MpsqCinematicMarkers.sync(scenes);
        left=(width-panelWidth())/2;top=(height-panelHeight())/2;
        cells.clear();if(!allowed()){error="Nur Offizier oder höher.";return;}if(loadFailed)return;
        if(scene==null){
            page=Math.max(0,Math.min(page,Math.max(0,(scenes.size()-1)/45)));
            for(int i=page*45;i<Math.min(scenes.size(),page*45+45);i++){
                var s=scenes.get(i);cell(i-page*45,Items.MAP,s.name,"Dauer: "+seconds(s.duration())+" s\nKamerapunkte: "+s.points.size()+"\nLinksklick: bearbeiten\nRechtsklick: umbenennen",b->{if(b==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT)openScene(s.id);else if(b==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT)input("Szenenname",s.name,v->{String name=MpsqCinematicScene.uniqueName(v,scenes,s.id);mutate(()->s.name=name);});});
            }
            cell(45,Items.ARROW,"Zurück","",b->onClose());
            cell(48,Items.ARROW,"Vorherige Seite","",b->{page--;refresh();});
            cell(49,Items.EMERALD,"Neue Szene","Name eingeben und erstellen",b->input("Szenenname","",v->{String name=MpsqCinematicScene.uniqueName(v,scenes,null);mutate(()->{scene=new MpsqCinematicScene();scene.name=name;scene.dimension=minecraft.level.dimension().identifier().toString();scenes.add(scene);section="";page=0;});}));
            cell(50,Items.ARROW,"Nächste Seite","",b->{page++;refresh();});return;
        }
        if(!section.isEmpty()){subMenu();return;}
        cell(0,Items.CLOCK,"Zeitpunkte","Sekunden ab Szenenbeginn\nJede Spalte gehört zu einem Kamerapunkt",b->{});
        cell(9,Items.SPYGLASS,"Kamera","Punkte aufnehmen, bearbeiten und prüfen",b->{});
        cell(18,Items.ARMOR_STAND,"NPCs","NPC bearbeiten",b->{queuedNpc=this;});
        cell(27,Items.BLAZE_ROD,"Effekte","Titel, Nachrichten und Szeneneffekte",b->{section="Effekte";refresh();});
        cell(36,Items.NOTE_BLOCK,"Sounds","Ton und Musik",b->{section="Sounds";refresh();});
        page=Math.max(0,Math.min(page,scene.points.size()/8));
        for(int column=0;column<8;column++){
            int index=page*8+column;int slot=column+1;
            if(index<scene.points.size()){
                var p=scene.points.get(index);
                cell(slot,Items.CLOCK,seconds(p.time)+" Sekunden","Linksklick: Zeitpunkt ändern",b->input("Zeitpunkt (Sekunden)",seconds(p.time),v->mutate(()->scene.points.get(index).time=number(v))));
                cell(slot+9,Items.SPYGLASS,"Kamera "+(index+1),"Übergang: "+mode(p.transition)+"\nLinksklick: bearbeiten\nMittelklick: Vorschau ab hier\nRechtsklick: löschen",b->{if(b==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT)editPoint(p);else if(b==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_MIDDLE)preview(p.time);else if(b==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT)confirm("Kamerapunkt löschen?",()->mutate(()->scene.points.remove(p)));});
            }else if(index==scene.points.size())cell(slot+9,Items.EMERALD,"Kamerapunkt hinzufügen","Menü schließen und Position wählen\nDann /mpsq-cinematic keyframe eingeben",b->{capture=this;captureWorld=minecraft.level;minecraft.gui.setScreen(null);de.galaxushd.mpsqcamera.compat.PlayerMessages.send(minecraft.player,Component.literal("Position und Blickrichtung wählen, dann /mpsq-cinematic keyframe. Abbrechen: /mpsq-cinematic abbrechen"), false);});
        }
        cell(45,Items.ARROW,"Szenenübersicht","Alle Änderungen werden sofort gespeichert",b->minecraft.gui.setScreen(new MpsqCinematicScreen(parent)));
        cell(46,Items.GOLD_BLOCK,"Kamerapunkte anzeigen: "+(MpsqCinematicMarkers.showing(scene)?"An":"Aus"),"Vollständig texturierte Würfel an den Kamerapositionen",b->{MpsqCinematicMarkers.toggle(scene);refresh();});
        cell(47,Items.REDSTONE,"Auslöser","Kamerafahrt mit Auslösern verknüpfen",b->{section="Auslöser";refresh();});
        cell(49,Items.EMERALD,"Kamerafahrt abspielen","Esc: abbrechen und zum Editor zurück",b->preview(0));
        cell(52,Items.ARROW,"Nächste Seite","Linksklick: nächste Seite\nRechtsklick: vorherige Seite",b->{page+=b==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT?-1:1;refresh();});
        cell(51,Items.CARVED_PUMPKIN,"Kinobalken: "+(scene.bars?"An":"Aus"),"",b->mutate(()->scene.bars=!scene.bars));
        cell(53,Items.BARRIER,"Szene löschen","Mit Sicherheitsabfrage",b->confirm("Szene wirklich löschen?",()->mutate(()->{scenes.remove(scene);scene=null;page=0;})));
    }
    private void subMenu(){
        cell(45,Items.ARROW,"Zur Zeitleiste","",b->{section="";refresh();});
        switch(section){
            case "NPCs" -> {disabled(10,Items.NAME_TAG,"Name");disabled(11,Items.PLAYER_HEAD,"Skin / Zuschauer-Skin");disabled(12,Items.IRON_HELMET,"Helm");disabled(13,Items.IRON_CHESTPLATE,"Brustplatte");disabled(14,Items.IRON_LEGGINGS,"Hose");disabled(15,Items.IRON_BOOTS,"Schuhe");disabled(19,Items.IRON_SWORD,"Haupthand");disabled(20,Items.SHIELD,"Nebenhand");disabled(22,Items.ARMOR_STAND,"Entität / Model");disabled(24,Items.EGG,"NPC platzieren");}
            case "Effekte" -> {event(22,Items.PAPER,"DIALOGUE");event(10,Items.BOOK,"CHAT");event(12,Items.WRITABLE_BOOK,"ACTIONBAR");event(14,Items.ENCHANTED_BOOK,"TITLE");event(16,Items.COMMAND_BLOCK,"COMMAND");disabled(28,Items.CLOCK,"Tageszeit");disabled(30,Items.WATER_BUCKET,"Wetter");event(32,Items.BLAZE_POWDER,"PARTICLE");event(34,Items.COAL,"FADE");}
            case "Sounds" -> {
                cells.clear();
                cell(0,Items.ARROW,"Zur Zeitleiste","",b->{section="";refresh();});
                event(2,Items.JUKEBOX,"MEDIA");
                event(3,Items.NOTE_BLOCK,"SOUND");
                cell(5,Items.REPEATER,"Lautstärke und Tonhöhe","Für jeden Sound einzeln im Ereigniseditor einstellbar",b->minecraft.gui.setScreen(new MpsqCinematicEventScreen(this,scene,"SOUND",change->mutate(change))));
            }
            case "Auslöser" -> {
                cell(11,Items.MAP,"Bereich betreten: "+(scene.triggerZone.isBlank()?"Aus":scene.triggerZone),"Name einer Kamerazone eingeben\nLeere Eingabe deaktiviert den Auslöser",b->input("Name der Kamerazone",scene.triggerZone,v->{String name=v.trim();if(!name.isEmpty()&&!MpsqZoneCatalog.cameraZoneExists(name))throw new IllegalArgumentException("Keine Kamerazone mit diesem Namen vorhanden.");mutate(()->scene.triggerZone=name);}));
                cell(13,Items.OAK_DOOR,"Beim Beitritt: "+(scene.onMasterEnter?"An":"Aus"),"Beim Betreten / Einfliegen in die Gesamtfläche",b->mutate(()->scene.onMasterEnter=!scene.onMasterEnter));
                cell(15,Items.REDSTONE,"MPSQ-Redstone: "+(scene.redstoneEnabled?"An":"Aus"),"Mit /mpsq-redstone und Szenenname verknüpfen",b->mutate(()->scene.redstoneEnabled=!scene.redstoneEnabled));
                cell(22,Items.CLOCK,scene.once?"Nur einmal pro Spieler":"Mehrmals auslösen","Gilt gemeinsam für alle Auslöser\nEinmaliger Start wird dauerhaft gespeichert\nEditor-Vorschauen zählen nicht",b->mutate(()->scene.once=!scene.once));
            }
        }
    }
    private void addPoint(){
        var c=Minecraft.getInstance();if(c.player==null||c.level==null||scene==null)return;
        if(!scene.dimension.equals(c.level.dimension().identifier().toString())){error="Der Punkt liegt in einer anderen Dimension.";return;}
        var pos=c.player.getEyePosition(1);TeamVisibilitySettings.requirePlacement(pos.x,pos.y,pos.z);mutate(()->{var point=new MpsqCinematicScene.Point(scene.points.isEmpty()?0:scene.duration()+5,pos.x,pos.y,pos.z,c.player.getYRot(),c.player.getXRot());point.transition="SMOOTH";scene.points.add(point);});
    }
    private void editPoint(MpsqCinematicScene.Point p){minecraft.gui.setScreen(new PointEditor(this,p));}
    private void preview(double from){
        if(!TeamVisibilitySettings.inArea()){error="Kamerafahrten sind nur innerhalb der Gesamtfläche verfügbar.";return;}if(scene.points.isEmpty()){error="Bitte zuerst Kamerapunkte hinzufügen.";return;}
        if(minecraft.level==null||!scene.dimension.equals(minecraft.level.dimension().identifier().toString())){error="Die Szene gehört zu einer anderen Dimension.";return;}
        if(ScreenCreationManager.isCameraViewActive()){error="Bitte zuerst die Bildschirmkamera verlassen.";return;}
        minecraft.gui.setScreen(new MpsqCinematicPreview(this,scene,from));
    }
    private void mutate(Runnable change){
        if(!allowed()||loadFailed)throw new IllegalStateException("Bearbeiten ist nicht verfügbar.");
        if(Minecraft.getInstance().level!=originWorld)throw new IllegalStateException("Die Welt wurde gewechselt. Editor bitte neu öffnen.");
        var originalScene=scene;String snapshot=new Gson().toJson(scenes);String id=scene==null?null:scene.id;
        try{change.run();MpsqCinematicStore.save(scenes);error="";refresh();}
        catch(Exception e){scenes=new ArrayList<>(Arrays.asList(new Gson().fromJson(snapshot,MpsqCinematicScene[].class)));scene=scenes.stream().filter(s->s.id.equals(id)).findFirst().orElse(null);if(originalScene!=null&&scene!=null){originalScene.restoreFrom(scene);scenes.set(scenes.indexOf(scene),originalScene);scene=originalScene;}error=e.getMessage()==null?"Speichern fehlgeschlagen.":e.getMessage();refresh();throw new IllegalArgumentException(error);}
    }
    private void input(String label,String value,Consumer<String> save){minecraft.gui.setScreen(new TextInput(this,label,value,save));}
    private void confirm(String label,Runnable action){minecraft.gui.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(ok->{if(ok)try{action.run();}catch(Exception ignored){}minecraft.gui.setScreen(this);},Component.literal(label),Component.literal("Dieser Eintrag wird aus der lokalen Szenendatei entfernt.")));}
    private static double number(String v){try{double n=Double.parseDouble(v.trim().replace(',','.'));if(!Double.isFinite(n))throw new NumberFormatException();return n;}catch(NumberFormatException e){throw new IllegalArgumentException("Bitte eine gültige Zahl eingeben.");}}
    private static String seconds(double n){return String.format(Locale.ROOT,"%.2f",n);}
    private static String mode(String value){return switch(value){case "CUT"->"Schnitt";case "SMOOTH"->"Sanfte Kurve";default->"Linear";};}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        super.extractRenderState(g,mx,my,delta);
        if(soundMenu()){
            g.blit(RenderPipelines.GUI_TEXTURED,SOUNDS,left,top,0,0,121,41,242,82,242,82);
        }else if(custom()){
            g.blit(RenderPipelines.GUI_TEXTURED,CUSTOM,left,top,0,0,175,131,350,262,350,262);
            g.centeredText(font,scene.name+" · Seite "+(page+1),width/2,top-12,0xFFFFFFFF);
        }else{
            g.blit(RenderPipelines.GUI_TEXTURED,CHEST,left,top,0,0,176,125,256,256);
            g.blit(RenderPipelines.GUI_TEXTURED,CHEST,left,top+125,0,126,176,97,256,256);
            g.text(font,font.plainSubstrByWidth(scene==null?"Kamerafahrten":section,162),left+8,top+6,0xFF404040,false);
        }
        for(var entry:cells.entrySet()){
            int slot=entry.getKey();int x=left+8+(slot%9)*18,y=top+18+(slot/9)*18;
            if(custom()&&slot>=10&&slot<=17&&entry.getValue().item==Items.EMERALD)g.blit(RenderPipelines.GUI_TEXTURED,PLUS,x,y,0,0,16,16,32,32,32,32);
            else if(custom()&&slot>0&&slot<9)g.blit(RenderPipelines.GUI_TEXTURED,CLOCK_ICON,x,y,0,0,16,16,32,32,32,32);
            else if(custom()&&slot>=10&&slot<=17)g.blit(RenderPipelines.GUI_TEXTURED,CAMERA_ICON,x,y,0,0,16,16,32,32,32,32);
            else if((!custom()&&!soundMenu())||(soundMenu()&&slot!=0)||(custom()&&slot<45&&slot%9!=0))g.item(new ItemStack(entry.getValue().item),x,y);
            if(custom()&&((slot==46&&MpsqCinematicMarkers.showing(scene))||(slot==51&&scene.bars)))g.fill(x,y+14,x+16,y+16,0xFF55FF55);
            if(mx>=x&&mx<x+16&&my>=y&&my<y+16){g.fill(x,y,x+16,y+16,0x55FFFFFF);g.setComponentTooltipForNextFrame(font,entry.getValue().lines,mx,my);}
        }
        if(!error.isEmpty())g.centeredText(font,font.plainSubstrByWidth(error,Math.max(100,width-16)),width/2,Math.min(height-12,top+panelHeight()+4),0xFFFF7777);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice){
        if(!allowed()){error="Nur Offizier oder höher.";return true;}
        // Hit-test the layout that was actually drawn; navigation updates it afterwards.
        if(cells.isEmpty())refresh();
        int x=(int)event.x()-left-8,y=(int)event.y()-top-18;
        if(x>=0&&x<(soundMenu()?108:162)&&y>=0&&y<(soundMenu()?18:108)){var cell=cells.get(y/18*9+x/18);if(cell!=null)try{cell.click.accept(event.button());}catch(Exception e){error=e.getMessage();MpsqCameraClient.LOGGER.warn("Kamerafahrten-Menüaktion fehlgeschlagen",e);}return true;}
        return super.mouseClicked(event,twice);
    }
    private void openScene(String id){
        scene=scenes.stream().filter(s->Objects.equals(s.id,id)).findFirst().orElse(null);
        if(scene==null){error="Kamerafahrt nicht gefunden.";return;}
        section="";page=0;error="";refresh();
    }
    @Override public void onClose(){
        if(scene!=null&&!section.isEmpty()){section="";refresh();}
        else if(scene!=null)minecraft.gui.setScreen(new MpsqCinematicScreen(parent));
        else minecraft.gui.setScreen(parent);
    }
    @Override public boolean isPauseScreen(){return false;}

    static final class TextInput extends Screen {
        private final Screen parent;private final Consumer<String> save;private final String value;private EditBox field;private String error="";
        TextInput(Screen parent,String label,String value,Consumer<String> save){super(Component.literal(label));this.parent=parent;this.value=value;this.save=save;}
        @Override protected void init(){field=addRenderableWidget(new EditBox(font,width/2-110,height/2-20,220,20,title));field.setMaxLength(64);field.setValue(value);setInitialFocus(field);
            addRenderableWidget(Button.builder(Component.literal("Speichern"),b->{try{save.accept(field.getValue());onClose();}catch(Exception e){error=e.getMessage();}}).bounds(width/2-110,height/2+8,105,20).build());
            addRenderableWidget(Button.builder(Component.literal("Abbrechen"),b->onClose()).bounds(width/2+5,height/2+8,105,20).build());}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){super.extractRenderState(g,x,y,d);g.centeredText(font,title,width/2,height/2-42,0xFFFFFFFF);g.centeredText(font,error,width/2,height/2+36,0xFFFF7777);}
        @Override public void onClose(){minecraft.gui.setScreen(parent);}
        @Override public boolean isPauseScreen(){return false;}
    }
    private static final class PointEditor extends Screen {
        private final MpsqCinematicScreen parent;private final MpsqCinematicScene.Point point;private final int pointIndex;private final EditBox[] fields=new EditBox[6];private String transition,error="";
        PointEditor(MpsqCinematicScreen parent,MpsqCinematicScene.Point p){super(Component.literal("Kamerapunkt bearbeiten"));this.parent=parent;point=p;pointIndex=parent.scene.points.indexOf(p);transition=p.transition;}
        @Override protected void init(){String[] labels={"Sekunden","X","Y (Augenhöhe)","Z","Blick links/rechts","Blick hoch/runter"};double[] values={point.time,point.x,point.y,point.z,point.yaw,point.pitch};int y=height/2-92;
            for(int i=0;i<6;i++){fields[i]=addRenderableWidget(new EditBox(font,width/2+4,y+i*22,104,20,Component.literal(labels[i])));fields[i].setMaxLength(24);fields[i].setValue(Double.toString(values[i]));}
            addRenderableWidget(Button.builder(Component.literal(mode(transition)),b->{transition=switch(transition){case "LINEAR"->"SMOOTH";case "SMOOTH"->"CUT";default->"LINEAR";};b.setMessage(Component.literal(mode(transition)));}).bounds(width/2-110,y+136,220,20).build());
            addRenderableWidget(Button.builder(Component.literal("Speichern"),b->{try{double[] v=new double[6];for(int i=0;i<6;i++)v[i]=number(fields[i].getValue());TeamVisibilitySettings.requirePlacement(v[1],v[2],v[3]);parent.mutate(()->{var replacement=new MpsqCinematicScene.Point(v[0],v[1],v[2],v[3],(float)v[4],(float)v[5]);replacement.transition=transition;parent.scene.points.set(pointIndex,replacement);});onClose();}catch(Exception e){error=e.getMessage();}}).bounds(width/2-110,y+160,105,20).build());
            addRenderableWidget(Button.builder(Component.literal("Abbrechen"),b->onClose()).bounds(width/2+5,y+160,105,20).build());}
        @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){super.extractRenderState(g,x,y,d);int top=height/2-92;g.centeredText(font,title,width/2,top-16,0xFFFFFFFF);String[] labels={"Sekunden","X","Y (Augenhöhe)","Z","Blick links/rechts","Blick hoch/runter"};for(int i=0;i<6;i++)g.text(font,labels[i],width/2-110,top+i*22+6,0xFFFFFFFF);g.centeredText(font,error,width/2,top+186,0xFFFF7777);}
        @Override public void onClose(){minecraft.gui.setScreen(parent);}
        @Override public boolean isPauseScreen(){return false;}
    }
}




