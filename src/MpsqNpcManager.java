package de.galaxushd.mpsqcamera;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Client interactions for virtual NPCs anchored above their support block. */
public final class MpsqNpcManager {
    private MpsqNpcManager() { }

    public static void initialize() {
        // MouseMixin calls handleRightClick before Minecraft forwards the
        // click to a block or item, so virtual models behave like targets.
    }

    /** Handles a client right-click aimed at one of the locally rendered NPCs. */
    public static boolean handleRightClick() {
        Minecraft client = Minecraft.getInstance();
        var player = client.player;
        if (player == null || client.level == null || client.gui.screen() != null || !TeamVisibilitySettings.inArea()) return false;
        JsonObject npc = targeted(player);
        if(npc==null){try{var target=MpsqScenes.target();if(target==null||target.sceneId()==null)return false;
            if(player.isShiftKeyDown()&&isOfficer()){client.gui.setScreen(new MpsqScenesScreen(target));return true;}
            MpsqNpcGlow.clicked(target.key());var row=new JsonObject();row.addProperty("id",target.key());row.addProperty("name",target.profile().npc.name);row.addProperty("task_type",target.profile().task);interact(row);return true;
        }catch(Exception e){return false;}}
        if (player.isShiftKeyDown() && isOfficer()) {
            MpsqScenes.open(npc);
            return true;
        }
        MpsqNpcGlow.clicked(str(npc,"id",""));
        if (MpsqAccessoryRenderer.WUMPUS_ASSET_ID.equals(str(npc, "asset_id", ""))) {
            MpsqWumpusBehavior.wave(str(npc, "id", ""));
            Minecraft.getInstance().gui.setScreen(new MpsqWumpusDiscordScreen(null));
            return true;
        }
        if(!MpsqScenes.interact(npc))interact(npc);
        return true;
    }

    /** Consumes a left-click only when the crosshair is aimed at the Wumpus. */
    public static boolean handleLeftClick() {
        Minecraft client = Minecraft.getInstance();
        var player = client.player;
        if (player == null || client.level == null || client.gui.screen() != null || !TeamVisibilitySettings.inArea()) return false;
        JsonObject npc = targeted(player);
        if(npc==null){try{var target=MpsqScenes.target();if(target==null)return false;MpsqNpcGlow.clicked(target.key());return true;}catch(Exception e){return false;}}
        MpsqNpcGlow.clicked(str(npc,"id",""));
        if(!MpsqAccessoryRenderer.WUMPUS_ASSET_ID.equals(str(npc,"asset_id","")))return true;
        MpsqNpcGlow.clicked(str(npc,"id",""));
        MpsqWumpusBehavior.leftClick(str(npc, "id", ""));
        return true;
    }

