package de.galaxushd.mpsqcamera.compat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
public final class WorldRenderContext {
    private final LevelRenderContext context;
    private final MultiBufferSource.BufferSource buffers;
    public WorldRenderContext(LevelRenderContext context){this.context=context;buffers=new MultiBufferSource.BufferSource(context.submitNodeCollector(),context.levelState().cameraRenderState);}
    public PoseStack matrixStack(){return context.poseStack();}
    public MultiBufferSource.BufferSource consumers(){return buffers;}
    public Camera camera(){return new Camera(context.levelState().cameraRenderState);}
    public net.minecraft.client.DeltaTracker tickCounter(){return net.minecraft.client.Minecraft.getInstance().getDeltaTracker();}
    public record Camera(CameraRenderState state){
        public Vec3 getPos(){return state.pos;}
        public Quaternionf getRotation(){return state.orientation;}
    }
}
