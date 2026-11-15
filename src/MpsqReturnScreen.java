package de.galaxushd.mpsqcamera;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class MpsqReturnScreen extends Screen {
    private final Screen parent;
    private EditBox amount;
    private String value="20",status="";
    private int page;
    public MpsqReturnScreen(Screen parent){super(Component.literal("Rückkehr-Auslosung"));this.parent=parent;}
    @Override protected void init(){
        if(!MpsqParticipation.allowed())return;
        int x=width/2-150;
        amount=addRenderableWidget(new EditBox(font,x,48,45,20,Component.literal("Anzahl 1–50")));amount.setMaxLength(2);amount.setValue(value);amount.setResponder(v->value=v);
        addRenderableWidget(Button.builder(Component.literal("Auslosen (1–50)"),b->{try{status=MpsqParticipation.draw(Integer.parseInt(amount.getValue()));}catch(NumberFormatException e){status="Bitte eine Zahl von 1 bis 50 eingeben.";}rebuildWidgets();}).bounds(x+51,48,150,20).build());
        var names=MpsqParticipation.selected();int size=Math.max(1,(height-145)/24);page=Math.max(0,Math.min(page,Math.max(0,(names.size()-1)/size)));
        for(int i=page*size;i<Math.min(names.size(),(page+1)*size);i++){
            String name=names.get(i);int y=80+(i-page*size)*24;
            addRenderableWidget(Button.builder(Component.literal(name+" · Zurückgebracht ✓"),b->{status=MpsqParticipation.confirm(name);rebuildWidgets();}).bounds(x,y,300,20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("◀"),b->{page--;rebuildWidgets();}).bounds(x,height-55,40,20).build());
        addRenderableWidget(Button.builder(Component.literal("▶"),b->{page++;rebuildWidgets();}).bounds(x+260,height-55,40,20).build());
        addRenderableWidget(Button.builder(Component.literal("Zurück"),b->onClose()).bounds(width/2-50,height-26,100,20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){super.extractRenderState(g,x,y,d);
        g.centeredText(font,title,width/2,12,0xFFFFFFFF);
        g.centeredText(font,"Kandidaten: "+MpsqParticipation.available()+" · Ausgewählt: "+MpsqParticipation.selected().size(),width/2,28,0xFF55FFFF);
        g.centeredText(font,status,width/2,height-69,0xFFFFFF55);
        g.centeredText(font,"Lokale Liste · Rückkehr erst nach manuellem Zurückbringen bestätigen",width/2,height-37,0xFFBBBBBB);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
