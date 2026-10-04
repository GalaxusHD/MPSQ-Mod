package de.galaxushd.mpsqcamera;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Per-client playback state for the bundled Wumpus NPC animations. */
final class MpsqWumpusBehavior {
    private record Playback(String animation, long startedAt) {}
    private record ClickState(long lastClickAt, int rapidClicks) {}

    private static final long WAVE_MS = 2_000L;
    private static final long DEATH_POSE_MS = 2_800L;
    private static final long RAPID_CLICK_MS = 900L;
    private static final Map<String, Playback> PLAYBACK = new ConcurrentHashMap<>();
    private static final Map<String, ClickState> CLICKS = new ConcurrentHashMap<>();
    private static final Set<String> LOOKING_AT = ConcurrentHashMap.newKeySet();

    private MpsqWumpusBehavior() {}

    static void observeLook(String npcId, boolean looking) {
        if (npcId == null || npcId.isBlank()) return;
        if (looking) {
            if (LOOKING_AT.add(npcId)) wave(npcId);
        } else {
            LOOKING_AT.remove(npcId);
        }
    }

    static void wave(String npcId) {
        if (npcId != null && !npcId.isBlank()) PLAYBACK.put(npcId, new Playback("wave", System.currentTimeMillis()));
    }

    static void leftClick(String npcId) {
        if (npcId == null || npcId.isBlank()) return;
        long now = System.currentTimeMillis();
        ClickState previous = CLICKS.get(npcId);
        int rapidClicks = previous != null && now - previous.lastClickAt() <= RAPID_CLICK_MS
                ? previous.rapidClicks() + 1 : 1;
        CLICKS.put(npcId, new ClickState(now, rapidClicks));
        if (rapidClicks >= 2 && ThreadLocalRandom.current().nextFloat() < 0.10f) {
            PLAYBACK.put(npcId, new Playback("death", now));
            CLICKS.remove(npcId);
        }
    }

    static String animation(String npcId) {
        Playback playback = PLAYBACK.get(npcId);
        if (playback == null) return "idle";
        long age = Math.max(0, System.currentTimeMillis() - playback.startedAt());
        if ("wave".equals(playback.animation()) && age >= WAVE_MS) {
            PLAYBACK.remove(npcId, playback);
            return "idle";
        }
        if ("death".equals(playback.animation()) && age >= DEATH_POSE_MS) {
            PLAYBACK.remove(npcId, playback);
            return "idle";
        }
        return playback.animation();
    }

    static float elapsedSeconds(String npcId) {
        Playback playback = PLAYBACK.get(npcId);
        return playback == null ? (System.currentTimeMillis() % 3500L) / 1000.0f
                : Math.max(0, System.currentTimeMillis() - playback.startedAt()) / 1000.0f;
    }
}
