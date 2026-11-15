package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class MpsqActionSync {
    private static String scope = "", cursor;
    private static boolean pending;
    private static boolean wasVisible=true;
    private static long next;
    private static int generation;
    private static final Map<UUID, String> ACTIVE_LINK_TRIGGERS = new HashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> LOCAL_KICK_SOUND_UNTIL = new java.util.concurrent.ConcurrentHashMap<>();
    private MpsqActionSync() {}
    public static void playKickSoundLocally(String targetName) {
        if (!TeamVisibilitySettings.visible() || targetName == null || targetName.isBlank()) return;
        LOCAL_KICK_SOUND_UNTIL.put(targetName.toLowerCase(java.util.Locale.ROOT), System.currentTimeMillis() + 5000L);
        MpsqKickSounds.shot();
    }

    private static boolean consumeLocalKickSound(String targetName) {
        if (targetName == null) return false;
        String key = targetName.toLowerCase(java.util.Locale.ROOT);
        Long until = LOCAL_KICK_SOUND_UNTIL.remove(key);
        return until != null && until >= System.currentTimeMillis();
    }
    static void rememberActiveLinkTrigger(UUID screenId, String triggerId) {
        if (screenId == null || triggerId == null || triggerId.isBlank()) return;
        ACTIVE_LINK_TRIGGERS.put(screenId, triggerId);
    }
    public static String server() {
        var entry = Minecraft.getInstance().getCurrentServer();
        return entry == null ? "" : entry.ip.toLowerCase(java.util.Locale.ROOT);
    }
    public static boolean isMpsqServer(){String host=server().replaceFirst(":\\d+$","");return host.equals("mixelpixel.net")||host.equals("play.mixelpixel.net");}
    public static String world() {
        var world = Minecraft.getInstance().level;
        return world == null ? "" : world.dimension().identifier().toString();
    }
    private static String encode(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->dispatcher.register(
            net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("mpsq-points")
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument("amount",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,10000))
                    .executes(context->{int amount=com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context,"amount");
                        var profile=TeamStateStore.self().orElse(null);
                        if(profile==null||profile.permissionRank().level()<TeamRank.OFFICER.level()){context.getSource().sendError(net.minecraft.network.chat.Component.literal("Dafür brauchst du den Rang Offizier oder höher."));return 0;}
                        if(server().isBlank()&&MpsqLocalWorldStore.available()){boolean saved=MpsqLocalWorldStore.addPoints(amount);context.getSource().sendFeedback(net.minecraft.network.chat.Component.literal(saved?amount+" lokale Punkte für diese Welt gutgeschrieben.":"Lokale Punkte konnten nicht gespeichert werden."));return saved?1:0;}
                        JsonObject body=new JsonObject();body.addProperty("amount",amount);
                        MpsqApiClient.post("/me/points/grant",body).whenComplete((data,error)->net.minecraft.client.Minecraft.getInstance().execute(()->context.getSource().sendFeedback(net.minecraft.network.chat.Component.literal(error==null?"Punkte gutgeschrieben.":"Punkte konnten nicht vergeben werden: "+error.getMessage()))));return 1;
                    }))));

        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->dispatcher.register(
            net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("mpsq-redstone").executes(context->{
                var client=net.minecraft.client.Minecraft.getInstance();
                if(MpsqTvFurniture.configureTarget())return 1;
                if(client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit && client.level!=null){
                    var position=hit.getBlockPos();
                    var state=client.level.getBlockState(position);
                    var kind=MpsqTriggerBlockPolicy.classify(state,client.level,position);
                    if(kind==MpsqTriggerBlockPolicy.Kind.NONE){
                        context.getSource().sendError(net.minecraft.network.chat.Component.literal("Dieser Block ist kein MPSQ-Redstone-Auslöser. Erlaubt sind Knöpfe, Hebel, Druckplatten, Sculk-Sensoren und Stolperdrähte."));
                    } else {
                        String block=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                        String properties=MpsqTriggerBlockPolicy.describeProperties(state);
                        client.execute(()->client.gui.setScreen(new MpsqActionSetupScreen(position,block,state.getBlock().getName().getString(),kind,properties)));
                    }
                } else context.getSource().sendError(net.minecraft.network.chat.Component.literal("Bitte einen Block anschauen."));
                return 1;
            })));
    }
    private static void tick(Minecraft client) {
        if(MpsqReplayPlayback.active())return;
        if(!TeamVisibilitySettings.visible()){if(wasVisible){MpsqAudioManager.stop();MpsqMediaAudioManager.stop();MpsqBossbarManager.clear();MpsqSystemController.stopActive();}wasVisible=false;return;}wasVisible=true;
        String current = server() + "|" + world();
        if (!current.equals(scope)) {
            scope=current; cursor=null; pending=false; generation++; next=0;
            MpsqAudioManager.stop();
            MpsqMediaAudioManager.stop();
            MpsqBossbarManager.clear();
            MpsqSystemController.stopActive();
        }
        if (client.level == null || !isMpsqServer() || !MpsqApiClient.isReady() || pending || System.currentTimeMillis()<next) return;
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
    public static void dispatch(JsonObject event) { if(!TeamVisibilitySettings.visible())return;
        if(MpsqReplayPlayback.active())return;
        JsonObject data=event.getAsJsonObject("action_data");
        String actionType=event.get("action_type").getAsString(); if(!TeamVisibilitySettings.inArea()&&!actionType.equals("OPEN_LINK"))return;
        boolean stateDriven=data.has("redstone_powered")&&!(data.has("redstone_pulse")&&data.get("redstone_pulse").getAsBoolean());
        boolean powered=stateDriven&&data.get("redstone_powered").getAsBoolean();
        if(stateDriven&&!powered&&!java.util.Set.of("TOGGLE_AUDIO","TOGGLE_COUNTDOWN","TOGGLE_BOSSBAR","SWITCH_SYSTEM").contains(actionType))return;
        switch(actionType) {
            case "START_CINEMATIC" -> {if(data.has("sceneId"))MpsqCinematicTriggers.redstone(data.get("sceneId").getAsString());}
            case "TOGGLE_AUDIO" -> {
                boolean playing=MpsqAudioManager.playing()||MpsqMediaAudioManager.playing();
                if(stateDriven){if(powered&&!playing)startTriggerAudio(data);else if(!powered&&playing){MpsqAudioManager.stop();MpsqMediaAudioManager.stop();}}
                else if(playing){MpsqAudioManager.stop();MpsqMediaAudioManager.stop();}
                else startTriggerAudio(data);
            }
            case "TOGGLE_BOSSBAR" -> {boolean shown=MpsqBossbarManager.get("event")!=null;String color=barColor(data);if(stateDriven){if(powered&&!shown)MpsqBossbarManager.apply(new MpsqBossbarState("event",data.get("title").getAsString(),color,1,true));else if(!powered&&shown)MpsqBossbarManager.remove("event");}else if(shown)MpsqBossbarManager.remove("event");else MpsqBossbarManager.apply(new MpsqBossbarState("event",data.get("title").getAsString(),color,1,true));}
            case "TOGGLE_COUNTDOWN" -> {if(data.has("timerName")){MpsqModularTimer.redstone(data.get("timerName").getAsString(),data.get("timerAction").getAsString(),stateDriven,powered);break;}boolean running=MpsqBossbarManager.countdownRunning();String color=barColor(data);if(stateDriven){if(powered&&!running)MpsqBossbarManager.startCountdown(data.get("title").getAsString(),data.get("duration").getAsInt(),event.get("created_at").getAsString(),color);else if(!powered&&running)MpsqBossbarManager.stopCountdown();}else if(running)MpsqBossbarManager.stopCountdown();else MpsqBossbarManager.startCountdown(data.get("title").getAsString(),data.get("duration").getAsInt(),event.get("created_at").getAsString(),color);}
            case "PLAY_AUDIO", "START_PLAYLIST" -> {
                var tracks=new ArrayList<String>();
                if(data.has("tracks")) for(JsonElement track:data.getAsJsonArray("tracks")) tracks.add(track.getAsString());
                else if(data.has("sound")) tracks.add(data.get("sound").getAsString());
                String source=data.has("sourceType")?data.get("sourceType").getAsString():("TOGGLE_AUDIO".equals(actionType)?"auto":"minecraft");
                if(source.equals("mp3")||source.equals("mp4")){MpsqAudioManager.stop();MpsqMediaAudioManager.play(source,tracks);}
                else if(source.equals("auto")){MpsqAudioManager.stop();for(String track:tracks)MpsqMediaAudioManager.playAuto(track);}
                else {MpsqMediaAudioManager.stop();var firstSound=tracks.isEmpty()?null:net.minecraft.resources.Identifier.tryParse(tracks.get(0));MpsqAudioManager.startPlaylist("MPSQ",tracks,MpsqAudioManager.categoryForSoundId(firstSound));}
            }
            case "STOP_AUDIO" -> {MpsqAudioManager.stop();MpsqMediaAudioManager.stop();}
            case "SWITCH_SYSTEM" -> {
                String systemId = data.get("systemId").getAsString();
                if(systemId.equals("glass_bridge")||systemId.equals("rope_jump")||systemId.equals("sky_squid")){
                    if(!data.has("height")||!Double.isFinite(data.get("height").getAsDouble())){
                        var player=Minecraft.getInstance().player;
                        if(player!=null)de.galaxushd.mpsqcamera.compat.PlayerMessages.send(player,Component.literal("Bitte diesen System-Knopf mit /mpsq-redstone neu einrichten und die Y-Grenze speichern."),false);
                        return;
                    }
                    MpsqGamesRuntime.height(data.get("height").getAsDouble());
                }
                MpsqSystemController.onTrigger(systemId, stateDriven, powered);
            }
            case "SKY_SQUID_START" -> {
                if(event.has("trigger_position")){
                    JsonObject pos=event.getAsJsonObject("trigger_position");
                    MpsqGamesRuntime.startSkyTower(new net.minecraft.core.BlockPos(pos.get("x").getAsInt(),pos.get("y").getAsInt(),pos.get("z").getAsInt()));
                }
            }
            case "RLGL_GREEN" -> MpsqMovementSensorSystem.beginGreenCountdown(data.has("duration") ? data.get("duration").getAsInt() : 5);
            case "SHOW_DIALOGUE" -> MpsqDialogueManager.start(data);
            case "KICK_ANIMATION" -> {
                String targetName = data.get("targetName").getAsString();
                MpsqGamesRuntime.kick(targetName);
                boolean localEcho = consumeLocalKickSound(targetName);
                if(!localEcho)TeamCommandManager.showKickMessage(targetName);
                if (!TeamCommandManager.kickEffectsEnabled()) break;
                if (data.has("clone") && data.get("clone").isJsonObject()) {
                    MpsqKickAnimationManager.startClone(targetName, data.getAsJsonObject("clone"), null);
                } else {
                    MpsqKickAnimationManager.start(targetName);
                }
                if (TeamVisibilitySettings.visible()) {
                    if (!localEcho) MpsqKickSounds.shot();

                }
            }
            case "START_COUNTDOWN" -> MpsqBossbarManager.startCountdown(data.get("title").getAsString(), data.get("duration").getAsInt(), event.get("created_at").getAsString(),barColor(data));
            case "SHOW_BOSSBAR" -> MpsqBossbarManager.apply(new MpsqBossbarState("event",data.get("title").getAsString(),barColor(data),1,true));
            case "HIDE_BOSSBAR" -> MpsqBossbarManager.remove("event");
            case "OPEN_LINK" -> openLinkOnScreen(data, event.has("trigger_id") && !event.get("trigger_id").isJsonNull()
                    ? event.get("trigger_id").getAsString() : null);
            default -> { }
        }
    }
    private static String barColor(JsonObject data){return MpsqBossbarManager.normalizeColor(data.has("color")&&!data.get("color").isJsonNull()?data.get("color").getAsString():"purple");}
    private static void startTriggerAudio(JsonObject data){String source=data.has("sourceType")?data.get("sourceType").getAsString():"auto";String sound=data.has("sound")?data.get("sound").getAsString():"";if(source.equals("mp3")||source.equals("mp4")){MpsqAudioManager.stop();MpsqMediaAudioManager.play(source,java.util.List.of(sound));}else if(source.equals("auto")){MpsqAudioManager.stop();MpsqMediaAudioManager.playAuto(sound);}else{MpsqMediaAudioManager.stop();var id=net.minecraft.resources.Identifier.tryParse(sound);MpsqAudioManager.startPlaylist("MPSQ",java.util.List.of(sound),MpsqAudioManager.categoryForSoundId(id));}}
    private static void openLinkOnScreen(JsonObject data, String triggerId) {
        try {
            CinemaBrowserManager.checkWorld();
            var screenId=java.util.UUID.fromString(data.get("screenId").getAsString());
            var screen=LocalScreenStore.findById(screenId).orElse(null);
            String url=data.get("url").getAsString();
            java.net.URI uri=java.net.URI.create(url);
            if(screen==null||(screen.inputType()!=LocalScreenStore.ScreenInputType.LINK && screen.inputType()!=LocalScreenStore.ScreenInputType.MPSQ_REDSTONE)
                    ||!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null
                    ||uri.getUserInfo()!=null||url.length()>2048)return;
            var old=CinemaPlaybackStore.get(screenId);
            long now=System.currentTimeMillis();
            boolean sameVideo=url.equals(screen.url());
            String activeTrigger=ACTIVE_LINK_TRIGGERS.get(screenId);
            boolean sameTrigger=triggerId==null?sameVideo:triggerId.equals(activeTrigger);
            if (sameTrigger && sameVideo && old.playing()) {
                long position=old.positionMs();
                if (old.updatedAtMs()>0L) position+=Math.max(0L,now-old.updatedAtMs());
                CinemaPlaybackStore.set(screenId,new CinemaPlaybackStore.PlaybackState(false,position,old.revision()+1L,now));
            } else {
                if (!sameVideo) LocalScreenStore.updateConfig(screenId,screen.inputType(),url,null);
                long position=sameTrigger&&sameVideo?old.positionMs():0L;
                CinemaPlaybackStore.set(screenId,new CinemaPlaybackStore.PlaybackState(true,position,old.revision()+1L,now));
            }
            rememberActiveLinkTrigger(screenId,triggerId);
            CinemaBrowserManager.synchronize();
            if (isMpsqServer()) java.util.concurrent.CompletableFuture
                    .delayedExecutor(1500L, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .execute(ScreenSyncManager::refresh);
        } catch(RuntimeException ignored) { }
    }
}






