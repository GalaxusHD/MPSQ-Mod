package de.galaxushd.mpsqcamera;

import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/** Build labels from the real account name, never from a server prefix/font. */
public final class MpsqNametags {
    private static final Identifier RANK_FONT = Identifier.of("mpsqcamera", "ranks");
    private static final Identifier NAME_FONT = Identifier.of("minecraft", "default");
    private MpsqNametags() {}

    public static Text forPlayer(String accountName) {
        TeamProfile profile = TeamStateStore.byMinecraftName(accountName).orElse(null);
        TeamRank rank = profile == null ? TeamRank.PLAYER : profile.displayedRank();
        Text icon = Text.literal(glyph(rank)).setStyle(Style.EMPTY.withFont(RANK_FONT).withColor(Formatting.WHITE));
        TeamProfile viewer = TeamStateStore.self().orElse(null);
        boolean visible = profile == null || profile.nameVisible()
                || viewer != null && viewer.permissionRank().canSeeHiddenNames();
        if (!visible) return icon;
        return Text.empty().append(icon).append(Text.literal(" " + accountName)
                .setStyle(Style.EMPTY.withFont(NAME_FONT).withColor(Formatting.WHITE)));
    }

    private static String glyph(TeamRank rank) {
        return switch (rank) {
            case VIP -> "\ue001";
            case PLAYER -> "\ue002";
            case UNDERCOVER_001 -> "\ue003";
            case SOLDIER -> "\ue004";
            case WORKER -> "\ue005";
            case OFFICER -> "\ue006";
            case FRONTMAN -> "\ue007";
            case SENIOR_OFFICER -> "\ue008";
            case STREAMER -> "\ue009";
        };
    }
}
