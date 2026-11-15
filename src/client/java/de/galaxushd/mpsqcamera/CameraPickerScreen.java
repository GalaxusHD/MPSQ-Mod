package de.galaxushd.mpsqcamera;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Reusable, click-safe camera chooser for screen creation. */
public final class CameraPickerScreen extends Screen {
    private final Screen parent; private final Consumer<LocalCameraStore.CameraData> selected; private int scroll;
    public CameraPickerScreen(Screen parent, Consumer<LocalCameraStore.CameraData> selected) { super(Component.literal("Kamera auswählen")); this.parent = parent; this.selected = selected; }
    @Override protected void init() { addRenderableWidget(Button.builder(Component.translatable("gui.mpsqcamera.back"), b -> minecraft.gui.setScreen(parent)).bounds(width / 2 - 75, height - 36, 150, 20).build()); }
    @Override public void extractBackground(GuiGraphicsExtractor c, int x, int y, float d) { super.extractBackground(c, x, y, d); MpsqTheme.drawBackground(c, width, height); }
    @Override public void extractRenderState(GuiGraphicsExtractor c, int x, int y, float d) { super.extractRenderState(c, x, y, d); int cx=width/2, row=58-scroll; c.centeredText(font,title,cx,28,MpsqTheme.TEXT_TITEL); List<LocalCameraStore.CameraData> cameras=LocalCameraStore.getAll(); if(cameras.isEmpty()) { c.centeredText(font,Component.literal("Keine Kameras vorhanden."),cx,72,MpsqTheme.TEXT_GEDAEMPT); return; } for(LocalCameraStore.CameraData camera:cameras){ if(row>=54&&row<height-62){ c.fill(cx-140,row,cx+140,row+20,0x55000000); c.centeredText(font,Component.literal(camera.name()),cx,row+6,MpsqTheme.TEXT_NORMAL); } row+=22; } }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        double x = event.x(), y = event.y();
        int button = event.button();
 if(button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT){ int row=58-scroll; for(LocalCameraStore.CameraData camera:LocalCameraStore.getAll()){ if(y>=row&&y<row+20&&row>=54&&row<height-62){ selected.accept(camera); minecraft.gui.setScreen(parent); return true;} row+=22; }} return super.mouseClicked(event, doubled); }
    @Override public boolean mouseScrolled(double x,double y,double h,double v){ scroll=Math.max(0,scroll-(int)Math.signum(v)*44); return true; }
    @Override public boolean isPauseScreen(){ return false; }
}
