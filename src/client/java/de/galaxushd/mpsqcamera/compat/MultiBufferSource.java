package de.galaxushd.mpsqcamera.compat;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import java.util.*;

/** Captures geometry now and submits immutable vertices to the 26.3 render pipeline. */
public class MultiBufferSource {
    private final OrderedSubmitNodeCollector collector;
    private final net.minecraft.client.renderer.state.level.CameraRenderState camera;
    private Integer outlineColor;
    public MultiBufferSource outline(int rgb){var result=new MultiBufferSource(collector,camera); result.outlineColor=0xFF000000|rgb;return result;}
    private final Map<RenderType, Capture> batches = new LinkedHashMap<>();
    public MultiBufferSource(OrderedSubmitNodeCollector collector, net.minecraft.client.renderer.state.level.CameraRenderState camera) { this.collector=Objects.requireNonNull(collector); this.camera=camera; }
    public net.minecraft.client.renderer.state.level.CameraRenderState camera(){return camera;}
    public VertexConsumer getBuffer(RenderType type) { return batches.computeIfAbsent(outlineColor==null?type:type.outline().orElseThrow(), key -> new Capture()); }
    public OrderedSubmitNodeCollector collector() { return collector; }
    public void endBatch() { for (var type : List.copyOf(batches.keySet())) endBatch(type); }
    public void endBatch(RenderType type) {
        Capture batch=batches.remove(type); if(batch==null)return;
        List<V> vertices=List.copyOf(batch.vertices);
        collector.submitCustomGeometry(new PoseStack(),type,(pose,out)->{for(V v:vertices){
            out.addVertex(v.x,v.y,v.z).setColor(outlineColor==null?v.color:outlineColor).setUv(v.u,v.v)
                .setUv1(v.o1,v.o2).setUv2(v.l1,v.l2).setUv3(v.u3,v.v3)
                .setNormal(v.nx,v.ny,v.nz).setLineWidth(v.width);
        }});
    }
    public static final class BufferSource extends MultiBufferSource {
        public BufferSource(OrderedSubmitNodeCollector collector, net.minecraft.client.renderer.state.level.CameraRenderState camera){super(collector,camera);}
    }
    private static final class V {
        float x,y,z,u,v,u3,v3,nx,ny,nz,width=1; int color=-1,o1,o2,l1,l2;
        V(float x,float y,float z){this.x=x;this.y=y;this.z=z;}
    }
    private static final class Capture implements VertexConsumer {
        final List<V> vertices=new ArrayList<>(); V current;
        public VertexConsumer addVertex(float x,float y,float z){current=new V(x,y,z);vertices.add(current);return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){return setColor(a<<24|r<<16|g<<8|b);}
        public VertexConsumer setColor(int color){current.color=color;return this;}
        public VertexConsumer setUv(float u,float v){current.u=u;current.v=v;return this;}
        public VertexConsumer setUv1(int u,int v){current.o1=u;current.o2=v;return this;}
        public VertexConsumer setUv2(int u,int v){current.l1=u;current.l2=v;return this;}
        public VertexConsumer setUv3(float u,float v){current.u3=u;current.v3=v;return this;}
        public VertexConsumer setNormal(float x,float y,float z){current.nx=x;current.ny=y;current.nz=z;return this;}
        public VertexConsumer setLineWidth(float width){current.width=width;return this;}
    }
}
