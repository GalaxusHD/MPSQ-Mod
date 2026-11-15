package de.galaxushd.mpsqcamera;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

final class MpsqChessPromotionScreen extends Screen {
    private final List<MpsqChessRules.Move> choices;
    MpsqChessPromotionScreen(List<MpsqChessRules.Move> choices){super(Component.literal("Bauernumwandlung"));this.choices=List.copyOf(choices);}
    @Override protected void init(){String[] labels={"♕ Dame","♖ Turm","♗ Läufer","♘ Springer"};char[] pieces={'q','r','b','n'};int w=Math.min(200,width-24);for(int i=0;i<4;i++){char piece=pieces[i];addRenderableWidget(Button.builder(Component.literal(labels[i]),b->{choices.stream().filter(m->m.promotion()==piece).findFirst().ifPresent(MpsqChess::move);onClose();}).bounds((width-w)/2,height/2-45+i*24,w,20).build());}}
    @Override public void tick(){if(!MpsqChess.game.active())onClose();}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){super.extractRenderState(g,x,y,d);g.centeredText(font,title,width/2,height/2-70,0xFFFFFFFF);}
    @Override public void onClose(){minecraft.gui.setScreen(null);}
    @Override public boolean isPauseScreen(){return false;}
}
