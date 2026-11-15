package de.galaxushd.mpsqcamera;
import de.galaxushd.mpsqcamera.compat.WorldRenderContext;
import net.minecraft.resources.Identifier;
final class MpsqNpcLabels {
 static double height(MpsqCinematicScene.Npc n,float scale){
  float h=n.category.startsWith("npc_skin")?1.8f:n.type.equals(MpsqAccessoryRenderer.WUMPUS_ASSET_ID)?1.45f:1f;
  if(n.category.equals("mob")){var id=Identifier.tryParse(n.type);if(id!=null)h=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(id).getDimensions().height();}
  return h*scale+.5;
 }
 static String name(MpsqCinematicScene.Npc n){if(!MpsqNpcGlow.lockedTag(n).isBlank())return "";var c=net.minecraft.client.Minecraft.getInstance();return n.name.equals("NPC")&&n.category.startsWith("npc_skin")&&c.player!=null?c.player.getGameProfile().name():n.name;}
 private static void shadow(WorldRenderContext ctx,double x,double y,double z,float w,float h){
  var m=ctx.matrixStack();m.pushPose();m.translate(x,y,z);m.rotate(ctx.camera().getRotation());var v=ctx.consumers().getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.debugQuads());
  float left=-w/2-.025f,right=w/2+.025f,bottom=-h/2-.025f,top=h/2+.025f;
  v.addVertex(m.last(),left,bottom,-.003f).setColor(0x40000000);v.addVertex(m.last(),right,bottom,-.003f).setColor(0x40000000);v.addVertex(m.last(),right,top,-.003f).setColor(0x40000000);v.addVertex(m.last(),left,top,-.003f).setColor(0x40000000);m.popPose();
 }
 static void draw(WorldRenderContext ctx,MpsqCinematicScene.Npc n,MpsqCinematicActors.Pose pose,float scale){
  if(ctx.matrixStack()==null||ctx.consumers()==null)return;
  if(ctx.camera().getPos().distanceToSqr(pose.x(),pose.y(),pose.z())>MpsqRenderRange.squared())return;
  var client=net.minecraft.client.Minecraft.getInstance();String tag=MpsqNpcGlow.tag(n),name=name(n);
  var rank=java.util.Arrays.stream(TeamRank.values()).filter(r->r.id().equals(tag)).findFirst();
  if(rank.isPresent()){
   var text=MpsqNametags.forRankAndName(rank.get(),name);
   var cam=ctx.camera().getPos();var m=ctx.matrixStack();m.pushPose();
   m.translate(pose.x()-cam.x,pose.y()+height(n,scale)-cam.y,pose.z()-cam.z);
   ctx.consumers().collector().submitNameTag(m,new net.minecraft.world.phys.Vec3(0,-.5+client.font.lineHeight*.0125,0),0,text,true,0xF000F0,ctx.consumers().camera());
   m.popPose();return;
  }
  float aspect=rank.isPresent()?rank.get().widthForHeight(320)/320f:switch(tag){case "accessories"->2624f/320f;case "quests"->1504f/320f;default->2176f/320f;};
  var text=n.name.equals("NPC")&&tag.isBlank()&&n.category.startsWith("npc_skin")?MpsqNametags.forPlayer(name):net.minecraft.network.chat.Component.literal(name);
  float h=.2f,tagW=tag.isBlank()?0:h*aspect,nameW=client.font.width(text)*.025f,gap=tagW>0&&!name.isBlank()?.10f:0,total=tagW+gap+nameW;
  double y=pose.y()+height(n,scale);var cam=ctx.camera().getPos();
  if(!tag.isBlank()){
   Identifier texture=rank.isPresent()?rank.get().texture():Identifier.fromNamespaceAndPath("mpsqcamera","textures/gui/npc_tags/"+tag+".png");
   var offset=new org.joml.Vector3f(-(nameW+gap)/2,0,0).rotate(ctx.camera().getRotation());
   if(!name.isBlank())shadow(ctx,pose.x()-cam.x+offset.x,y-cam.y,pose.z()-cam.z+offset.z,tagW,h);
   MpsqAccessoryRenderer.drawBillboard(ctx,ctx.matrixStack(),ctx.consumers(),texture,pose.x()-cam.x+offset.x,y-cam.y,pose.z()-cam.z+offset.z,tagW,h);
  }
  if(!name.isBlank()){
   var offset=new org.joml.Vector3f((tagW+gap)/2,0,0).rotate(ctx.camera().getRotation());
   var m=ctx.matrixStack();m.pushPose();m.translate(pose.x()-cam.x+offset.x,y-cam.y,pose.z()-cam.z+offset.z);
   ctx.consumers().collector().submitNameTag(m,new net.minecraft.world.phys.Vec3(0,-.5+client.font.lineHeight*.0125,0),0,text,true,0xF000F0,ctx.consumers().camera());m.popPose();
  }
 }
}
