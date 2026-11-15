package de.galaxushd.mpsqcamera;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.*;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;

/** A real custom widget: AbstractButton would paint the vanilla sprite underneath. */
final class MpsqScenesWidget extends AbstractWidget {
    private final String icon;
    private final Runnable action;
    private boolean transparent;
    MpsqScenesWidget transparent(){transparent=true;return this;}
    MpsqScenesWidget(int x,int y,int w,int h,String label,String icon,Runnable action){
        super(x,y,w,h,Component.literal(label));this.icon=icon;this.action=action;setTooltip(Tooltip.create(Component.literal(label)));
    }
    void press(){if(active)action.run();}
    @Override public void onClick(MouseButtonEvent e,boolean twice){press();}
    @Override public boolean keyPressed(KeyEvent e){if(isFocused()&&(e.key()==257||e.key()==32||e.key()==335)){press();return true;}return false;}
    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        if(icon!=null){g.blit(RenderPipelines.GUI_TEXTURED,Identifier.fromNamespaceAndPath("mpsqcamera","textures/gui/scenes/"+icon+".png"),getX()+1,getY()+1,0,0,getWidth()-2,getHeight()-2,16,16,16,16);}
        else{int x=getX(),y=getY();g.fill(x,y,x+width,y+1,0xFFDFAF20);g.fill(x,y+height-1,x+width,y+height,0xFFDFAF20);g.fill(x,y+1,x+1,y+height-1,0xFFDFAF20);g.fill(x+width-1,y+1,x+width,y+height-1,0xFFDFAF20);g.fill(x+1,y+1,x+width-1,y+height-1,transparent?0x1A102638:0xFF102638);var f=Minecraft.getInstance().font;String text=f.plainSubstrByWidth(getMessage().getString(),width-6);g.centeredText(f,text,x+width/2,y+(height-9)/2,0xFFFFFFFF);}
        if(isHoveredOrFocused())g.fill(getX(),getY(),getX()+width,getY()+height,0x30FFFFFF);
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output){output.add(NarratedElementType.TITLE,getMessage());}
}
