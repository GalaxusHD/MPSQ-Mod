package de.galaxushd.mpsqcamera;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/** Shared MPSQ Team roles. The order is the permission order, not a public leaderboard. */
public enum TeamRank {
    VIP("vip", "VIP", 0, 992),
    PLAYER("spieler", "Spieler", 1, 1568),
    STREAMER("streamer", "Streamer", 2, 1760),
    UNDERCOVER_001("001", "001", 3, 864),
    SOLDIER("soldat", "Soldat", 4, 1376),
    WORKER("arbeiter", "Arbeiter", 5, 1760),
    OFFICER("offizier", "Offizier", 6, 1760),
    FRONTMAN("frontman", "Frontman", 7, 1760),
    SENIOR_OFFICER("sr_offizier", "Sr Offizier", 8, 2272);

    private static final int TEXTURE_HEIGHT = 320;
    private final String id;
    private final String label;
    private final int level;
    private final int textureWidth;

    TeamRank(String id, String label, int level, int textureWidth) {
        this.id = id;
        this.label = label;
        this.level = level;
        this.textureWidth = textureWidth;
    }

    public String id() { return id; }
    public String label() { return label; }
    public int level() { return level; }
    /** Colour used for the normal Minecraft chat name, not for the rank image. */
    public Formatting chatColor() {
        return switch (this) {
            case UNDERCOVER_001, VIP -> Formatting.GOLD;
            case STREAMER -> Formatting.LIGHT_PURPLE;
            case SOLDIER, WORKER, OFFICER -> Formatting.RED;
            case FRONTMAN, SENIOR_OFFICER -> Formatting.DARK_GRAY;
            case PLAYER -> Formatting.AQUA;
        };
    }
    public boolean canViewCameras() { return this == STREAMER || level >= SOLDIER.level; }
    public boolean canSeeHiddenNames() { return level >= SOLDIER.level; }
    public Identifier texture() { return Identifier.of(MpsqCameraClient.MOD_ID, "textures/gui/ranks/" + id + ".png"); }
    public int widthForHeight(int height) { return Math.max(1, textureWidth * height / TEXTURE_HEIGHT); }

    public void draw(DrawContext context, int x, int y, int height) {
        int width = widthForHeight(height);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture(), x, y, 0, 0, width, height,
                textureWidth, TEXTURE_HEIGHT, textureWidth, TEXTURE_HEIGHT);
    }

    public static TeamRank fromId(String value) {
        if (value == null) return PLAYER;
        for (TeamRank rank : values()) if (rank.id.equalsIgnoreCase(value)) return rank;
        return PLAYER;
    }
}
