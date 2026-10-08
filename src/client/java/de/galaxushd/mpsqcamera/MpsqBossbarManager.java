package de.galaxushd.mpsqcamera;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Stores validated bossbar state received from MPSQ actions. */
public final class MpsqBossbarManager {
    private static final Map<String, MpsqBossbarState> STATES = new ConcurrentHashMap<>();
    private static long countdownEnd; private static int countdownDuration; private static String countdownTitle, countdownColor="purple";
    private MpsqBossbarManager() { }
    public static void apply(MpsqBossbarState state) { if (state != null) STATES.put(state.id(), state); }
    public static void remove(String id) { STATES.remove(id); }
    public static boolean countdownRunning(){return countdownEnd>System.currentTimeMillis();}
    public static void stopCountdown(){countdownEnd=0;STATES.remove("countdown");}
    public static MpsqBossbarState get(String id) { return STATES.get(id); }
    public static void startCountdown(String title,int seconds,String createdAt) {
        startCountdown(title,seconds,createdAt,"purple");
    }
    public static void startCountdown(String title,int seconds,String createdAt,String color) {
        if(seconds<1||seconds>7200) return;
        countdownDuration=seconds; countdownTitle=title; countdownColor=normalizeColor(color);
        countdownEnd=java.time.Instant.parse(createdAt).toEpochMilli()+seconds*1000L;
    }
    public static String normalizeColor(String color) {
        return switch(String.valueOf(color).toLowerCase(java.util.Locale.ROOT)) {
            case "pink","red","green" -> color.toLowerCase(java.util.Locale.ROOT);
            default -> "purple";
        };
    }
    public static java.util.Collection<MpsqBossbarState> all() {
        if(countdownEnd>0) {
            long remaining=Math.max(0,countdownEnd-System.currentTimeMillis());
            if(remaining==0){countdownEnd=0;STATES.remove("countdown");}
            else {
                long seconds=(remaining+999)/1000;
                String title=countdownTitle==null?"":countdownTitle;
                String separator=title.isBlank()?"":" &7· ";
                String timerColor=seconds>10?"&e":"&c";
                apply(new MpsqBossbarState("countdown",title+separator+timerColor+seconds+" s",countdownColor,(float)remaining/(countdownDuration*1000),true));
            }
        }
        return STATES.values().stream()
                .sorted(java.util.Comparator.comparing(state -> "rlgl_phase".equals(state.id()) ? "~~~~rlgl_phase" : state.id()))
                .toList();
    }
    public static void clear(){STATES.clear();countdownEnd=0;countdownColor="purple";}
}

