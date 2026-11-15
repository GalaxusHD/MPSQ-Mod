package de.galaxushd.mpsqcamera.compat;
import java.util.function.Consumer;
import java.util.function.BiPredicate;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.world.phys.HitResult;
public final class WorldRenderEvents {
    private WorldRenderEvents(){}
    public static final GeometryEvent AFTER_ENTITIES=new GeometryEvent();
    public static final GeometryEvent LAST=new GeometryEvent();
    public static final EndEvent END=new EndEvent();
    public static final OutlineEvent BEFORE_BLOCK_OUTLINE=new OutlineEvent();
    public static final class GeometryEvent {
        public void register(Consumer<WorldRenderContext> callback){
            LevelRenderEvents.COLLECT_SUBMITS.register(context->{
                var wrapped=new WorldRenderContext(context);
                callback.accept(wrapped); wrapped.consumers().endBatch();
            });
        }
    }
    public static final class EndEvent {
        public void register(Consumer<Object> callback){LevelRenderEvents.END_MAIN.register(context->callback.accept(context));}
    }
    public static final class OutlineEvent {
        public void register(BiPredicate<WorldRenderContext,HitResult> callback){
            LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context,outline)->callback.test(new WorldRenderContext(context),net.minecraft.client.Minecraft.getInstance().hitResult));
        }
    }
}
