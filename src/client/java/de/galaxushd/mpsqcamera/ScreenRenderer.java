package de.galaxushd.mpsqcamera;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import de.galaxushd.mpsqcamera.compat.WorldRenderContext;
import de.galaxushd.mpsqcamera.compat.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import de.galaxushd.mpsqcamera.compat.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;


public final class ScreenRenderer {
private static final double RENDER_RANGE = 64.0;
    private static final double SURFACE_OFFSET = 0.003;
    private static final double FRAME_THICKNESS = 0.075;
    private static final double DECORATION_OFFSET = 0.002;
    private static final int STATUS_OVERLAY_ALPHA = 255;
    private static final Identifier INACTIVE_OVERLAY = Identifier.fromNamespaceAndPath("mpsqcamera", "textures/screens/screen_inactive.png");
    private static final Identifier REDSTONE_BUTTON_OVERLAY = Identifier.fromNamespaceAndPath("mpsqcamera", "textures/screens/screen_button_press.png");
    private static final Identifier OFFLINE_OVERLAY = Identifier.fromNamespaceAndPath("mpsqcamera", "textures/screens/screen_offline.png");

    private static final int FRAME_RED = 48;
    private static final int FRAME_GREEN = 52;
    private static final int FRAME_BLUE = 58;

    private ScreenRenderer() {
    }

