package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class MpsqActionSync {
    private static String scope = "", cursor;
    private static boolean pending;
    private static boolean wasVisible=true;
    private static long next;
    private static int generation;
    private static final Map<UUID, String> ACTIVE_LINK_TRIGGERS = new HashMap<>();
    private MpsqActionSync() {}
    static void rememberActiveLinkTrigger(UUID screenId, String triggerId) {
        if (screenId == null || triggerId == null || triggerId.isBlank()) return;
        ACTIVE_LINK_TRIGGERS.put(screenId, triggerId);
    }
    public static String server() {
        var entry = MinecraftClient.getInstance().getCurrentServerEntry();
        return entry == null ? "" : entry.address.toLowerCase(java.util.Locale.ROOT);
    }
    public static boolean isMpsqServer(){String host=server().replaceFirst(":\\d+$","");return host.equals("mixelpixel.net")||host.equals("play.mixelpixel.net");}
    public static String world() {
        var world = MinecraftClient.getInstance().world;
        return world == null ? "" : world.getRegistryKey().getValue().toString();
    }
    private static String encode(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->dispatcher.register(
            net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("mpsq-points")
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument("amount",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,10000))
                    .executes(context->{int amount=com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context,"amount");
                        var profile=TeamStateStore.self().orElse(null);
                        if(profile==null||profile.permissionRank().level()<TeamRank.OFFICER.level()){context.getSource().sendError(Text.literal("Dafür brauchst du den Rang Offizier oder höher."));return 0;}
                        if(server().isBlank()&&MpsqLocalWorldStore.available()){boolean saved=MpsqLocalWorldStore.addPoints(amount);context.getSource().sendFeedback(Text.literal(saved?amount+" lokale Punkte für diese Welt gutgeschrieben.":"Lokale Punkte konnten nicht gespeichert werden."));return saved?1:0;}
                        JsonObject body=new JsonObject();body.addProperty("amount",amount);
                        MpsqApiClient.post("/me/points/grant",body).whenComplete((data,error)->MinecraftClient.getInstance().execute(()->context.getSource().sendFeedback(Text.literal(error==null?"Punkte gutgeschrieben.":"Punkte konnten nicht vergeben werden: "+error.getMessage()))));return 1;
                    }))));

        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->dispatcher.register(
            net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("mpsq-redstone").executes(context->{
                var client=MinecraftClient.getInstance();
                if(client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit && client.world!=null){
                    var position=hit.getBlockPos();
                    var state=client.world.getBlockState(position);
                    var kind=MpsqTriggerBlockPolicy.classify(state,client.world,position);
                    if(kind==MpsqTriggerBlockPolicy.Kind.NONE){
                        context.getSource().sendError(Text.literal("Dieser Block ist kein MPSQ-Redstone-Auslöser. Erlaubt sind Knöpfe, Hebel, Druckplatten, Sculk-Sensoren und Stolperdrähte."));
                    } else {
                        String block=net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).toString();
                        String properties=MpsqTriggerBlockPolicy.describeProperties(state);
                        client.send(()->client.setScreen(new MpsqActionSetupScreen(position,block,state.getBlock().getName().getString(),kind,properties)));
                    }
                } else context.getSource().sendError(Text.literal("Bitte einen Block anschauen."));
                return 1;
            })));
    }
    private static void tick(MinecraftClient client) {
        if(!TeamVisibilitySettings.visible()){if(wasVisible){MpsqAudioManager.stop();MpsqMediaAudioManager.stop();MpsqBossbarManager.clear();MpsqSystemController.stopActive();}wasVisible=false;return;}wasVisible=true;
        String current = server() + "|" + world();
        if (!current.equals(scope)) {
            scope=current; cursor=null; pending=false; generation++; next=0;
            MpsqAudioManager.stop();
            MpsqMediaAudioManager.stop();
            MpsqBossbarManager.clear();
            MpsqSystemController.stopActive();
        }
        if (client.world == null || !isMpsqServer() || !MpsqApiClient.isReady() || pending || System.currentTimeMillis()<next) return;
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
        String actionType=event.get("action_type").getAsString();
        boolean stateDriven=data.has("redstone_powered")&&!(data.has("redstone_pulse")&&data.get("redstone_pulse").getAsBoolean());
        boolean powered=stateDriven&&data.get("redstone_powered").getAsBoolean();
        if(stateDriven&&!powered&&!java.util.Set.of("TOGGLE_AUDIO","TOGGLE_COUNTDOWN","TOGGLE_BOSSBAR","SWITCH_SYSTEM").contains(actionType))return;
        switch(actionType) {
            case "TOGGLE_AUDIO" -> {
                boolean playing=MpsqAudioManager.playing()||MpsqMediaAudioManager.playing();
                if(stateDriven){if(powered&&!playing)startTriggerAudio(data);else if(!powered&&playing){MpsqAudioManager.stop();MpsqMediaAudioManager.stop();}}
                else if(playing){MpsqAudioManager.stop();MpsqMediaAudioManager.stop();}
                else startTriggerAudio(data);
            }
            case "TOGGLE_BOSSBAR" -> {boolean shown=MpsqBossbarManager.get("event")!=null;String color=barColor(data);if(stateDriven){if(powered&&!shown)MpsqBossbarManager.apply(new MpsqBossbarState("event",data.get("title").getAsString(),color,1,true));else if(!powered&&shown)MpsqBossbarManager.remove("event");}else if(shown)MpsqBossbarManager.remove("event");else MpsqBossbarManager.apply(new MpsqBossbarState("event",data.get("title").getAsString(),color,1,true));}
            case "TOGGLE_COUNTDOWN" -> {boolean running=MpsqBossbarManager.countdownRunning();String color=barColor(data);if(stateDriven){if(powered&&!running)MpsqBossbarManager.startCountdown(data.get("title").getAsString(),data.get("duration").getAsInt(),event.get("created_at").getAsString(),color);else if(!powered&&running)MpsqBossbarManager.stopCountdown();}else if(running)MpsqBossbarManager.stopCountdown();else MpsqBossbarManager.startCountdown(data.get("title").getAsString(),data.get("duration").getAsInt(),event.get("created_at").getAsString(),color);}
            case "PLAY_AUDIO", "START_PLAYLIST" -> {
                var tracks=new ArrayList<String>();
                if(data.has("tracks")) for(JsonElement track:data.getAsJsonArray("tracks")) tracks.add(track.getAsString());
                else if(data.has("sound")) tracks.add(data.get("sound").getAsString());
                String source=data.has("sourceType")?data.get("sourceType").getAsString():("TOGGLE_AUDIO".equals(actionType)?"auto":"minecraft");
                if(source.equals("mp3")||source.equals("mp4")){MpsqAudioManager.stop();MpsqMediaAudioManager.play(source,tracks);}
                else if(source.equals("auto")){MpsqAudioManager.stop();for(String track:tracks)MpsqMediaAudioManager.playAuto(track);}
                else {MpsqMediaAudioManager.stop();var firstSound=tracks.isEmpty()?null:net.minecraft.util.Identifier.tryParse(tracks.get(0));MpsqAudioManager.startPlaylist("MPSQ",tracks,MpsqAudioManager.categoryForSoundId(firstSound));}
            }
            case "STOP_AUDIO" -> {MpsqAudioManager.stop();MpsqMediaAudioManager.stop();}
            case "SWITCH_SYSTEM" -> MpsqSystemController.onTrigger(data.get("systemId").getAsString(), stateDriven, powered);
            case "SHOW_DIALOGUE" -> MpsqDialogueManager.start(data);
            case "KICK_ANIMATION" -> {
                String targetName = data.get("targetName").getAsString();
                MpsqKickAnimationManager.start(targetName);
                if (TeamVisibilitySettings.visible()) {
                    MpsqMediaAudioManager.playBundledMp3("/assets/mpsqcamera/sounds/kick.mp3", 0.28f);
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.inGameHud != null) client.inGameHud.getChatHud().addMessage(
                            Text.empty()
                                    .append(Text.literal("Spieler " + targetName + " ").formatted(Formatting.DARK_AQUA))
                                    .append(Text.literal("wurde disqualifiziert.").formatted(Formatting.RED)));
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
    private static void startTriggerAudio(JsonObject data){String source=data.has("sourceType")?data.get("sourceType").getAsString():"auto";String sound=data.has("sound")?data.get("sound").getAsString():"";if(source.equals("mp3")||source.equals("mp4")){MpsqAudioManager.stop();MpsqMediaAudioManager.play(source,java.util.List.of(sound));}else if(source.equals("auto")){MpsqAudioManager.stop();MpsqMediaAudioManager.playAuto(sound);}else{MpsqMediaAudioManager.stop();var id=net.minecraft.util.Identifier.tryParse(sound);MpsqAudioManager.startPlaylist("MPSQ",java.util.List.of(sound),MpsqAudioManager.categoryForSoundId(id));}}
    private static void openLinkOnScreen(JsonObject data, String triggerId) {
        try {
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






