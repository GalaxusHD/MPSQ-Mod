package de.galaxushd.mpsqcamera;

import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;

/** Coordinates mutually exclusive custom game systems selected by Redstone actions. */
public final class MpsqSystemController {
    public interface Listener {
        void onActivated();
        void onDeactivated();
    }

    private static final Map<String, Listener> LISTENERS = new ConcurrentHashMap<>();
    private static final Map<String, String> SYSTEM_LABELS = new ConcurrentHashMap<>();
    private static volatile String activeSystemId = "";
    private static final List<String> ORDER = List.of("red_light_green_light", "dalgona", "tug_of_war", "marbles", "glass_bridge", "finale", "six_leg", "room_game", "hide_and_seek", "rope_jump", "sky_squid");
    static {
        String[] labels = {"Dalgona", "Tauziehen", "Murmelspiel", "Glass Bridge", "Finale", "Sechsbein-Spiel", "Raumspiel", "Hide and Seek", "Seilspringen", "Sky Squid Game"};
        for (int i = 0; i < labels.length; i++) SYSTEM_LABELS.put(ORDER.get(i + 1), labels[i] + " (Platzhalter)");
    }
    public static boolean implemented(String id) { return LISTENERS.containsKey(normalize(id)); }

    private MpsqSystemController() { }

    /** Registers a future/custom system implementation under its Redstone System-ID. */
    public static void register(String systemId, Listener listener) {
        register(systemId, systemId, listener);
    }

    public static void register(String systemId, String label, Listener listener) {
        String id = normalize(systemId);
        if (id.isEmpty() || listener == null) throw new IllegalArgumentException("System-ID und Listener sind erforderlich.");
        LISTENERS.put(id, listener);
        SYSTEM_LABELS.put(id, label == null || label.isBlank() ? id : label.trim());
        if (id.equals(activeSystemId)) listener.onActivated();
    }

    /** Keeps old saved Redstone IDs working without showing a duplicate choice in the selector. */
    public static void registerLegacyAlias(String systemId, Listener listener) {
        String id = normalize(systemId);
        if (id.isEmpty() || listener == null) throw new IllegalArgumentException("System-ID und Listener sind erforderlich.");
        LISTENERS.put(id, listener);
        if (id.equals(activeSystemId)) listener.onActivated();
    }

    public static List<SystemOption> availableSystems() {
        return SYSTEM_LABELS.entrySet().stream()
                .map(entry -> new SystemOption(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(option -> ORDER.indexOf(option.id()) < 0 ? 999 : ORDER.indexOf(option.id())))
                .toList();
    }

    public record SystemOption(String id, String label) { }

    public static void unregister(String systemId, Listener listener) {
        String id = normalize(systemId);
        if (!id.isEmpty() && listener != null) LISTENERS.remove(id, listener);
    }

    public static String activeSystemId() { return activeSystemId; }

    /** Starts one registered system without toggling it off on repeated button presses. */
    public static synchronized void startSystem(String systemId) { activate(normalize(systemId)); }

    /** Stops the named system only when it is currently active. */
    public static synchronized void stopSystem(String systemId) {
        String id = normalize(systemId);
        if (activeSystemId.equals(id)) activate("");
    }

    /** Stops the selected system when the MPSQ session/world is left or disabled. */
    public static synchronized void stopActive() { activate(""); }

    /**
     * Pulse triggers toggle their target; powered blocks select it while on
     * and stop it on release. Selecting a different ID replaces the old one.
     */
    static synchronized void onTrigger(String systemId, boolean stateDriven, boolean powered) {
        String id = normalize(systemId);
        if (id.isEmpty()) return;
        if (stateDriven) {
            if (powered) activate(id);
            else if (activeSystemId.equals(id)) activate("");
        } else {
            activate(activeSystemId.equals(id) ? "" : id);
        }
    }

    private static void activate(String nextId) {
        if (!nextId.isEmpty() && MpsqReplayPlayback.active()) return;
        if (!nextId.isEmpty() && !implemented(nextId)) return;
        if(!nextId.isEmpty()){
            String error=MpsqGamesRuntime.configurationError(nextId);
            if(!error.isEmpty()){var c=net.minecraft.client.Minecraft.getInstance();if(c.player!=null)de.galaxushd.mpsqcamera.compat.PlayerMessages.send(c.player, net.minecraft.network.chat.Component.literal(error),false);return;}
        }
        if (activeSystemId.equals(nextId)) return;
        MpsqReplayManager.finish();
        String previous = activeSystemId;
        activeSystemId = nextId;
        notifyListener(previous, false);
        notifyListener(nextId, true);
        MpsqReplayManager.transition(nextId);
    }

    private static void notifyListener(String id, boolean activated) {
        if (id.isEmpty()) return;
        Listener listener = LISTENERS.get(id);
        if (listener == null) return;
        try {
            if (activated) listener.onActivated();
            else listener.onDeactivated();
        } catch (RuntimeException error) {
            MpsqCameraClient.LOGGER.error("MPSQ-Systemaktion '{}' konnte nicht {} werden", id,
                    activated ? "gestartet" : "beendet", error);
        }
    }

    private static String normalize(String id) {
        if (id == null) return "";
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        return normalized.matches("[a-z0-9_-]{1,64}") ? normalized : "";
    }
}
