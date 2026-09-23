package de.galaxushd.mpsqcamera;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Stores validated bossbar state received from MPSQ actions. */
public final class MpsqBossbarManager {
    private static final Map<String, MpsqBossbarState> STATES = new ConcurrentHashMap<>();
    private static long countdownEnd; private static int countdownDuration; private static String countdownTitle;
    private MpsqBossbarManager() { }
    public static void apply(MpsqBossbarState state) { if (state != null) STATES.put(state.id(), state); }
    public static void remove(String id) { STATES.remove(id); }
    public static MpsqBossbarState get(String id) { return STATES.get(id); }
    public static void startCountdown(String title,int seconds,String createdAt) {
        if(seconds<1||seconds>7200) return;
        countdownDuration=seconds; countdownTitle=title;
        countdownEnd=java.time.Instant.parse(createdAt).toEpochMilli()+seconds*1000L;
    }
    public static java.util.Collection<MpsqBossbarState> all() {
        if(countdownEnd>0) {
            long remaining=Math.max(0,countdownEnd-System.currentTimeMillis());
            if(remaining==0){countdownEnd=0;STATES.remove("countdown");}
            else apply(new MpsqBossbarState("countdown",countdownTitle+" · "+((remaining+999)/1000)+" s","purple",(float)remaining/(countdownDuration*1000),true));
        }
        return java.util.List.copyOf(STATES.values());
    }
    public static void clear(){STATES.clear();countdownEnd=0;}
}

