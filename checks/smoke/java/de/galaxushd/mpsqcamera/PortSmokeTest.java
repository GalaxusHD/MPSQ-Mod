package de.galaxushd.mpsqcamera;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
public final class PortSmokeTest implements FabricClientGameTest {
 private static boolean renderModels;
 private static net.minecraft.client.renderer.entity.state.AvatarRenderState lastNpcState;
 private static void registerModels() {
  de.galaxushd.mpsqcamera.compat.WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
   var client=net.minecraft.client.Minecraft.getInstance();
   if (!renderModels || client.player==null) return;
   var camera=ctx.camera().getPos();var p=client.player;
   var skin=new MpsqNpcSkinRenderer.Skin(p.getSkin().body().texturePath(),false);
   var state=MpsqNpcSkinRenderer.createState(skin,180,0,0,true);lastNpcState=state;
   MpsqNpcSkinRenderer.render(state,p.getX()+2-camera.x,p.getY()-camera.y,p.getZ()+3-camera.z,1,ctx.matrixStack(),ctx.consumers(),0xF000F0,0xFFAA00);
   MpsqBudgieModel.render(ctx.matrixStack(),ctx.consumers(),0xF000F0,(float)(p.getX()-2-camera.x),(float)(p.getY()-camera.y),(float)(p.getZ()+3-camera.z),180,0.58f,"green","walk",1.0);
  });
 }

 private static void checkCollection(ClientGameTestContext context,int x,int y,Class<?> expected) {
  context.setScreen(() -> new MpsqCollectionScreen(null));context.waitTicks(3);
  context.runOnClient(c -> {
   var screen=c.gui.screen();float scale=Math.max(.1f,Math.min(1f,Math.min((screen.width-16f)/352,(screen.height-16f)/264)));
   double left=(screen.width-Math.round(352*scale))/2,top=(screen.height-Math.round(264*scale))/2;
   screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(left+x*scale,top+y*scale,new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),false);
   if(!expected.isInstance(c.gui.screen()))throw new AssertionError("Collection click did not open "+expected.getSimpleName());
  });context.waitTicks(3);
 }
 public void runTest(ClientGameTestContext context) {
  context.takeScreenshot("mpsq-26.3-title");
  try (var world = context.worldBuilder().create()) {
   world.getConnection().waitForChunksRender();
   world.getServer().runCommand("execute at @p run summon minecraft:villager ~ ~ ~3 {NoAI:1b,Glowing:1b}");
   world.getServer().runCommand("give @p minecraft:clock");
   context.runOnClient(c -> {
    registerModels();renderModels=true;
    var npc=new com.google.gson.JsonObject();npc.addProperty("asset_id","mpsq_wumpus");npc.addProperty("scale",1.0);npc.addProperty("face_player",true);
    MpsqLocalNpcStore.set(MpsqActionSync.world(),c.player.blockPosition().offset(-2,0,2),false,npc);MpsqAccessoryRenderer.refresh();
   });
   context.waitTicks(60);
   context.takeScreenshot("mpsq-26.3-world");
   if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("skinlayers3d"))context.runOnClient(c->{
    try {
     var ext=Class.forName("dev.tr7zw.transition.mc.entitywrapper.EntityRenderStateExtender");
     Object proxy=ext.getMethod("getTransitionEntity").invoke(lastNpcState);
     if(proxy==null)throw new AssertionError("Skin Layers NPC data missing");
     if(Class.forName("dev.tr7zw.skinlayers.accessor.PlayerSettings").getMethod("getHeadMesh").invoke(proxy)==null)throw new AssertionError("Skin Layers mesh was not generated");
     if(c.level.getEntity(((net.minecraft.world.entity.Entity)proxy).getId())!=null)throw new AssertionError("Render proxy entered world");
    }catch(ReflectiveOperationException e){throw new AssertionError(e);}
   });
   if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("flashback")) {
    context.runOnClient(c->{
     try {
      var f=Class.forName("com.moulberry.flashback.Flashback");
      if(f.getField("RECORDER").get(null)!=null)f.getMethod("finishRecordingReplay").invoke(null);
      Object config=f.getMethod("getConfig").invoke(null);
      Object controls=config.getClass().getField("recordingControls").get(config);
      controls.getClass().getField("quicksave").setBoolean(controls,true);
      MpsqReplayManager.transition("dalgona");
      if(f.getField("RECORDER").get(null)==null||!MpsqExternalReplay.active())throw new AssertionError("Flashback recording did not start");
     }catch(ReflectiveOperationException e){throw new AssertionError(e);}
    });
    context.waitTicks(40);
    context.runOnClient(c->{
     MpsqReplayManager.finish();
     try {if(Class.forName("com.moulberry.flashback.Flashback").getField("RECORDER").get(null)!=null)throw new AssertionError("Flashback recording did not stop");}
     catch(ReflectiveOperationException e){throw new AssertionError(e);}
    });
    context.waitTicks(40);
    context.runOnClient(c->{
     try {
      var f=Class.forName("com.moulberry.flashback.Flashback");f.getMethod("startRecordingReplay").invoke(null);
      Object manual=f.getField("RECORDER").get(null);
      if(manual==null)throw new AssertionError("Manual recording setup failed");
      MpsqReplayManager.transition("dalgona");MpsqReplayManager.finish();
      if(f.getField("RECORDER").get(null)!=manual)throw new AssertionError("Manual recording was interrupted");
      f.getMethod("finishRecordingReplay").invoke(null);
     }catch(ReflectiveOperationException e){throw new AssertionError(e);}
    });
   }

   checkCollection(context,100,160,MpsqOwnedAccessoriesScreen.class);
   checkCollection(context,245,160,MpsqPetMenuScreen.class);
   checkCollection(context,320,230,MpsqCollectionTutorialScreen.class);
   context.setScreen(() -> new MpsqAccessoriesScreen(null));
   context.waitTicks(30);
   context.takeScreenshot("mpsq-26.3-accessories");
   context.setScreen(() -> null);
   context.runOnClient(c -> c.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
   context.waitTicks(20);
   context.takeScreenshot("mpsq-26.3-player");
   context.runOnClient(c -> {renderModels=false;c.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);});
   context.waitTicks(10);
  }
  System.out.println("MPSQ_PORT_SMOKE_PASS");
 }
}
