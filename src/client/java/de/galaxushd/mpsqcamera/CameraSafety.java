package de.galaxushd.mpsqcamera;

import net.minecraft.client.Minecraft;

/** Occupied camera cells are handled by the render-only cavity, not disabled. */
public final class CameraSafety {
    private CameraSafety(){}
    public static boolean isStaticCameraBlocked(Minecraft client,LocalCameraStore.CameraData camera){return false;}
}
