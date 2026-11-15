package de.galaxushd.mpsqcamera;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Rotations;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client-only entities. They are never sent to a Minecraft server. */
public final class CameraHologramManager {
    private static final String CAMERA_HEAD_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGZiZWQzNzI1YzA1Mjc0OGI2NWJlODQ3ZTE5NDJmNzU5YzNhOGRhMDY0OWY4MDUwODdiMDM2Nzk2NDE2MWI0ZCJ9fX0=";
    private static final Map<UUID, ArmorStand> HOLOGRAMS = new HashMap<>();
    private static int nextEntityId = -2_000_000_000;
	private static boolean hiddenForCameraView;

    private CameraHologramManager() { }

    public static void initialize() {
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> clear()); net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(c->{if(c.level==null||!TeamVisibilitySettings.inArea()){clear();return;}if(!ScreenCreationManager.isCameraViewActive())LocalCameraStore.getAll().forEach(CameraHologramManager::show);});
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                client.execute(() -> LocalCameraStore.getAll().forEach(CameraHologramManager::show)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    public static void show(LocalCameraStore.CameraData camera) {
        Minecraft client = Minecraft.getInstance();
        if (!TeamVisibilitySettings.inArea() || client.level == null || camera.position() == null) return;
        if (!camera.dimension().equals(client.level.dimension().identifier().toString()) || MpsqCameraInteraction.usesWorldHead(camera.position())) { remove(camera.id()); return; }
        if (HOLOGRAMS.containsKey(camera.id())) return;
        ArmorStand stand = new ArmorStand(client.level, camera.position().x, camera.position().y - 1.55, camera.position().z);
        stand.setId(nextEntityId++);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        applyTransform(stand, camera);
        stand.setItemSlot(EquipmentSlot.HEAD, createCameraHead());
        client.level.addEntity(stand);
        HOLOGRAMS.put(camera.id(), stand);
    }

    /** Applies a saved move/rotation immediately to an already visible client-only hologram. */
    public static void update(LocalCameraStore.CameraData camera) {
        ArmorStand stand = HOLOGRAMS.get(camera.id());
        if (stand == null) { show(camera); return; }
        if (camera.position() == null || MpsqCameraInteraction.usesWorldHead(camera.position())) { remove(camera.id()); return; }
        applyTransform(stand, camera);
    }

    private static void applyTransform(ArmorStand stand, LocalCameraStore.CameraData camera) {
        stand.setPos(camera.position().x, camera.position().y - 1.55, camera.position().z);
        stand.setYRot(camera.yaw());
        stand.setXRot(camera.pitch());
        // The armor stand already owns the horizontal direction through setYaw.
        // Applying the same yaw a second time to its head rotates the camera
        // marker twice (most visible when it faces along the Z axis).
        stand.setHeadPose(new Rotations(camera.pitch(), 0.0F, 0.0F));
    }

    public static void remove(UUID cameraId) {
        ArmorStand stand = HOLOGRAMS.remove(cameraId);
        if (stand != null) stand.discard();
    }

    public static void clear() {
        for (ArmorStand stand : HOLOGRAMS.values()) stand.discard();
        HOLOGRAMS.clear();
		hiddenForCameraView = false;
    }

	/** Hides local camera markers while their world is used as a camera view. */
	public static void hideForCameraView() {
		if (hiddenForCameraView) return;
		hiddenForCameraView = true;
		for (ArmorStand stand : HOLOGRAMS.values()) {
			stand.setInvisible(true);
			stand.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		}
	}

	/** Restores all local camera markers after leaving the camera view. */
	public static void showAfterCameraView() {
		if (!hiddenForCameraView || !TeamVisibilitySettings.inArea()) return;
		hiddenForCameraView = false;
		for (LocalCameraStore.CameraData camera : LocalCameraStore.getAll()) {
			ArmorStand stand = HOLOGRAMS.get(camera.id());
			if (stand == null || camera.position() == null) continue;
			applyTransform(stand, camera);
			stand.setItemSlot(EquipmentSlot.HEAD, createCameraHead());
		}
	}

    private static ItemStack createCameraHead() {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        var properties = com.google.common.collect.ArrayListMultimap.<String, Property>create();
        properties.put("textures", new Property("textures", CAMERA_HEAD_TEXTURE));
        GameProfile profile = new GameProfile(UUID.randomUUID(), "MPSQ Camera", new com.mojang.authlib.properties.PropertyMap(properties));
        head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
        return head;
    }
}
