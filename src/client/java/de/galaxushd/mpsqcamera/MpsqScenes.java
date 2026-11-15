package de.galaxushd.mpsqcamera;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.*;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** World-scoped client NPC direction. Independent of the Scenes server plugin. */
final class MpsqScenes {
    static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    static final class Profile {
        transient String runtimeId="";
        public MpsqCinematicScene.Npc npc=new MpsqCinematicScene.Npc();
        public float scale=1;
        public String clip="idle",dialogue="",command="",sound="";
        public boolean enabled=true,loop=true,deleted;
        public double pathWidth;
        public String sharedPath=""; public int pathSlot;
        public String direction="FORWARD",visibility="ALL",task="none";
        void restoreFrom(Profile saved){
            npc.restoreFrom(saved.npc);scale=saved.scale;clip=saved.clip;dialogue=saved.dialogue;
            command=saved.command;sound=saved.sound;enabled=saved.enabled;loop=saved.loop;
            sharedPath=saved.sharedPath;pathSlot=saved.pathSlot;deleted=saved.deleted;pathWidth=saved.pathWidth;direction=saved.direction;visibility=saved.visibility;task=saved.task;
        }
        void validate(){
            if(npc==null||npc.category==null||!Set.of("npc_skin_normal","npc_skin_slim","npc_model","mob").contains(npc.category)
                ||npc.name==null||npc.name.length()>64||npc.type==null||npc.url==null
                ||npc.equipment==null||npc.equipment.length!=6||Arrays.stream(npc.equipment).anyMatch(Objects::isNull))
                throw new IllegalArgumentException("Ungültige Spieler-NPC-Daten.");
            npc.scenesTask=task;npc.validate();String lockedTag=MpsqNpcGlow.lockedTag(npc);if(!lockedTag.isBlank())npc.rankTag=lockedTag;if(!Set.of("ALL","CINEMATIC").contains(visibility)||!Set.of("none","tutorial","quest","accessories").contains(task))throw new IllegalArgumentException("Ungültige Sichtbarkeit/Aufgabe");
            if(!Float.isFinite(scale)||scale<.25||scale>3||!Double.isFinite(pathWidth)||pathWidth<0||pathWidth>8
                ||direction==null||!Set.of("FORWARD","REVERSE","BOTH").contains(direction)||clip==null||(npc.category.startsWith("npc_skin")&&!Set.of("idle","lie","swim","jump","swing").contains(clip)&&MpsqScenesClips.get(clip)==null)
                ||dialogue==null||dialogue.length()>240||command==null||command.length()>256||command.contains("\n")||command.contains("\r")
                ||sound==null||sound.length()>128||(!sound.isBlank()&&!sound.matches("[a-zA-Z0-9_.:/-]+")))throw new IllegalArgumentException("Ungültige Scenes-Einstellungen.");
            if(!Double.isFinite(npc.x)||!Double.isFinite(npc.y)||!Double.isFinite(npc.z)||!Float.isFinite(npc.yaw))throw new IllegalArgumentException("Ungültige Position.");
        }
    }
    record Target(String key,String sceneId,JsonObject row,Profile profile){}
    private static final Map<String,Profile> profiles=new LinkedHashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel loadedWorld;
    private static String loadError;
    private static long start;
    private record Rotation(MpsqCinematicActors.Pose pose,double time){}
    private record ClipClock(String clip,double start){}
    private static final Map<String,ClipClock> clipClocks=new HashMap<>();
    private static Path clockFile;
    private static final Map<Profile,Rotation> rotations=new WeakHashMap<>();
    private static Screen queued;
    private static MpsqScenesScreen capture;
    private static int captureIndex;
    static boolean allowed(){return TeamVisibilitySettings.inArea()&&TeamStateStore.self().map(p->p.permissionRank().level()>=TeamRank.OFFICER.level()).orElse(false);}
    static void initialize(){
        MpsqScenesHub.initialize();
        MpsqMotionCapture.initialize();
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->saveClocks());
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client->saveClocks());
        MpsqSceneWorldNpcs.initialize();
        MpsqSeagullDrops.initialize();
        ClientCommandRegistrationCallback.EVENT.register((d,c)->d.register(ClientCommands.literal("mpsq-npc")
            .executes(ctx->{try{if(!allowed())throw new IllegalArgumentException("Nur ab Offizier innerhalb der Gesamtfläche verfügbar.");Target t=target();queued=new MpsqScenesScreen(t==null?create():t);}catch(Exception e){ctx.getSource().sendError(Component.literal(e.getMessage()));}return 1;})
            .then(ClientCommands.literal("position").executes(ctx->{if(capture!=null){try{capture.capturePosition(captureIndex);queued=capture;capture=null;}catch(Exception e){ctx.getSource().sendError(Component.literal(e.getMessage()));}}else ctx.getSource().sendError(Component.literal("Zuerst einen Wegpunkt im NPC-Menü auswählen."));return 1;}))
            .then(ClientCommands.literal("abbrechen").executes(ctx->{queued=capture;capture=null;return 1;}))));
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            MpsqNpcReactions.tick();
            if(loadedWorld!=c.level){saveClocks();profiles.clear();loadedWorld=null;queued=null;capture=null;}
            if(!allowed()){queued=null;capture=null;}
            if(queued!=null){var s=queued;queued=null;c.gui.setScreen(s);}
        });
    }
    private static Path file(){Path scenes=MpsqCinematicStore.file();return scenes.resolveSibling(scenes.getFileName().toString().replace(".json","-npc-scenes.json"));}
    private static void load(){
        var c=Minecraft.getInstance();if(loadedWorld==c.level&&loadedWorld!=null){if(loadError!=null)throw new IllegalStateException(loadError);return;}
        saveClocks();profiles.clear();clipClocks.clear();rotations.clear();loadedWorld=c.level;start=System.nanoTime();loadError=null;
        clockFile=file().resolveSibling(file().getFileName().toString().replace(".json","-clocks.json"));
        try{if(Files.exists(clockFile))for(var e:JsonParser.parseString(Files.readString(clockFile)).getAsJsonObject().entrySet()){var v=e.getValue().getAsJsonObject();double age=v.get("age").getAsDouble();if(Double.isFinite(age)&&age>=0)clipClocks.put(e.getKey(),new ClipClock(v.get("clip").getAsString(),-age));}}catch(Exception e){MpsqCameraClient.LOGGER.warn("NPC-Animationszeiten nicht lesbar",e);}
        try{if(Files.exists(file())){JsonObject root=JsonParser.parseString(Files.readString(file())).getAsJsonObject();for(var e:root.entrySet()){Profile p=JSON.fromJson(e.getValue(),Profile.class);p.validate();p.runtimeId=e.getKey();profiles.put(e.getKey(),p);}}}
        catch(Exception e){profiles.clear();loadError="Scenes-Daten konnten nicht gelesen werden: "+e.getMessage();throw new IllegalStateException(loadError);}
    }
    private static void saveClocks(){
        if(clockFile==null)return;
        try{JsonObject root=new JsonObject();double now=time();for(var e:clipClocks.entrySet()){JsonObject v=new JsonObject();v.addProperty("clip",e.getValue().clip);v.addProperty("age",Math.max(0,now-e.getValue().start));root.add(e.getKey(),v);}Files.createDirectories(clockFile.getParent());Path tmp=Files.createTempFile(clockFile.getParent(),"npc-clock-",".tmp");Files.writeString(tmp,root.toString());try{Files.move(tmp,clockFile,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,clockFile,StandardCopyOption.REPLACE_EXISTING);}}
        catch(Exception e){MpsqCameraClient.LOGGER.warn("NPC-Animationszeiten nicht gespeichert",e);}finally{clockFile=null;}
    }
    static Profile stored(String id){var preview=MpsqScenesTool.preview(id);if(preview!=null)return preview;var actor=MpsqSharedPaths.actor(id);if(actor!=null)return actor;try{load();return profiles.get("npc:"+id);}catch(Exception e){return null;}}
    static double time(){return (System.nanoTime()-start)/1e9;}
    static String clip(Profile p){var a=p.npc.action(time());return a==null||MpsqSeagullDrops.ACTION.equals(a.clip)?p.clip:a.clip;}
    private static double motionTime(Profile p){String key=p.runtimeId+"#recording";String clip="recording:"+p.npc.recordingId;var clock=clipClocks.get(key);if(clock==null||!clock.clip.equals(clip)){clock=new ClipClock(clip,time());clipClocks.put(key,clock);}return Math.max(0,time()-clock.start);}
    static MpsqCinematicActors.Pose pose(Profile p){
        if(p.enabled){var capture=p.sharedPath.isBlank()?MpsqMotionCapture.sample(p.npc.recording,motionTime(p),p.loop,p.direction):MpsqSharedPaths.recordedPose(p,time());if(capture!=null)return capture;}
        double t=time();var pos=position(p,t);var old=position(p,Math.max(0,t-.05));var n=p.npc;
        double dx=pos.x-old.x,dz=pos.z-old.z;float yaw=Math.abs(dx)+Math.abs(dz)>1e-6?(float)Math.toDegrees(Math.atan2(-dx,dz)):n.yaw;
        float head=n.headYaw,pitch=n.pitch;var action=n.action(t);if(action!=null&&MpsqSeagullDrops.ACTION.equals(action.clip))action=null;String look=action==null||action.look.equals("DEFAULT")?n.look:action.look;
        var c=Minecraft.getInstance();var viewer=look.startsWith("CAMERA")?c.getCameraEntity():c.player;
        if(!look.equals("FIXED")&&viewer!=null&&c.player!=null&&MpsqNpcManager.withinLookRange(c.player.position(),new Vec3(pos.x,pos.y,pos.z))){double x=viewer.getX()-pos.x,z=viewer.getZ()-pos.z,y=viewer.getEyeY()-pos.y-1.6*p.scale;
            float target=(float)Math.toDegrees(Math.atan2(-x,z));pitch=(float)-Math.toDegrees(Math.atan2(y,Math.sqrt(x*x+z*z)));
            if(look.endsWith("HEAD"))head=Math.max(-75,Math.min(75,net.minecraft.util.Mth.wrapDegrees(target-yaw)));else{yaw=target;head=0;}}
        var oldRotation=rotations.get(p);
        if(oldRotation!=null){float blend=(float)(1-Math.exp(-Math.max(0,Math.min(.1,t-oldRotation.time))/.16));
            yaw=oldRotation.pose.yaw()+(float)MpsqCinematicScene.angleDelta(oldRotation.pose.yaw(),yaw)*blend;
            head=oldRotation.pose.headYaw()+(float)MpsqCinematicScene.angleDelta(oldRotation.pose.headYaw(),head)*blend;
            pitch=oldRotation.pose.pitch()+(pitch-oldRotation.pose.pitch())*blend;}
        String currentClip=clip(p);if(action==null&&p.npc.category.equals("npc_model")&&Math.abs(dx)+Math.abs(dz)>1e-6&&MpsqCinematicActors.clips(p.npc).contains("walk"))currentClip="walk";
        var clock=clipClocks.get(p.runtimeId);if(clock==null||!clock.clip.equals(currentClip)){clock=new ClipClock(currentClip,t);clipClocks.put(p.runtimeId,clock);}
        var reaction=action==null?MpsqNpcReactions.choose(p,new Vec3(pos.x,pos.y,pos.z),currentClip,t):null;
        if(reaction!=null)currentClip=reaction.clip();
        float animationAge=action==null?MpsqCinematicActors.loopTime(p.npc,currentClip,t-clock.start):(float)(t-action.time);
        if(reaction!=null)animationAge=reaction.age();
        if(action==null&&p.npc.type.equals(MpsqAccessoryRenderer.WUMPUS_ASSET_ID)&&currentClip.equals("idle")){MpsqWumpusBehavior.observeLook(p.runtimeId.replaceFirst("^npc:",""),MpsqNpcManager.looksAt(new Vec3(pos.x,pos.y,pos.z),p.scale,true,20));currentClip=MpsqWumpusBehavior.animation(p.runtimeId.replaceFirst("^npc:",""));animationAge=MpsqWumpusBehavior.elapsedSeconds(p.runtimeId.replaceFirst("^npc:",""));}
        var result=new MpsqCinematicActors.Pose(pos.x,pos.y+(clip(p).equals("jump")?Math.max(0,Math.sin((t%1)*Math.PI))*.8:0),pos.z,yaw,head,pitch,currentClip,animationAge);
        rotations.put(p,new Rotation(result,t));return result;
    }
    static void decorate(net.minecraft.client.renderer.entity.state.AvatarRenderState state,Profile p){
        var pose=pose(p);state.pose=pose.clip().equals("crouch")?net.minecraft.world.entity.Pose.CROUCHING:pose.clip().equals("lie")?net.minecraft.world.entity.Pose.SLEEPING:pose.clip().equals("swim")?net.minecraft.world.entity.Pose.SWIMMING:net.minecraft.world.entity.Pose.STANDING;state.swimAmount=pose.clip().equals("swim")?1:0;state.bodyRot=pose.yaw();state.yRot=pose.headYaw();state.xRot=pose.pitch();
        var now=position(p,time());var before=position(p,Math.max(0,time()-.05));
        state.walkAnimationSpeed=(float)Math.min(1,Math.hypot(now.x-before.x,now.z-before.z)*12);state.walkAnimationPos=(float)(time()*7);
        state.headEquipment=MpsqCinematicNpcScreen.stack(p.npc.equipment[0]);state.chestEquipment=MpsqCinematicNpcScreen.stack(p.npc.equipment[1]);state.legsEquipment=MpsqCinematicNpcScreen.stack(p.npc.equipment[2]);state.feetEquipment=MpsqCinematicNpcScreen.stack(p.npc.equipment[3]);
    }
    static boolean interact(JsonObject row){
        Profile p=stored(str(row,"id",""));if(p==null||p.deleted||!TeamVisibilitySettings.inArea())return false;
        if(p.dialogue.isBlank()&&p.command.isBlank()&&p.sound.isBlank())return false;
        if(!p.dialogue.isBlank())MpsqNpcReactions.opened(str(row,"id",""));
        if(!p.sound.isBlank())MpsqMediaAudioManager.playAuto(p.sound);
        if(!p.dialogue.isBlank()){JsonObject data=new JsonObject();JsonArray pages=new JsonArray();pages.add(p.dialogue);data.add("pages",pages);MpsqDialogueManager.start(data);}
        if(!p.command.isBlank()){var player=Minecraft.getInstance().player;if(player!=null)player.connection.sendCommand(p.command.startsWith("/")?p.command.substring(1):p.command);}
        return true;
    }
    static Profile copy(Profile p){Profile copy=JSON.fromJson(JSON.toJson(p),Profile.class);copy.runtimeId=p.runtimeId;return copy;}
    static void save(Target t,Profile p){
        if(!allowed())throw new IllegalStateException("Keine Bearbeitungsrechte oder außerhalb der Gesamtfläche.");
        load();p.validate();
        TeamVisibilitySettings.requirePlacement(p.npc.x,p.npc.y,p.npc.z);
        for(var w:p.npc.route)if(w!=null){TeamVisibilitySettings.requirePlacement(w.x-p.pathWidth/2,w.y,w.z-p.pathWidth/2);TeamVisibilitySettings.requirePlacement(w.x+p.pathWidth/2,w.y,w.z+p.pathWidth/2);}
        try{
            if(t.sceneId!=null){
                var all=MpsqCinematicStore.load();var scene=all.stream().filter(s->s.id.equals(t.sceneId)).findFirst().orElseThrow(()->new IllegalArgumentException("Kamerafahrt nicht mehr vorhanden."));
                scene.npc=JSON.fromJson(JSON.toJson(p.npc),MpsqCinematicScene.Npc.class);scene.npc.scenesClip=p.clip;scene.npc.scenesScale=p.scale;scene.npc.placed=!p.deleted;
                scene.npc.visibility=p.visibility;scene.npc.scenesTask=p.task;scene.npc.scenesMotionEnabled=p.enabled;scene.npc.scenesMotionLoop=p.loop;scene.npc.scenesDirection=p.direction;
                MpsqCinematicStore.save(all);MpsqCinematicMarkers.sync(all);
            }else{
                var next=new LinkedHashMap<>(profiles);next.put(t.key,copy(p));Path path=file();Files.createDirectories(path.getParent());Path tmp=Files.createTempFile(path.getParent(),"npc-scenes-",".tmp");
                try{Files.writeString(tmp,JSON.toJson(next));try{Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(tmp);}
                profiles.clear();profiles.putAll(next);
            }
        }catch(Exception e){throw new IllegalStateException("Speichern fehlgeschlagen: "+e.getMessage(),e);}
    }
    static void capture(MpsqScenesScreen screen,int index){capture=screen;captureIndex=index;Minecraft.getInstance().gui.setScreen(null);message("Position wählen, dann /mpsq-npc position. Abbruch: /mpsq-npc abbrechen");}
    private static void message(String message){var c=Minecraft.getInstance();if(c.player!=null)de.galaxushd.mpsqcamera.compat.PlayerMessages.send(c.player,Component.literal(message),false);}
    static Profile fromRow(JsonObject row){
        Profile p=new Profile();p.runtimeId="npc:"+str(row,"id","");var n=p.npc;n.name=str(row,"display_name",str(row,"name","NPC"));n.category=str(row,"category","");n.type=str(row,"asset_id","");n.url=str(row,"url","");
        n.x=coordinate(row,"x");n.y=coordinate(row,"y");n.z=coordinate(row,"z");n.placed=true;n.yaw=row.has("yaw")?row.get("yaw").getAsFloat():0;
        p.task=str(row,"task_type","none");n.scenesTask=p.task;n.pitch=row.has("pitch")?row.get("pitch").getAsFloat():0;n.look=row.has("face_player")&&row.get("face_player").getAsBoolean()?"PLAYER_HEAD":"FIXED";
        p.scale=row.has("scale")?row.get("scale").getAsFloat():1;return p;
    }
    static Target target() throws Exception {
        load();var c=Minecraft.getInstance();Vec3 eye=c.player.getEyePosition(),end=eye.add(c.player.getViewVector(1).scale(6));double nearest=36;Target selected=null;
        var block=c.level.clip(new net.minecraft.world.level.ClipContext(eye,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,c.player));
        if(block.getType()!=HitResult.Type.MISS)nearest=eye.distanceToSqr(block.getLocation());
        for(var element:MpsqAccessoryRenderer.npcsSnapshot()){
            var row=element.getAsJsonObject();
            String key="npc:"+str(row,"id","");Profile p=profiles.containsKey(key)?copy(profiles.get(key)):fromRow(row);if(p.deleted)continue;
            var pos=position(p,time());double distance=hit(eye,end,pos.x,pos.y,pos.z,p.scale);
            if(distance<nearest){nearest=distance;selected=new Target(key,null,row,p);}
        }
        for(var scene:MpsqCinematicStore.load()){
            var n=scene.npc;if(!n.placed||(!"ALL".equals(n.visibility)&&!MpsqCinematicPreview.active()&&!MpsqCinematicMarkers.showing(scene))||!scene.dimension.equals(c.level.dimension().identifier().toString()))continue;
            var playing=MpsqCinematicActors.scenePose(scene.id);
            double distance=playing==null?hit(eye,end,n.x,n.y,n.z,n.scenesScale):hit(eye,end,playing.x(),playing.y(),playing.z(),n.scenesScale);
            if(distance<nearest){nearest=distance;Profile p=new Profile();p.runtimeId="cinematic:"+scene.id;p.npc=JSON.fromJson(JSON.toJson(n),MpsqCinematicScene.Npc.class);p.clip=n.scenesClip;p.scale=n.scenesScale;p.enabled=n.scenesMotionEnabled;p.loop=n.scenesMotionLoop;p.direction=n.scenesDirection;p.visibility=n.visibility;p.task=n.scenesTask;selected=new Target("cinematic:"+scene.id,scene.id,null,p);}
        }
        return selected;
    }
    static Target fromNpc(JsonObject row){load();String id=str(row,"id","");var p=profiles.get("npc:"+id);return new Target("npc:"+id,null,row,p==null?fromRow(row):copy(p));}
    static Target fromScene(MpsqCinematicScene scene){var p=new Profile();p.runtimeId="cinematic:"+scene.id;p.npc=JSON.fromJson(JSON.toJson(scene.npc),MpsqCinematicScene.Npc.class);if(!p.npc.placed){var player=Minecraft.getInstance().player;if(player!=null){p.npc.x=player.getX();p.npc.y=player.getY();p.npc.z=player.getZ();p.npc.yaw=MpsqCinematicScene.snapRotation(player.getYRot());p.npc.placed=true;}}p.clip=p.npc.scenesClip;p.scale=p.npc.scenesScale;p.enabled=p.npc.scenesMotionEnabled;p.loop=p.npc.scenesMotionLoop;p.direction=p.npc.scenesDirection;p.visibility=p.npc.visibility;p.task=p.npc.scenesTask;return new Target("cinematic:"+scene.id,scene.id,null,p);}
    static Target create(){
        var c=Minecraft.getInstance();if(!allowed()||c.player==null)throw new IllegalStateException("NPCs können nur in der Gesamtfläche erstellt werden.");
        var row=new JsonObject();String id=UUID.randomUUID().toString();row.addProperty("id",id);row.addProperty("asset_id","self");row.addProperty("url","self");row.addProperty("category","npc_skin_normal");row.addProperty("display_name","NPC");row.addProperty("task_type","none");row.addProperty("world_id",MpsqActionSync.world());
        row.addProperty("x",c.player.getBlockX());row.addProperty("y",c.player.getBlockY());row.addProperty("z",c.player.getBlockZ());row.addProperty("world_x",c.player.getX());row.addProperty("world_y",c.player.getY());row.addProperty("world_z",c.player.getZ());row.addProperty("yaw",MpsqCinematicScene.snapRotation(c.player.getYRot()));
        var rows=MpsqLocalWorldStore.array("npcs");rows.add(row);if(!MpsqLocalWorldStore.setArray("npcs",rows))throw new IllegalStateException("NPC konnte nicht gespeichert werden.");
        var target=fromNpc(row);save(target,target.profile);MpsqAccessoryRenderer.refresh();return target;
    }
    static void openCreate(){try{Minecraft.getInstance().gui.setScreen(new MpsqScenesScreen(create()));}catch(Exception e){message(e.getMessage());}}
    static void open(JsonObject row){Minecraft.getInstance().gui.setScreen(str(row,"id","").startsWith("path:")?new MpsqScenesHub(null):new MpsqScenesScreen(fromNpc(row)));}
    static boolean showPaths;
    static boolean editorVisible(){return showPaths|| Minecraft.getInstance().gui.screen() instanceof MpsqScenesScreen||capture!=null;}
    private static double hit(Vec3 start,Vec3 end,double x,double y,double z,float s){var box=new AABB(x-.4*s,y-.1,z-.4*s,x+.4*s,y+1.95*s,z+.4*s);if(box.contains(start))return 0;return box.clip(start,end).map(start::distanceToSqr).orElse(Double.MAX_VALUE);}
    static MpsqCinematicScene.Waypoint position(Profile p,double time){
        if(p.enabled&&p.sharedPath!=null&&!p.sharedPath.isBlank())return MpsqSharedPaths.position(p,time);
        if(!p.enabled)return new MpsqCinematicScene.Waypoint(p.npc.x,p.npc.y,p.npc.z,1);
        if(!p.npc.recording.isEmpty()){
            var capture=MpsqMotionCapture.sample(p.npc.recording,motionTime(p)+time-time(),p.loop,p.direction);
            return new MpsqCinematicScene.Waypoint(capture.x(),capture.y(),capture.z(),1);
        }
        return p.npc.position(time,p.loop,p.direction);
    }
    static JsonObject apply(JsonObject row){
        Profile p=stored(str(row,"id",""));if(p==null)return row;
        JsonObject result=row.deepCopy();result.addProperty("scenes_hidden",p.deleted||(p.visibility.equals("CINEMATIC")&&!MpsqCinematicPreview.active()&&MpsqCinematicActors.linkedPose(str(row,"id",""))==null&&!editorVisible()));result.addProperty("asset_id",p.npc.type);result.addProperty("task_type",p.task);var current=MpsqCinematicActors.linkedPose(str(row,"id",""));if(current==null)current=pose(p);var pos=new MpsqCinematicScene.Waypoint(current.x(),current.y(),current.z(),1);
        result.addProperty("world_x",pos.x);result.addProperty("world_y",pos.y);result.addProperty("world_z",pos.z);
        result.addProperty("display_name",p.npc.name);result.addProperty("category",p.npc.category);result.addProperty("url",p.npc.url);result.addProperty("scale",p.scale);result.addProperty("yaw",p.npc.yaw);result.addProperty("pitch",p.npc.pitch);result.addProperty("face_player",!p.npc.look.equals("FIXED"));
        return result;
    }
    static String str(JsonObject o,String k,String fallback){return o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsString():fallback;}
    private static double coordinate(JsonObject o,String axis){return o.has("world_"+axis)?o.get("world_"+axis).getAsDouble():o.get(axis).getAsDouble()+(axis.equals("y")?0:.5);}
}
