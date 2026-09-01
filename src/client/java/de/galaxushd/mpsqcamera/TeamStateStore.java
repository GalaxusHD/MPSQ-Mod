package de.galaxushd.mpsqcamera;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Client cache for the MPSQ Team API. */
public final class TeamStateStore {
    private static TeamProfile self;
    private static List<TeamProfile> members = List.of();

    private TeamStateStore() { }
    public static Optional<TeamProfile> self() { return Optional.ofNullable(self); }
    public static List<TeamProfile> members() { return members; }
    public static void setSelf(TeamProfile value) { self = value; }
    public static void setMembers(List<TeamProfile> value) {
        // Reinstalling the mod can create a second Supabase client/token for
        // the same Minecraft account. Minecraft names are unique in a live
        // session, so retain only the strongest stored profile for rendering
        // and menus. This prevents MP_SquidGame from appearing as both
        // Spieler and Sr Offizier without granting the duplicate token rights.
        Map<String, TeamProfile> unique = new LinkedHashMap<>();
        for (TeamProfile profile : new ArrayList<>(value)) {
            String key = profile.displayName().trim().toLowerCase(Locale.ROOT);
            if (key.isEmpty()) key = profile.id().toString();
            TeamProfile current = unique.get(key);
            if (current == null || profileStrength(profile) > profileStrength(current)) {
                unique.put(key, profile);
            }
        }
        members = List.copyOf(unique.values());
    }

    private static int profileStrength(TeamProfile profile) {
        // The protected base rank wins over temporary display roles. For all
        // other profiles, retain the strongest visible role.
        if (profile.baseRank() == TeamRank.SENIOR_OFFICER) return Integer.MAX_VALUE;
        return Math.max(profile.baseRank().level(), profile.displayedRank().level());
    }
}
