package de.galaxushd.mpsqcamera;

import de.galaxushd.mpsqcamera.compat.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** Local editor markers: no blocks or entities are added to the world. */
final class MpsqCinematicMarkers {
    private static MpsqCinematicScene scene;
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static boolean editorActor;
    static boolean visible(){var c=Minecraft.getInstance();return scene!=null&&world==c.level&&TeamVisibilitySettings.inArea()&&scene.dimension.equals(c.level.dimension().identifier().toString());}
    static void sync(java.util.List<MpsqCinematicScene> scenes){
        if(scene==null)return;
        String id=scene.id;
        scene=scenes.stream().filter(s->java.util.Objects.equals(s.id,id)).findFirst().orElse(null);
        if(editorActor&&!MpsqCinematicPreview.active()){MpsqCinematicActors.stop();editorActor=false;}
        if(scene==null)world=null;
    }
    static boolean showing(MpsqCinematicScene s){return scene!=null&&scene.id.equals(s.id)&&world==Minecraft.getInstance().level;}
    static void toggle(MpsqCinematicScene s){if(showing(s)){scene=null;world=null;}else{scene=s;world=Minecraft.getInstance().level;}}
    static void initialize(){
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(c->{
            if(MpsqCinematicPreview.active()){editorActor=false;return;}
            if(visible()&&scene.npc.placed){MpsqCinematicActors.editor(scene);editorActor=true;}
            else if(editorActor){MpsqCinematicActors.stop();editorActor=false;}
        });
        WorldRenderEvents.AFTER_ENTITIES.register(ctx->{
            var client=Minecraft.getInstance();
            if(scene==null)return;
            if(client.level!=world||!MpsqParticipation.allowed()){scene=null;world=null;return;}
            if(!TeamVisibilitySettings.inArea()||MpsqCinematicPreview.active()||!scene.dimension.equals(client.level.dimension().identifier().toString()))return;
            var m=ctx.matrixStack();var buffers=ctx.consumers();if(m==null||buffers==null)return;
            var camera=ctx.camera().getPos();
            var out=buffers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(Identifier.withDefaultNamespace("textures/block/gold_block.png")));
            var lines=buffers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.lines());
            var npc=scene.npc;
            if(npc.placed){
                double end=npc.duration();MpsqPathSeconds.draw(ctx,npc);
                var previous=npc.position(npc.movementStart,false,"FORWARD");
                int steps=Math.max(1,Math.min(512,(int)Math.ceil((end-npc.movementStart)*16)));
                for(int i=1;i<=steps;i++){
                    var next=npc.position(npc.movementStart+(end-npc.movementStart)*i/steps,false,"FORWARD");
                    segment(lines,m,camera,previous.x,previous.y+.08,previous.z,next.x,next.y+.08,next.z,0xFF33FFCC);previous=next;
                }
                for(var p:npc.route)if(p!=null){
                    segment(lines,m,camera,p.x-.3,p.y+.08,p.z,p.x+.3,p.y+.08,p.z,0xFF33FFCC);
                    segment(lines,m,camera,p.x,p.y+.08,p.z-.3,p.x,p.y+.08,p.z+.3,0xFF33FFCC);
                    segment(lines,m,camera,p.x,p.y,p.z,p.x,p.y+.65,p.z,0xFF33FFCC);
                }
            }
            for(int index=1;index<scene.points.size();index++){
                var from=scene.points.get(index-1);var to=scene.points.get(index);
                if("CUT".equals(to.transition))continue;
                // Same sampler as playback; smooth timing never invents a different spatial path.
                var previous=scene.sample(from.time);
                for(int step=1;step<=48;step++){
                    var next=scene.sample(from.time+(to.time-from.time)*step/48.0);
                    double dx=next.x-previous.x,dy=next.y-previous.y,dz=next.z-previous.z;
                    double length=Math.sqrt(dx*dx+dy*dy+dz*dz);
                    if(length>1e-7){
                        for(var point:new MpsqCinematicScene.Point[]{previous,next})
                            lines.addVertex(m.last(),(float)(point.x-camera.x),(float)(point.y-camera.y),(float)(point.z-camera.z))
                                .setColor(0xFFFFFF33).setNormal(m.last(),(float)(dx/length),(float)(dy/length),(float)(dz/length)).setLineWidth(2f);
                    }
                    previous=next;
                }
            }
            float[][][] faces={
                {{0,0,0},{0,1,0},{1,1,0},{1,0,0}},
                {{1,0,1},{1,1,1},{0,1,1},{0,0,1}},
                {{0,0,1},{0,1,1},{0,1,0},{0,0,0}},
                {{1,0,0},{1,1,0},{1,1,1},{1,0,1}},
                {{0,1,0},{0,1,1},{1,1,1},{1,1,0}},
                {{0,0,1},{0,0,0},{1,0,0},{1,0,1}}
            };
            float[][] normals={{0,0,-1},{0,0,1},{-1,0,0},{1,0,0},{0,1,0},{0,-1,0}};
            float[][] uv={{0,1},{0,0},{1,0},{1,1}};
            for(var p:scene.points){
                if(camera.distanceToSqr(p.x,p.y,p.z)>256*256)continue;
                m.pushPose();m.translate(p.x-camera.x-0.25,p.y-camera.y-0.25,p.z-camera.z-0.25);
                for(int f=0;f<6;f++)for(int i=0;i<4;i++){
                    var v=faces[f][i];var n=normals[f];
                    out.addVertex(m.last(),v[0]*0.5f,v[1]*0.5f,v[2]*0.5f).setColor(0xFFFFFFFF)
                       .setUv(uv[i][0],uv[i][1]).setOverlay(0).setLight(0xF000F0).setNormal(m.last(),n[0],n[1],n[2]);
                }
                m.popPose();
            }
        });
    }
    static void segment(com.mojang.blaze3d.vertex.VertexConsumer out,com.mojang.blaze3d.vertex.PoseStack m,net.minecraft.world.phys.Vec3 camera,double x,double y,double z,double X,double Y,double Z,int color){
        double d=Math.sqrt((X-x)*(X-x)+(Y-y)*(Y-y)+(Z-z)*(Z-z));if(d<1e-7)return;
        out.addVertex(m.last(),(float)(x-camera.x),(float)(y-camera.y),(float)(z-camera.z)).setColor(color).setNormal(m.last(),(float)((X-x)/d),(float)((Y-y)/d),(float)((Z-z)/d)).setLineWidth(2);
        out.addVertex(m.last(),(float)(X-camera.x),(float)(Y-camera.y),(float)(Z-camera.z)).setColor(color).setNormal(m.last(),(float)((X-x)/d),(float)((Y-y)/d),(float)((Z-z)/d)).setLineWidth(2);
    }
}
