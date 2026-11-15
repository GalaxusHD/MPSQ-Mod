package de.galaxushd.mpsqcamera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.*;
/** Also runs during held-button repeats, before Minecraft can touch a real block. */
public final class MpsqClickShield {
 public static boolean blocks(){
  var c=Minecraft.getInstance();if(c.player==null||c.level==null||c.gui.screen()!=null||!TeamVisibilitySettings.visible())return false;
  if(MpsqChess.blocks())return true;
  Vec3 eye=c.player.getEyePosition(),end=c.hitResult instanceof BlockHitResult b&&b.getType()==HitResult.Type.BLOCK?b.getLocation():eye.add(c.player.getViewVector(1).scale(6));
  if(ScreenRenderer.screenOccludesBlock(eye,end))return true;
  if(!TeamVisibilitySettings.inArea())return false;
  if(MpsqCameraInteraction.target()!=null||MpsqFurnitureManager.blocksClick()||MpsqNpcManager.targeted(c.player)!=null)return true;
  try{return MpsqScenes.target()!=null;}catch(Exception e){return false;}
 }
}
