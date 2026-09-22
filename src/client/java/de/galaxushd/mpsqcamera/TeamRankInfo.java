package de.galaxushd.mpsqcamera;

import java.util.List;

/** Description shown when a rank icon is selected in the Ränge view. */
public record TeamRankInfo(String id, String name, String description, List<String> permissions) { }
