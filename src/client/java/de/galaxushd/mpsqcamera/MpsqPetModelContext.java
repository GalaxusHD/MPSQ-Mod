package de.galaxushd.mpsqcamera;

/** Marks player-model renders that should use Mini-Me pet proportions. */
public final class MpsqPetModelContext {
    private static int renderDepth;

    private MpsqPetModelContext() { }

    public static boolean isRenderingPet() {
        return renderDepth > 0;
    }

    static void render(Runnable renderer) {
        renderDepth++;
        try {
            renderer.run();
        } finally {
            renderDepth--;
        }
    }
}
