package de.galaxushd.mpsqcamera;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Client interactions for virtual NPCs anchored above their support block. */
public final class MpsqNpcManager {
    private MpsqNpcManager() { }

    public static void initialize() {
        // MouseMixin calls handleRightClick before Minecraft forwards the
        // click to a block or item, so virtual models behave like targets.
    }

    /** Handles a client right-click aimed at one of the locally rendered NPCs. */
    public static boolean handleRightClick() {
        MinecraftClient client = MinecraftClient.getInstance();
        var player = client.player;
        if (player == null || client.world == null || client.currentScreen != null || !TeamVisibilitySettings.visible()) return false;
        JsonObject npc = targeted(player);
        if (npc == null) return false;
        if (player.isSneaking() && isOfficer()) {
            client.setScreen(new MpsqNpcConfiguratorScreen(null, npc));
        } else {
            interact(npc);
        }
        return true;
    }

    private static JsonObject targeted(net.minecraft.entity.player.PlayerEntity player) {
        JsonArray all = MpsqAccessoryRenderer.npcsSnapshot();
        Vec3d eye=player.getCameraPosVec(1),look=player.getRotationVec(1);double nearest=Double.MAX_VALUE;JsonObject selected=null;
        for (var element : all) {
            JsonObject npc = element.getAsJsonObject();
            if(!npc.has("x")||!npc.has("y")||!npc.has("z"))continue;
            double x=npc.has("world_x")?npc.get("world_x").getAsDouble():npc.get("x").getAsDouble()+0.5;
            double y=npc.has("world_y")?npc.get("world_y").getAsDouble():npc.get("y").getAsDouble();
            double z=npc.has("world_z")?npc.get("world_z").getAsDouble():npc.get("z").getAsDouble()+0.5;
            double size=npc.has("scale")?npc.get("scale").getAsDouble():1;
            String category=npc.has("category")?npc.get("category").getAsString():"npc_model";
            double height=category.startsWith("npc_skin_")?1.8*size:size;
            // Keep the click volume centered on the same world-space anchor used by
            // the renderer, and cover the full standing skin with a small margin.
            double halfWidth=category.startsWith("npc_skin_")?0.36*size:0.45*size;
            double verticalMargin=0.08*size;
            Box hitbox=new Box(x-halfWidth,y-verticalMargin,z-halfWidth,
                    x+halfWidth,y+height+verticalMargin,z+halfWidth);
            var hit=hitbox.raycast(eye,eye.add(look.multiply(6.0)));
            if(hit.isPresent()){
                double distance=eye.squaredDistanceTo(hit.get());
                if(distance<nearest){nearest=distance;selected=npc;}
            }
        }
        return selected;
    }

    private static boolean isOfficer() {
        return TeamStateStore.self().map(p -> p.permissionRank().level() >= TeamRank.OFFICER.level()).orElse(false);
    }

    private static void interact(JsonObject npc) {
        String task = npc.has("task_type") && !npc.get("task_type").isJsonNull() ? npc.get("task_type").getAsString() : "none";
        String npcId=npc.has("id")&&!npc.get("id").isJsonNull()?npc.get("id").getAsString():"";
        // Visit state is read by the renderer each frame. A full refresh clears
        // player-skin textures and makes the NPC disappear while they reload.
        MpsqNpcVisitStore.markVisited(task,npcId);
        if ("accessories".equals(task)) {
            MinecraftClient.getInstance().setScreen(new MpsqAccessoriesScreen(null,true));
            return;
        }
        if ("quest".equals(task)) {
            MinecraftClient.getInstance().setScreen(new MpsqQuestsScreen(null,npc.get("id").getAsString()));
            return;
        }
        JsonObject data = npc.has("interaction_data") && npc.get("interaction_data").isJsonObject()
                ? npc.getAsJsonObject("interaction_data") : new JsonObject();
        if (!data.has("pages") || !data.get("pages").isJsonArray() || data.getAsJsonArray("pages").isEmpty()) {
            JsonArray pages = new JsonArray();
            pages.add(npc.has("name") ? npc.get("name").getAsString() : "Hallo!");
            data.add("pages", pages);
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
                        if (MinecraftClient.getInstance().player != null) MinecraftClient.getInstance().player.sendMessage(net.minecraft.text.Text.literal("Tutorial abgeschlossen und in dieser Welt gespeichert."), false);
                    } else if (MinecraftClient.getInstance().player != null) {
                        MinecraftClient.getInstance().player.sendMessage(net.minecraft.text.Text.literal("Tutorial-Abschluss konnte lokal nicht gespeichert werden."), false);
                    }
                    return;
                }
                JsonObject body = new JsonObject(); body.addProperty("server", MpsqActionSync.server()); body.addProperty("world", MpsqActionSync.world());
                MpsqApiClient.post("/npcs/" + id + "/tutorial-complete", body).whenComplete((result, error) -> MinecraftClient.getInstance().execute(() -> {
                    if (error == null) {
                        MpsqAccessoryRenderer.markTutorialCompleted(id);
                        if (MinecraftClient.getInstance().player != null) MinecraftClient.getInstance().player.sendMessage(net.minecraft.text.Text.literal("Tutorial abgeschlossen und gespeichert."), false);
                    } else if (MinecraftClient.getInstance().player != null) MinecraftClient.getInstance().player.sendMessage(net.minecraft.text.Text.literal("Tutorial-Abschluss fehlgeschlagen: " + error.getMessage()), false);
                }));
            });
        } else {
            MpsqDialogueManager.start(data);
        }
    }
}
