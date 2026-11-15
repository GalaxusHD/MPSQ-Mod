package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.command.v2.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import de.galaxushd.mpsqcamera.compat.WorldRenderEvents;

/** Independent, persistent world timers. Live timers never drive replay playback. */
final class MpsqModularTimer {
    static final class Timer {
        String name,world,format="auto",idle="bob",animation="size",end="sound",system="",endInput="";
        double x,y,z;float yaw,pitch,scale=.22f;int color=0xFFFFFFFF;
        boolean bossbar=false,tickSound=false,redEnding=false;final MpsqTimerClock clock=new MpsqTimerClock();
        long age;String shown="";final long[] changed={-10000,-10000,-10000,-10000};
        String digits(){return MpsqTimerClock.digits(clock.seconds(),"auto");} int displayColor(){return redEnding&&!clock.up&&clock.seconds()<=10?0xFFFF3030:color;}
        void visual(){String next=digits();for(int i=0;i<4;i++)if(shown.length()==4&&shown.charAt(i)!=next.charAt(i))changed[i]=age;shown=next;}
    }
    private static final List<Timer> timers=new ArrayList<>();
    private static Object level;private static long last,saved;private static String previousSystem="";
    static List<Timer> current(){String world=MpsqActionSync.world();return timers.stream().filter(t->t.world.equals(world)).toList();}
    static boolean editable(){return Minecraft.getInstance().level!=null&&MpsqLocalWorldStore.available()&&!MpsqReplayPlayback.active();}
    static Timer find(String name){return current().stream().filter(t->t.name.equalsIgnoreCase(name)).findFirst().orElseThrow(()->new IllegalArgumentException("Timer nicht gefunden: "+name));}
    static Timer create(String name,long ms){
        requireEdit();if(!name.matches("[A-Za-z0-9_-]{1,32}"))throw new IllegalArgumentException("Name: 1–32 Buchstaben, Ziffern, _ oder -.");
        if(current().stream().anyMatch(t->t.name.equalsIgnoreCase(name)))throw new IllegalArgumentException("Dieser Timername existiert bereits.");
        if(timers.size()>=64)throw new IllegalArgumentException("Maximal 64 Timer pro Weltdatei.");
        Timer t=new Timer();t.name=name;t.world=MpsqActionSync.world();t.clock.set(ms);place(t);t.visual();timers.add(t);save();return t;
    }
    static void requireEdit(){if(!editable())throw new IllegalArgumentException("Timer-Verwaltung derzeit in lokalen Welten verfügbar; nicht im Replay.");}
    static void place(Timer t){var p=Minecraft.getInstance().player;if(p==null)return;t.x=p.getX();t.y=p.getY()+1;t.z=p.getZ();t.yaw=p.getYRot()+180;}
    static void change(Timer t,String action,long amount){requireEdit();if(MpsqChess.ownsClock(t)&&MpsqChess.game.active())throw new IllegalArgumentException("Schachuhr während der Partie gesperrt.");switch(action){case "start"->t.clock.start();case "pause"->t.clock.running=false;case "reset"->t.clock.reset();case "set"->t.clock.set(amount);case "add"->t.clock.add(amount);case "remove"->timers.remove(t);case "here"->place(t);default->throw new IllegalArgumentException("Unbekannte Aktion.");}t.visual();save();}
    static void redstone(String name,String action,boolean stateDriven,boolean powered){if(!editable())return;try{Timer t=find(name);String chosen=stateDriven?(powered?"start":"pause"):action.equals("toggle")?(t.clock.running?"pause":"start"):action;change(t,chosen,0);}catch(IllegalArgumentException e){MpsqCameraClient.LOGGER.warn("Timer-Redstone: {}",e.getMessage());}}
    static void initialize(){
        ClientTickEvents.END_CLIENT_TICK.register(MpsqModularTimer::tick);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((h,c)->{safeSave();timers.clear();level=null;});
        WorldRenderEvents.AFTER_ENTITIES.register(ctx->{
            if(!TeamVisibilitySettings.visible()||ctx.matrixStack()==null||ctx.consumers()==null)return;
            List<Timer> shown=current();
            if(MpsqReplayPlayback.active()){
                shown=new ArrayList<>();JsonObject scene=MpsqReplayPlayback.sceneSnapshot();
                if(scene.has("timers"))for(JsonElement e:scene.getAsJsonArray("timers"))try{shown.add(decode(e.getAsJsonObject(),true));}catch(Exception ignored){}
            }
            for(Timer t:shown)if(ctx.camera().getPos().distanceToSqr(t.x,t.y,t.z)<=150*150)MpsqAccessoryRenderer.drawTimer(ctx,t);
        });
        ClientCommandRegistrationCallback.EVENT.register((d,r)->{
            var root=ClientCommands.literal("mpsq-timer").executes(ctx->{Minecraft.getInstance().execute(()->Minecraft.getInstance().gui.setScreen(new MpsqTimerScreen(null)));return 1;});
            root.then(ClientCommands.literal("create").then(ClientCommands.argument("name",StringArgumentType.word()).then(ClientCommands.argument("time",StringArgumentType.word()).executes(ctx->command(ctx,()->create(StringArgumentType.getString(ctx,"name"),MpsqTimerClock.parse(StringArgumentType.getString(ctx,"time"))))))));
            for(String action:List.of("start","pause","reset","remove","here"))root.then(ClientCommands.literal(action).then(ClientCommands.argument("name",StringArgumentType.word()).suggests((ctx,b)->{current().forEach(t->b.suggest(t.name));return b.buildFuture();}).executes(ctx->command(ctx,()->change(find(StringArgumentType.getString(ctx,"name")),action,0)))));
            for(String action:List.of("set","add","subtract"))root.then(ClientCommands.literal(action).then(ClientCommands.argument("name",StringArgumentType.word()).then(ClientCommands.argument("time",StringArgumentType.word()).executes(ctx->command(ctx,()->change(find(StringArgumentType.getString(ctx,"name")),action.equals("subtract")?"add":action,(action.equals("subtract")?-1:1)*MpsqTimerClock.parse(StringArgumentType.getString(ctx,"time"))))))));
            d.register(root);
        });
    }
    private static int command(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> ctx,Runnable action){try{action.run();ctx.getSource().sendFeedback(Component.literal("Timer aktualisiert."));return 1;}catch(Exception e){ctx.getSource().sendError(Component.literal(e.getMessage()==null?"Ungültige Eingabe.":e.getMessage()));return 0;}}
    private static void tick(Minecraft c){
        long now=System.nanoTime();
        if(c.level!=level){timers.clear();level=c.level;last=now;saved=now;previousSystem="";if(level!=null)for(JsonElement e:MpsqLocalWorldStore.array("modular_timers"))try{timers.add(decode(e.getAsJsonObject(),false));}catch(Exception ex){MpsqCameraClient.LOGGER.warn("Ungültiger Timer übersprungen",ex);}return;}
        long dt=Math.max(0,(now-last)/1_000_000);last=now;if(level==null||c.isPaused()||MpsqReplayPlayback.active())return;
        String system=MpsqSystemController.activeSystemId();
        for(Timer t:current()){
            if(!t.system.isEmpty()&&!system.equals(previousSystem)){if(system.equals(t.system)){t.clock.reset();t.clock.start();}else if(previousSystem.equals(t.system))t.clock.running=false;}
            String before=t.digits();t.age+=dt;boolean done=t.clock.advance(dt);t.visual();
            if(t.tickSound&&!before.equals(t.digits()))sound(c,t,net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(),.15f);
            if(done){finish(c,t);safeSave();}
        }
        previousSystem=system;if(now-saved>5_000_000_000L){safeSave();saved=now;}
    }
    private static void sound(Minecraft c,Timer t,net.minecraft.sounds.SoundEvent sound,float volume){c.level.playLocalSound(t.x,t.y,t.z,sound,net.minecraft.sounds.SoundSource.BLOCKS,volume,1,false);}
    private static void finish(Minecraft c,Timer t){
        if(t.end.equals("none"))return;
        if(t.end.equals("message")){de.galaxushd.mpsqcamera.compat.PlayerMessages.send(c.player,Component.literal(t.endInput.isBlank()?"Timer "+t.name+" ist abgelaufen.":t.endInput),false);return;}
        if(t.end.equals("sound")&&!t.endInput.isBlank()){MpsqMediaAudioManager.playAuto(t.endInput);return;}sound(c,t,net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_BLAST,1);
        if(t.end.equals("firework")){var r=new Random();for(int i=0;i<70;i++)c.level.addParticle(net.minecraft.core.particles.ParticleTypes.FIREWORK,t.x,t.y+1,t.z,(r.nextDouble()-.5)*.5,r.nextDouble()*.5,(r.nextDouble()-.5)*.5);}
        if(t.end.equals("stop_game")&&!t.system.isEmpty())MpsqSystemController.stopSystem(t.system);
    }
    private static void safeSave(){try{save();}catch(Exception e){MpsqCameraClient.LOGGER.warn("Timer-Speicherung fehlgeschlagen",e);}}
    static void save(){if(!MpsqLocalWorldStore.available()||MpsqReplayPlayback.active())return;JsonArray a=new JsonArray();timers.forEach(t->a.add(encode(t)));if(!MpsqLocalWorldStore.setArray("modular_timers",a))throw new IllegalStateException("Timer konnte nicht gespeichert werden.");}
    static JsonArray snapshot(){JsonArray a=new JsonArray();current().forEach(t->a.add(encode(t)));return a;}
    static JsonObject encode(Timer t){JsonObject o=new JsonObject();o.addProperty("name",t.name);o.addProperty("endInput",t.endInput);o.addProperty("redEnding",t.redEnding);o.addProperty("world",t.world);o.addProperty("x",t.x);o.addProperty("y",t.y);o.addProperty("z",t.z);o.addProperty("yaw",t.yaw);o.addProperty("pitch",t.pitch);o.addProperty("scale",t.scale);o.addProperty("color",t.color);o.addProperty("format",t.format);o.addProperty("idle",t.idle);o.addProperty("animation",t.animation);o.addProperty("end",t.end);o.addProperty("system",t.system);o.addProperty("bossbar",t.bossbar);o.addProperty("tickSound",t.tickSound);o.addProperty("initial",t.clock.initial);o.addProperty("value",t.clock.value);o.addProperty("up",t.clock.up);o.addProperty("running",t.clock.running);o.addProperty("finished",t.clock.finished);o.addProperty("age",t.age);JsonArray changes=new JsonArray();for(long n:t.changed)changes.add(n);o.add("changed",changes);return o;}
    static Timer decode(JsonObject o,boolean replay){Timer t=new Timer();t.name=o.get("name").getAsString();t.world=o.get("world").getAsString();t.x=o.get("x").getAsDouble();t.y=o.get("y").getAsDouble();t.z=o.get("z").getAsDouble();t.yaw=o.get("yaw").getAsFloat();t.pitch=o.get("pitch").getAsFloat();t.scale=o.get("scale").getAsFloat();if(!Double.isFinite(t.x+t.y+t.z)||!Float.isFinite(t.scale+t.pitch+t.yaw)||t.scale<.02||t.scale>3)throw new IllegalArgumentException("Timer transform");t.color=o.get("color").getAsInt();t.format="auto";t.endInput=o.has("endInput")?o.get("endInput").getAsString():"";t.redEnding=o.has("redEnding")&&o.get("redEnding").getAsBoolean();t.idle=o.get("idle").getAsString();t.animation=o.get("animation").getAsString();t.end=o.get("end").getAsString();t.system=o.get("system").getAsString();t.bossbar=false;t.tickSound=o.get("tickSound").getAsBoolean();t.clock.set(o.get("initial").getAsLong());t.clock.value=Math.clamp(o.get("value").getAsLong(),0,MpsqTimerClock.MAX);t.clock.up=o.get("up").getAsBoolean();t.clock.running=replay&&o.get("running").getAsBoolean();t.clock.finished=o.get("finished").getAsBoolean();t.age=o.get("age").getAsLong();t.shown=t.digits();if(o.has("changed"))for(int i=0;i<4;i++)t.changed[i]=o.getAsJsonArray("changed").get(i).getAsLong();return t;}
}
