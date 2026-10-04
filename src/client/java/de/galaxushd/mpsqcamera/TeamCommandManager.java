package de.galaxushd.mpsqcamera;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Client-only /mpsq command. It is consumed locally and never sent to the Minecraft server. */
public final class TeamCommandManager {
    private TeamCommandManager() { }

    public static void initialize() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("mpsq")
                        .executes(context -> {
                            submit("");
                            return 1;
                        })
                        .then(ClientCommandManager.argument("message", StringArgumentType.greedyString())
                                .executes(context -> {
                                    submit(StringArgumentType.getString(context, "message"));
                                    return 1;
                                }))));

        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            String normalized = command.startsWith("/") ? command.substring(1) : command;
            if (normalized.regionMatches(true, 0, "p kick ", 0, 7)) {
                String target = normalized.substring(7).trim().split("\\s+", 2)[0];
                AbstractClientPlayerEntity targetEntity = findOnlinePlayer(target);
                JsonObject clone = targetEntity == null ? null : createKickCloneData(targetEntity);
                if (clone != null) MpsqKickAnimationManager.startClone(target, clone, targetEntity);
                else if (!target.isBlank()) MpsqKickAnimationManager.start(target);
                TeamStateStore.self().ifPresent(profile -> {
                    if (profile.canOpenTeamArea() && !target.isBlank()) {
                        JsonObject body = new JsonObject();
                        body.addProperty("displayName", target);
                        String server = MpsqActionSync.server(), world = MpsqActionSync.world();
                        if (!server.isBlank() && !world.isBlank()) {
                            body.addProperty("serverId", server);
                            body.addProperty("worldId", world);
                            if (clone != null) body.add("clone", clone);
                            MpsqActionSync.playKickSoundLocally(target);
                            MpsqApiClient.post("/kick-animation", body);
                        } else if (TeamVisibilitySettings.visible()) {
                            MpsqMediaAudioManager.playBundledMp3("/assets/mpsqcamera/sounds/kick.mp3", 0.28f);
                            showKickMessage(target);
                        }
                    }
                });
                return true;
            }
            if (!normalized.equalsIgnoreCase("mpsq") && !normalized.regionMatches(true, 0, "mpsq ", 0, 5)) return true;
            submit(normalized.length() > 5 ? normalized.substring(5) : "");
            return false;
        });
    }

    private static AbstractClientPlayerEntity findOnlinePlayer(String name) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (name == null || client.world == null) return null;
        for (PlayerEntity player : client.world.getPlayers()) {
            if (player instanceof AbstractClientPlayerEntity remote
                    && remote.getGameProfile().getName().equalsIgnoreCase(name)) return remote;
        }
        return null;
    }

    /** Capture the player's pre-teleport pose; the event distributes it to every mod client. */
    private static JsonObject createKickCloneData(AbstractClientPlayerEntity player) {
        JsonObject clone = new JsonObject();
        clone.addProperty("cloneId", java.util.UUID.randomUUID().toString());
        clone.addProperty("x", player.getX());
        clone.addProperty("y", player.getY());
        clone.addProperty("z", player.getZ());
        clone.addProperty("yaw", player.getYaw());
        clone.addProperty("pitch", player.getPitch());
        return clone;
    }


    private static void showKickMessage(String target) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud == null) return;
        Text message = Text.empty()
                .append(Text.literal("Spieler " + target + " ").formatted(Formatting.DARK_AQUA))
                .append(Text.literal("wurde disqualifiziert.").formatted(Formatting.RED));
        client.inGameHud.getChatHud().addMessage(message);
    }
    private static void submit(String rawMessage) {
            String message = TeamChatPolicy.prepare(rawMessage);
            MinecraftClient client = MinecraftClient.getInstance();
            if (message.isEmpty()) {
                if (client.player != null) client.player.sendMessage(Text.translatable("gui.mpsqcamera.team.command.usage"), true);
                return;
            }
            if (TeamChatPolicy.containsForbiddenContent(message)) {
                if (client.player != null) client.player.sendMessage(Text.translatable("gui.mpsqcamera.team.command.filtered"), true);
                return;
            }
            TeamStateStore.self().ifPresentOrElse(profile -> {
                if (!profile.canOpenTeamArea()) {
                    if (client.player != null) client.player.sendMessage(Text.translatable("gui.mpsqcamera.team.command.denied"), true);
                    return;
                }
                MpsqApiClient.sendTeamMessage(message).whenComplete((ignored, error) -> client.execute(() -> {
                    if (client.player == null) return;
                    client.player.sendMessage(error == null
                            ? Text.translatable("gui.mpsqcamera.team.command.sent")
                            : Text.translatable("gui.mpsqcamera.team.command.failed"), true);
                }));
            }, () -> { if (client.player != null) client.player.sendMessage(Text.translatable("gui.mpsqcamera.team.command.denied"), true); });
    }
}






