package de.galaxushd.mpsqcamera.compat;
import com.mojang.blaze3d.vertex.*;
/** Wireframe cuboid using the submitted line pipeline. */
public final class ShapeRenderer {
 private ShapeRenderer(){}
 public static void renderLineBox(PoseStack pose,VertexConsumer out,double x1,double y1,double z1,double x2,double y2,double z2,float r,float g,float b,float a){
  float[][] v={{(float)x1,(float)y1,(float)z1},{(float)x2,(float)y1,(float)z1},{(float)x2,(float)y1,(float)z2},{(float)x1,(float)y1,(float)z2},{(float)x1,(float)y2,(float)z1},{(float)x2,(float)y2,(float)z1},{(float)x2,(float)y2,(float)z2},{(float)x1,(float)y2,(float)z2}};
  int[][] edges={{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}};
  for(int[] edge:edges){float[] f=v[edge[0]],t=v[edge[1]];float dx=t[0]-f[0],dy=t[1]-f[1],dz=t[2]-f[2];float len=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);if(len==0)continue;
   for(float[] point:new float[][]{f,t})out.addVertex(pose.last(),point[0],point[1],point[2]).setColor(r,g,b,a).setNormal(pose.last(),dx/len,dy/len,dz/len).setLineWidth(1f);
  }
 }
}
