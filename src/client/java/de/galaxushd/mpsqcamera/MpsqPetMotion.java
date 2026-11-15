package de.galaxushd.mpsqcamera;
/** Interpolates tick positions without changing movement or collision decisions. */
final class MpsqPetMotion {
    private double x,y,z; private float yaw; private boolean ready;
    void tick(double x,double y,double z,float yaw,boolean positioned) {
        this.x=x;this.y=y;this.z=z;this.yaw=yaw;ready=positioned;
    }
    double[] frame(double nx,double ny,double nz,float angle,float partial) {
        double t=Math.max(0,Math.min(1,partial));
        if(!ready || Math.abs(nx-x)+Math.abs(ny-y)+Math.abs(nz-z)>8)return new double[]{nx,ny,nz,angle};
        return new double[]{x+(nx-x)*t,y+(ny-y)*t,z+(nz-z)*t,
                yaw+net.minecraft.util.Mth.wrapDegrees(angle-yaw)*t};
    }
}
