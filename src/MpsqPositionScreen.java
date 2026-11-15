package de.galaxushd.mpsqcamera;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** Shared live position controls; one save at a time prevents lost button presses. */
final class MpsqPositionScreen extends Screen {
    private final Screen parent;
    private final Function<Vec3,CompletableFuture<Void>> save;
    private final net.minecraft.client.multiplayer.ClientLevel world;
    private Vec3 position;
    private boolean pending;
    private String status="";
    MpsqPositionScreen(Screen parent,String label,Vec3 position,Function<Vec3,CompletableFuture<Void>> save){
        super(Component.literal(label));this.parent=parent;this.position=position;this.save=save;
        world=net.minecraft.client.Minecraft.getInstance().level;
    }
    @Override protected void init(){
        int y=Math.max(55,height/2-48);
        for(int axis=0;axis<3;axis++){final int a=axis;
            addRenderableWidget(new MpsqScenesWidget(width/2-122,y+axis*28,118,24,"XYZ".charAt(axis)+" −0,1",null,()->move(a,-.1)).transparent());
            addRenderableWidget(new MpsqScenesWidget(width/2+4,y+axis*28,118,24,"XYZ".charAt(axis)+" +0,1",null,()->move(a,.1)).transparent());
        }
        addRenderableWidget(new MpsqScenesWidget(width/2-60,height-28,120,20,"Zurück",null,this::onClose).transparent());
    }
    private void move(int axis,double amount){
        if(pending||minecraft.level!=world)return;
        Vec3 next=position.add(axis==0?amount:0,axis==1?amount:0,axis==2?amount:0);
        try{TeamVisibilitySettings.requirePlacement(next.x,next.y,next.z);pending=true;status="Speichert …";
            save.apply(next).whenComplete((ignored,error)->minecraft.execute(()->{
                pending=false;if(error==null){position=next;status="Gespeichert";}else status="Nicht gespeichert: "+error.getMessage();
            }));
        }catch(Exception e){pending=false;status=e.getMessage();}
    }
    @Override public void tick(){if(minecraft.level!=world)minecraft.gui.setScreen(null);}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){g.fill(0,0,width,height,0x40102638);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){
        super.extractRenderState(g,x,y,d);g.centeredText(font,title,width/2,20,0xFFFFFFFF);
        g.centeredText(font,String.format(java.util.Locale.GERMANY,"X %.2f   Y %.2f   Z %.2f",position.x,position.y,position.z),width/2,36,0xFFFFFFFF);
        g.centeredText(font,status,width/2,height-44,0xFFFFFFFF);
    }
    @Override public void onClose(){if(!pending)minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
