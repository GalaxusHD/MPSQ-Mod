package de.galaxushd.mpsqcamera;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Displays private MPSQ Team messages in the normal Minecraft chat. */
public final class TeamChatRelayManager {
    private static final Set<String> KNOWN = new HashSet<>();
    private static int ticks;
    private static boolean primed;
    private static boolean loading;

    private TeamChatRelayManager() { }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(TeamChatRelayManager::tick);
    }

    private static void tick(MinecraftClient client) {
        if (client.player == null || loading || !MpsqApiClient.isReady() || !TeamVisibilitySettings.visible()) return;
        if (++ticks < 20) return;
        ticks = 0;
        TeamProfile self = TeamStateStore.self().orElse(null);
        if (self == null) return;
        loading = true;
        MpsqApiClient.loadTeamMessages().whenComplete((messages, error) -> client.execute(() -> {
            loading = false;
            if (error != null || messages == null) return;
            if (!primed) {
                messages.forEach(message -> KNOWN.add(key(message)));
                primed = true;
                return;
            }
            for (TeamChatMessage message : messages) {
                if (KNOWN.add(key(message)) && client.player != null) client.player.sendMessage(format(message), false);
            }
            while (KNOWN.size() > 300) {
                var iterator = KNOWN.iterator();
                iterator.next();
                iterator.remove();
            }
        }));
    }

    private static String key(TeamChatMessage message) {
        return message.id();
    }

    private static Text format(TeamChatMessage message) {
        MutableText text = Text.literal("<").formatted(Formatting.GRAY)
                .append(Text.literal("MPSQ").formatted(Formatting.DARK_RED, Formatting.BOLD))
                .append(Text.literal("> ").formatted(Formatting.GRAY));
        String content = message.message();
        if (content.equalsIgnoreCase(message.senderName())) content = "";
        String duplicatePrefix = message.senderName() + ": ";
        if (content.regionMatches(true, 0, duplicatePrefix, 0, duplicatePrefix.length())) content = content.substring(duplicatePrefix.length());
        if (content.matches("(?i).+ wurde disqualifiziert\\.")) return text.append(Text.literal(content).formatted(Formatting.WHITE));
        text.append(Text.literal(message.senderName()).formatted(message.senderRank().chatColor()));
        text.append(Text.literal(": ").formatted(Formatting.GRAY));
        return text.append(TeamChatText.fromAmpersandCodes(content, Formatting.WHITE));
    }
}
