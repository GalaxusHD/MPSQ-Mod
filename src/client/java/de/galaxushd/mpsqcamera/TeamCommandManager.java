package de.galaxushd.mpsqcamera;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Client-only /mpsq command. It is consumed locally and never sent to the Minecraft server. */
public final class TeamCommandManager {
    private TeamCommandManager() { }
    private static boolean sendingDelayedKick;
    private static final java.util.List<PendingKick> pendingKicks=new java.util.ArrayList<>();
    private record PendingKick(String command,String target,long due,net.minecraft.client.multiplayer.ClientLevel world,
                               net.minecraft.client.multiplayer.ClientPacketListener connection){}
    public static boolean kickEffectsEnabled(){
        return TeamVisibilitySettings.visible()&&MpsqMovementSensorSystem.SYSTEM_ID.equals(MpsqSystemController.activeSystemId());
    }
    private static void tickKicks(Minecraft client){
        MpsqParticipation.scan();
        var iterator=pendingKicks.iterator();
        while(iterator.hasNext()){
            PendingKick kick=iterator.next();
            // Never send an old command after leaving/rejoining a world or server.
            if(client.level!=kick.world()||client.getConnection()!=kick.connection()){iterator.remove();continue;}
            boolean effects=kickEffectsEnabled();
            if(effects&&System.nanoTime()<kick.due())continue;
            iterator.remove();
            if(MpsqFinishZoneTool.staff())MpsqGamesRuntime.kick(kick.target());
            if(effects){
                AbstractClientPlayer source=findOnlinePlayer(kick.target());
                JsonObject clone=source==null?null:createKickCloneData(source);
                MpsqActionSync.playKickSoundLocally(kick.target());
                if(clone!=null)MpsqKickAnimationManager.startClone(kick.target(),clone,source);else MpsqKickAnimationManager.start(kick.target());
                TeamStateStore.self().filter(profile->profile.canOpenTeamArea()).ifPresent(profile->{
                    String server=MpsqActionSync.server(),world=MpsqActionSync.world();
                    if(!server.isBlank()&&!world.isBlank()){
                        JsonObject body=new JsonObject();body.addProperty("displayName",kick.target());
                        body.addProperty("serverId",server);body.addProperty("worldId",world);
                        if(clone!=null)body.add("clone",clone);
                        MpsqApiClient.post("/kick-animation",body);
                    }
                });
            }
            sendingDelayedKick=true;
            try{kick.connection().sendCommand(kick.command());}finally{sendingDelayedKick=false;}
        }
    }

