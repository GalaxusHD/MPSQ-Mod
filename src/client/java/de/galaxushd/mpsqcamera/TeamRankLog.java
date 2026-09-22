package de.galaxushd.mpsqcamera;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Immutable rank-history row returned by the MPSQ API. */
public record TeamRankLog(OffsetDateTime createdAt, String targetName, TeamRank oldRank,
                          TeamRank newRank, String actorName) {
    public static TeamRankLog from(String createdAt, String targetName, String oldRank,
                                   String newRank, String actorName) {
        return new TeamRankLog(OffsetDateTime.parse(createdAt), targetName,
                TeamRank.fromId(oldRank), TeamRank.fromId(newRank), actorName);
    }
}
