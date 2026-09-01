package de.galaxushd.mpsqcamera.mixin.client;

import de.galaxushd.mpsqcamera.NametagRenderContext;

import de.galaxushd.mpsqcamera.MpsqCameraClient;
import de.galaxushd.mpsqcamera.TeamProfile;
import de.galaxushd.mpsqcamera.TeamRank;
import de.galaxushd.mpsqcamera.TeamStateStore;
import de.galaxushd.mpsqcamera.TeamVisibilitySettings;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

/** Ersetzt das serverseitige Nametag lokal durch MPSQ-Rangbild und Spielername. */
@Mixin(net.minecraft.client.render.entity.PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    private static final Identifier MPSQ_RANK_FONT = Identifier.of(MpsqCameraClient.MOD_ID, "ranks");
    private static final Identifier MINECRAFT_DEFAULT_FONT = Identifier.of("minecraft", "default");

    @ModifyVariable(method = "renderLabelIfPresent", at = @At("HEAD"), argsOnly = true)
    private Text mpsq$replaceServerRank(
            Text original,
            PlayerEntityRenderState state,
            Text renderedText,
            MatrixStack matrices,
            VertexConsumerProvider consumers,
            int light
    ) {
        NametagRenderContext.clear();
        if (!TeamVisibilitySettings.visible()) return original;

        String stateName = state.name == null ? "" : state.name;
        String originalName = original.getString();
        TeamProfile profile = TeamStateStore.members().stream()
                .filter(value -> matchesProfileName(value.displayName(), stateName)
                        || matchesProfileName(value.displayName(), originalName))
                .findFirst().orElse(null);

        // Players without the mod have no Supabase team profile. As long as
        // this is the actual player-name label (and not a scoreboard line),
        // remove the server prefix and show the neutral MPSQ Spieler rank.
        if (profile == null) {
            if (stateName.isBlank() || !matchesProfileName(stateName, originalName)) return original;
            NametagRenderContext.activate();
            return nametag(TeamRank.PLAYER, stateName);
        }
        NametagRenderContext.activate();

        TeamProfile viewer = TeamStateStore.self().orElse(null);
        boolean maySeeName = profile.nameVisible()
                || (viewer != null && viewer.permissionRank().canSeeHiddenNames());

        // Nur das private Sonderzeichen verwendet die Bitmap-Schrift. Leerzeichen
        // und Spielername erzwingen wieder die normale Minecraft-Schrift, damit
        // dort keine fehlenden Zeichen/Kästchen erscheinen.
        if (!maySeeName) return rankIcon(profile.displayedRank());
        return nametag(profile.displayedRank(), profile.displayName());
    }


    @Inject(method = "renderLabelIfPresent", at = @At("RETURN"))
    private void mpsq$finishNametag(
            PlayerEntityRenderState state,
            Text text,
            MatrixStack matrices,
            VertexConsumerProvider consumers,
            int light,
            CallbackInfo ci
    ) {
        NametagRenderContext.clear();
    }
    private static String rankGlyph(TeamRank rank) {
        return switch (rank) {
            case VIP -> "\ue001";
            case PLAYER -> "\ue002";
            case STREAMER -> "\ue009";
            case UNDERCOVER_001 -> "\ue003";
            case SOLDIER -> "\ue004";
            case WORKER -> "\ue005";
            case OFFICER -> "\ue006";
            case FRONTMAN -> "\ue007";
            case SENIOR_OFFICER -> "\ue008";
        };
    }

    private static Text nametag(TeamRank rank, String playerName) {
        Text separator = Text.literal(" ").setStyle(Style.EMPTY.withFont(MINECRAFT_DEFAULT_FONT));
        Text name = Text.literal(playerName).setStyle(Style.EMPTY
                .withFont(MINECRAFT_DEFAULT_FONT).withColor(Formatting.WHITE));
        return Text.empty().append(rankIcon(rank)).append(separator).append(name);
    }

    private static Text rankIcon(TeamRank rank) {
        // Only the private-use glyph uses the bitmap font. Normal spaces and
        // player names explicitly switch back to Minecraft's default font.
        return Text.literal(rankGlyph(rank))
                .setStyle(Style.EMPTY.withFont(MPSQ_RANK_FONT).withColor(Formatting.WHITE));
    }

    private static boolean matchesProfileName(String profileName, String renderedName) {
        if (profileName == null || profileName.isBlank() || renderedName == null) return false;
        String expected = profileName.trim().toLowerCase(Locale.ROOT);
        String rendered = renderedName.toLowerCase(Locale.ROOT);
        int from = 0;
        while (from <= rendered.length() - expected.length()) {
            int match = rendered.indexOf(expected, from);
            if (match < 0) return false;
            int end = match + expected.length();
            boolean cleanStart = match == 0 || !isMinecraftNameCharacter(rendered.charAt(match - 1));
            boolean cleanEnd = end == rendered.length() || !isMinecraftNameCharacter(rendered.charAt(end));
            if (cleanStart && cleanEnd) return true;
            from = match + 1;
        }
        return false;
    }

    private static boolean isMinecraftNameCharacter(char value) {
        return value == '_' || value >= '0' && value <= '9'
                || value >= 'a' && value <= 'z';
    }
}
