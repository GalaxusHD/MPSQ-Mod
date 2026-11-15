package de.galaxushd.mpsqcamera;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Shared movement for local actors: world bounds, interpolation and ground animation state. */
final class MpsqNpcMotion {
 private static final MpsqWeakIdentityCache<LivingEntity,MpsqCinematicActors.Pose> previousPoses=new MpsqWeakIdentityCache<>();
 static void place(LivingEntity entity,MpsqCinematicActors.Pose pose,boolean interpolate){
  var last=previousPoses.put(entity,pose);
  Vec3 previous=last==null?entity.position():new Vec3(last.x(),last.y(),last.z());float body=last==null?pose.yaw():last.yaw(),head=last==null?pose.yaw()+pose.headYaw():last.yaw()+last.headYaw(),pitch=last==null?pose.pitch():last.pitch();
  entity.setPos(pose.x(),pose.y(),pose.z());
  entity.setYRot(pose.yaw());entity.setXRot(pose.pitch());entity.setYBodyRot(pose.yaw());entity.setYHeadRot(pose.yaw()+pose.headYaw());
  boolean smooth=interpolate&&previous.distanceToSqr(entity.position())<256;
  entity.setOldPosAndRot(smooth?previous:entity.position(),smooth?pose.yaw()-(float)MpsqCinematicScene.angleDelta(body,pose.yaw()):pose.yaw(),smooth?pitch:pose.pitch());
  entity.yBodyRotO=smooth?pose.yaw()-(float)MpsqCinematicScene.angleDelta(body,pose.yaw()):pose.yaw();
  entity.yHeadRotO=smooth?pose.yaw()+pose.headYaw()-(float)MpsqCinematicScene.angleDelta(head,pose.yaw()+pose.headYaw()):pose.yaw()+pose.headYaw();
  entity.setDeltaMovement(Vec3.ZERO);
  boolean grounded=!pose.clip().equals("jump")&&!pose.clip().equals("swim")&&!entity.level().noCollision(entity,entity.getBoundingBox().move(0,-.12,0));
  entity.setOnGround(grounded);
  if(grounded&&entity instanceof net.minecraft.world.entity.animal.chicken.Chicken chicken){chicken.flapSpeed=0;chicken.oFlapSpeed=0;chicken.flapping=0;chicken.oFlap=chicken.flap;}
 }
}