    /** Used by the renderer so gaze reactions use the same click volume as interaction. */
    public static boolean isLookingAt(JsonObject npc) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || !TeamVisibilitySettings.inArea()) return false;
        JsonObject selected = targeted(client.player,20);
        return selected != null && str(selected, "id", "").equals(str(npc, "id", ""));
    }

    static boolean withinLookRange(Vec3 player,Vec3 npc){return player.distanceToSqr(npc)<=400;}
    static boolean looksAt(Vec3 pos,float scale,boolean wumpus,double range){
        var c=Minecraft.getInstance();if(c.player==null||c.level==null||c.gui.screen()!=null||!TeamVisibilitySettings.inArea()||c.player.distanceToSqr(pos)>range*range)return false;
        Vec3 eye=c.player.getEyePosition(),end=eye.add(c.player.getViewVector(1).scale(range));
        double w=(wumpus?.62:.36)*scale,h=(wumpus?1.45:1.8)*scale;
        AABB box=new AABB(pos.x-w,pos.y-.08*scale,pos.z-w,pos.x+w,pos.y+h+.08*scale,pos.z+w);
        var hit=box.contains(eye)?java.util.Optional.of(eye):box.clip(eye,end);
        return hit.isPresent()&&clear(eye,hit.get(),c.player);
    }
    private static boolean clear(Vec3 eye,Vec3 end,net.minecraft.world.entity.player.Player player){
        var hit=player.level().clip(new net.minecraft.world.level.ClipContext(eye,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
        return hit.getType()==net.minecraft.world.phys.HitResult.Type.MISS||eye.distanceToSqr(hit.getLocation())+.0001>=eye.distanceToSqr(end);
    }
    private static String str(JsonObject object, String key, String fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
    }

    static JsonObject targeted(net.minecraft.world.entity.player.Player player) {return targeted(player,6);}
    private static JsonObject targeted(net.minecraft.world.entity.player.Player player,double range) {
        JsonArray all = MpsqAccessoryRenderer.npcsSnapshot();
        Vec3 eye=player.getEyePosition(1),look=player.getViewVector(1);double nearest=Double.MAX_VALUE;JsonObject selected=null;
        for (var element : all) {
            JsonObject npc = MpsqScenes.apply(element.getAsJsonObject());
            if(npc.has("scenes_hidden")&&npc.get("scenes_hidden").getAsBoolean())continue;
            if(!npc.has("x")||!npc.has("y")||!npc.has("z"))continue;
            double x=npc.has("world_x")?npc.get("world_x").getAsDouble():npc.get("x").getAsDouble()+0.5;
            double y=npc.has("world_y")?npc.get("world_y").getAsDouble():npc.get("y").getAsDouble();
            double z=npc.has("world_z")?npc.get("world_z").getAsDouble():npc.get("z").getAsDouble()+0.5;
            if(player.distanceToSqr(x,y,z)>range*range)continue;
            double size=npc.has("scale")?npc.get("scale").getAsDouble():1;
            String category=npc.has("category")?npc.get("category").getAsString():"npc_model";
            boolean wumpus=MpsqAccessoryRenderer.WUMPUS_ASSET_ID.equals(str(npc,"asset_id",""));
            double height=category.startsWith("npc_skin_")?1.8*size:wumpus?1.45*size:size;
            // Keep the click volume centered on the same world-space anchor used by
            // the renderer, and cover the full standing skin with a small margin.
            double halfWidth=category.startsWith("npc_skin_")?0.36*size:wumpus?0.62*size:0.45*size;
            double verticalMargin=0.08*size;
            AABB hitbox=new AABB(x-halfWidth,y-verticalMargin,z-halfWidth,
                    x+halfWidth,y+height+verticalMargin,z+halfWidth);
            var hit=hitbox.clip(eye,eye.add(look.scale(range)));
            if(hit.isPresent()){
                double distance=eye.distanceToSqr(hit.get());
                if(distance<nearest&&clear(eye,hit.get(),player)){nearest=distance;selected=npc;}
            }
        }
        return selected;
    }

    private static boolean isOfficer() {
        return TeamStateStore.self().map(p -> p.permissionRank().level() >= TeamRank.OFFICER.level()).orElse(false);
    }

    private static void interact(JsonObject npc) {
        JsonObject interaction=npc.has("interaction_data")&&npc.get("interaction_data").isJsonObject()?npc.getAsJsonObject("interaction_data"):new JsonObject();
        String configuredSound=interaction.has("soundId")&&!interaction.get("soundId").isJsonNull()?interaction.get("soundId").getAsString().trim():"";
        if(!configuredSound.isBlank())MpsqMediaAudioManager.playAuto(configuredSound);
        String task = npc.has("task_type") && !npc.get("task_type").isJsonNull() ? npc.get("task_type").getAsString() : "none";
        String npcId=npc.has("id")&&!npc.get("id").isJsonNull()?npc.get("id").getAsString():"";
        // Visit state is read by the renderer each frame. A full refresh clears
        // player-skin textures and makes the NPC disappear while they reload.
        MpsqNpcVisitStore.markVisited(task,npcId);
        MpsqNpcReactions.opened(npcId);
        if ("accessories".equals(task)) {
            Minecraft.getInstance().gui.setScreen(new MpsqAccessoriesScreen(null,true));
            return;
        }
        if ("quest".equals(task)) {
            Minecraft.getInstance().gui.setScreen(new MpsqQuestMenuScreen(null, npcId));
            return;
        }
        JsonObject data = interaction;
        if (!data.has("pages") || !data.get("pages").isJsonArray() || data.getAsJsonArray("pages").isEmpty()) {
            return;
        }
        boolean tutorialDone=npc.has("tutorial_completed")&&npc.get("tutorial_completed").getAsBoolean();
        if ("tutorial".equals(task) && !tutorialDone) {
            String id = npc.get("id").getAsString();
            MpsqDialogueManager.start(data, (ignored, done) -> {
                if (!done) return;
                if (MpsqActionSync.server().isBlank()) {
                    JsonObject patch = new JsonObject(); patch.addProperty("tutorialCompleted", true);
                    boolean saved = MpsqLocalNpcStore.update(MpsqActionSync.world(), id, patch);
                    if (saved) {
                        MpsqAccessoryRenderer.markTutorialCompleted(id);
                        if (Minecraft.getInstance().player != null) de.galaxushd.mpsqcamera.compat.PlayerMessages.send(Minecraft.getInstance().player, net.minecraft.network.chat.Component.literal("Tutorial abgeschlossen und in dieser Welt gespeichert."), false);
                    } else if (Minecraft.getInstance().player != null) {
                        de.galaxushd.mpsqcamera.compat.PlayerMessages.send(Minecraft.getInstance().player, net.minecraft.network.chat.Component.literal("Tutorial-Abschluss konnte lokal nicht gespeichert werden."), false);
                    }
                    return;
                }
                JsonObject body = new JsonObject(); body.addProperty("server", MpsqActionSync.server()); body.addProperty("world", MpsqActionSync.world());
                MpsqApiClient.post("/npcs/" + id + "/tutorial-complete", body).whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
                    if (error == null) {
                        MpsqAccessoryRenderer.markTutorialCompleted(id);
                        if (Minecraft.getInstance().player != null) de.galaxushd.mpsqcamera.compat.PlayerMessages.send(Minecraft.getInstance().player, net.minecraft.network.chat.Component.literal("Tutorial abgeschlossen und gespeichert."), false);
                    } else if (Minecraft.getInstance().player != null) de.galaxushd.mpsqcamera.compat.PlayerMessages.send(Minecraft.getInstance().player, net.minecraft.network.chat.Component.literal("Tutorial-Abschluss fehlgeschlagen: " + error.getMessage()), false);
                }));
            });
        } else {
            MpsqDialogueManager.start(data);
        }
    }
}

