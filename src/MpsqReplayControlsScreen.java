package de.galaxushd.mpsqcamera;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class MpsqReplayControlsScreen extends Screen {
    private Button play,speed;
    public MpsqReplayControlsScreen(){super(Component.literal("Replay-Steuerung"));}
    @Override protected void init(){
        int y=height-66;
        addRenderableWidget(Button.builder(Component.literal("−10 s"),b->MpsqReplayPlayback.seek(MpsqReplayPlayback.position()-10000)).bounds(width/2-154,y,60,20).build());
        play=addRenderableWidget(Button.builder(Component.literal("Start"),b->MpsqReplayPlayback.pause()).bounds(width/2-90,y,80,20).build());
        addRenderableWidget(Button.builder(Component.literal("+10 s"),b->MpsqReplayPlayback.seek(MpsqReplayPlayback.position()+10000)).bounds(width/2-6,y,60,20).build());
        speed=addRenderableWidget(Button.builder(Component.literal("1×"),b->MpsqReplayPlayback.speedNext()).bounds(width/2+58,y,45,20).build());
        addRenderableWidget(Button.builder(Component.literal("Anfang"),b->{MpsqReplayPlayback.seek(0);if(MpsqReplayPlayback.playing())MpsqReplayPlayback.pause();}).bounds(width/2+107,y,58,20).build());
        addRenderableWidget(Button.builder(Component.literal("Freie Kamera (F8: Menü)"),b->onClose()).bounds(width/2-154,height-36,195,20).build());
        addRenderableWidget(Button.builder(Component.literal("Replay verlassen"),b->MpsqReplayPlayback.close()).bounds(width/2+45,height-36,120,20).build());
    }
    @Override public void tick(){play.setMessage(Component.literal(MpsqReplayPlayback.playing()?"Pause":"Start"));speed.setMessage(Component.literal(MpsqReplayPlayback.speed()+"×"));}
    @Override public void extractBackground(GuiGraphicsExtractor c,int x,int y,float d){}
    @Override public void extractRenderState(GuiGraphicsExtractor c,int x,int y,float d){
        c.fill(0,0,width,49,0xAA000000);c.fill(0,height-112,width,height,0xCC000000);super.extractRenderState(c,x,y,d);
        c.centeredText(font,MpsqReplayPlayback.label(),width/2,12,0xFFFFFFFF);
        c.centeredText(font,"WASD / Maus · Leertaste / Shift · Mausrad: Flugtempo · F8: Steuerung",width/2,30,0xFFCCCCCC);
        c.centeredText(font,time(MpsqReplayPlayback.position())+" / "+time(MpsqReplayPlayback.duration()),width/2,height-106,0xFFFFFFFF);
        c.fill(20,height-88,width-20,height-80,0xFF444444);int end=20+(int)((width-40)*MpsqReplayPlayback.position()/(double)Math.max(1,MpsqReplayPlayback.duration()));c.fill(20,height-88,end,height-80,0xFFFF3366);
        if(!MpsqReplayPlayback.error.isEmpty())c.centeredText(font,MpsqReplayPlayback.error,width/2,height/2,0xFFFF5555);
    }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        double x = event.x(), y = event.y();
        int button = event.button();
if(button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT&&y>=height-94&&y<=height-75&&x>=20&&x<=width-20){MpsqReplayPlayback.seek((long)((x-20)/(width-40)*MpsqReplayPlayback.duration()));return true;}return super.mouseClicked(event, doubled);}
    @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        double x = event.x(), y = event.y();
        int b = event.button();
if(b==0&&y>=height-94&&y<=height-75)return mouseClicked(event,false);return super.mouseDragged(event, dx,dy);}
    public static String time(long ms){long s=ms/1000;return String.format(java.util.Locale.ROOT,"%02d:%02d",s/60,s%60);}
    @Override public void onClose(){minecraft.gui.setScreen(null);}
    @Override public boolean isPauseScreen(){return false;}
}
