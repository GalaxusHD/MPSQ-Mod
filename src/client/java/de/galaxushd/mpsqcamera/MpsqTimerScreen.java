package de.galaxushd.mpsqcamera;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Simple replaceable timer UI; editing never pauses the running world. */
final class MpsqTimerScreen extends Screen {
    private final Screen parent;private MpsqModularTimer.Timer timer;private int page,tab;
    private String status="";private EditBox name,time,x,y,z,yaw,pitch,scale,color,endInput;
    MpsqTimerScreen(Screen parent){super(Component.literal("ModularTimer"));this.parent=parent;}
    MpsqTimerScreen(Screen parent,MpsqModularTimer.Timer timer){this(parent);this.timer=timer;}
    private void button(String label,int x,int y,int w,Runnable action){addRenderableWidget(Button.builder(Component.literal(label),b->{try{if(timer!=null&&MpsqChess.ownsClock(timer)&&MpsqChess.game.active()&&!List.of("Steuerung","Darstellung","Position","Ablauf","Zur Timerliste","Zurück").contains(label))throw new IllegalArgumentException("Schachuhr während der Partie gesperrt. Einstellungen vor dem nächsten Spiel ändern.");action.run();}catch(Exception e){status=e.getMessage()==null?"Ungültige Eingabe.":e.getMessage();}}).bounds(x,y,w,20).build());}
    private EditBox field(String value,String hint,int x,int y,int w){EditBox f=addRenderableWidget(new EditBox(font,x,y,w,20,Component.literal(hint)));f.setMaxLength(64);f.setValue(value);f.setHint(Component.literal(hint));return f;}
    private void refresh(){clearWidgets();init();}
    @Override protected void init(){int left=width/2-150;
        if(!MpsqModularTimer.editable()){status="Lokale Welt erforderlich. Im Replay ist die Bearbeitung gesperrt.";button("Zurück",left+100,height-27,100,this::onClose);return;}
        if(timer==null){
            var all=MpsqModularTimer.current();int count=Math.max(1,(height-145)/25);page=Math.clamp(page,0,Math.max(0,(all.size()-1)/count));
            for(int i=page*count;i<Math.min(all.size(),(page+1)*count);i++){var t=all.get(i);button(t.name+" · "+MpsqTimerClock.label(t.clock.seconds()),left,45+(i-page*count)*25,300,()->{timer=t;tab=0;status="";refresh();});}
            int bottom=height-95;name=field("","Name",left,bottom,210);
            button("Erstellen",left+215,bottom,85,()->{timer=MpsqModularTimer.create(name.getValue(),300000);status="An deiner Position erstellt.";refresh();});
            button("<",left,height-67,40,()->{page=Math.max(0,page-1);refresh();});button(">",left+260,height-67,40,()->{page++;refresh();});
        }else{
            for(int i=0;i<4;i++){int index=i;button(List.of("Steuerung","Darstellung","Position","Ablauf").get(i),left+i*76,42,73,()->{tab=index;refresh();});}
            if(tab==0){
                time=field((timer.clock.initial/1000)+"s","Startzeit",left,80,140);button("Zeit setzen",left+145,80,155,()->act("set",MpsqTimerClock.parse(time.getValue())));
                button(timer.clock.running?"Pause":"Start / Fortsetzen",left,103,145,()->act(timer.clock.running?"pause":"start",0));button("Zurücksetzen",left+150,103,150,()->act("reset",0));
                button("−10 s",left,126,95,()->act("add",-10000));button("+10 s",left+100,126,95,()->act("add",10000));button("+1 min",left+200,126,100,()->act("add",60000));
                button(timer.clock.up?"Modus: Stoppuhr":"Modus: Countdown",left,149,145,()->{timer.clock.up=!timer.clock.up;timer.clock.set(timer.clock.up?0:300000);commit();});
                button("Ende: "+endLabel(timer.end),left+150,149,150,()->{timer.end=next(timer.end,List.of("sound","message","firework","stop_game","none"));commit();});
                endInput=field(timer.endInput,"Nachricht / Sound-ID / minecraft",left,172,245);endInput.setMaxLength(240);button("OK",left+250,172,50,this::saveEnd);
            }else if(tab==1){
                button("Letzte 10 Sekunden rot: "+on(timer.redEnding),left,80,300,()->{timer.redEnding=!timer.redEnding;commit();});
                button("Ziffern: "+timer.animation,left,103,145,()->{timer.animation=next(timer.animation,List.of("size","bounce","flip","spin","bump","none"));commit();});button("Bewegung: "+timer.idle,left+150,103,150,()->{timer.idle=next(timer.idle,List.of("bob","yaw","none"));commit();});
                color=field(String.format("%06X",timer.color&0xFFFFFF),"Farbe HEX",left,126,145);scale=field(Float.toString(timer.scale),"Größe",left+150,126,150);
                button("Farbe / Größe anwenden",left,149,300,()->{int c=Integer.parseInt(color.getValue().replace("#",""),16);float size=finite(scale);if(size<.02||size>3||c<0||c>0xFFFFFF)throw new IllegalArgumentException("Größe 0.02–3; Farbe als 6 HEX-Ziffern.");timer.color=0xFF000000|c;timer.scale=size;commit();});
                button("Tick-Sound: "+on(timer.tickSound),left,172,300,()->{timer.tickSound=!timer.tickSound;commit();});
            }else if(tab==2){
                x=field(Double.toString(timer.x),"X",left,80,95);y=field(Double.toString(timer.y),"Y",left+100,80,95);z=field(Double.toString(timer.z),"Z",left+200,80,100);
                yaw=field(Float.toString(timer.yaw),"Drehung",left,103,145);pitch=field(Float.toString(timer.pitch),"Neigung",left+150,103,150);
                button("Position anwenden",left,126,300,()->{double px=number(x),py=number(y),pz=number(z);float ry=finite(yaw),rp=finite(pitch);if(Math.abs(px)>30000000||Math.abs(pz)>30000000||Math.abs(py)>20000000)throw new IllegalArgumentException("Position außerhalb der Welt.");timer.x=px;timer.y=py;timer.z=pz;timer.yaw=ry;timer.pitch=rp;commit();});
                button("Zu meiner Position",left,149,300,()->act("here",0));
                button("Timer löschen",left,172,300,()->{MpsqModularTimer.change(timer,"remove",0);timer=null;refresh();});
            }else{
                button("Ende: "+endLabel(timer.end),left,80,300,()->{timer.end=next(timer.end,List.of("sound","message","firework","stop_game","none"));commit();});
                endInput=field(timer.endInput,timer.end.equals("message")?"Nachricht":timer.end.equals("firework")?"minecraft (LightTheNight folgt)":"Minecraft- oder MPSQ-Sound-ID",left,110,300);endInput.setMaxLength(240);
                button("Ablauf speichern",left,140,300,this::saveEnd);button("Spiel: "+(timer.system.isEmpty()?"unabhängig":timer.system),left,172,300,()->{List<String> ids=new ArrayList<>();ids.add("");MpsqSystemController.availableSystems().stream().filter(s->MpsqSystemController.implemented(s.id())).forEach(s->ids.add(s.id()));timer.system=next(timer.system,ids);commit();});
            }
        }
        button(timer==null?"Zurück":"Zur Timerliste",left+75,height-27,150,()->{if(timer==null)onClose();else{timer=null;status="";refresh();}});
    }
    private void saveEnd(){String value=endInput.getValue().trim();if(timer.end.equals("firework")&&!value.isBlank()&&!value.equalsIgnoreCase("minecraft"))throw new IllegalArgumentException("LightTheNight folgt; derzeit minecraft verwenden.");if(timer.end.equals("sound")&&!value.isBlank()){if(value.length()>128||!value.matches("[a-zA-Z0-9_.:/-]+"))throw new IllegalArgumentException("Ungültige Sound-ID.");if(value.contains(":")){var id=net.minecraft.resources.Identifier.tryParse(value);if(id==null||!net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.containsKey(id))throw new IllegalArgumentException("Minecraft-Sound nicht gefunden.");}}timer.endInput=value;commit();}
    private static float finite(EditBox f){float n=Float.parseFloat(f.getValue());if(!Float.isFinite(n))throw new IllegalArgumentException("Endliche Zahl erforderlich.");return n;}
    private static double number(EditBox f){double n=Double.parseDouble(f.getValue());if(!Double.isFinite(n))throw new IllegalArgumentException("Endliche Zahl erforderlich.");return n;}
    private static String next(String s,List<String> values){return values.get((values.indexOf(s)+1)%values.size());}
    private static String on(boolean b){return b?"an":"aus";}
    private static String endLabel(String s){return switch(s){case "message"->"Nachricht";case "firework"->"Feuerwerk";case "stop_game"->"Spiel stoppen";case "none"->"keine Aktion";default->"Sound";};}
    private void act(String s,long n){MpsqModularTimer.change(timer,s,n);refresh();}
    private void commit(){MpsqModularTimer.requireEdit();timer.visual();MpsqModularTimer.save();status="Gespeichert.";refresh();}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){super.extractBackground(g,x,y,d);MpsqTheme.drawBackground(g,width,height);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float d){super.extractRenderState(g,mx,my,d);g.centeredText(font,timer==null?"ModularTimer · lokale Welt":timer.name+" · "+MpsqTimerClock.label(timer.clock.seconds()),width/2,20,0xFFFFFFFF);if(timer!=null){g.centeredText(font,tab==0?"Startzeit · läuft auch bei geschlossenem Menü":tab==1?"Farbe (HEX) und Größe unten":tab==2?"X / Y / Z · darunter Drehung / Neigung":"Nachricht, Sound-ID oder Feuerwerk: minecraft",width/2,72,0xFFBBBBBB);}g.centeredText(font,font.plainSubstrByWidth(status,width-16),width/2,height-42,0xFFFFD080);}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}

