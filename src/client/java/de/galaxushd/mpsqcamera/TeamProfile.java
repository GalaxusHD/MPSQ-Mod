package de.galaxushd.mpsqcamera;

import java.util.UUID;

/** A base role survives temporary VIP/001 event roles. */
public record TeamProfile(UUID id, String displayName, TeamRank baseRank, TeamRank activeRank, boolean nameVisible) {
    public TeamRank displayedRank() { return activeRank == null ? baseRank : activeRank; }
    public TeamRank permissionRank() {
        // VIP and 001 intentionally suppress the saved worker/soldier powers for the event.
        return displayedRank() == TeamRank.VIP ? TeamRank.VIP : displayedRank();
    }
    public boolean canOpenTeamArea() { return displayedRank().level() >= TeamRank.UNDERCOVER_001.level(); }
    public boolean canViewCameras() { return permissionRank().canViewCameras(); }
    public boolean canManageMember(TeamProfile target) {
        TeamRank own = permissionRank();
        if (own == TeamRank.SENIOR_OFFICER) return target.displayedRank() != TeamRank.SENIOR_OFFICER;
        if (own.level() < TeamRank.OFFICER.level()) return false;
        return target.displayedRank().level() <= TeamRank.WORKER.level();
    }
}
