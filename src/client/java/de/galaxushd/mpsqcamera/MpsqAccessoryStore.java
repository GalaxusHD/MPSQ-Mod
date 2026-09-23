package de.galaxushd.mpsqcamera;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Local presentation cache; ownership is always decided by Supabase. */
public final class MpsqAccessoryStore {
    private static final Set<String> OWNED = ConcurrentHashMap.newKeySet();
    private MpsqAccessoryStore() { }
    public static void replace(Iterable<String> keys) { OWNED.clear(); if (keys != null) keys.forEach(OWNED::add); }
    public static boolean has(String key) { return OWNED.contains(key); }
    public static Set<String> all() { return Set.copyOf(OWNED); }
}
