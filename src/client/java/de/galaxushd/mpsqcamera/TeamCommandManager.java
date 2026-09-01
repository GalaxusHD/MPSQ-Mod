package de.galaxushd.mpsqcamera;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

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
                TeamStateStore.self().ifPresent(profile -> {
                    if (profile.canOpenTeamArea() && !target.isBlank()) {
                        JsonObject body = new JsonObject();
                        body.addProperty("displayName", target);
                        MpsqApiClient.post("/team/disqualify", body);
                    }
                });
                return true;
            }
            if (!normalized.equalsIgnoreCase("mpsq") && !normalized.regionMatches(true, 0, "mpsq ", 0, 5)) return true;
            submit(normalized.length() > 5 ? normalized.substring(5) : "");
            return false;
        });
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
