package de.galaxushd.mpsqcamera;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.Files;

/** One latest completed recording per game. */
public final class MpsqReplayScreen extends Screen {
    private final Screen parent;
    private String status="Je Spiel die letzte abgeschlossene Aufnahme.";
    private boolean loading;
    public MpsqReplayScreen(Screen parent){super(Component.literal("Replay"));this.parent=parent;}
    @Override protected void init(){
        if(!MpsqExternalReplay.provider().isEmpty()) status="Neue Aufnahmen: "+MpsqExternalReplay.provider()+" · Spiele: bisherige MPSQ-Aufnahmen.";
        var options=MpsqSystemController.availableSystems();
        int w=Math.min(210,(width-36)/2),top=38;
        int gap=Math.min(24,Math.max(16,(height-110)/7)),buttonHeight=Math.min(20,gap-2);
        for(int i=0;i<options.size();i++){
            var option=options.get(i);
            int x=i==0?width/2-w/2:width/2+(i<=5?-w-4:4);
            int row=i==0?0:(i<=5?i:i-5);
            var button=addRenderableWidget(Button.builder(Component.literal(MpsqZoneCatalog.label(option.id())),b->load(option.id())).bounds(x,top+row*gap,w,buttonHeight).build());
            button.active=MpsqSystemController.implemented(option.id())&&!loading&&!MpsqReplayManager.isRecording()&&!MpsqReplayManager.isSaving()&&Files.isRegularFile(MpsqReplayManager.file(option.id()));
        }
        addRenderableWidget(Button.builder(Component.literal("Aufnahmeordner"),b->openRecordings()).bounds(width/2-w/2,top+6*gap,w,buttonHeight).build());
        addRenderableWidget(Button.builder(Component.literal("Aktualisieren"),b->rebuildWidgets()).bounds(width/2-105,height-30,100,20).build());
        addRenderableWidget(Button.builder(Component.literal("Zurück"),b->onClose()).bounds(width/2+5,height-30,100,20).build());
    }
    private void openRecordings(){
        if(!MpsqExternalReplay.provider().isEmpty()){MpsqExternalReplay.openDirectory();return;}
        try{Files.createDirectories(MpsqReplayManager.DIRECTORY);java.awt.Desktop.getDesktop().open(MpsqReplayManager.DIRECTORY.toFile());}
        catch(Exception e){status="Aufnahmeordner: "+MpsqReplayManager.DIRECTORY;}
    }
    private void load(String id){loading=true;status="Aufnahme wird geladen …";rebuildWidgets();
        MpsqReplayManager.load(id).whenComplete((data,error)->minecraft.execute(()->{
            if(minecraft.gui.screen()!=this)return;loading=false;
            if(error!=null){status="Aufnahme konnte nicht gelesen werden.";rebuildWidgets();return;}
            minecraft.gui.setScreen(new ConfirmScreen(confirmed->{if(confirmed){try{MpsqReplayPlayback.open(data);}catch(Exception e){MpsqReplayPlayback.reset();status="Replay konnte nicht geöffnet werden.";minecraft.gui.setScreen(this);MpsqCameraClient.LOGGER.error(status,e);}}else minecraft.gui.setScreen(this);},Component.literal("Replay öffnen?"),Component.literal("Die aktuelle Welt / Serververbindung wird verlassen. Dauer: "+MpsqReplayControlsScreen.time(data.duration)+". Herkunft: "+data.world+". Wiedergabe in einer separaten lokalen Zuschauerwelt.")));
        }));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor c,int x,int y,float delta){super.extractRenderState(c,x,y,delta);c.centeredText(font,title,width/2,18,0xFFFFFFFF);c.centeredText(font,font.plainSubstrByWidth(status,width-20),width/2,height-56,0xFFFFFFFF);c.centeredText(font,font.plainSubstrByWidth(MpsqReplayManager.status,width-20),width/2,height-43,0xFFAAAAAA);}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
