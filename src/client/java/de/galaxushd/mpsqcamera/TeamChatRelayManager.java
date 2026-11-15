package de.galaxushd.mpsqcamera;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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

    private static void tick(Minecraft client) {
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
                if (KNOWN.add(key(message)) && client.player != null) de.galaxushd.mpsqcamera.compat.PlayerMessages.send(client.player, format(message), false);
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

    private static Component format(TeamChatMessage message) {
        MutableComponent text = Component.literal("<").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("MPSQ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
                .append(Component.literal("> ").withStyle(ChatFormatting.GRAY));
        String content = message.message();
        if (content.equalsIgnoreCase(message.senderName())) content = "";
        String duplicatePrefix = message.senderName() + ": ";
        if (content.regionMatches(true, 0, duplicatePrefix, 0, duplicatePrefix.length())) content = content.substring(duplicatePrefix.length());
        if (content.matches("(?i).+ wurde disqualifiziert\\.")) return text.append(Component.literal(content).withStyle(ChatFormatting.WHITE));
        text.append(Component.literal(message.senderName()).withStyle(message.senderRank().chatColor()));
        text.append(Component.literal(": ").withStyle(ChatFormatting.GRAY));
        return text.append(TeamChatText.fromAmpersandCodes(content, ChatFormatting.WHITE));
    }
}
