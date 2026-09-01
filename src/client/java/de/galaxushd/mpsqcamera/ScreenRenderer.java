package de.galaxushd.mpsqcamera;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;


public final class ScreenRenderer {
    private static final double RENDER_RANGE = 64.0;
    private static final double SURFACE_OFFSET = 0.003;
    private static final double FRAME_THICKNESS = 0.075;

    private static final int FRAME_RED = 48;
    private static final int FRAME_GREEN = 52;
    private static final int FRAME_BLUE = 58;

    private ScreenRenderer() {
    }

    public static void initialize() {
        WorldRenderEvents.AFTER_ENTITIES.register(ScreenRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        VertexConsumerProvider consumers = context.consumers();
        MatrixStack matrices = context.matrixStack();

        if (consumers == null || matrices == null) {
            return;
        }

        Vec3d camera = context.camera().getPos();

        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        VertexConsumer vertices = consumers.getBuffer(RenderLayer.getDebugQuads());

        for (LocalScreenStore.LocalScreenData screen : LocalScreenStore.getAllScreens()) {
            BlockPos first = screen.pos1();
            BlockPos second = screen.pos2();

            if (first.getSquaredDistance(camera.x, camera.y, camera.z)
                    > RENDER_RANGE * RENDER_RANGE) {
                continue;
            }

            double x1 = Math.min(first.getX(), second.getX());
            double y1 = Math.min(first.getY(), second.getY());
            double z1 = Math.min(first.getZ(), second.getZ());

            double x2 = Math.max(first.getX(), second.getX()) + 1.0;
            double y2 = Math.max(first.getY(), second.getY()) + 1.0;
            double z2 = Math.max(first.getZ(), second.getZ()) + 1.0;

            boolean cameraScreen = screen.inputType() == LocalScreenStore.ScreenInputType.CAMERA;
            boolean mayViewCamera = TeamStateStore.self().map(TeamProfile::canViewCameras).orElse(false);
            java.util.UUID activeCamera = cameraScreen ? ScreenCameraStore.active(screen.id()) : null;
            // Older records and freshly-created screens may not have reached the
            // per-screen cache yet. The primary camera is still a valid fallback.
            if (cameraScreen && activeCamera == null) {
                activeCamera = screen.cameraId();
            }
            boolean blockedCamera = cameraScreen && LocalCameraStore.find(activeCamera)
                    .map(cameraData -> CameraSafety.isStaticCameraBlocked(MinecraftClient.getInstance(), cameraData))
                    .orElse(false);
            Identifier texture = cameraScreen && (!mayViewCamera || blockedCamera)
                    ? null
                    : (cameraScreen ? RemoteCameraFrameManager.texture(activeCamera) : CinemaBrowserManager.texture(screen.id()));
            CinemaBrowserManager.ScreenStatus screenStatus = cameraScreen
                    ? (!mayViewCamera ? CinemaBrowserManager.ScreenStatus.OFFLINE
                    : (blockedCamera ? CinemaBrowserManager.ScreenStatus.BLOCKED
                    : (texture == null ? CinemaBrowserManager.ScreenStatus.OFFLINE : CinemaBrowserManager.ScreenStatus.NONE)))
                    : CinemaBrowserManager.status(screen);

            drawScreenFace(
                    matrices,
                    consumers,
                    vertices,
                    ScreenAccessStore.front(screen.id()),
                    texture,
                    screenStatus,
                    x1, y1, z1,
                    x2, y2, z2
            );
        }

        matrices.pop();
    }

    private static void drawScreenFace(
            MatrixStack matrices,
            VertexConsumerProvider consumers,
            VertexConsumer vertices,
            String front,
            Identifier browserTexture,
            CinemaBrowserManager.ScreenStatus status,
            double x1, double y1, double z1,
            double x2, double y2, double z2
    ) {
        if ("SOUTH".equals(front)) {
            drawPlane(matrices, consumers, vertices, browserTexture, status,
                    x1, y1, z2 + SURFACE_OFFSET,
                    x2, y1, z2 + SURFACE_OFFSET,
                    x2, y2, z2 + SURFACE_OFFSET,
                    x1, y2, z2 + SURFACE_OFFSET);
            return;
        }

        if ("WEST".equals(front)) {
            drawPlane(matrices, consumers, vertices, browserTexture, status,
                    x1 - SURFACE_OFFSET, y1, z1,
                    x1 - SURFACE_OFFSET, y1, z2,
                    x1 - SURFACE_OFFSET, y2, z2,
                    x1 - SURFACE_OFFSET, y2, z1);
            return;
        }

        if ("EAST".equals(front)) {
            drawPlane(matrices, consumers, vertices, browserTexture, status,
                    x2 + SURFACE_OFFSET, y1, z2,
                    x2 + SURFACE_OFFSET, y1, z1,
                    x2 + SURFACE_OFFSET, y2, z1,
                    x2 + SURFACE_OFFSET, y2, z2);
            return;
        }

        if ("UP".equals(front)) {
            drawPlane(matrices, consumers, vertices, browserTexture, status,
                    x1, y2 + SURFACE_OFFSET, z1,
                    x1, y2 + SURFACE_OFFSET, z2,
                    x2, y2 + SURFACE_OFFSET, z2,
                    x2, y2 + SURFACE_OFFSET, z1);
            return;
        }

        if ("DOWN".equals(front)) {
            drawPlane(matrices, consumers, vertices, browserTexture, status,
                    x2, y1 - SURFACE_OFFSET, z1,
                    x2, y1 - SURFACE_OFFSET, z2,
                    x1, y1 - SURFACE_OFFSET, z2,
                    x1, y1 - SURFACE_OFFSET, z1);
            return;
        }

        // NORTH ist der Standard für ältere Bildschirme.
        drawPlane(matrices, consumers, vertices, browserTexture, status,
                x2, y1, z1 - SURFACE_OFFSET,
                x1, y1, z1 - SURFACE_OFFSET,
                x1, y2, z1 - SURFACE_OFFSET,
                x2, y2, z1 - SURFACE_OFFSET);
    }

    private static void drawPlane(
            MatrixStack matrices,
            VertexConsumerProvider consumers,
            VertexConsumer vertices,
            Identifier browserTexture,
            CinemaBrowserManager.ScreenStatus status,
            double ax, double ay, double az,
            double bx, double by, double bz,
            double cx, double cy, double cz,
            double dx, double dy, double dz
    ) {
        // Schwarze Bildschirmfläche.
        if (browserTexture == null) {
            quad(matrices, vertices, ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz, 0, 0, 0, 235);
            drawStatusText(matrices, vertices, status, ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz);
        } else {
            VertexConsumer textureVertices = MinecraftRenderCompat.textureVertices(consumers, browserTexture);
            if (textureVertices == null) {
                quad(matrices, vertices, ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz, 0, 0, 0, 235);
                drawStatusText(matrices, vertices, CinemaBrowserManager.ScreenStatus.ERROR,
                        ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz);
            } else {
                texturedQuad(matrices, textureVertices, ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz);
            }
        }

        Vec3d bottomLeft = new Vec3d(ax, ay, az);
        Vec3d bottomRight = new Vec3d(bx, by, bz);
        Vec3d topRight = new Vec3d(cx, cy, cz);
        Vec3d topLeft = new Vec3d(dx, dy, dz);

        Vec3d horizontal = bottomRight.subtract(bottomLeft);
        Vec3d vertical = topLeft.subtract(bottomLeft);

        if (horizontal.lengthSquared() == 0.0 || vertical.lengthSquared() == 0.0) {
            return;
        }

        Vec3d horizontalBorder = horizontal.normalize().multiply(FRAME_THICKNESS);
        Vec3d verticalBorder = vertical.normalize().multiply(FRAME_THICKNESS);
        // Keep the frame in front of the black surface. Otherwise the depth buffer
        // alternates between both coplanar quads while the camera is moving.
        Vec3d frameOffset = horizontal.crossProduct(vertical).normalize().multiply(0.0015);
        bottomLeft = bottomLeft.add(frameOffset);
        bottomRight = bottomRight.add(frameOffset);
        topRight = topRight.add(frameOffset);
        topLeft = topLeft.add(frameOffset);

        // getEntityCutoutNoCull(texture) above can flush the previously
        // obtained debug-quad buffer.  Never reuse that stale consumer for
        // the frame, otherwise a second client can hit "Not building!" while
        // joining a world with an already visible screen.
        VertexConsumer frameVertices = consumers.getBuffer(RenderLayer.getDebugQuads());

        coloredQuad(matrices, frameVertices,
                bottomLeft,
                bottomRight,
                bottomRight.add(verticalBorder),
                bottomLeft.add(verticalBorder),
                FRAME_RED, FRAME_GREEN, FRAME_BLUE);

        coloredQuad(matrices, frameVertices,
                topLeft.subtract(verticalBorder),
                topRight.subtract(verticalBorder),
                topRight,
                topLeft,
                FRAME_RED, FRAME_GREEN, FRAME_BLUE);

        coloredQuad(matrices, frameVertices,
                bottomLeft,
                bottomLeft.add(horizontalBorder),
                topLeft.add(horizontalBorder),
                topLeft,
                FRAME_RED, FRAME_GREEN, FRAME_BLUE);

        coloredQuad(matrices, frameVertices,
                bottomRight.subtract(horizontalBorder),
                bottomRight,
                topRight,
                topRight.subtract(horizontalBorder),
                FRAME_RED, FRAME_GREEN, FRAME_BLUE);
    }

    private static void drawStatusText(
            MatrixStack matrices,
            VertexConsumer vertices,
            CinemaBrowserManager.ScreenStatus status,
            double ax, double ay, double az,
            double bx, double by, double bz,
            double cx, double cy, double cz,
            double dx, double dy, double dz
    ) {
        if (status == CinemaBrowserManager.ScreenStatus.NONE) {
            return;
        }

        Vec3d baseOrigin = new Vec3d(ax, ay, az);
        Vec3d horizontal = new Vec3d(bx, by, bz).subtract(baseOrigin);
        Vec3d vertical = new Vec3d(dx, dy, dz).subtract(baseOrigin);

        double width = horizontal.length();
        double height = vertical.length();

        if (width <= 0.0 || height <= 0.0) {
            return;
        }

        horizontal = horizontal.normalize();
        vertical = vertical.normalize();
        // Keep status pixels slightly in front of the black plane to prevent z-fighting while moving.
        Vec3d origin = baseOrigin.add(horizontal.crossProduct(vertical).multiply(0.001));

        String text = status.label();
        double cellSize = Math.min(width / (text.length() * 4.0 + 1.0), height / 7.0);

        if (cellSize < 0.025) {
            return;
        }

        double totalWidth = (text.length() * 4.0 - 1.0) * cellSize;
        double left = (width - totalWidth) / 2.0;
        double bottom = (height - 5.0 * cellSize) / 2.0;
        double inset = cellSize * 0.12;

        for (int character = 0; character < text.length(); character++) {
            String[] pixels = glyph(text.charAt(character));

            for (int row = 0; row < pixels.length; row++) {
                for (int column = 0; column < pixels[row].length(); column++) {
                    if (pixels[row].charAt(column) != '1') {
                        continue;
                    }

                    double u = left + (character * 4.0 + column) * cellSize + inset;
                    double v = bottom + (4 - row) * cellSize + inset;

                    Vec3d a = origin.add(horizontal.multiply(u)).add(vertical.multiply(v));
                    Vec3d b = a.add(horizontal.multiply(cellSize - 2 * inset));
                    Vec3d d = a.add(vertical.multiply(cellSize - 2 * inset));
                    Vec3d c = b.add(vertical.multiply(cellSize - 2 * inset));

                    coloredQuad(
                            matrices,
                            vertices,
                            a, b, c, d,
                            status.red(),
                            status.green(),
                            status.blue()
                    );
                }
            }
        }
    }

    private static String[] glyph(char character) {
        return switch (character) {
            case 'A' -> new String[]{"010", "101", "111", "101", "101"};
            case 'D' -> new String[]{"110", "101", "101", "101", "110"};
            case 'E' -> new String[]{"111", "100", "110", "100", "111"};
            case 'F' -> new String[]{"111", "100", "110", "100", "100"};
            case 'H' -> new String[]{"101", "101", "111", "101", "101"};
            case 'I' -> new String[]{"111", "010", "010", "010", "111"};
            case 'K' -> new String[]{"101", "101", "110", "101", "101"};
            case 'L' -> new String[]{"100", "100", "100", "100", "111"};
            case 'N' -> new String[]{"101", "111", "111", "111", "101"};
            case 'O' -> new String[]{"010", "101", "101", "101", "010"};
            case 'R' -> new String[]{"110", "101", "110", "101", "101"};
            case 'T' -> new String[]{"111", "010", "010", "010", "010"};
            default -> new String[]{"000", "000", "000", "000", "000"};
        };
    }

    private static void coloredQuad(
            MatrixStack matrices,
            VertexConsumer vertices,
            Vec3d a, Vec3d b, Vec3d c, Vec3d d,
            int red, int green, int blue
    ) {
        quad(matrices, vertices,
                a.x, a.y, a.z,
                b.x, b.y, b.z,
                c.x, c.y, c.z,
                d.x, d.y, d.z,
                red, green, blue, 255);
    }

    private static void quad(
            MatrixStack matrices,
            VertexConsumer vertices,
            double ax, double ay, double az,
            double bx, double by, double bz,
            double cx, double cy, double cz,
            double dx, double dy, double dz,
            int red, int green, int blue, int alpha
    ) {
        vertex(matrices, vertices, ax, ay, az, red, green, blue, alpha);
        vertex(matrices, vertices, bx, by, bz, red, green, blue, alpha);
        vertex(matrices, vertices, cx, cy, cz, red, green, blue, alpha);
        vertex(matrices, vertices, dx, dy, dz, red, green, blue, alpha);
    }

    private static void vertex(
            MatrixStack matrices,
            VertexConsumer vertices,
            double x, double y, double z,
            int red, int green, int blue, int alpha
    ) {
        vertices.vertex(matrices.peek(), (float) x, (float) y, (float) z)
                .color(red, green, blue, alpha);
    }

    private static void texturedQuad(
            MatrixStack matrices,
            VertexConsumer vertices,
            double ax, double ay, double az,
            double bx, double by, double bz,
            double cx, double cy, double cz,
            double dx, double dy, double dz
    ) {
        texturedVertex(matrices, vertices, ax, ay, az, 0.0f, 1.0f);
        texturedVertex(matrices, vertices, bx, by, bz, 1.0f, 1.0f);
        texturedVertex(matrices, vertices, cx, cy, cz, 1.0f, 0.0f);
        texturedVertex(matrices, vertices, dx, dy, dz, 0.0f, 0.0f);
    }

    private static void texturedVertex(
            MatrixStack matrices,
            VertexConsumer vertices,
            double x, double y, double z,
            float u, float v
    ) {
        vertices.vertex(matrices.peek(), (float) x, (float) y, (float) z)
                .color(255, 255, 255, 255)
                .texture(u, v)
                // A value of 0 selects Minecraft's red damage-overlay pixel.
                // Browser textures must use the neutral overlay instead.
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(0xF000F0)
                .normal(0.0f, 0.0f, 1.0f);
    }

    /** Obtains a texture render layer across the small 1.21.x mapping differences. */
    private static final class MinecraftRenderCompat {
        private static boolean warningLogged;

        private MinecraftRenderCompat() {
        }

        private static VertexConsumer textureVertices(VertexConsumerProvider consumers, Identifier texture) {
            try {
                return consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
            } catch (RuntimeException exception) {
                if (!warningLogged) {
                    warningLogged = true;
                    MpsqCameraClient.LOGGER.warn("MCEF-Textur konnte nicht gerendert werden", exception);
                }
                return null;
            }
        }

    }
}
