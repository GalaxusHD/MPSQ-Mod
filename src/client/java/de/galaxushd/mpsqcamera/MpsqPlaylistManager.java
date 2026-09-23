package de.galaxushd.mpsqcamera;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Client-side playlist state. Audio files remain supplied by the server pack. */
public final class MpsqPlaylistManager {
    private static final List<String> CURRENT_TRACKS = new CopyOnWriteArrayList<>();
    private static volatile String currentPlaylist = "";
    private MpsqPlaylistManager() { }
    public static void start(String playlist, List<String> tracks) { currentPlaylist = playlist == null ? "" : playlist; CURRENT_TRACKS.clear(); if (tracks != null) CURRENT_TRACKS.addAll(tracks); }
    public static void stop() { currentPlaylist = ""; CURRENT_TRACKS.clear(); }
    public static String currentPlaylist() { return currentPlaylist; }
    public static List<String> tracks() { return List.copyOf(CURRENT_TRACKS); }
}
