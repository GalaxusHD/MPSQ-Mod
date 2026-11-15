package de.galaxushd.mpsqcamera.compat;
import java.util.*;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
/** Associates copied render state with the animation identity without depending on vanilla labels. */
public final class AvatarIdentity {
 private static final Map<AvatarRenderState,String> NAMES=Collections.synchronizedMap(new WeakHashMap<>());
 private AvatarIdentity(){}
 public static String name(AvatarRenderState state){return NAMES.get(state);}
 public static void set(AvatarRenderState state,String name){if(name==null)NAMES.remove(state);else NAMES.put(state,name);}
}
