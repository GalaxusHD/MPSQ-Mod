package de.galaxushd.mpsqcamera;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.Item;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

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
	private static KeyBinding hauptMenuKey;

	private static ViewSession activeViewSession;
	private static boolean exitSnapshotPending;

	private ScreenCreationManager() {}

	public static void initialize() {
		hauptMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.mpsqcamera.hauptmenu",
				GLFW.GLFW_KEY_K,
				"category.mpsqcamera.main"
		));

		ClientTickEvents.START_CLIENT_TICK.register(ScreenCreationManager::onStartTick);
		ClientTickEvents.END_CLIENT_TICK.register(ScreenCreationManager::onEndTick);
	}

	private static void onStartTick(MinecraftClient client) {
		if (activeViewSession == null || client.player == null || client.world == null || client.options == null) {
			return;
		}

		suppressMovementAndInteraction(client);
		// Do not teleport the real player every tick. On a multiplayer server that
		// causes repeated position/rotation corrections, which in turn makes the
		// client camera snap back or spin. The disabled movement keys and zero
		// velocity keep the body in place without fighting the server.
		client.player.setVelocity(Vec3d.ZERO);
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

	private static void onEndTick(MinecraftClient client) {
		if (client.options == null) return;

		boolean usePressed = client.options.useKey.isPressed();
		boolean escPressed = InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_ESCAPE);
		boolean previousCameraPressed = InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT);
		boolean nextCameraPressed = InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT);

		if (client.player == null || client.world == null) {
			if (activeViewSession != null) {
				exitViewMode(client, false);
			}
			wasUsePressedLastTick = usePressed;
			wasEscPressedLastTick = escPressed;
			return;
		}

		ClientPlayerEntity player = client.player;
		showSelectionDimensions(player);

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
		while (hauptMenuKey.wasPressed()) {
			if (client.currentScreen == null) {
				client.setScreen(new ModConfigScreen());
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
		MinecraftClient client = MinecraftClient.getInstance();

		// The selected camera is always scoped to the selected screen by
		// ScreenCameraStore. While a local view is active, move that view and
		// the optional remote broadcaster to the newly selected camera as well.
		if (activeViewSession != null && activeViewSession.sourceAnchor() != null) {
			LocalCameraStore.find(camera).ifPresent(data -> {
				Vec3d position = cameraPosition(client, data);
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
				client.player.sendMessage(Text.literal(data.name() + " " + current + "/" + total), true);
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
		MinecraftClient client = MinecraftClient.getInstance();
		LocalCameraStore.find(cameraId).ifPresent(camera -> {
			Vec3d position = cameraPosition(client, camera);
			if (position == null || CameraSafety.isStaticCameraBlocked(client, camera)) return;
			placeCameraProxy(activeViewSession.cameraEntity(), position,
					cameraYaw(client, camera), cameraPitch(client, camera));
			activeViewSession.setViewRotation(cameraYaw(client, camera), cameraPitch(client, camera));
			activeViewSession.setCameraId(cameraId);
			RemoteCameraFrameManager.startPublishing(cameraId);
			if (client.player != null) {
				client.player.sendMessage(Text.literal(camera.name() + " " + (next + 1) + "/" + cameras.size()), true);
			}
		});
	}

	private static UUID activeCameraId(LocalScreenStore.LocalScreenData screen) {
		UUID active = ScreenCameraStore.active(screen.id());
		return active != null ? active : screen.cameraId();
	}

	private static PlayerEntity bodycamWearer(MinecraftClient client, LocalCameraStore.CameraData camera) {
		if (camera.kind() != LocalCameraStore.CameraKind.BODYCAM || client.world == null) return null;
		if (camera.wearerId() != null) {
			PlayerEntity byUuid = client.world.getPlayerByUuid(camera.wearerId());
			if (byUuid != null) return byUuid;
		}
		if (camera.wearerName() != null && !camera.wearerName().isBlank()) {
			for (PlayerEntity candidate : client.world.getPlayers()) {
				if (camera.wearerName().equalsIgnoreCase(candidate.getName().getString())) return candidate;
			}
		}
		return null;
	}

	private static Vec3d cameraPosition(MinecraftClient client, LocalCameraStore.CameraData camera) {
		PlayerEntity wearer = bodycamWearer(client, camera);
		return wearer != null ? wearer.getEyePos() : camera.position();
	}

	private static float cameraYaw(MinecraftClient client, LocalCameraStore.CameraData camera) {
		PlayerEntity wearer = bodycamWearer(client, camera);
		return wearer != null ? wearer.getYaw() : camera.yaw();
	}

	private static float cameraPitch(MinecraftClient client, LocalCameraStore.CameraData camera) {
		PlayerEntity wearer = bodycamWearer(client, camera);
		return wearer != null ? wearer.getPitch() : camera.pitch();
	}

	private static void updateBodycamProxy(MinecraftClient client, ViewSession session) {
		LocalCameraStore.find(session.cameraId).ifPresent(camera -> {
			if (camera.kind() != LocalCameraStore.CameraKind.BODYCAM) return;
			Vec3d position = cameraPosition(client, camera);
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

	private static void handlePassiveScreenLook(MinecraftClient client, ClientPlayerEntity player) {
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

		Vec3d cameraPos = cameraPosition(client, cameraScreen.get());
		if (!isCameraAreaLoadedByAnyPlayer(client, cameraPos)) {
			sendOfflineHint(player);
		}
	}

	private static void sendOfflineHint(ClientPlayerEntity player) {
		long now = System.currentTimeMillis();
		if (now - lastOfflineHintMs >= OFFLINE_HINT_INTERVAL_MS) {
			player.sendMessage(Text.translatable("status.mpsqcamera.camera_offline"), true);
			lastOfflineHintMs = now;
		}
	}

	private static void sendBlockedHint(ClientPlayerEntity player) {
		long now = System.currentTimeMillis();
		if (now - lastOfflineHintMs >= OFFLINE_HINT_INTERVAL_MS) {
			player.sendMessage(Text.translatable("status.mpsqcamera.camera_blocked"), true);
			lastOfflineHintMs = now;
		}
	}

	private static void tryEnterViewMode(MinecraftClient client, ClientPlayerEntity player) {
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

		Vec3d cameraPos = cameraPosition(client, cameraScreen);
		if (!isCameraAreaLoadedByAnyPlayer(client, cameraPos)) {
			sendOfflineHint(player);
			return;
		}

		beginViewMode(client, player, cameraScreen, screen.pos1().toImmutable(), List.of());
	}

	/** Opens the normal camera view from the player-bound mobile clock. */
	public static boolean enterMobileView(List<UUID> linkedCameraIds) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || activeViewSession != null) return false;
		if (!TeamVisibilitySettings.visible()) return false;
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

	private static void beginViewMode(MinecraftClient client, ClientPlayerEntity player,
			LocalCameraStore.CameraData cameraScreen, BlockPos sourceAnchor, List<UUID> mobileCameraIds) {
		UUID cameraId = cameraScreen.id();
		Vec3d cameraPos = cameraPosition(client, cameraScreen);
		if (cameraPos == null) return;

		// Die gespeicherte Position ist der Blickpunkt der Kamera. Die genaue
		// Augenhoehe des unsichtbaren Proxis wird deshalb nach dem Erzeugen
		// dynamisch ausgeglichen, statt durch einen festen Wert geschaetzt.
		ArmorStandEntity cameraEntity = new ArmorStandEntity(client.world, cameraPos.x, cameraPos.y, cameraPos.z);
		cameraEntity.setId(nextCameraProxyEntityId++);
		cameraEntity.setNoGravity(true);
		cameraEntity.setInvisible(true);
		placeCameraProxy(cameraEntity, cameraPos, cameraYaw(client, cameraScreen), cameraPitch(client, cameraScreen));
		// A camera entity must be present in the client world. An untracked entity
		// has no stable previous render position, which makes Minecraft interpolate
		// the view between unrelated places and causes the visible flicker.
		client.world.addEntity(cameraEntity);

		Entity previousCamera = client.getCameraEntity();
		Perspective previousPerspective = client.options.getPerspective();

		activeViewSession = new ViewSession(
				sourceAnchor,
				cameraId,
				player.getPos(),
				client.world.getRegistryKey(),
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
		client.options.setPerspective(Perspective.FIRST_PERSON);
		CameraHologramManager.hideForCameraView();
		RemoteCameraFrameManager.startPublishing(cameraId);
		player.sendMessage(Text.translatable("status.mpsqcamera.view_enter"), true);
	}

	private static void handleActiveViewSession(
			MinecraftClient client,
			ClientPlayerEntity player,
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

	private static boolean isSessionStillValid(MinecraftClient client, ClientPlayerEntity player, ViewSession session) {
		if (player.isRemoved() || !player.isAlive()) return false;
		if (client.world == null) return false;
		if (!client.world.getRegistryKey().equals(session.originDimension())) return false;
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

	private static void suppressMovementAndInteraction(MinecraftClient client) {
		client.options.forwardKey.setPressed(false);
		client.options.backKey.setPressed(false);
		client.options.leftKey.setPressed(false);
		client.options.rightKey.setPressed(false);
		client.options.jumpKey.setPressed(false);
		client.options.sneakKey.setPressed(false);
		client.options.sprintKey.setPressed(false);
		client.options.attackKey.setPressed(false);
		client.options.useKey.setPressed(false);
	}

	private static void lockPlayerPosition(ClientPlayerEntity player, Vec3d originPos) {
		player.setVelocity(Vec3d.ZERO);
	}

	private static void exitViewMode(MinecraftClient client, boolean notify) {
		if (activeViewSession == null || exitSnapshotPending) return;
		ViewSession session = activeViewSession;

		// For version 1.0 a screen retains one clean image from the saved
		// camera direction after leaving. Keep the proxy camera active until
		// WorldRenderEvents captured that one frame.
		LocalCameraStore.find(session.cameraId).ifPresentOrElse(camera -> {
			Vec3d position = cameraPosition(client, camera);
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

	private static void finishExitViewMode(MinecraftClient client, ViewSession session, boolean notify) {
		if (activeViewSession != session) return;
		activeViewSession = null;
		exitSnapshotPending = false;
		RemoteCameraFrameManager.stopPublishing();
		CameraHologramManager.showAfterCameraView();

		if (client.options != null) {
			client.options.setPerspective(session.previousPerspective());
		}

		Entity fallbackCamera = client.player == null ? null : client.player;
		client.setCameraEntity(session.previousCameraEntity() != null ? session.previousCameraEntity() : fallbackCamera);
		session.cameraEntity().discard();

		if (client.player != null) {
			lockPlayerPosition(client.player, session.originPos());
			if (notify) {
				client.player.sendMessage(Text.translatable("status.mpsqcamera.view_exit"), true);
			}
		}
	}

	private static Optional<LocalScreenStore.LocalScreenData> getScreenAtCrosshair(MinecraftClient client) {
		if (client.player == null) return Optional.empty();

		double reach = client.player.getBlockInteractionRange();
		Vec3d eye = client.player.getCameraPosVec(1.0f);
		Vec3d direction = client.player.getRotationVec(1.0f);

		// Prüfe alle Bildschirme via Ray-AABB-Test (keine Block-Abhängigkeit mehr)
		LocalScreenStore.LocalScreenData closest = null;
		double closestDist = Double.MAX_VALUE;

		for (LocalScreenStore.LocalScreenData screen : LocalScreenStore.getAllScreens()) {
			if (testRayBox(eye, direction, reach, screen.pos1(), screen.pos2())) {
				double dist = screen.pos1().getSquaredDistance(eye.x, eye.y, eye.z);
				if (dist < closestDist) {
					closestDist = dist;
					closest = screen;
				}
			}
		}

		if (closest != null) return Optional.of(closest);

		// Fallback: Legacy-Prüfung via Block-Treffer
		if (client.crosshairTarget instanceof BlockHitResult hit
				&& client.crosshairTarget.getType() == HitResult.Type.BLOCK) {
			return LocalScreenStore.findByAnchor(hit.getBlockPos());
		}

		return Optional.empty();
	}

	/**
	 * Prüft ob ein Strahl (Ursprung + Richtung, max. Länge reach) die Bounding-Box
	 * eines Bildschirms (pos1..pos2) schneidet (Slab-Methode / AABB-Ray-Test).
	 */
	private static boolean testRayBox(Vec3d eye, Vec3d dir, double reach,
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

	private static boolean isCameraAreaLoadedByAnyPlayer(MinecraftClient client, Vec3d cameraPos) {
		// The integrated singleplayer server belongs to this client. There is no
		// second mod user required to make one of its cameras available.
		if (client.isInSingleplayer()) {
			return true;
		}
		double maxDistSq = CAMERA_LOAD_RANGE * CAMERA_LOAD_RANGE;
		for (PlayerEntity worldPlayer : client.world.getPlayers()) {
			if (worldPlayer.squaredDistanceTo(cameraPos) <= maxDistSq) {
				return true;
			}
		}
		return false;
	}

	private static void showSelectionDimensions(ClientPlayerEntity player) {
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
		player.sendMessage(Text.translatable("gui.mpsqcamera.selection.dimensions", width, height), true);
	}

	private static Vec3d toCameraProxyPos(ArmorStandEntity proxy, Vec3d cameraEyePos) {
		double eyeOffset = proxy.getEyeY() - proxy.getY();
		return cameraEyePos.add(0.0, -eyeOffset, 0.0);
	}

	private static void placeCameraProxy(ArmorStandEntity proxy, Vec3d cameraEyePos, float yaw, float pitch) {
		Vec3d bodyPos = toCameraProxyPos(proxy, cameraEyePos);
		proxy.refreshPositionAndAngles(bodyPos.x, bodyPos.y, bodyPos.z, yaw, pitch);
		applyProxyRotation(proxy, yaw, pitch);
	}

	private static void applyProxyRotation(ArmorStandEntity proxy, float yaw, float pitch) {
		proxy.setYaw(yaw);
		proxy.setPitch(pitch);
		// Armor stands keep body and head yaw separately. Minecraft's camera
		// follows the head direction, so all three values must agree for a full
		// horizontal (green axis) rotation.
		proxy.setBodyYaw(yaw);
		proxy.setHeadYaw(yaw);
	}

	static BlockPos getSelectionPos1() {
		return selectionPos1;
	}

	static BlockPos getSelectionPos2Preview() {
		if (selectionPos1 == null) {
			return null;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.crosshairTarget == null || client.crosshairTarget.getType() != HitResult.Type.BLOCK) {
			return selectionPos1;
		}
		return ((BlockHitResult) client.crosshairTarget).getBlockPos();
	}

	private static void tryCreateScreen(MinecraftClient client, ClientPlayerEntity player) {
		if (!isHoldingToolItem(player)) return;

		if (client.crosshairTarget == null || client.crosshairTarget.getType() != HitResult.Type.BLOCK) {
			MpsqCameraClient.LOGGER.info("[MPSQ Team] Kein Block anvisiert.");
			return;
		}

		BlockHitResult hit    = (BlockHitResult) client.crosshairTarget;
		BlockPos       target = hit.getBlockPos();
		BlockState     state  = client.world.getBlockState(target);

		if (state.isAir()) {
			player.sendMessage(
					Text.translatable("gui.mpsqcamera.auswahl.luft"), true);
			return;
		}

		if (selectionPos1 == null) {
			// ── Erster Klick: Startpunkt markieren ───────────────────────────
			selectionPos1 = target.toImmutable();
			selectionSide = hit.getSide();
			player.sendMessage(
					Text.translatable("gui.mpsqcamera.auswahl.pos1_gesetzt"), true);
			MpsqCameraClient.LOGGER.info("[MPSQ Team] Pos 1 markiert: {}", selectionPos1);
		} else {
			// ── Zweiter Klick: Endpunkt → automatisch bestätigen & Menü öffnen
			BlockPos pos1 = selectionPos1;
			BlockPos pos2 = target.toImmutable();
			Direction side = selectionSide;
			selectionPos1 = null; // Auswahl-Modus beenden
			selectionSide = null;

			MpsqCameraClient.LOGGER.info("[MPSQ Team] Pos 2 markiert: {} → Erstell-Menü öffnen", pos2);
			client.setScreen(new BildschirmErstellenScreen(pos1, pos2, side));
		}
	}

	private static boolean isHoldingToolItem(ClientPlayerEntity player) {
		Item toolItem = getConfiguredToolItem();
		return player.getMainHandStack().isOf(toolItem) || player.getOffHandStack().isOf(toolItem);
	}

	private static Item getConfiguredToolItem() {
		Identifier identifier = Identifier.tryParse(ModConfig.toolItemId);
		if (identifier != null && Registries.ITEM.containsId(identifier)) {
			return Registries.ITEM.get(identifier);
		}
		return Items.INK_SAC;
	}

	private static final class ViewSession {
		private final BlockPos sourceAnchor;
		private UUID cameraId;
		private final Vec3d originPos;
		private final RegistryKey<World> originDimension;
		private final Entity previousCameraEntity;
		private final Perspective previousPerspective;
		private final ArmorStandEntity cameraEntity;
		private float viewYaw;
		private float viewPitch;
		private final List<UUID> mobileCameraIds;

		private ViewSession(BlockPos sourceAnchor, UUID cameraId, Vec3d originPos,
				RegistryKey<World> originDimension, Entity previousCameraEntity,
				Perspective previousPerspective, ArmorStandEntity cameraEntity,
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
		private Vec3d originPos() { return originPos; }
		private RegistryKey<World> originDimension() { return originDimension; }
		private Entity previousCameraEntity() { return previousCameraEntity; }
		private Perspective previousPerspective() { return previousPerspective; }
		private ArmorStandEntity cameraEntity() { return cameraEntity; }
		private UUID cameraId() { return cameraId; }
		private boolean isMobile() { return sourceAnchor == null; }
		private List<UUID> mobileCameraIds() { return mobileCameraIds; }
		private void setCameraId(UUID cameraId) { this.cameraId = cameraId; }

		private void applyMouseLook(double cursorDeltaX, double cursorDeltaY) {
			// Matches Minecraft's normal Entity.changeLookDirection scale, but
			// retains the values independently from ArmorStandEntity's own tick.
			viewYaw = MathHelper.wrapDegrees(viewYaw + (float) cursorDeltaX * 0.15F);
			viewPitch = MathHelper.clamp(viewPitch + (float) cursorDeltaY * 0.15F, -90.0F, 90.0F);
			applyViewRotation();
		}

		private void setViewRotation(float yaw, float pitch) {
			viewYaw = yaw;
			viewPitch = MathHelper.clamp(pitch, -90.0F, 90.0F);
			applyViewRotation();
		}

		private void applyViewRotation() {
			applyProxyRotation(cameraEntity, viewYaw, viewPitch);
		}
	}
}
