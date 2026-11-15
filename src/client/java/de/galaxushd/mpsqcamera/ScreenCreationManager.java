package de.galaxushd.mpsqcamera;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ScreenCreationManager {
	private static final double CAMERA_LOAD_RANGE = 48.0;
	private static final long VIEW_ENTER_COOLDOWN_MS = 400L;
	private static final long OFFLINE_HINT_INTERVAL_MS = 1000L;
	private static int nextCameraProxyEntityId = -1_900_000_000;

	private static boolean wasUsePressedLastTick = false;
	private static boolean wasEscPressedLastTick = false;
	private static boolean ignoreEscapeUntilReleased = false;
	private static boolean wasPreviousCameraPressed = false;
	private static boolean wasNextCameraPressed = false;
	private static BlockPos selectionPos1;
	private static Direction selectionSide;
	private static long lastViewEnterAttemptMs = 0L;
	private static long lastOfflineHintMs = 0L;

	/** K – Hauptmenü des Mods */
	private static KeyMapping hauptMenuKey;

	private static ViewSession activeViewSession;
	private static boolean exitSnapshotPending;

	private ScreenCreationManager() {}

	public static void initialize() {
		hauptMenuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.mpsqcamera.hauptmenu",
				com.mojang.blaze3d.platform.InputConstants.KEY_K,
				MpsqKeyCategories.MAIN
		));

		ClientTickEvents.START_CLIENT_TICK.register(ScreenCreationManager::onStartTick);
		ClientTickEvents.END_CLIENT_TICK.register(ScreenCreationManager::onEndTick);
	}

	private static void onStartTick(Minecraft client) {
		if (activeViewSession == null || client.player == null || client.level == null || client.options == null) {
			return;
		}

		suppressMovementAndInteraction(client);
		// Do not teleport the real player every tick. On a multiplayer server that
		// causes repeated position/rotation corrections, which in turn makes the
		// client camera snap back or spin. The disabled movement keys and zero
		// velocity keep the body in place without fighting the server.
		client.player.setDeltaMovement(Vec3.ZERO);
		updateBodycamProxy(client, activeViewSession);
		activeViewSession.applyViewRotation();
	}

	/** True while Minecraft's mouse input belongs to the remote camera. */
	public static boolean isCameraViewActive() {
		return activeViewSession != null && !exitSnapshotPending;
	}

	/** Marks an ESC press that belongs to closing a vanilla UI such as chat. */
	public static void ignoreEscapeForOpenScreen() {
		ignoreEscapeUntilReleased = true;
	}

	/**
	 * Receives Minecraft's already sensitivity-adjusted mouse deltas directly.
	 * The real player is deliberately never changed here: changing their look
	 * first and restoring it afterwards causes the server/client correction loop
	 * that produced the visible flickering.
	 */
	public static void applyCameraLook(double cursorDeltaX, double cursorDeltaY) {
		if (!isCameraViewActive()) return;
		activeViewSession.applyMouseLook(cursorDeltaX, cursorDeltaY);
	}

	private static void onEndTick(Minecraft client) {
		if (client.options == null) return;

		boolean usePressed = client.options.keyUse.isDown();
		boolean escPressed = InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);
		boolean previousCameraPressed = InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_LEFT);
		boolean nextCameraPressed = InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT);

		if (client.player == null || client.level == null) {
			if (activeViewSession != null) {
				exitViewMode(client, false);
			}
			wasUsePressedLastTick = usePressed;
			wasEscPressedLastTick = escPressed;
			return;
		}

		LocalPlayer player = client.player; if(activeViewSession!=null&&!TeamVisibilitySettings.inArea())finishExitViewMode(client,activeViewSession,false);
		// Selection size is drawn by the selection UI; do not overwrite its action bar.

		if (activeViewSession != null) {
			if (activeViewSession.isMobile()) {
				if (previousCameraPressed && !wasPreviousCameraPressed) switchMobileCamera(-1);
				if (nextCameraPressed && !wasNextCameraPressed) switchMobileCamera(1);
			} else {
				LocalScreenStore.findByAnchor(activeViewSession.sourceAnchor()).ifPresent(screen -> {
					if (previousCameraPressed && !wasPreviousCameraPressed) switchCamera(screen.id(), -1);
					if (nextCameraPressed && !wasNextCameraPressed) switchCamera(screen.id(), 1);
				});
			}
			handleActiveViewSession(client, player, usePressed, escPressed);
		} else {
			LocalScreenStore.LocalScreenData lookedAt = getScreenAtCrosshair(client).orElse(null);
			if (lookedAt != null && previousCameraPressed && !wasPreviousCameraPressed) switchCamera(lookedAt.id(), -1);
			if (lookedAt != null && nextCameraPressed && !wasNextCameraPressed) switchCamera(lookedAt.id(), 1);
			handlePassiveScreenLook(client, player);

			if (usePressed && !wasUsePressedLastTick) {
				if (isHoldingToolItem(player)) {
					tryCreateScreen(client, player);
				} else {
					tryEnterViewMode(client, player);
				}
			}
		}

		// K => Hauptmenü öffnen
		while (hauptMenuKey.consumeClick()) {
			if (client.gui.screen() == null) {
				client.gui.setScreen(new ModConfigScreen());
			}
		}

		wasUsePressedLastTick = usePressed;
		wasEscPressedLastTick = escPressed;
		wasPreviousCameraPressed = previousCameraPressed;
		wasNextCameraPressed = nextCameraPressed;
	}

	private static void switchCamera(UUID screenId, int direction) {
		UUID camera = ScreenCameraStore.next(screenId, direction);
		if (camera == null) return;
		Minecraft client = Minecraft.getInstance();

		// The selected camera is always scoped to the selected screen by
		// ScreenCameraStore. While a local view is active, move that view and
		// the optional remote broadcaster to the newly selected camera as well.
		if (activeViewSession != null && activeViewSession.sourceAnchor() != null) {
			LocalCameraStore.find(camera).ifPresent(data -> {
				Vec3 position = cameraPosition(client, data);
				if (position != null) {
					placeCameraProxy(activeViewSession.cameraEntity(), position, cameraYaw(client, data), cameraPitch(client, data));
					activeViewSession.setViewRotation(cameraYaw(client, data), cameraPitch(client, data));
					activeViewSession.setCameraId(camera);
				}
			});
			RemoteCameraFrameManager.startPublishing(camera);
		}

		LocalCameraStore.find(camera).ifPresent(data -> {
			int current = ScreenCameraStore.activePosition(screenId);
			int total = ScreenCameraStore.cameras(screenId).size();
			if (client.player != null) {
				de.galaxushd.mpsqcamera.compat.PlayerMessages.send(client.player, Component.literal(data.name() + " " + current + "/" + total), true);
			}
		});
		MpsqCameraClient.LOGGER.info("[MPSQ] Aktive Kamera gewechselt: {}", camera);
	}

	private static void switchMobileCamera(int direction) {
		if (activeViewSession == null || !activeViewSession.isMobile()) return;
		List<UUID> cameras = activeViewSession.mobileCameraIds();
		if (cameras.isEmpty()) return;
		int current = cameras.indexOf(activeViewSession.cameraId());
		int next = Math.floorMod((current < 0 ? 0 : current) + direction, cameras.size());
		UUID cameraId = cameras.get(next);
		Minecraft client = Minecraft.getInstance();
		LocalCameraStore.find(cameraId).ifPresent(camera -> {
			Vec3 position = cameraPosition(client, camera);
			if (position == null || CameraSafety.isStaticCameraBlocked(client, camera)) return;
			placeCameraProxy(activeViewSession.cameraEntity(), position,
					cameraYaw(client, camera), cameraPitch(client, camera));
			activeViewSession.setViewRotation(cameraYaw(client, camera), cameraPitch(client, camera));
			activeViewSession.setCameraId(cameraId);
			RemoteCameraFrameManager.startPublishing(cameraId);
			if (client.player != null) {
				de.galaxushd.mpsqcamera.compat.PlayerMessages.send(client.player, Component.literal(camera.name() + " " + (next + 1) + "/" + cameras.size()), true);
			}
		});
	}

	private static UUID activeCameraId(LocalScreenStore.LocalScreenData screen) {
		UUID active = ScreenCameraStore.active(screen.id());
		return active != null ? active : screen.cameraId();
	}

	private static Player bodycamWearer(Minecraft client, LocalCameraStore.CameraData camera) {
		if (camera.kind() != LocalCameraStore.CameraKind.BODYCAM || client.level == null) return null;
		if (camera.wearerId() != null) {
			Player byUuid = client.level.getPlayerByUUID(camera.wearerId());
			if (byUuid != null) return byUuid;
		}
		if (camera.wearerName() != null && !camera.wearerName().isBlank()) {
			for (Player candidate : client.level.players()) {
				if (camera.wearerName().equalsIgnoreCase(candidate.getName().getString())) return candidate;
			}
		}
		return null;
	}

	private static Vec3 cameraPosition(Minecraft client, LocalCameraStore.CameraData camera) {
		Player wearer = bodycamWearer(client, camera);
		return wearer != null ? wearer.getEyePosition() : camera.position();
	}

	private static float cameraYaw(Minecraft client, LocalCameraStore.CameraData camera) {
		Player wearer = bodycamWearer(client, camera);
		return wearer != null ? wearer.getYRot() : camera.yaw();
	}

	private static float cameraPitch(Minecraft client, LocalCameraStore.CameraData camera) {
		Player wearer = bodycamWearer(client, camera);
		return wearer != null ? wearer.getXRot() : camera.pitch();
	}

	private static void updateBodycamProxy(Minecraft client, ViewSession session) {
		LocalCameraStore.find(session.cameraId).ifPresent(camera -> {
			if (camera.kind() != LocalCameraStore.CameraKind.BODYCAM) return;
			Vec3 position = cameraPosition(client, camera);
			if (position != null) {
				float yaw = cameraYaw(client, camera);
				float pitch = cameraPitch(client, camera);
				placeCameraProxy(session.cameraEntity(), position, yaw, pitch);
				// A bodycam is the wearer's first-person perspective. Synchronise
				// the view before the normal session rotation is applied below.
				session.setViewRotation(yaw, pitch);
			}
		});
	}

	private static void handlePassiveScreenLook(Minecraft client, LocalPlayer player) {
		LocalScreenStore.LocalScreenData screen = getScreenAtCrosshair(client).orElse(null);
		UUID cameraId = screen == null ? null : activeCameraId(screen);
		if (screen == null || screen.inputType() != LocalScreenStore.ScreenInputType.CAMERA || cameraId == null) {
			return;
		}

		Optional<LocalCameraStore.CameraData> cameraScreen = LocalCameraStore.find(cameraId);
		if (cameraScreen.isEmpty() || cameraPosition(client, cameraScreen.get()) == null) {
			sendOfflineHint(player);
			return;
		}
		if (CameraSafety.isStaticCameraBlocked(client, cameraScreen.get())) {
			sendBlockedHint(player);
			return;
		}

		Vec3 cameraPos = cameraPosition(client, cameraScreen.get());
		if (!isCameraAreaLoadedByAnyPlayer(client, cameraPos)) {
			sendOfflineHint(player);
		}
	}

	private static void sendOfflineHint(LocalPlayer player) {
		long now = System.currentTimeMillis();
		if (now - lastOfflineHintMs >= OFFLINE_HINT_INTERVAL_MS) {
			de.galaxushd.mpsqcamera.compat.PlayerMessages.send(player, Component.translatable("status.mpsqcamera.camera_offline"), true);
			lastOfflineHintMs = now;
		}
	}

	private static void sendBlockedHint(LocalPlayer player) {
		long now = System.currentTimeMillis();
		if (now - lastOfflineHintMs >= OFFLINE_HINT_INTERVAL_MS) {
			de.galaxushd.mpsqcamera.compat.PlayerMessages.send(player, Component.translatable("status.mpsqcamera.camera_blocked"), true);
			lastOfflineHintMs = now;
		}
	}

	private static void tryEnterViewMode(Minecraft client, LocalPlayer player) { if(!TeamVisibilitySettings.inArea())return;
		if (!TeamStateStore.self().map(TeamProfile::canViewCameras).orElse(false)) return;
		long now = System.currentTimeMillis();
		if (now - lastViewEnterAttemptMs < VIEW_ENTER_COOLDOWN_MS) {
			return;
		}
		lastViewEnterAttemptMs = now;

		LocalScreenStore.LocalScreenData screen = getScreenAtCrosshair(client).orElse(null);
		UUID cameraId = screen == null ? null : activeCameraId(screen);
		if (screen == null || screen.inputType() != LocalScreenStore.ScreenInputType.CAMERA || cameraId == null) {
			return;
		}

		LocalCameraStore.CameraData cameraScreen = LocalCameraStore.find(cameraId).orElse(null);
		if (cameraScreen == null || cameraPosition(client, cameraScreen) == null) {
			sendOfflineHint(player);
			return;
		}
		if (CameraSafety.isStaticCameraBlocked(client, cameraScreen)) {
			sendBlockedHint(player);
			return;
		}

		Vec3 cameraPos = cameraPosition(client, cameraScreen);
		if (!isCameraAreaLoadedByAnyPlayer(client, cameraPos)) {
			sendOfflineHint(player);
			return;
		}

		beginViewMode(client, player, cameraScreen, screen.pos1().immutable(), List.of());
	}

	/** Opens the normal camera view from the player-bound mobile clock. */
	public static boolean enterMobileView(List<UUID> linkedCameraIds) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player; if(activeViewSession!=null&&!TeamVisibilitySettings.inArea())finishExitViewMode(client,activeViewSession,false);
		if (player == null || client.level == null || activeViewSession != null) return false;
		if (!TeamVisibilitySettings.inArea()) return false;
		if (!TeamStateStore.self().map(TeamProfile::canViewCameras).orElse(false)) return false;

		List<UUID> available = linkedCameraIds.stream()
				.distinct()
				.filter(id -> LocalCameraStore.find(id).isPresent())
				.toList();
		if (available.isEmpty()) return false;

		for (UUID cameraId : available) {
			LocalCameraStore.CameraData camera = LocalCameraStore.find(cameraId).orElse(null);
			if (camera == null || cameraPosition(client, camera) == null) continue;
			if (CameraSafety.isStaticCameraBlocked(client, camera)) continue;
			if (!isCameraAreaLoadedByAnyPlayer(client, cameraPosition(client, camera))) continue;
			beginViewMode(client, player, camera, null, available);
			return true;
		}
		return false;
	}

	private static void beginViewMode(Minecraft client, LocalPlayer player,
			LocalCameraStore.CameraData cameraScreen, BlockPos sourceAnchor, List<UUID> mobileCameraIds) {
		UUID cameraId = cameraScreen.id();
		Vec3 cameraPos = cameraPosition(client, cameraScreen);
		if (cameraPos == null) return;

		// Die gespeicherte Position ist der Blickpunkt der Kamera. Die genaue
		// Augenhoehe des unsichtbaren Proxis wird deshalb nach dem Erzeugen
		// dynamisch ausgeglichen, statt durch einen festen Wert geschaetzt.
		ArmorStand cameraEntity = new ArmorStand(client.level, cameraPos.x, cameraPos.y, cameraPos.z);
		cameraEntity.setId(nextCameraProxyEntityId++);
		cameraEntity.setNoGravity(true);
		cameraEntity.setInvisible(true);
		placeCameraProxy(cameraEntity, cameraPos, cameraYaw(client, cameraScreen), cameraPitch(client, cameraScreen));
		// A camera entity must be present in the client world. An untracked entity
		// has no stable previous render position, which makes Minecraft interpolate
		// the view between unrelated places and causes the visible flicker.
		client.level.addEntity(cameraEntity);

		Entity previousCamera = client.getCameraEntity();
		CameraType previousPerspective = client.options.getCameraType();

		activeViewSession = new ViewSession(
				sourceAnchor,
				cameraId,
				player.position(),
				client.level.dimension(),
				previousCamera,
				previousPerspective,
				cameraEntity,
				cameraYaw(client, cameraScreen),
				cameraPitch(client, cameraScreen),
				mobileCameraIds
		);

		// A mobile view is opened by the same use-key press that would normally
		// close an active view. Arm the edge detector immediately so this opening
		// click cannot be interpreted as a second, new click in onEndTick.
		wasUsePressedLastTick = true;

		client.setCameraEntity(cameraEntity);
		client.options.setCameraType(CameraType.FIRST_PERSON);
		CameraHologramManager.hideForCameraView();
		RemoteCameraFrameManager.startPublishing(cameraId);
		de.galaxushd.mpsqcamera.compat.PlayerMessages.send(player, Component.translatable("status.mpsqcamera.view_enter"), true);
	}

	private static void handleActiveViewSession(
			Minecraft client,
			LocalPlayer player,
			boolean usePressed,
			boolean escPressed
	) {
		if (!isSessionStillValid(client, player, activeViewSession)) {
			exitViewMode(client, true);
			return;
		}

		if (escPressed && !wasEscPressedLastTick) {
			if (!ignoreEscapeUntilReleased) {
				exitViewMode(client, true);
				return;
			}
		}

		if (!escPressed) {
			// The UI-close press has ended. A later ESC press may leave the camera.
			ignoreEscapeUntilReleased = false;
		}

		if (usePressed && !wasUsePressedLastTick) {
			exitViewMode(client, true);
		}
	}

	private static boolean isSessionStillValid(Minecraft client, LocalPlayer player, ViewSession session) {
		if (player.isRemoved() || !player.isAlive()) return false;
		if (client.level == null) return false;
		if (!client.level.dimension().equals(session.originDimension())) return false;
		// Do not abort a remote view merely because Windows temporarily steals
		// focus or Minecraft briefly creates an overlay while focus returns.
		// A deliberate ESC press is handled separately in handleActiveViewSession.

		if (session.isMobile()) {
			LocalCameraStore.CameraData camera = LocalCameraStore.find(session.cameraId()).orElse(null);
			return camera != null && cameraPosition(client, camera) != null;
		}

		LocalScreenStore.LocalScreenData sourceScreen = LocalScreenStore.findByAnchor(session.sourceAnchor()).orElse(null);
		if (sourceScreen == null) return false;
		UUID cameraId = activeCameraId(sourceScreen);
		if (sourceScreen.inputType() != LocalScreenStore.ScreenInputType.CAMERA || cameraId == null) return false;

		LocalCameraStore.CameraData cameraScreen = LocalCameraStore.find(cameraId).orElse(null);
		return cameraScreen != null && cameraPosition(client, cameraScreen) != null;
	}

	private static void suppressMovementAndInteraction(Minecraft client) {
		client.options.keyUp.setDown(false);
		client.options.keyDown.setDown(false);
		client.options.keyLeft.setDown(false);
		client.options.keyRight.setDown(false);
		client.options.keyJump.setDown(false);
		client.options.keyShift.setDown(false);
		client.options.keySprint.setDown(false);
		client.options.keyAttack.setDown(false);
		client.options.keyUse.setDown(false);
	}

	private static void lockPlayerPosition(LocalPlayer player, Vec3 originPos) {
		player.setDeltaMovement(Vec3.ZERO);
	}

	private static void exitViewMode(Minecraft client, boolean notify) {
		if (activeViewSession == null || exitSnapshotPending) return;
		ViewSession session = activeViewSession;

		// For version 1.0 a screen retains one clean image from the saved
		// camera direction after leaving. Keep the proxy camera active until
		// WorldRenderEvents captured that one frame.
		LocalCameraStore.find(session.cameraId).ifPresentOrElse(camera -> {
			Vec3 position = cameraPosition(client, camera);
			if (position == null) {
				finishExitViewMode(client, session, notify);
				return;
			}
			placeCameraProxy(session.cameraEntity(), position, cameraYaw(client, camera), cameraPitch(client, camera));
			exitSnapshotPending = true;
			RemoteCameraFrameManager.captureFinalSnapshot(session.cameraId,
					() -> finishExitViewMode(client, session, notify));
		}, () -> finishExitViewMode(client, session, notify));
	}

	private static void finishExitViewMode(Minecraft client, ViewSession session, boolean notify) {
		if (activeViewSession != session) return;
		activeViewSession = null;
		exitSnapshotPending = false;
		RemoteCameraFrameManager.stopPublishing();
		CameraHologramManager.showAfterCameraView();

		if (client.options != null) {
			client.options.setCameraType(session.previousPerspective());
		}

		Entity fallbackCamera = client.player == null ? null : client.player;
		client.setCameraEntity(session.previousCameraEntity() != null ? session.previousCameraEntity() : fallbackCamera);
		session.cameraEntity().discard();

		if (client.player != null) {
			lockPlayerPosition(client.player, session.originPos());
			if (notify) {
				de.galaxushd.mpsqcamera.compat.PlayerMessages.send(client.player, Component.translatable("status.mpsqcamera.view_exit"), true);
			}
		}
	}

	private static Optional<LocalScreenStore.LocalScreenData> getScreenAtCrosshair(Minecraft client) {
		if (client.player == null || !TeamVisibilitySettings.visible()) return Optional.empty();

		double reach = client.player.blockInteractionRange();
		Vec3 eye = client.player.getEyePosition(1.0f);
		Vec3 direction = client.player.getViewVector(1.0f);

		// Prüfe alle Bildschirme via Ray-AABB-Test (keine Block-Abhängigkeit mehr)
		LocalScreenStore.LocalScreenData closest = null;
		double closestDist = Double.MAX_VALUE;

		for (LocalScreenStore.LocalScreenData screen : LocalScreenStore.getAllScreens()) {
			if ((screen.inputType()==LocalScreenStore.ScreenInputType.LINK||TeamVisibilitySettings.inArea()) && testRayBox(eye, direction, reach, screen.pos1(), screen.pos2())) {
				double dist = screen.pos1().distToLowCornerSqr(eye.x, eye.y, eye.z);
				if (dist < closestDist) {
					closestDist = dist;
					closest = screen;
				}
			}
		}

		if (closest != null) return Optional.of(closest);

		// Fallback: Legacy-Prüfung via Block-Treffer
		if (client.hitResult instanceof BlockHitResult hit
				&& client.hitResult.getType() == HitResult.Type.BLOCK) {
			return LocalScreenStore.findByAnchor(hit.getBlockPos()).filter(s->s.inputType()==LocalScreenStore.ScreenInputType.LINK||TeamVisibilitySettings.inArea());
		}

		return Optional.empty();
	}

	/**
	 * Prüft ob ein Strahl (Ursprung + Richtung, max. Länge reach) die Bounding-Box
	 * eines Bildschirms (pos1..pos2) schneidet (Slab-Methode / AABB-Ray-Test).
	 */
	private static boolean testRayBox(Vec3 eye, Vec3 dir, double reach,
			BlockPos p1, BlockPos p2) {
		double minX = Math.min(p1.getX(), p2.getX());
		double minY = Math.min(p1.getY(), p2.getY());
		double minZ = Math.min(p1.getZ(), p2.getZ());
		double maxX = Math.max(p1.getX(), p2.getX()) + 1.0;
		double maxY = Math.max(p1.getY(), p2.getY()) + 1.0;
		double maxZ = Math.max(p1.getZ(), p2.getZ()) + 1.0;

		double tNear = 0.0;
		double tFar  = reach;

		// X-Achse
		if (Math.abs(dir.x) < 1e-9) {
			if (eye.x < minX || eye.x > maxX) return false;
		} else {
			double t1 = (minX - eye.x) / dir.x;
			double t2 = (maxX - eye.x) / dir.x;
			if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
			tNear = Math.max(tNear, t1);
			tFar  = Math.min(tFar, t2);
			if (tNear > tFar) return false;
		}

		// Y-Achse
		if (Math.abs(dir.y) < 1e-9) {
			if (eye.y < minY || eye.y > maxY) return false;
		} else {
			double t1 = (minY - eye.y) / dir.y;
			double t2 = (maxY - eye.y) / dir.y;
			if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
			tNear = Math.max(tNear, t1);
			tFar  = Math.min(tFar, t2);
			if (tNear > tFar) return false;
		}

		// Z-Achse
		if (Math.abs(dir.z) < 1e-9) {
			if (eye.z < minZ || eye.z > maxZ) return false;
		} else {
			double t1 = (minZ - eye.z) / dir.z;
			double t2 = (maxZ - eye.z) / dir.z;
			if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
			tNear = Math.max(tNear, t1);
			tFar  = Math.min(tFar, t2);
			if (tNear > tFar) return false;
		}

		return tFar > 0.0;
	}

	private static boolean isCameraAreaLoadedByAnyPlayer(Minecraft client, Vec3 cameraPos) {
		// The integrated singleplayer server belongs to this client. There is no
		// second mod user required to make one of its cameras available.
		if (client.isLocalServer()) {
			return true;
		}
		double maxDistSq = CAMERA_LOAD_RANGE * CAMERA_LOAD_RANGE;
		for (Player worldPlayer : client.level.players()) {
			if (worldPlayer.distanceToSqr(cameraPos) <= maxDistSq) {
				return true;
			}
		}
		return false;
	}

	private static void showSelectionDimensions(LocalPlayer player) {
		if (selectionPos1 == null || !isHoldingToolItem(player)) {
			return;
		}
		BlockPos preview = getSelectionPos2Preview();
		if (preview == null) {
			return;
		}
		int width = Math.max(
				Math.abs(preview.getX() - selectionPos1.getX()),
				Math.abs(preview.getZ() - selectionPos1.getZ())
		) + 1;
		int height = Math.abs(preview.getY() - selectionPos1.getY()) + 1;
		de.galaxushd.mpsqcamera.compat.PlayerMessages.send(player, Component.translatable("gui.mpsqcamera.selection.dimensions", width, height), true);
	}

	private static Vec3 toCameraProxyPos(ArmorStand proxy, Vec3 cameraEyePos) {
		double eyeOffset = proxy.getEyeY() - proxy.getY();
		return cameraEyePos.add(0.0, -eyeOffset, 0.0);
	}

	private static void placeCameraProxy(ArmorStand proxy, Vec3 cameraEyePos, float yaw, float pitch) {
		Vec3 bodyPos = toCameraProxyPos(proxy, cameraEyePos);
		proxy.snapTo(bodyPos.x, bodyPos.y, bodyPos.z, yaw, pitch);
		applyProxyRotation(proxy, yaw, pitch);
	}

	private static void applyProxyRotation(ArmorStand proxy, float yaw, float pitch) {
		proxy.setYRot(yaw);
		proxy.setXRot(pitch);
		// Armor stands keep body and head yaw separately. Minecraft's camera
		// follows the head direction, so all three values must agree for a full
		// horizontal (green axis) rotation.
		proxy.setYBodyRot(yaw);
		proxy.setYHeadRot(yaw);
        proxy.yRotO=yaw;proxy.xRotO=pitch;proxy.yHeadRotO=yaw;proxy.yBodyRotO=yaw;
	}

	static BlockPos getSelectionPos1() {
		return selectionPos1;
	}

	static BlockPos getSelectionPos2Preview() {
		if (selectionPos1 == null) {
			return null;
		}
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.hitResult == null || client.hitResult.getType() != HitResult.Type.BLOCK) {
			return selectionPos1;
		}
		return ((BlockHitResult) client.hitResult).getBlockPos();
	}

	private static void tryCreateScreen(Minecraft client, LocalPlayer player) {
		if (!TeamVisibilitySettings.visible() || !isHoldingToolItem(player)) return;

		if (client.hitResult == null || client.hitResult.getType() != HitResult.Type.BLOCK) {
			MpsqCameraClient.LOGGER.info("[MPSQ Team] Kein Block anvisiert.");
			return;
		}

		BlockHitResult hit    = (BlockHitResult) client.hitResult;
		BlockPos       target = hit.getBlockPos();
		BlockState     state  = client.level.getBlockState(target);

		if (state.isAir()) {
			de.galaxushd.mpsqcamera.compat.PlayerMessages.send(player, 
					Component.translatable("gui.mpsqcamera.auswahl.luft"), true);
			return;
		}

		if (selectionPos1 == null) {
			// ── Erster Klick: Startpunkt markieren ───────────────────────────
			selectionPos1 = target.immutable();
			selectionSide = hit.getDirection();
			de.galaxushd.mpsqcamera.compat.PlayerMessages.send(player, 
					Component.translatable("gui.mpsqcamera.auswahl.pos1_gesetzt"), true);
			MpsqCameraClient.LOGGER.info("[MPSQ Team] Pos 1 markiert: {}", selectionPos1);
		} else {
			// ── Zweiter Klick: Endpunkt → automatisch bestätigen & Menü öffnen
			BlockPos pos1 = selectionPos1;
			BlockPos pos2 = target.immutable();
			Direction side = selectionSide;
			selectionPos1 = null; // Auswahl-Modus beenden
			selectionSide = null;

			MpsqCameraClient.LOGGER.info("[MPSQ Team] Pos 2 markiert: {} → Erstell-Menü öffnen", pos2);
			client.gui.setScreen(new BildschirmErstellenScreen(pos1, pos2, side));
		}
	}

	private static boolean isHoldingToolItem(LocalPlayer player) {
		Item toolItem = getConfiguredToolItem();
		return player.getMainHandItem().is(toolItem) || player.getOffhandItem().is(toolItem);
	}

	private static Item getConfiguredToolItem() {
		Identifier identifier = Identifier.tryParse(ModConfig.toolItemId);
		if (identifier != null && BuiltInRegistries.ITEM.containsKey(identifier)) {
			return BuiltInRegistries.ITEM.getValue(identifier);
		}
		return Items.INK_SAC;
	}

	private static final class ViewSession {
		private final BlockPos sourceAnchor;
		private UUID cameraId;
		private final Vec3 originPos;
		private final ResourceKey<Level> originDimension;
		private final Entity previousCameraEntity;
		private final CameraType previousPerspective;
		private final ArmorStand cameraEntity;
		private float viewYaw;
		private float viewPitch;
		private final List<UUID> mobileCameraIds;

		private ViewSession(BlockPos sourceAnchor, UUID cameraId, Vec3 originPos,
				ResourceKey<Level> originDimension, Entity previousCameraEntity,
				CameraType previousPerspective, ArmorStand cameraEntity,
				float viewYaw, float viewPitch, List<UUID> mobileCameraIds) {
			this.sourceAnchor = sourceAnchor;
			this.cameraId = cameraId;
			this.originPos = originPos;
			this.originDimension = originDimension;
			this.previousCameraEntity = previousCameraEntity;
			this.previousPerspective = previousPerspective;
			this.cameraEntity = cameraEntity;
			this.viewYaw = viewYaw;
			this.viewPitch = viewPitch;
			this.mobileCameraIds = List.copyOf(mobileCameraIds);
		}

		private BlockPos sourceAnchor() { return sourceAnchor; }
		private Vec3 originPos() { return originPos; }
		private ResourceKey<Level> originDimension() { return originDimension; }
		private Entity previousCameraEntity() { return previousCameraEntity; }
		private CameraType previousPerspective() { return previousPerspective; }
		private ArmorStand cameraEntity() { return cameraEntity; }
		private UUID cameraId() { return cameraId; }
		private boolean isMobile() { return sourceAnchor == null; }
		private List<UUID> mobileCameraIds() { return mobileCameraIds; }
		private void setCameraId(UUID cameraId) { this.cameraId = cameraId; }

		private void applyMouseLook(double cursorDeltaX, double cursorDeltaY) {
			// Matches Minecraft's normal Entity.changeLookDirection scale, but
			// retains the values independently from ArmorStandEntity's own tick.
			viewYaw = Mth.wrapDegrees(viewYaw + (float) cursorDeltaX * 0.15F);
			viewPitch = Mth.clamp(viewPitch + (float) cursorDeltaY * 0.15F, -90.0F, 90.0F);
			applyViewRotation();
		}

		private void setViewRotation(float yaw, float pitch) {
			viewYaw = yaw;
			viewPitch = Mth.clamp(pitch, -90.0F, 90.0F);
			applyViewRotation();
		}

		private void applyViewRotation() {
			applyProxyRotation(cameraEntity, viewYaw, viewPitch);
		}
	}
}
