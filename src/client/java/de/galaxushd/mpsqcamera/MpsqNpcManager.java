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
            if(!npc.has("x")||!npc.has("y")||!npc.has("z"))continue;int x=npc.get("x").getAsInt(),y=npc.get("y").getAsInt(),z=npc.get("z").getAsInt();
            double size=npc.has("scale")?npc.get("scale").getAsDouble():1;
            // Uploaded NPC models use a 16-unit canvas, rendered at scale / 16,
            // so the visible model is approximately `scale` blocks tall.
            Box hitbox=new Box(x+.5-size*.45,y,z+.5-size*.45,x+.5+size*.45,y+size,z+.5+size*.45);
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
                JsonObject body = new JsonObject(); body.addProperty("server", MpsqActionSync.server()); body.addProperty("world", MpsqActionSync.world());
                MpsqApiClient.post("/npcs/" + id + "/tutorial-complete", body).whenComplete((result, error) -> MinecraftClient.getInstance().execute(() -> {
                    if (error == null) MpsqAccessoryRenderer.refresh();
                    else if (MinecraftClient.getInstance().player != null) MinecraftClient.getInstance().player.sendMessage(net.minecraft.text.Text.literal("Tutorial-Abschluss konnte nicht gespeichert werden. Bitte erneut versuchen."), false);
                }));
            });
        } else {
            MpsqDialogueManager.start(data);
        }
    }
}
