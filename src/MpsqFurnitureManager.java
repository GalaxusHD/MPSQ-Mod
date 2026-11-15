package de.galaxushd.mpsqcamera;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;


/** Handles right-click interaction with client-rendered furniture. */
public final class MpsqFurnitureManager {
    private MpsqFurnitureManager() { }

    /**
     * The virtual furniture hitbox is a one-block cube centered on the model.
     * Right-clicks on the hitbox are consumed even when no sound is configured.
     */
    public static boolean handleRightClick() {return hit(true);}
    static boolean blocksClick(){return hit(false);}
    private static boolean hit(boolean interact) {
        Minecraft client = Minecraft.getInstance();
        var player = client.player;
        if (player == null || client.level == null || client.gui.screen() != null
                || !TeamVisibilitySettings.inArea()) {
            return false;
        }

        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 end = eye.add(player.getViewVector(1.0f).scale(6.0));
        JsonObject selected = null;
        double nearest = Double.MAX_VALUE;
        boolean selectedHead=false;
        var block=client.level.clip(new net.minecraft.world.level.ClipContext(eye,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
        double blockDistance=block.getType()==net.minecraft.world.phys.HitResult.Type.MISS?Double.MAX_VALUE:eye.distanceToSqr(block.getLocation());

        for (var element : MpsqAccessoryRenderer.objectsSnapshot()) {
            if (!element.isJsonObject()) continue;
            JsonObject furniture = element.getAsJsonObject();
            if (!furniture.has("x") || !furniture.has("y") || !furniture.has("z")) continue;

            double x = furniture.get("x").getAsDouble() + 0.5;
            double centerY = MpsqAccessoryRenderer.furnitureHitboxCenterY(furniture);
            double z = furniture.get("z").getAsDouble() + 0.5;
            AABB hitbox = new AABB(x - 0.5, centerY - 0.5, z - 0.5,
                    x + 0.5, centerY + 0.5, z + 0.5);
            var headHit=MpsqAccessoryRenderer.figureHeadHit(furniture,eye,end);
            var hit = headHit.isPresent()?headHit:hitbox.clip(eye, end);
            if (hit.isEmpty()) continue;

            double distance = eye.distanceToSqr(hit.get());
            if (distance < nearest && distance<=blockDistance+.001) {
                nearest = distance;
                selected = furniture;
                selectedHead=headHit.isPresent();
            }
        }

        if (selected == null) return false;
        if (!interact) return true;
        if (player.isShiftKeyDown() && canConfigure(client)) {
            client.gui.setScreen(new MpsqObjectScreen(selected));
            return true;
        }

        if(MpsqCrate.isCrate(selected))MpsqCrate.open(selected);
        if(selectedHead)MpsqAccessoryRenderer.wobbleFigure(selected);

        String soundId = selected.has("sound_id") && !selected.get("sound_id").isJsonNull()
                ? selected.get("sound_id").getAsString()
                : selected.has("soundId") && !selected.get("soundId").isJsonNull()
                ? selected.get("soundId").getAsString() : "";
        if (!soundId.isBlank() && soundId.length() <= 128) {
            MpsqMediaAudioManager.playAuto(soundId);
        }
        return true;
    }

    private static boolean canConfigure(Minecraft client) {
        if (client.getSingleplayerServer() != null) return true;
        if (!MpsqActionSync.isMpsqServer()) return false;
        return TeamStateStore.self()
                .map(profile -> profile.permissionRank().level() >= TeamRank.OFFICER.level())
                .orElse(false);
    }
}
