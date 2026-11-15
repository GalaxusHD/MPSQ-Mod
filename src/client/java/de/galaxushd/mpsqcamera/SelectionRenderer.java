package de.galaxushd.mpsqcamera;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.galaxushd.mpsqcamera.compat.WorldRenderContext;
import de.galaxushd.mpsqcamera.compat.WorldRenderEvents;
import de.galaxushd.mpsqcamera.compat.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import de.galaxushd.mpsqcamera.compat.ShapeRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Zeichnet einen roten Wireframe-Rahmen im Welt-Raum während der zweistufigen
 * Positions-Auswahl (Rechtsklick mit Werkzeug-Item).
 *
 * Farbe: Rot (RGBA 255, 0, 0, 0.8)
 */
public final class SelectionRenderer {

    private SelectionRenderer() {}

    public static void initialize() {
        WorldRenderEvents.AFTER_ENTITIES.register(SelectionRenderer::render);
    }

    private static void render(WorldRenderContext ctx) {
        if(MpsqFinishZoneTool.editorEnabled()&&ctx.consumers()!=null&&ctx.matrixStack()!=null){
            var m=ctx.matrixStack();var cam=ctx.camera().getPos();m.pushPose();m.translate(-cam.x,-cam.y,-cam.z);
            var lines=ctx.consumers().getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.lines());
            for(var entry:MpsqZoneCatalog.all()){
                var b=entry.box();int color=MpsqZoneCatalog.frameColor(entry.kind());
                ShapeRenderer.renderLineBox(m,lines,b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ(),((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f,1f);
            }
            m.popPose();
        }
        BlockPos pos1 = ScreenCreationManager.getSelectionPos1();
        boolean gameZone = MpsqFinishZoneTool.firstCorner() != null;
        if(gameZone) pos1=MpsqFinishZoneTool.firstCorner();
        if (pos1 == null) return;

        // Vorschau-Endpunkt: aktuell anvisierter Block (oder pos1 wenn kein Block)
        BlockPos pos2 = ScreenCreationManager.getSelectionPos2Preview();
        if(gameZone && net.minecraft.client.Minecraft.getInstance().hitResult instanceof net.minecraft.world.phys.BlockHitResult hit) pos2=hit.getBlockPos();
        if (pos2 == null) pos2 = pos1;

        MultiBufferSource consumers = ctx.consumers();
        if (consumers == null) return;

        PoseStack matrices = ctx.matrixStack();
        if (matrices == null) return;

        Vec3 camPos = ctx.camera().getPos();

        matrices.pushPose();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

        // Begrenzende Box zwischen den beiden Positionen (je +1 für volle Blockausdehnung)
        double x1 = Math.min(pos1.getX(), pos2.getX());
        double y1 = Math.min(pos1.getY(), pos2.getY());
        double z1 = Math.min(pos1.getZ(), pos2.getZ());
        double x2 = Math.max(pos1.getX(), pos2.getX()) + 1.0;
        double y2 = Math.max(pos1.getY(), pos2.getY()) + 1.0;
        double z2 = Math.max(pos1.getZ(), pos2.getZ()) + 1.0;

        VertexConsumer vc = consumers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.lines());
        // Roter Wireframe-Rahmen (R=1.0, G=0.0, B=0.0, A=0.8)
        int color=gameZone?MpsqFinishZoneTool.selectedColor():0xFF0000;
        ShapeRenderer.renderLineBox(matrices, vc, x1, y1, z1, x2, y2, z2,
                ((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f,0.8f);

        matrices.popPose();
    }
}
