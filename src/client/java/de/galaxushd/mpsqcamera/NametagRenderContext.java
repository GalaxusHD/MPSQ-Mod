package de.galaxushd.mpsqcamera;

/** Thread-lokaler Marker für das aktuell gerenderte MPSQ-Nametag. */
public final class NametagRenderContext {
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    private NametagRenderContext() {
    }

    public static void activate() {
        ACTIVE.set(true);
    }

    public static boolean active() {
        return ACTIVE.get();
    }

    public static void clear() {
        ACTIVE.remove();
    }
}