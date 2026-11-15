package de.galaxushd.mpsqcamera;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

/** Alpha-tested copies of status artwork; source resources remain unchanged. */
final class ScreenStatusTextures {
    // The supplied artwork contains isolated background residue below this alpha.
    // Apply this before GPU sampling so it cannot turn into bright screen speckles.
    private static final int MIN_ALPHA = 80;
    private static final Map<Identifier, Identifier> CACHE = new HashMap<>();

    private ScreenStatusTextures() { }

    static void initialize() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return Identifier.fromNamespaceAndPath(MpsqCameraClient.MOD_ID, "screen_status_textures");
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager manager) {
                        var textures = Minecraft.getInstance().getTextureManager();
                        CACHE.forEach((source, runtime) -> {
                            if (!source.equals(runtime)) textures.release(runtime);
                        });
                        CACHE.clear();
                    }
                });
    }

    static Identifier resolve(Identifier source) {
        return CACHE.computeIfAbsent(source, ScreenStatusTextures::load);
    }

    static int brighten(int pixel) {
        int alpha=pixel>>>24;
        if(alpha<MIN_ALPHA)return 0;
        int r=Math.min(255,Math.round(((pixel>>>16)&255)*1.6f));
        int g=Math.min(255,Math.round(((pixel>>>8)&255)*1.6f));
        int b=Math.min(255,Math.round((pixel&255)*1.6f));
        return (alpha<<24)|(r<<16)|(g<<8)|b;
    }
    private static Identifier load(Identifier source) {
        var client = Minecraft.getInstance();
        NativeImage image = null;
        DynamicTexture texture = null;
        Identifier runtime = Identifier.fromNamespaceAndPath(MpsqCameraClient.MOD_ID, "screen_status_runtime/" + source.getPath());
        try (InputStream stream = client.getResourceManager().open(source)) {
            image = NativeImage.read(stream);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int pixel = image.getPixel(x, y);
                    image.setPixel(x, y, brighten(pixel));
                }
            }
            texture = new DynamicTexture(() -> "MPSQ screen status", image);
            image = null; // The texture now owns the native image.
            client.getTextureManager().register(runtime, texture);
            texture.upload();
            return runtime;
        } catch (Exception error) {
            if (texture != null) client.getTextureManager().release(runtime);
            if (image != null) image.close();
            MpsqCameraClient.LOGGER.warn("Bildschirmtitel konnte nicht aufbereitet werden: {}", source, error);
            return source;
        }
    }
}
