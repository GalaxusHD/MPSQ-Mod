package de.galaxushd.mpsqcamera;
import net.minecraft.client.Minecraft;
import java.util.*;
/** Client-world adapter; plans at most once per second and checks each movement step. */
final class MpsqMiniNavigation {
    private final double radius,height;
    MpsqMiniNavigation(){this(.18,.64);}
    MpsqMiniNavigation(double radius,double height){this.radius=radius;this.height=height;}
    private List<MpsqPetPath.Point> route=List.of();
    private int index;
    private long nextPlan;
    private double goalX,goalZ;
    void reset(){route=List.of();index=0;nextPlan=0;}
    private double straight(Minecraft c,double x,double y,double z,double tx,double tz){
        int samples=Math.max(1,(int)Math.ceil(Math.hypot(tx-x,tz-z)*5));double px=x,py=y,pz=z;
        for(int i=1;i<=samples;i++){double nx=x+(tx-x)*i/samples,nz=z+(tz-z)*i/samples;
            double ny=MpsqPetGrounding.stepY(c,px,py,pz,nx,nz,radius,height);
            if(!Double.isFinite(ny))return Double.NaN;px=nx;py=ny;pz=nz;
        }return py;
    }
    MpsqPetPath.Point next(Minecraft c,double x,double y,double z,double tx,double tz,long now,boolean returning,double ownerX,double ownerZ){
        if(returning||Math.hypot(tx-ownerX,tz-ownerZ)<=10){double direct=straight(c,x,y,z,tx,tz);if(Double.isFinite(direct)){route=List.of();index=0;return new MpsqPetPath.Point(tx,direct,tz);}}
        if(now>=nextPlan&&(index>=route.size()||Math.hypot(tx-goalX,tz-goalZ)>1.5)){
            goalX=tx;goalZ=tz;index=0;nextPlan=now+1000;
            route=MpsqPetPath.find(new MpsqPetPath.Point(x,y,z),tx,tz,(from,nx,nz)->{
                if(!returning&&Math.hypot(nx-ownerX,nz-ownerZ)>10)return Double.NaN;
                // Sample the complete edge, preventing routes through thin walls and fence corners.
                double px=from.x(),py=from.y(),pz=from.z();
                for(int i=1;i<=4;i++){
                    double qx=from.x()+(nx-from.x())*i/4,qz=from.z()+(nz-from.z())*i/4;
                    double qy=MpsqPetGrounding.stepY(c,px,py,pz,qx,qz,radius,height);
                    if(!Double.isFinite(qy))return Double.NaN;px=qx;py=qy;pz=qz;
                }
                return py;
            });
        }
        while(index<route.size()&&Math.hypot(x-route.get(index).x(),z-route.get(index).z())<.12)index++;
        // Look ahead across clear segments instead of steering through every grid corner.
        for(int i=Math.min(route.size()-1,index+5);i>index;i--){var p=route.get(i);double floor=straight(c,x,y,z,p.x(),p.z());if(Double.isFinite(floor)&&Math.abs(floor-p.y())<.1){index=i;break;}}
        return index<route.size()?route.get(index):null;
    }
    void obstructed(long now){route=List.of();index=0;nextPlan=Math.max(nextPlan,now+250);}
    static MpsqPetPath.Point safeReturn(Minecraft c,double ox,double oy,double oz){
        return MpsqPetGrounding.safeSpawn(c,ox,oy,oz,.18,.64);
    }
}
