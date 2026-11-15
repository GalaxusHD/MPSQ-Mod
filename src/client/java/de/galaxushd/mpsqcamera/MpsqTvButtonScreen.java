package de.galaxushd.mpsqcamera;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class MpsqTvButtonScreen extends Screen {
    private final MpsqTvFurniture.Hit hit;
    MpsqTvButtonScreen(MpsqTvFurniture.Hit hit){super(Component.literal("TV-Knopf "+(hit.button()+1)));this.hit=hit;}
    @Override protected void init(){
        addRenderableWidget(Button.builder(Component.literal("MPSQ-Redstone-Aktion"),b->{
            MpsqActionSetupScreen editor=new MpsqActionSetupScreen(hit.anchor(),"mpsq:tv_button","TV-Knopf "+(hit.button()+1),MpsqTriggerBlockPolicy.Kind.BUTTON,"");
            editor.virtualButton(hit.screen(),(type,data)->MpsqTvFurniture.saveButton(hit,type,data));minecraft.gui.setScreen(editor);
        }).bounds(width/2-110,65,220,20).build());
        addRenderableWidget(Button.builder(Component.literal("Kamera anzeigen"),b->minecraft.gui.setScreen(new CameraPickerScreen(this,camera->{JsonObject data=new JsonObject();data.addProperty("cameraId",camera.id().toString());MpsqTvFurniture.message(MpsqTvFurniture.saveButton(hit,"TV_CAMERA",data)?"TV-Kamera gespeichert.":"Speichern fehlgeschlagen.");}))).bounds(width/2-110,91,220,20).build());
        addRenderableWidget(Button.builder(Component.literal("Bildschirm-Einstellungen"),b->minecraft.gui.setScreen(new BildschirmDetailScreen(this,hit.screen().toString(),"MPSQ TV",true))).bounds(width/2-110,117,220,20).build());
        addRenderableWidget(Button.builder(Component.literal("Schließen"),b->onClose()).bounds(width/2-110,143,220,20).build());
    }
    @Override public boolean isPauseScreen(){return false;}
}
