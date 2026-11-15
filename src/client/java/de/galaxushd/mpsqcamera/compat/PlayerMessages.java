package de.galaxushd.mpsqcamera.compat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
public final class PlayerMessages {
 private PlayerMessages(){}
 public static void send(Player player,Component message,boolean overlay){
  if(overlay)player.sendOverlayMessage(message);else player.sendSystemMessage(message);
 }
}
