package de.galaxushd.mpsqcamera;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

final class MpsqChessScreen extends Screen {
    private String error="";
    MpsqChessScreen(){super(Component.literal("Schach · lokaler Test"));}
    @Override protected void init(){
        int w=Math.min(240,width-24),top=height/2-35;
        for(int i=0;i<2;i++){boolean white=i==0;var button=addRenderableWidget(Button.builder(Component.literal(white?"Weiß beitreten":"Schwarz beitreten"),b->{try{MpsqChess.join(white);onClose();}catch(Exception e){error=e.getMessage();}}).bounds((width-w)/2,top+i*26,w,20).build());button.active=!MpsqChess.game.active()&&!MpsqChess.game.joined(white);}
        addRenderableWidget(Button.builder(Component.literal(MpsqChess.game.active()?"Aufgeben":"Abbrechen"),b->{MpsqChess.resign();onClose();}).bounds((width-w)/2,top+52,w,20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float delta){super.extractRenderState(g,x,y,delta);g.centeredText(font,title,width/2,height/2-65,0xFFFFFFFF);g.centeredText(font,error.isEmpty()?MpsqChess.status():error,width/2,height/2+55,0xFFFFCC66);}
    @Override public void onClose(){minecraft.gui.setScreen(null);}
    @Override public boolean isPauseScreen(){return false;}
}
