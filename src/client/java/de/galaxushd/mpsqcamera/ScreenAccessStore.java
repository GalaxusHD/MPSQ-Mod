package de.galaxushd.mpsqcamera;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server metadata for codes, ownership and groups. */
public final class ScreenAccessStore {
    private static final Map<UUID, String> CODES = new HashMap<>();
    private static final Map<UUID, LocalScreenStore.LocalGroupData> GROUPS = new HashMap<>();
    private static final Set<UUID> OWNED = new HashSet<>();
    private static final Set<UUID> TRIGGER_LINKED_ONLY = new HashSet<>();
    private static final Map<UUID, String> FRONTS = new HashMap<>();
    private ScreenAccessStore() { }
    public static void replace(Map<UUID, String> codes, Map<UUID, LocalScreenStore.LocalGroupData> groups, Set<UUID> owned, Set<UUID> triggerLinkedOnly) { CODES.clear(); CODES.putAll(codes); GROUPS.clear(); GROUPS.putAll(groups); OWNED.clear(); OWNED.addAll(owned); TRIGGER_LINKED_ONLY.clear(); TRIGGER_LINKED_ONLY.addAll(triggerLinkedOnly); }
    public static String code(UUID id) { LocalScreenStore.LocalGroupData group = GROUPS.get(id); return group == null ? CODES.getOrDefault(id, "------") : group.sharedCode(); }
    public static boolean isOwner(UUID id) { return OWNED.contains(id); }
    public static boolean isTriggerLinkedOnly(UUID id) { return TRIGGER_LINKED_ONLY.contains(id); }
    public static boolean inGroup(UUID id) { return GROUPS.containsKey(id); }
    public static void setFront(UUID id, String front) { FRONTS.put(id, front); }
    public static String front(UUID id) { return FRONTS.getOrDefault(id, "NORTH"); }
}
