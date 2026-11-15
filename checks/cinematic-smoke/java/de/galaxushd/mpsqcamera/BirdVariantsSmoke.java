package de.galaxushd.mpsqcamera;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import com.mojang.blaze3d.platform.InputConstants;
final class BirdVariantsSmoke {
 static void click(net.minecraft.client.gui.screens.Screen s,int x,int y){QuestGuiSmoke.click(s,x,y,InputConstants.MOUSE_BUTTON_LEFT);}
 static boolean flight; static float phase; static double px,py,pz;
 static void run(ClientGameTestContext ctx){
  ctx.setScreen(()->new MpsqPetMenuScreen(null,MpsqPetMenuScreen.PetKind.ANIMALS));ctx.waitTicks(5);
  ctx.runOnClient(c->{click(c.gui.screen(),32,88);click(c.gui.screen(),32,126);if(!(boolean)QuestGuiSmoke.get(c.gui.screen(),"variantsOpen"))throw new AssertionError("Budgie variants toggle");});
  ctx.waitTicks(5);ctx.takeScreenshot("bird-budgie-ten-variants");
  ctx.runOnClient(c->{click(c.gui.screen(),178,150);if(!MpsqPetSelectionStore.budgieVariant().equals("yellow"))throw new AssertionError("Budgie selection");click(c.gui.screen(),32,126);if((boolean)QuestGuiSmoke.get(c.gui.screen(),"variantsOpen"))throw new AssertionError("Close variants");click(c.gui.screen(),140,88);click(c.gui.screen(),140,126);});
  ctx.waitTicks(5);ctx.takeScreenshot("bird-owl-four-variants");
  ctx.runOnClient(c->{click(c.gui.screen(),165,150);if(!MpsqPetSelectionStore.owlVariant().equals("nogs_owl_snowy"))throw new AssertionError("Owl selection");});
  ctx.waitTicks(5);ctx.takeScreenshot("bird-owl-selected-snowy");
  ctx.setScreen(()->null);
  ctx.runOnClient(c->{px=c.player.getX();py=c.player.getEyeY()-.5;pz=c.player.getZ()-4;c.player.setYRot(180);c.player.setXRot(0);
   de.galaxushd.mpsqcamera.compat.WorldRenderEvents.AFTER_ENTITIES.register(w->{if(!flight)return;var cam=w.camera().getPos();MpsqOwlModel.select("nogs_owl_snowy");MpsqOwlModel.render(w.matrixStack(),w.consumers(),0xF000F0,(float)(px-1-cam.x),(float)(py-cam.y),(float)(pz-cam.z),90,1,"walk",phase);MpsqAccessoryRenderer.drawCinematicModel(w,"builtin://mpsq/nm_seagull",px+1,py,pz,90,"walk",phase,0,0);});flight=true;
   if(!MpsqOwlPetRenderer.animation(c,px,c.player.getY()+3,pz).equals("walk"))throw new AssertionError("Airborne owl must fly");
  });ctx.waitTicks(25);ctx.takeScreenshot("bird-flight-pose-start");ctx.runOnClient(c->phase=.3f);ctx.waitTicks(3);ctx.takeScreenshot("bird-flight-pose-mid");ctx.runOnClient(c->flight=false);
  System.out.println("BIRD_VARIANTS_SMOKE_PASS");
 }
}

