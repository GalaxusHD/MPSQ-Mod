package de.galaxushd.mpsqcamera;

import java.util.UUID;

/** The permanent base role owns permissions; the active role only changes the displayed role. */
public record TeamProfile(UUID id, String displayName, TeamRank baseRank, TeamRank activeRank, boolean nameVisible) {
    public TeamRank displayedRank() { return activeRank == null ? baseRank : activeRank; }
    public TeamRank permissionRank() { return baseRank; }
    public boolean canOpenTeamArea() { return baseRank.level() >= TeamRank.UNDERCOVER_001.level(); }
    public boolean canViewCameras() { return permissionRank().canViewCameras(); }
    public boolean canManageMember(TeamProfile target) {
        TeamRank own = permissionRank();
        if (own == TeamRank.SENIOR_OFFICER) return target.baseRank() != TeamRank.SENIOR_OFFICER;
        if (own.level() < TeamRank.OFFICER.level()) return false;
        return target.baseRank().level() <= TeamRank.WORKER.level();
    }
}

