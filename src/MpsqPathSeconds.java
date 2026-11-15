package de.galaxushd.mpsqcamera;
import de.galaxushd.mpsqcamera.compat.WorldRenderContext;
/** Time ruler shown only while editing routes, culled around the editor. */
final class MpsqPathSeconds {
 static boolean hasMovement(MpsqCinematicScene.Npc npc){
  boolean moving=false;
  var start=npc.position(npc.movementStart,false,"FORWARD");
  if(npc.recording!=null&&!npc.recording.isEmpty())moving=npc.recording.stream().anyMatch(f->Math.abs(f.x-start.x)+Math.abs(f.y-start.y)+Math.abs(f.z-start.z)>.01);
  else for(var point:npc.route)if(point!=null&&Math.abs(point.x-start.x)+Math.abs(point.y-start.y)+Math.abs(point.z-start.z)>.01){moving=true;break;}
  return moving;
 }
 static void draw(WorldRenderContext ctx,MpsqCinematicScene.Npc npc){
  if(!hasMovement(npc))return;
  double end=npc.movementStart;
  if(npc.recording!=null&&!npc.recording.isEmpty())end+=npc.recording.getLast().time;else{for(int i=1;i<npc.route.length;i++)if(npc.route[i]!=null)end+=npc.route[i].duration;end+=npc.closingDuration();}
 int step=Math.max(1,(int)Math.ceil((end-npc.movementStart)/180));
  for(double time=npc.movementStart;time<=end;time+=step){var point=npc.position(time,false,"FORWARD");var cam=ctx.camera().getPos();if(cam.distanceToSqr(point.x,point.y,point.z)>32*32)continue;
   var m=ctx.matrixStack();m.pushPose();m.translate(point.x-cam.x,point.y+.12-cam.y,point.z-cam.z);
   ctx.consumers().collector().submitNameTag(m,new net.minecraft.world.phys.Vec3(0,-.5,0),0,net.minecraft.network.chat.Component.literal(String.format(java.util.Locale.GERMANY,"%.0f s",time)),true,0xF000F0,ctx.consumers().camera());m.popPose();
  }
 }
}
