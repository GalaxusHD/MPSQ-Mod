package de.galaxushd.mpsqcamera;

/** Persistent local switch for optional MPSQ client features. */
public final class TeamVisibilitySettings {
    private TeamVisibilitySettings() { }
    public static boolean visible() { return ModConfig.mpsqEnabled; }
    public static void toggle() {
        ModConfig.mpsqEnabled = !ModConfig.mpsqEnabled;
        ModConfig.save();
    }
}
