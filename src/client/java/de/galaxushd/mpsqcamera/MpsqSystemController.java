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
                .sorted(Comparator.comparing(SystemOption::label, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public record SystemOption(String id, String label) { }

    public static void unregister(String systemId, Listener listener) {
        String id = normalize(systemId);
        if (!id.isEmpty() && listener != null) LISTENERS.remove(id, listener);
    }

    public static String activeSystemId() { return activeSystemId; }

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
        if (activeSystemId.equals(nextId)) return;
        String previous = activeSystemId;
        activeSystemId = nextId;
        notifyListener(previous, false);
        notifyListener(nextId, true);
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
