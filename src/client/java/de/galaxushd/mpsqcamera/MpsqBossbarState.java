package de.galaxushd.mpsqcamera;

/** Client cache for a server-controlled bossbar/countdown. */
public record MpsqBossbarState(String id, String title, String color, float value, boolean visible) { }
