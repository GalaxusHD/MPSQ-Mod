package de.galaxushd.mpsqcamera;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
/** Selection pauses the recording clock so time spent in the menu is not replayed. */
final class MpsqRecordingAnimationScreen extends Screen {
 private int page;
 MpsqRecordingAnimationScreen(){super(Component.literal("Animation für die Aufnahme"));}
 @Override protected void init(){
  var clips=MpsqScenesClips.all();int rows=Math.max(1,(height-95)/24),count=rows*2;
  page=Math.min(page,Math.max(0,(clips.size()-1)/count));int start=page*count,w=Math.min(210,(width-30)/2);
  for(int i=0;i<count&&start+i<clips.size();i++){var clip=clips.get(start+i);int x=width/2-w-4+(i%2)*(w+8),y=28+(i/2)*24;
   addRenderableWidget(Button.builder(Component.literal(clip.label()),b->MpsqMotionCapture.selectAnimation(clip.id())).bounds(x,y,w,20).build());}
  addRenderableWidget(Button.builder(Component.literal("Zurück"),b->{page=Math.max(0,page-1);rebuildWidgets();}).bounds(width/2-155,height-54,100,20).build());
  addRenderableWidget(Button.builder(Component.literal("Weiter"),b->{page++;rebuildWidgets();}).bounds(width/2+55,height-54,100,20).build());
  addRenderableWidget(Button.builder(Component.literal("Ohne Animation fortsetzen"),b->onClose()).bounds(width/2-115,height-28,230,20).build());
 }
 @Override public void onClose(){minecraft.gui.setScreen(null);}
 @Override public boolean isPauseScreen(){return false;}
}
