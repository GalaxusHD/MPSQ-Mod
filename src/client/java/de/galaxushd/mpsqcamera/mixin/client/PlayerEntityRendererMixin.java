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
        if (profile == null) return original;
        NametagRenderContext.activate();

        TeamProfile viewer = TeamStateStore.self().orElse(null);
        boolean maySeeName = profile.nameVisible()
                || (viewer != null && viewer.permissionRank().canSeeHiddenNames());

        // Nur das private Sonderzeichen verwendet die Bitmap-Schrift. Leerzeichen
        // und Spielername erzwingen wieder die normale Minecraft-Schrift, damit
        // dort keine fehlenden Zeichen/Kästchen erscheinen.
        Text icon = Text.literal(rankGlyph(profile.displayedRank()))
                .setStyle(Style.EMPTY.withFont(MPSQ_RANK_FONT).withColor(Formatting.WHITE));
        if (!maySeeName) return icon;
        Text separator = Text.literal(" ").setStyle(Style.EMPTY.withFont(MINECRAFT_DEFAULT_FONT));
        Text name = Text.literal(profile.displayName()).setStyle(Style.EMPTY
                .withFont(MINECRAFT_DEFAULT_FONT).withColor(Formatting.WHITE));
        return Text.empty().append(icon).append(separator).append(name);
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

    private static boolean matchesProfileName(String profileName, String renderedName) {
        if (profileName == null || profileName.isBlank() || renderedName == null) return false;
        return renderedName.equalsIgnoreCase(profileName)
                || renderedName.regionMatches(true, Math.max(0, renderedName.length() - profileName.length()),
                profileName, 0, profileName.length());
    }
}
