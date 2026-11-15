package de.galaxushd.mpsqcamera;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** The permission and board identity are checked again on every edit. */
final class MpsqChessEditScreen extends Screen {
    private final MpsqChess.Board target;
    private String error="";
    MpsqChessEditScreen(MpsqChess.Board target){super(Component.literal("Schachbrett bearbeiten"));this.target=target;}
    private void edit(Runnable action){try{action.run();onClose();}catch(Exception e){error=e.getMessage();}}
    @Override protected void init(){
        int w=Math.min(250,width-24),x=(width-w)/2,y=height/2-22;
        addRenderableWidget(Button.builder(Component.literal("Schachbrett löschen"),b->edit(()->MpsqChess.deleteBoard(target))).bounds(x,y,w,20).build());
        addRenderableWidget(Button.builder(Component.literal("Schachbrett drehen (+90°)"),b->edit(()->MpsqChess.rotateBoard(target))).bounds(x,y+26,w,20).build());
    }
    @Override public void tick(){if(!MpsqChess.canEdit()||MpsqChess.board!=target)onClose();}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float delta){super.extractRenderState(g,x,y,delta);g.centeredText(font,title,width/2,height/2-52,0xFFFFFFFF);g.centeredText(font,"Esc: schließen",width/2,height/2+38,0xFFBBBBBB);if(!error.isEmpty())g.centeredText(font,font.plainSubstrByWidth(error,width-16),width/2,height/2+53,0xFFFF8888);}
    @Override public void onClose(){minecraft.gui.setScreen(null);}
    @Override public boolean isPauseScreen(){return false;}
}
