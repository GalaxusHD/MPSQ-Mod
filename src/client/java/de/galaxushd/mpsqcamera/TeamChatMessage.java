package de.galaxushd.mpsqcamera;

/** One private team chat row, rendered with the sender's rank image. */
public record TeamChatMessage(String id, String senderName, TeamRank senderRank, String message) { }
