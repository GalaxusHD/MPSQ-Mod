package de.galaxushd.mpsqcamera;

import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

/** Local camera-only preview. Never teleports the actual player. */
public final class MpsqCinematicPreview extends Screen {
    private static MpsqCinematicPreview active;
    private final Screen parent;
    private final MpsqCinematicScene scene;
    private final double start;
    private ArmorStand camera;
    private Entity previous;
    private CameraType perspective;
    private long began;
    private boolean closed, cursorCaptured;
    private MpsqCinematicEvents events;
    private double elapsed;

    public MpsqCinematicPreview(Screen parent,MpsqCinematicScene scene,double start){super(Component.literal("Kameravorschau"));this.parent=parent;this.scene=scene;this.start=start;}
    public static boolean active(){return active!=null;}
    @Override protected void init(){
        if(camera!=null)return;
        if(minecraft.level==null||minecraft.player==null||scene.points.isEmpty()||!TeamVisibilitySettings.inArea()||ScreenCreationManager.isCameraViewActive()){onClose();return;}
        previous=minecraft.getCameraEntity();perspective=minecraft.options.getCameraType();
        camera=new ArmorStand(minecraft.level,0,0,0);camera.setId(-1900000001);camera.setInvisible(true);camera.setNoGravity(true);
        place(scene.sample(start));minecraft.level.addEntity(camera);minecraft.setCameraEntity(camera);minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        began=System.nanoTime();active=this;MpsqCinematicActors.start(scene);events=new MpsqCinematicEvents(scene,start);
    }
    /** Called before world rendering, so position and rotation follow the same frame clock. */
    public static void frame(){
        var a=active;if(a==null)return;var c=Minecraft.getInstance();
        if(c.level==null||c.player==null||c.level!=a.camera.level()||c.gui.screen()!=a||!c.player.isAlive()){a.finish();return;}
        if(!TeamVisibilitySettings.inArea()){a.onClose();return;}
        a.updateCursor();
        double t=a.start+(System.nanoTime()-a.began)/1_000_000_000.0;
        a.elapsed=Math.min(t,a.scene.duration());a.events.advance(a.elapsed);MpsqCinematicActors.advance(a.elapsed);
        if(t>a.scene.duration()+0.25){a.onClose();return;}a.place(a.scene.sample(t));
    }
    private void updateCursor(){
        boolean capture=!closed&&minecraft.isWindowActive()&&minecraft.gui.screen()==this;
        if(capture==cursorCaptured)return;
        if(capture){
            var window=minecraft.getWindow();
            com.mojang.blaze3d.platform.InputConstants.grabMouse(window,window.getScreenWidth()/2.0,window.getScreenHeight()/2.0);
            cursorCaptured=true;
        }else releaseCursor();
    }
    private void releaseCursor(){
        if(!cursorCaptured)return;
        var window=minecraft.getWindow();
        com.mojang.blaze3d.platform.InputConstants.releaseMouse(window,window.getScreenWidth()/2.0,window.getScreenHeight()/2.0);
        cursorCaptured=false;
    }
    @Override public void tick(){updateCursor();}
    private void place(MpsqCinematicScene.Point p){
        camera.snapTo(p.x,p.y-camera.getEyeHeight(),p.z,p.yaw,p.pitch);
        camera.setYHeadRot(p.yaw);camera.setYBodyRot(p.yaw);camera.yRotO=p.yaw;camera.xRotO=p.pitch;camera.yHeadRotO=p.yaw;
    }
    private void finish(){
        if(closed)return;closed=true;releaseCursor();if(events!=null)events.stop();MpsqCinematicActors.stop();if(active==this)active=null;
        if(camera!=null){if(minecraft.getCameraEntity()==camera)minecraft.setCameraEntity(previous!=null&&previous.level()==minecraft.level&&!previous.isRemoved()?previous:minecraft.player);minecraft.options.setCameraType(perspective);camera.discard();}
    }
    @Override public void removed(){finish();}
    @Override public void onClose(){finish();if(minecraft!=null)minecraft.gui.setScreen(minecraft.level==null?null:parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float delta){}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float delta){
        if(scene.bars){int h=Math.max(12,height/10);g.fill(0,0,width,h,0xFF000000);g.fill(0,height-h,width,height,0xFF000000);}
        if(events!=null){int alpha=events.fade(elapsed);if(alpha>0)g.fill(0,0,width,height,alpha<<24);}
        if(events!=null){var dialogue=events.dialogue(elapsed);if(!dialogue.getString().isBlank()){
            var lines=font.split(dialogue,Math.max(80,width-40));int textY=height-Math.max(26,height/10)-lines.size()*11;
            g.fill(12,textY-4,width-12,textY+lines.size()*11+2,0x99000000);
            for(var line:lines){g.text(font,line,(width-font.width(line))/2,textY,0xFFFFFFFF);textY+=11;}
        }}
        g.centeredText(font,parent==null?"Kamerafahrt · Esc: beenden":"Vorschau · Esc: zurück zum Editor",width/2,height-12,0xFFFFFFFF);
    }
}

