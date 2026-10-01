package de.galaxushd.mpsqcamera;

/** Client cache for a server-controlled bossbar/countdown. */
public record MpsqBossbarState(String id, String title, String color, float value, boolean visible,
                               int remainingSeconds) {
    public MpsqBossbarState(String id, String title, String color, float value, boolean visible) {
        this(id, title, color, value, visible, -1);
    }
}
