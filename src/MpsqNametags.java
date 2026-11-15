package de.galaxushd.mpsqcamera;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/** Build labels from the real account name, never from a server prefix/font. */
public final class MpsqNametags {
    private static final Identifier RANK_FONT = Identifier.fromNamespaceAndPath("mpsqcamera", "ranks");
    private static final Identifier NAME_FONT = Identifier.fromNamespaceAndPath("minecraft", "default");
    private MpsqNametags() {}

    public static Component forPlayer(String accountName) {
        TeamProfile profile = TeamStateStore.byMinecraftName(accountName).orElse(null);
        TeamRank rank = profile == null ? TeamRank.PLAYER : profile.displayedRank();
        TeamProfile viewer = TeamStateStore.self().orElse(null);
        boolean visible = profile == null || profile.nameVisible()
                || viewer != null && viewer.permissionRank().canSeeHiddenNames();
        return forRankAndName(rank,visible?accountName:"");
    }

    /** One text component gives icon, spacing and name one vanilla background and baseline. */
    static Component forRankAndName(TeamRank rank,String name) {
        Component icon=Component.literal(glyph(rank)).setStyle(Style.EMPTY.withFont(new net.minecraft.network.chat.FontDescription.Resource(RANK_FONT)).withColor(ChatFormatting.WHITE));
        if(name==null||name.isBlank())return icon;
        return Component.empty().append(icon).append(Component.literal(" " + name)
                .setStyle(Style.EMPTY.withFont(new net.minecraft.network.chat.FontDescription.Resource(NAME_FONT)).withColor(ChatFormatting.WHITE)));
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
