from pathlib import Path
p=Path('src/client/java/de/galaxushd/mpsqcamera/MpsqMiniYouPetRenderer.java');s=p.read_text(encoding='utf-8-sig').replace('ROAM_RADIUS = 4.5','ROAM_RADIUS = 9.5').replace('RETURN_DISTANCE = 5.0','RETURN_DISTANCE = 10.0');s=s.replace('    private static final MpsqPetMotion motion=', '    private static final MpsqMiniNavigation navigation=new MpsqMiniNavigation();\n    private static long nextTeleportAttempt;\n    private static final MpsqPetMotion motion=')
s=s.replace('            positioned = false;', '            navigation.reset();\n            positioned = false;',1).replace('            waveUntil = 0;','            navigation.reset();\n            waveUntil = 0;')
s=s.replace('        boolean returning = ownerDistance > RETURN_DISTANCE;', '''        if((ownerDistance>20||Math.abs(owner.getY()-y)>20)&&now>=nextTeleportAttempt){
            nextTeleportAttempt=now+1000;
            var safe=MpsqMiniNavigation.safeReturn(client,owner.getX(),owner.getY(),owner.getZ());
            if(safe!=null){x=safe.x();y=safe.y()+.01;z=safe.z();motion.tick(x,y,z,yaw,false);navigation.reset();chooseTarget(owner,now);return;}
        }
        boolean returning = ownerDistance > RETURN_DISTANCE;''')
s=s.replace('/away*4.0','/away*8.0')
a=s.index('        double dx = targetX - x, dz = targetZ - z;');z=s.index('        ny+=.01;',a)
s=s[:a]+'''        double targetDistance=horizontalDistance(x,z,targetX,targetZ);
        if(targetDistance<.8){
            if(pauseUntil==0)pauseUntil=now+ThreadLocalRandom.current().nextLong(900,3500);
            if(now>=pauseUntil)chooseTarget(owner,now);
            return;
        }
        if(now<pauseUntil)return;
        var waypoint=navigation.next(client,x,y-.01,z,targetX,targetZ,now);
        if(waypoint==null){if(!returning&&now>=nextWanderAt)chooseTarget(owner,now);return;}
        double dx=waypoint.x()-x,dz=waypoint.z()-z,distance=Math.hypot(dx,dz);
        if(distance<.001)return;
        double speed=followSpeed(returning,ownerDistance,ownerSpeed);
        alternateWalk=returning;
        double step=Math.min(speed,distance),nx=x+dx/distance*step,nz=z+dz/distance*step;
        double ny=MpsqPetGrounding.stepY(client,x,y-.01,z,nx,nz,.18,.64);
        if(!Double.isFinite(ny)){navigation.obstructed(now);return;}
        if(!returning&&horizontalDistance(nx,nz,owner.getX(),owner.getZ())>RETURN_DISTANCE){navigation.obstructed(now);nextWanderAt=0;return;}
        moving=true;
''' +s[z:]
a=s.index('        if(!following)return .075;');z=s.index('\n    }',a);s=s[:a]+'''        return following ? .15 : .075;'''+s[z:]
s=s.replace('    private static void chooseTarget(net.minecraft.client.player.LocalPlayer owner, long now) {','    private static void chooseTarget(net.minecraft.client.player.LocalPlayer owner, long now) {\n        navigation.reset();');p.write_text(s,encoding='utf-8')
p=Path('checks/MiniPetModelCheck.java');s=p.read_text(encoding='utf-8-sig').replace('double walk=(double)speed.invoke(null,true,2.,.1);','double walk=(double)speed.invoke(null,false,2.,.1);').replace('if(walk>.14||run<=.28)','if(Math.abs(run-walk*2)>1e-9)');p.write_text(s,encoding='utf-8')
