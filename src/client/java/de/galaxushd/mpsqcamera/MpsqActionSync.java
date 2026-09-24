package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public final class MpsqActionSync {
    private static String scope = "", cursor;
    private static boolean pending;
    private static long next;
    private static int generation;
    private MpsqActionSync() {}
    public static String server() {
        var entry = MinecraftClient.getInstance().getCurrentServerEntry();
        return entry == null ? "" : entry.address.toLowerCase(java.util.Locale.ROOT);
    }
    public static String world() {
        var world = MinecraftClient.getInstance().world;
        return world == null ? "" : world.getRegistryKey().getValue().toString();
    }
    private static String encode(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->dispatcher.register(
            net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("mpsq-objekt").executes(context->{
                var client=MinecraftClient.getInstance();
                if(client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit && client.world!=null)client.send(()->client.setScreen(new MpsqObjectScreen(hit.getBlockPos())));
                else context.getSource().sendError(Text.literal("Bitte den Bodenblock unter dem Objekt anschauen."));
                return 1;
            })));

        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->dispatcher.register(
            net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("mpsq-npc").executes(context->{
                var client=MinecraftClient.getInstance();
                if(client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit && client.world!=null)client.send(()->client.setScreen(new MpsqNpcPlacementScreen(null,hit.getBlockPos())));
                else context.getSource().sendError(Text.literal("Bitte den Bodenblock unter dem NPC anschauen."));
                return 1;
            })));

        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->dispatcher.register(
            net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("mpsq-knopf").executes(context->{
                var client=MinecraftClient.getInstance();
                if(client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit && client.world!=null){
                    var position=hit.getBlockPos();
                    String block=net.minecraft.registry.Registries.BLOCK.getId(client.world.getBlockState(position).getBlock()).toString();
                    client.send(()->client.setScreen(new MpsqActionSetupScreen(position,block)));
                } else context.getSource().sendError(Text.literal("Bitte einen Block anschauen."));
                return 1;
            })));
    }
    private static void tick(MinecraftClient client) {
        String current = server() + "|" + world();
        if (!current.equals(scope)) {
            scope=current; cursor=null; pending=false; generation++; next=0;
            MpsqAudioManager.stop();
            MpsqBossbarManager.clear();
        }
        if (client.world == null || server().isBlank() || !MpsqApiClient.isReady() || pending || System.currentTimeMillis()<next) return;
        int requestGeneration=generation;
        pending=true;
        String path="/action-events?server="+encode(server())+"&world="+encode(world())+(cursor==null?"":"&after="+cursor);
        MpsqApiClient.get(path).whenComplete((result,error)->client.execute(()->{
            if (generation!=requestGeneration) return;
            pending=false; next=System.currentTimeMillis()+(error==null?1000:5000);
            if(error!=null) { MpsqCameraClient.LOGGER.debug("MPSQ-Ereignisse nicht erreichbar",error); return; }
            JsonObject data=result.getAsJsonObject();
            for(JsonElement item:data.getAsJsonArray("events")) {
                try { dispatch(item.getAsJsonObject()); }
                catch(RuntimeException ex) { MpsqCameraClient.LOGGER.warn("Ungültige MPSQ-Aktion",ex); }
            }
            cursor=data.get("cursor").getAsString();
        }));
    }
    public static void dispatch(JsonObject event) {
        JsonObject data=event.getAsJsonObject("action_data");
        switch(event.get("action_type").getAsString()) {
            case "PLAY_AUDIO", "START_PLAYLIST" -> {
                var tracks=new ArrayList<String>();
                if(data.has("tracks")) for(JsonElement track:data.getAsJsonArray("tracks")) tracks.add(track.getAsString());
                else if(data.has("sound")) tracks.add(data.get("sound").getAsString());
                MpsqAudioManager.startPlaylist("MPSQ",tracks);
            }
            case "STOP_AUDIO" -> MpsqAudioManager.stop();
            case "SHOW_DIALOGUE" -> MpsqDialogueManager.start(data);
            case "KICK_ANIMATION" -> MpsqKickAnimationManager.start(data.get("targetName").getAsString());
            case "START_COUNTDOWN" -> MpsqBossbarManager.startCountdown(data.get("title").getAsString(), data.get("duration").getAsInt(), event.get("created_at").getAsString());
            case "SHOW_BOSSBAR" -> MpsqBossbarManager.apply(new MpsqBossbarState("event",data.get("title").getAsString(),"purple",1,true));
            case "HIDE_BOSSBAR" -> MpsqBossbarManager.remove("event");
            case "SEND_ANNOUNCEMENT" -> {
                var client=MinecraftClient.getInstance();
                client.inGameHud.getChatHud().addMessage(TeamChatText.fromAmpersandCodes(data.get("text").getAsString(),net.minecraft.util.Formatting.WHITE));
                if(data.has("sound")) MpsqAudioManager.startPlaylist("Ansage",java.util.List.of(data.get("sound").getAsString()));
            }
            default -> { }
        }
    }
}



