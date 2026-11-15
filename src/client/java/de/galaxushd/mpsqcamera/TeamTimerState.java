package de.galaxushd.mpsqcamera;

/** Server-authoritative timer state; endsAt is ISO-8601 or null. */
public record TeamTimerState(boolean running, String endsAt, String label) { }
