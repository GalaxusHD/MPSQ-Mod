package de.galaxushd.mpsqcamera;

import java.util.*;

/** Activation freezes the roster; tower buttons only arm its survivors. */
public final class MpsqSkyRound {
    public static final long DURATION = 15 * 60 * 1000L;
    private final Set<UUID> roster = new LinkedHashSet<>(), out = new HashSet<>();
    private int tower;
    private boolean running;
    private long deadline;

    public void begin(Set<UUID> participants) {
        reset();
        roster.addAll(participants);
    }
    public boolean press(int index, Set<UUID> inside, long now) {
        if (running || tower >= 3 || index != tower) return false;
        Set<UUID> remaining = survivors();
        if (remaining.isEmpty() || !inside.containsAll(remaining)) return false;
        running = true;
        deadline = now + DURATION;
        return true;
    }

    /** Missing/unloaded entities are not proof that somebody crossed the boundary. */
    public void tick(Set<UUID> outside, long now) {
        if (!running) return;
        if (now >= deadline) {
            out.addAll(roster);
            running = false;
            tower = 3;
            return;
        }
        Set<UUID> losses = survivors();
        losses.retainAll(outside);
        if (!losses.isEmpty()) {
            out.addAll(losses);
            advance();
        }
    }

    /** Falls remain fatal during green transit and after the final tower. */
    public void fall(Set<UUID> ids) {
        Set<UUID> losses = survivors();
        losses.retainAll(ids);
        if (losses.isEmpty()) return;
        out.addAll(losses);
        if (running) advance();
        else if (survivors().isEmpty()) tower = 3;
    }
    public void kick(UUID id) { fall(Set.of(id)); }
    private void advance() { running = false; tower++; if (survivors().isEmpty()) tower = 3; }
    public Set<UUID> survivors() { Set<UUID> ids = new LinkedHashSet<>(roster); ids.removeAll(out); return ids; }
    public Set<UUID> eliminated() { return Set.copyOf(out); }
    public int initialCount() { return roster.size(); }
    public int tower() { return tower; }
    public boolean running() { return running; }
    public long remaining(long now) { return Math.max(0, deadline - now); }
    public void reset() { roster.clear(); out.clear(); tower = 0; running = false; deadline = 0; }
}