    public static void initialize() {
        ScreenStatusTextures.initialize();
        WorldRenderEvents.LAST.register(ScreenRenderer::render);
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, hit) ->
                !(hit instanceof net.minecraft.world.phys.BlockHitResult blockHit)
                        || !screenOccludesBlock(context.camera().getPos(), blockHit.getLocation()));
    }

    /** Cancel only outlines whose sight line crosses the visible screen face first. */
    static boolean screenOccludesBlock(Vec3 camera, Vec3 target) {return targetedScreen(camera,target)!=null;}
    static LocalScreenStore.LocalScreenData targetedScreen(Vec3 camera, Vec3 target) {
        LocalScreenStore.LocalScreenData chosen=null;double nearest=Double.POSITIVE_INFINITY;
        for (var screen : LocalScreenStore.getAllScreens()) {
            var first = screen.pos1();
            var second = screen.pos2();
            if (first.distToLowCornerSqr(camera.x, camera.y, camera.z) > MpsqRenderRange.squared()) continue;
            double minX = Math.min(first.getX(), second.getX());
            double minY = Math.min(first.getY(), second.getY());
            double minZ = Math.min(first.getZ(), second.getZ());
            double maxX = Math.max(first.getX(), second.getX()) + 1.0;
            double maxY = Math.max(first.getY(), second.getY()) + 1.0;
            double maxZ = Math.max(first.getZ(), second.getZ()) + 1.0;
            int axis;
            double plane, sign;
            String front = ScreenAccessStore.front(screen.id());
            switch (front == null ? "NORTH" : front) {
                case "SOUTH" -> { axis = 2; sign = 1; plane = maxZ + SURFACE_OFFSET; }
                case "WEST" -> { axis = 0; sign = -1; plane = minX - SURFACE_OFFSET; }
                case "EAST" -> { axis = 0; sign = 1; plane = maxX + SURFACE_OFFSET; }
                case "UP" -> { axis = 1; sign = 1; plane = maxY + SURFACE_OFFSET; }
                case "DOWN" -> { axis = 1; sign = -1; plane = minY - SURFACE_OFFSET; }
                default -> { axis = 2; sign = -1; plane = minZ - SURFACE_OFFSET; }
            }
            double start = axis == 0 ? camera.x : axis == 1 ? camera.y : camera.z;
            double end = axis == 0 ? target.x : axis == 1 ? target.y : target.z;
            if ((start - plane) * sign <= 0 || Math.abs(end - start) < 1.0e-9) continue;
            double t = (plane - start) / (end - start);
            if (t < 0 || t > 1) continue;
            Vec3 crossing = camera.add(target.subtract(camera).scale(t));
            boolean inside = switch (axis) {
                case 0 -> crossing.y >= minY && crossing.y <= maxY && crossing.z >= minZ && crossing.z <= maxZ;
                case 1 -> crossing.x >= minX && crossing.x <= maxX && crossing.z >= minZ && crossing.z <= maxZ;
                default -> crossing.x >= minX && crossing.x <= maxX && crossing.y >= minY && crossing.y <= maxY;
            };
            if (inside&&t<nearest){nearest=t;chosen=screen;}
        }
        return chosen;
    }

    public static boolean handleRightClick(){
        var c=Minecraft.getInstance();
        if(c.player==null||c.level==null||c.gui.screen()!=null||!c.options.keyShift.isDown()||!TeamVisibilitySettings.inArea()||ScreenCreationManager.isCameraViewActive())return false;
        var eye=c.player.getEyePosition();
        var end=c.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit&&hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK?hit.getLocation():eye.add(c.player.getViewVector(1).scale(6));
        var screen=targetedScreen(eye,end);if(screen==null)return false;
        c.gui.setScreen(new BildschirmDetailScreen(null,screen.id().toString(),screen.name(),ScreenAccessStore.isOwner(screen.id())));return true;
    }

    private static void render(WorldRenderContext context) { if(!TeamVisibilitySettings.visible())return;
        MultiBufferSource.BufferSource consumers = context.consumers();
        PoseStack matrices = context.matrixStack();

        if (consumers == null || matrices == null) {
            return;
        }

        Vec3 camera = context.camera().getPos();

        matrices.pushPose();
        // Subtract the camera before converting to float, preserving the small
        // layer separation even at large world coordinates.

        for (LocalScreenStore.LocalScreenData screen : LocalScreenStore.getAllScreens()) { if(screen.inputType()==LocalScreenStore.ScreenInputType.CAMERA&&!TeamVisibilitySettings.inArea())continue;
            BlockPos first = screen.pos1();
            BlockPos second = screen.pos2();

            if (first.distToLowCornerSqr(camera.x, camera.y, camera.z)
                    > MpsqRenderRange.squared()) {
                continue;
            }

            double x1 = Math.min(first.getX(), second.getX()) - camera.x;
            double y1 = Math.min(first.getY(), second.getY()) - camera.y;
            double z1 = Math.min(first.getZ(), second.getZ()) - camera.z;

            double x2 = Math.max(first.getX(), second.getX()) + 1.0 - camera.x;
            double y2 = Math.max(first.getY(), second.getY()) + 1.0 - camera.y;
            double z2 = Math.max(first.getZ(), second.getZ()) + 1.0 - camera.z;

            boolean cameraScreen = screen.inputType() == LocalScreenStore.ScreenInputType.CAMERA;
            boolean mayViewCamera = TeamStateStore.self().map(TeamProfile::canViewCameras).orElse(false);
            java.util.UUID activeCamera = cameraScreen ? ScreenCameraStore.active(screen.id()) : null;
            // Older records and freshly-created screens may not have reached the
            // per-screen cache yet. The primary camera is still a valid fallback.
            if (cameraScreen && activeCamera == null) {
                activeCamera = screen.cameraId();
            }
            boolean blockedCamera = cameraScreen && LocalCameraStore.find(activeCamera)
                    .map(cameraData -> CameraSafety.isStaticCameraBlocked(Minecraft.getInstance(), cameraData))
                    .orElse(false);
            Identifier texture = cameraScreen && (!mayViewCamera || blockedCamera)
                    ? null
                    : (cameraScreen ? RemoteCameraFrameManager.texture(activeCamera) : CinemaBrowserManager.texture(screen.id()));
            CinemaBrowserManager.ScreenStatus screenStatus = cameraScreen
                    ? (activeCamera == null ? CinemaBrowserManager.ScreenStatus.OFFLINE
                    : !mayViewCamera ? CinemaBrowserManager.ScreenStatus.INACTIVE
                    : (blockedCamera ? CinemaBrowserManager.ScreenStatus.BLOCKED
                    : (texture == null ? CinemaBrowserManager.ScreenStatus.INACTIVE : CinemaBrowserManager.ScreenStatus.NONE)))
                    : CinemaBrowserManager.status(screen);

            var tv=MpsqTvFurniture.placement(screen.id());
            if(tv!=null){
                Vec3 a=MpsqTvFurniture.worldPoint(tv,23,1,7.97).subtract(camera),b=MpsqTvFurniture.worldPoint(tv,-7,1,7.97).subtract(camera),
                     d=MpsqTvFurniture.worldPoint(tv,23,19,7.97).subtract(camera),e=MpsqTvFurniture.worldPoint(tv,-7,19,7.97).subtract(camera);
                drawPlane(matrices,consumers,texture,screenStatus,a.x,a.y,a.z,b.x,b.y,b.z,e.x,e.y,e.z,d.x,d.y,d.z,false);
                continue;
            }
            drawScreenFace(
                    matrices,
                    consumers,
                    ScreenAccessStore.front(screen.id()),
                    texture,
                    screenStatus,
                    x1, y1, z1,
                    x2, y2, z2
            );
        }

        consumers.endBatch();
        matrices.popPose();
    }

    private static void drawScreenFace(
            PoseStack matrices,
            MultiBufferSource consumers,
            String front,
            Identifier browserTexture,
            CinemaBrowserManager.ScreenStatus status,
            double x1, double y1, double z1,
            double x2, double y2, double z2
    ) {
        if ("SOUTH".equals(front)) {
            drawPlane(matrices, consumers, browserTexture, status,
                    x1, y1, z2 + SURFACE_OFFSET,
                    x2, y1, z2 + SURFACE_OFFSET,
                    x2, y2, z2 + SURFACE_OFFSET,
                    x1, y2, z2 + SURFACE_OFFSET);
            return;
        }

        if ("WEST".equals(front)) {
            drawPlane(matrices, consumers, browserTexture, status,
                    x1 - SURFACE_OFFSET, y1, z1,
                    x1 - SURFACE_OFFSET, y1, z2,
                    x1 - SURFACE_OFFSET, y2, z2,
                    x1 - SURFACE_OFFSET, y2, z1);
            return;
        }

        if ("EAST".equals(front)) {
            drawPlane(matrices, consumers, browserTexture, status,
                    x2 + SURFACE_OFFSET, y1, z2,
                    x2 + SURFACE_OFFSET, y1, z1,
                    x2 + SURFACE_OFFSET, y2, z1,
                    x2 + SURFACE_OFFSET, y2, z2);
            return;
        }

        if ("UP".equals(front)) {
            drawPlane(matrices, consumers, browserTexture, status,
                    x1, y2 + SURFACE_OFFSET, z1,
                    x1, y2 + SURFACE_OFFSET, z2,
                    x2, y2 + SURFACE_OFFSET, z2,
                    x2, y2 + SURFACE_OFFSET, z1);
            return;
        }

        if ("DOWN".equals(front)) {
            drawPlane(matrices, consumers, browserTexture, status,
                    x2, y1 - SURFACE_OFFSET, z1,
                    x2, y1 - SURFACE_OFFSET, z2,
                    x1, y1 - SURFACE_OFFSET, z2,
                    x1, y1 - SURFACE_OFFSET, z1);
            return;
        }

        // NORTH ist der Standard für ältere Bildschirme.
        drawPlane(matrices, consumers, browserTexture, status,
                x2, y1, z1 - SURFACE_OFFSET,
                x1, y1, z1 - SURFACE_OFFSET,
                x1, y2, z1 - SURFACE_OFFSET,
                x2, y2, z1 - SURFACE_OFFSET);
    }

    private static void drawPlane(
            PoseStack matrices,
            MultiBufferSource consumers,
            Identifier browserTexture,
            CinemaBrowserManager.ScreenStatus status,
            double ax, double ay, double az,
            double bx, double by, double bz,
            double cx, double cy, double cz,
            double dx, double dy, double dz
    ) {
        drawPlane(matrices,consumers,browserTexture,status,ax,ay,az,bx,by,bz,cx,cy,cz,dx,dy,dz,true);
    }
    private static void drawPlane(PoseStack matrices,MultiBufferSource consumers,Identifier browserTexture,CinemaBrowserManager.ScreenStatus status,
            double ax,double ay,double az,double bx,double by,double bz,double cx,double cy,double cz,double dx,double dy,double dz,boolean frame){
        // A layer switch flushes the shared buffer; reacquire it for each screen.
        VertexConsumer vertices = consumers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.debugQuads());
        // Schwarze Bildschirmfläche.
        if (browserTexture == null) {
            offlineBacking(matrices, vertices, ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz, frame);
            // Submit the backing before the non-depth-writing title decal.
            if (consumers instanceof MultiBufferSource.BufferSource immediate) {
                immediate.endBatch(net.minecraft.client.renderer.rendertype.RenderTypes.debugQuads());
            }
            vertices = consumers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.debugQuads());
            Identifier statusOverlay = statusOverlay(status);
            if (statusOverlay != null) {
                Vec3 h = new Vec3(bx - ax, by - ay, bz - az).normalize();
                Vec3 v = new Vec3(dx - ax, dy - ay, dz - az).normalize();
                Vec3 offset = h.cross(v).scale(DECORATION_OFFSET);
                h = h.scale(FRAME_THICKNESS);
                v = v.scale(FRAME_THICKNESS);
                Vec3 a = new Vec3(ax, ay, az).add(offset).add(h).add(v);
                Vec3 b = new Vec3(bx, by, bz).add(offset).subtract(h).add(v);
                Vec3 c = new Vec3(cx, cy, cz).add(offset).subtract(h).subtract(v);
                Vec3 d = new Vec3(dx, dy, dz).add(offset).add(h).subtract(v);
                // The title is an unlit decal, not depth-writing geometry. Its
                // transparent pixels must not occlude the screen or its frame.
                VertexConsumer overlayVertices = consumers.getBuffer(
                        net.minecraft.client.renderer.rendertype.RenderTypes.breezeEyes(ScreenStatusTextures.resolve(statusOverlay)));
                texturedQuad(matrices, overlayVertices,
                        a.x, a.y, a.z, b.x, b.y, b.z,
                        c.x, c.y, c.z, d.x, d.y, d.z,
                        STATUS_OVERLAY_ALPHA);
            } else {
                drawStatusText(matrices, vertices, status, ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz);
            }
        } else {
            VertexConsumer textureVertices = MinecraftRenderCompat.textureVertices(consumers, browserTexture);
            if (textureVertices == null) {
                vertices = consumers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.debugQuads());
                offlineBacking(matrices, vertices, ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz, frame);
                drawStatusText(matrices, vertices, CinemaBrowserManager.ScreenStatus.ERROR,
                        ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz);
            } else {
                texturedQuad(matrices, textureVertices, ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz, 255);
            }
        }

        if(!frame)return;
        Vec3 bottomLeft = new Vec3(ax, ay, az);
        Vec3 bottomRight = new Vec3(bx, by, bz);
        Vec3 topRight = new Vec3(cx, cy, cz);
        Vec3 topLeft = new Vec3(dx, dy, dz);

        Vec3 horizontal = bottomRight.subtract(bottomLeft);
        Vec3 vertical = topLeft.subtract(bottomLeft);

        if (horizontal.lengthSqr() == 0.0 || vertical.lengthSqr() == 0.0) {
            return;
        }

        Vec3 horizontalBorder = horizontal.normalize().scale(FRAME_THICKNESS);
        Vec3 verticalBorder = vertical.normalize().scale(FRAME_THICKNESS);
        // Keep the frame in front of the black surface. Otherwise the depth buffer
        // alternates between both coplanar quads while the camera is moving.
        Vec3 frameOffset = horizontal.cross(vertical).normalize().scale(DECORATION_OFFSET);
        bottomLeft = bottomLeft.add(frameOffset);
        bottomRight = bottomRight.add(frameOffset);
        topRight = topRight.add(frameOffset);
        topLeft = topLeft.add(frameOffset);

        // getEntityCutoutNoCull(texture) above can flush the previously
        // obtained debug-quad buffer.  Never reuse that stale consumer for
        // the frame, otherwise a second client can hit "Not building!" while
        // joining a world with an already visible screen.
        VertexConsumer frameVertices = consumers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.debugQuads());

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

    private static void offlineBacking(PoseStack matrices, VertexConsumer out,
            double ax,double ay,double az,double bx,double by,double bz,double cx,double cy,double cz,double dx,double dy,double dz,boolean frame) {
        Vec3 a=new Vec3(ax,ay,az),b=new Vec3(bx,by,bz),c=new Vec3(cx,cy,cz),d=new Vec3(dx,dy,dz);
        if(frame){
            Vec3 horizontal=b.subtract(a),vertical=d.subtract(a);
            Vec3 h=horizontal.normalize().scale(Math.min(FRAME_THICKNESS,horizontal.length()/2));
            Vec3 v=vertical.normalize().scale(Math.min(FRAME_THICKNESS,vertical.length()/2));
            a=a.add(h).add(v);b=b.subtract(h).add(v);c=c.subtract(h).subtract(v);d=d.add(h).subtract(v);
        }
        // The opaque backing occupies only the display opening. It never covers
        // frame geometry, regardless of translucent-pass sorting while offline.
        quad(matrices,out,a.x,a.y,a.z,b.x,b.y,b.z,c.x,c.y,c.z,d.x,d.y,d.z,0,0,0,255);
    }

    private static Identifier statusOverlay(CinemaBrowserManager.ScreenStatus status) {
        return status.overlayTexture();
    }

    private static void drawStatusText(
            PoseStack matrices,
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

        Vec3 baseOrigin = new Vec3(ax, ay, az);
        Vec3 horizontal = new Vec3(bx, by, bz).subtract(baseOrigin);
        Vec3 vertical = new Vec3(dx, dy, dz).subtract(baseOrigin);

        double width = horizontal.length();
        double height = vertical.length();

        if (width <= 0.0 || height <= 0.0) {
            return;
        }

        horizontal = horizontal.normalize();
        vertical = vertical.normalize();
        // Keep status pixels slightly in front of the black plane to prevent z-fighting while moving.
        Vec3 origin = baseOrigin.add(horizontal.cross(vertical).scale(DECORATION_OFFSET));

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

                    Vec3 a = origin.add(horizontal.scale(u)).add(vertical.scale(v));
                    Vec3 b = a.add(horizontal.scale(cellSize - 2 * inset));
                    Vec3 d = a.add(vertical.scale(cellSize - 2 * inset));
                    Vec3 c = b.add(vertical.scale(cellSize - 2 * inset));

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
            PoseStack matrices,
            VertexConsumer vertices,
            Vec3 a, Vec3 b, Vec3 c, Vec3 d,
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
            PoseStack matrices,
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
            PoseStack matrices,
            VertexConsumer vertices,
            double x, double y, double z,
            int red, int green, int blue, int alpha
    ) {
        vertices.addVertex(matrices.last(), (float) x, (float) y, (float) z)
                .setColor(red, green, blue, alpha);
    }

    private static void texturedQuad(
            PoseStack matrices,
            VertexConsumer vertices,
            double ax, double ay, double az,
            double bx, double by, double bz,
            double cx, double cy, double cz,
            double dx, double dy, double dz,
            int alpha
    ) {
        texturedVertex(matrices, vertices, ax, ay, az, 0.0f, 1.0f, alpha);
        texturedVertex(matrices, vertices, bx, by, bz, 1.0f, 1.0f, alpha);
        texturedVertex(matrices, vertices, cx, cy, cz, 1.0f, 0.0f, alpha);
        texturedVertex(matrices, vertices, dx, dy, dz, 0.0f, 0.0f, alpha);
    }

    private static void texturedVertex(
            PoseStack matrices,
            VertexConsumer vertices,
            double x, double y, double z,
            float u, float v, int alpha
    ) {
        vertices.addVertex(matrices.last(), (float) x, (float) y, (float) z)
                .setColor(255, 255, 255, alpha)
                .setUv(u, v)
                // A value of 0 selects Minecraft's red damage-overlay pixel.
                // Browser textures must use the neutral overlay instead.
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(0.0f, 0.0f, 1.0f);
    }

    /** Obtains a texture render layer across the small 1.21.x mapping differences. */
    private static final class MinecraftRenderCompat {
        private static boolean warningLogged;

        private MinecraftRenderCompat() {
        }

        private static VertexConsumer textureVertices(MultiBufferSource consumers, Identifier texture) {
            try {
                return consumers.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(texture));
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