    public static void initialize() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,registry)->dispatcher.register(ClientCommands.literal("mpsq-remove").then(ClientCommands.argument("Spieler",StringArgumentType.word()).executes(ctx->{
            if(!MpsqFinishZoneTool.staff()){ctx.getSource().sendError(Component.literal("Offizier-Rang erforderlich."));return 0;}
            String name=StringArgumentType.getString(ctx,"Spieler");if(!name.matches("[A-Za-z0-9_]{1,16}")){ctx.getSource().sendError(Component.literal("Ungültiger Spielername."));return 0;}
            MpsqGamesRuntime.removeFromRound(name);ctx.getSource().sendFeedback(Component.literal(name+" wurde aus Safezone/Team entfernt."));return 1;
        }))));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(TeamCommandManager::tickKicks);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommands.literal("mpsq")
                        .executes(context -> {
                            submit("");
                            return 1;
                        })
                        .then(ClientCommands.argument("message", StringArgumentType.greedyString())
                                .executes(context -> {
                                    submit(StringArgumentType.getString(context, "message"));
                                    return 1;
                                }))));

        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            String normalized = command.startsWith("/") ? command.substring(1) : command;
            if (normalized.regionMatches(true, 0, "p kick ", 0, 7)) {
                String target = normalized.substring(7).trim().split("\\s+", 2)[0];
                if (sendingDelayedKick) return true;
                if(target.isBlank())return true;
                if (!kickEffectsEnabled()) {
                    showKickMessage(target);
                    if(MpsqFinishZoneTool.staff()) MpsqGamesRuntime.kick(target);
                    return true;
                }
                net.minecraft.client.Minecraft client=net.minecraft.client.Minecraft.getInstance();
                if(client.level==null||client.getConnection()==null)return true;
                if(pendingKicks.stream().anyMatch(k->k.target().equalsIgnoreCase(target)))return false;
                showKickMessage(target);
                pendingKicks.add(new PendingKick(normalized,target,System.nanoTime()+1_000_000_000L,client.level,client.getConnection()));
                return false;
            }
            if (!normalized.equalsIgnoreCase("mpsq") && !normalized.regionMatches(true, 0, "mpsq ", 0, 5)) return true;
            submit(normalized.length() > 5 ? normalized.substring(5) : "");
            return false;
        });
    }

    private static AbstractClientPlayer findOnlinePlayer(String name) {
        Minecraft client = Minecraft.getInstance();
        if (name == null || client.level == null) return null;
        for (Player player : client.level.players()) {
            if (player instanceof AbstractClientPlayer remote
                    && remote.getGameProfile().name().equalsIgnoreCase(name)) return remote;
        }
        return null;
    }

    /** Capture the player's pre-teleport pose; the event distributes it to every mod client. */
    private static JsonObject createKickCloneData(AbstractClientPlayer player) {
        JsonObject clone = new JsonObject();
        clone.addProperty("cloneId", java.util.UUID.randomUUID().toString());
        clone.addProperty("x", player.getX());
        clone.addProperty("y", player.getY());
        clone.addProperty("z", player.getZ());
        clone.addProperty("yaw", player.getYRot());
        clone.addProperty("bodyYaw", player.yBodyRot);
        clone.addProperty("pitch", player.getXRot());
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            clone.addProperty("sourceX", client.player.getX());
            clone.addProperty("sourceZ", client.player.getZ());
        }
        return clone;
    }


    static void showKickMessage(String target) {
        Minecraft client = Minecraft.getInstance();
        if (client.gui == null) return;
        Component message = Component.empty()
                .append(Component.literal("Spieler " + target + " ").withStyle(ChatFormatting.DARK_AQUA))
                .append(Component.literal("wurde disqualifiziert.").withStyle(ChatFormatting.RED));
        client.gui.hud.getChat().addClientSystemMessage(message);
    }
    private static void submit(String rawMessage) {
            String message = TeamChatPolicy.prepare(rawMessage);
            Minecraft client = Minecraft.getInstance();
            if (message.isEmpty()) {
                if (client.player != null) de.galaxushd.mpsqcamera.compat.PlayerMessages.send(client.player, Component.translatable("gui.mpsqcamera.team.command.usage"), true);
                return;
            }
            if (TeamChatPolicy.containsForbiddenContent(message)) {
                if (client.player != null) de.galaxushd.mpsqcamera.compat.PlayerMessages.send(client.player, Component.translatable("gui.mpsqcamera.team.command.filtered"), true);
                return;
            }
            TeamStateStore.self().ifPresentOrElse(profile -> {
                if (!profile.canOpenTeamArea()) {
                    if (client.player != null) de.galaxushd.mpsqcamera.compat.PlayerMessages.send(client.player, Component.translatable("gui.mpsqcamera.team.command.denied"), true);
                    return;
                }
                MpsqApiClient.sendTeamMessage(message).whenComplete((ignored, error) -> client.execute(() -> {
                    if (client.player == null) return;
                    de.galaxushd.mpsqcamera.compat.PlayerMessages.send(client.player, error == null
                            ? Component.translatable("gui.mpsqcamera.team.command.sent")
                            : Component.translatable("gui.mpsqcamera.team.command.failed"), true);
                }));
            }, () -> { if (client.player != null) de.galaxushd.mpsqcamera.compat.PlayerMessages.send(client.player, Component.translatable("gui.mpsqcamera.team.command.denied"), true); });
    }
}






