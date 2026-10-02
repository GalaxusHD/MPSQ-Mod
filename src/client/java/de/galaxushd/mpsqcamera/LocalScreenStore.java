package de.galaxushd.mpsqcamera;

import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Client cache for screens. The API is the persistent source of truth. */
public final class LocalScreenStore {
    private static final List<LocalScreenData> SCREENS = new ArrayList<>();
    private static final List<LocalGroupData> GROUPS = new ArrayList<>();
    private static final double LOAD_RANGE = 48.0;
    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private LocalScreenStore() { }

    public static void replaceAll(List<LocalScreenData> screens) {
        SCREENS.clear();
        SCREENS.addAll(screens);
        GROUPS.clear();
    }

    public static LocalScreenData addScreen(BlockPos anchor, Vec3d createdFrom) {
        return addScreenFromSelection(anchor, anchor, "Bildschirm", ScreenInputType.LINK, "", createdFrom);
    }

    public static LocalScreenData addScreenFromSelection(BlockPos pos1, BlockPos pos2, String name) {
        return addScreenFromSelection(pos1, pos2, name, ScreenInputType.LINK, "", new Vec3d(pos1.getX(), pos1.getY(), pos1.getZ()));
    }

    public static LocalScreenData addScreenFromSelection(
            BlockPos pos1, BlockPos pos2, String name, ScreenInputType inputType, String url, Vec3d createdFrom
    ) {
        LocalScreenData screen = new LocalScreenData(
                UUID.randomUUID(), pos1.toImmutable(), pos2.toImmutable(),
                name.isBlank() ? "Bildschirm" : name, createdFrom, inputType,
                url == null ? "" : url, null, null
        );
        SCREENS.add(screen);
        return screen;
    }

    public static void removeScreen(UUID id) {
        LocalScreenData screen = findById(id).orElse(null);
        if (screen == null) return;
        if (screen.groupId() != null) removeGroup(screen.groupId());
        else SCREENS.removeIf(item -> item.id().equals(id));
    }

    public static LocalGroupData createGroup(List<UUID> screenIds) {
        LocalGroupData group = new LocalGroupData(UUID.randomUUID(), generateGroupCode());
        GROUPS.add(group);
        for (int i = 0; i < SCREENS.size(); i++) {
            LocalScreenData screen = SCREENS.get(i);
            if (screenIds.contains(screen.id())) {
                SCREENS.set(i, new LocalScreenData(screen.id(), screen.pos1(), screen.pos2(), screen.name(),
                        screen.createdFrom(), screen.inputType(), screen.url(), screen.cameraId(), group.id()));
            }
        }
        return group;
    }

    public static void removeGroup(UUID groupId) {
        GROUPS.removeIf(group -> group.id().equals(groupId));
        SCREENS.removeIf(screen -> groupId.equals(screen.groupId()));
    }

    public static Optional<LocalGroupData> getGroupForScreen(UUID screenId) {
        LocalScreenData screen = findById(screenId).orElse(null);
        if (screen == null || screen.groupId() == null) return Optional.empty();
        return GROUPS.stream().filter(group -> group.id().equals(screen.groupId())).findFirst();
    }

    public static List<LocalGroupData> getAllGroups() { return List.copyOf(GROUPS); }
    public static List<LocalScreenData> getScreensInGroup(UUID groupId) {
        return SCREENS.stream().filter(screen -> groupId.equals(screen.groupId())).toList();
    }
    public static List<LocalScreenData> getAllScreens() { return List.copyOf(SCREENS); }
    public static List<LocalScreenData> getInRange(Vec3d playerPos) {
        return SCREENS.stream().filter(screen -> screen.pos1().getSquaredDistance(playerPos.x, playerPos.y, playerPos.z) <= LOAD_RANGE * LOAD_RANGE).toList();
    }
    public static Optional<LocalScreenData> findByAnchor(BlockPos anchor) {
        return SCREENS.stream().filter(screen -> screen.pos1().equals(anchor)).findFirst();
    }
    public static Optional<LocalScreenData> findById(UUID id) {
        return SCREENS.stream().filter(screen -> screen.id().equals(id)).findFirst();
    }
    public static Optional<LocalScreenData> findNearest(Vec3d pos, double maxDistance) {
        LocalScreenData result = null;
        double best = maxDistance * maxDistance;
        for (LocalScreenData screen : SCREENS) {
            double distance = screen.pos1().getSquaredDistance(pos.x, pos.y, pos.z);
            if (distance <= best) { best = distance; result = screen; }
        }
        return Optional.ofNullable(result);
    }

    public static void updateConfig(UUID id, ScreenInputType mode, String url, UUID cameraId) {
        for (int i = 0; i < SCREENS.size(); i++) {
            LocalScreenData screen = SCREENS.get(i);
            if (screen.id().equals(id)) {
                SCREENS.set(i, new LocalScreenData(screen.id(), screen.pos1(), screen.pos2(), screen.name(),
                        screen.createdFrom(), mode, url == null ? "" : url, cameraId, screen.groupId()));
                return;
            }
        }
    }

    private static String generateGroupCode() {
        StringBuilder code = new StringBuilder(6);
        for (int i = 0; i < 6; i++) code.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        return code.toString();
    }

    public enum ScreenInputType {
        LINK("Kino"), CAMERA("Kamera"), REDSTONE("MPSQ Redstone");
        private final String label;
        ScreenInputType(String label) { this.label = label; }
        public Text text() { return Text.literal(label); }
    }

    public enum DeleteBehavior {
        NIE("Nie"), CREATOR_OFFLINE("Wenn Creator off"), NICHT_GELADEN("Wenn nicht geladen");
        private final String label;
        DeleteBehavior(String label) { this.label = label; }
        public String getLabel() { return label; }
        public DeleteBehavior next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public record LocalScreenData(UUID id, BlockPos pos1, BlockPos pos2, String name, Vec3d createdFrom,
                                  ScreenInputType inputType, String url, UUID cameraId, UUID groupId) { }
    public record LocalGroupData(UUID id, String sharedCode) { }
}
