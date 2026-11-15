package de.galaxushd.mpsqcamera;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

/** Configuration shared by the donor, voter and trophy furniture figures. */
final class MpsqFigureSkinScreen extends Screen {
    private final Screen parent;
    private final Consumer<String> apply;
    private String value;
    private EditBox name;
    private String message="";
    MpsqFigureSkinScreen(Screen parent,String value,Consumer<String> apply){super(Component.literal("Top-Figur – Spieler-Skin"));this.parent=parent;this.value=value;this.apply=apply;}
    @Override protected void init(){
        if(name!=null)value=name.getValue();
        int y=height/2-45;
        name=addRenderableWidget(new EditBox(font,width/2-120,y,240,20,Component.literal("Minecraft-Spielername")));
        name.setMaxLength(16);name.setHint(Component.literal("Minecraft-Spielername"));name.setValue(value);
        addRenderableWidget(Button.builder(Component.literal("Skin laden"),b->{String n=name.getValue().trim();if(!MpsqFigureSkins.valid(n)){message="Bitte einen gültigen Java-Spielernamen eingeben.";return;}message="";MpsqFigureSkins.retry(n);}).bounds(width/2-120,y+28,116,20).build());
        addRenderableWidget(Button.builder(Component.literal("Original-Skin"),b->{name.setValue("");message="Original-Skin ausgewählt.";}).bounds(width/2+4,y+28,116,20).build());
        addRenderableWidget(Button.builder(Component.literal("Übernehmen"),b->{String n=name.getValue().trim();if(!n.isEmpty()&&!MpsqFigureSkins.valid(n)){message="Ungültiger Spielername.";return;}apply.accept(n);onClose();}).bounds(width/2-120,height-50,116,20).build());
        addRenderableWidget(Button.builder(Component.literal("Abbrechen"),b->onClose()).bounds(width/2+4,height-50,116,20).build());
    }
    @Override public void extractBackground(GuiGraphicsExtractor c,int x,int y,float d){super.extractBackground(c,x,y,d);MpsqTheme.drawBackground(c,width,height);}
    @Override public void extractRenderState(GuiGraphicsExtractor c,int x,int y,float d){
        super.extractRenderState(c,x,y,d);c.centeredText(font,title,width/2,22,0xFFFFFFFF);
        c.centeredText(font,"Nur die Figur erhält den Skin. Der Sockel bleibt erhalten.",width/2,42,0xFFAAAAAA);
        String n=name.getValue().trim();
        // Show the resolved skin's face without changing the placed furniture before Save.
        if(MpsqFigureSkins.valid(n)){
            var skin=MpsqFigureSkins.peek(n);
            if(skin!=null){int px=28;c.blit(skin.body().texturePath(),width/2-px/2,height/2+20,px,px,8f/64,8f/64,16f/64,16f/64);c.blit(skin.body().texturePath(),width/2-px/2,height/2+20,px,px,40f/64,8f/64,48f/64,16f/64);}
        }
        c.centeredText(font,message.isBlank()?MpsqFigureSkins.status(n):message,width/2,height-68,0xFFFFFFFF);
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
