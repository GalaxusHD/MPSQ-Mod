package de.galaxushd.mpsqcamera;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.dimaskama.mcef.api.MCEFApi;
import net.dimaskama.mcef.api.MCEFBrowser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Adapts asynchronously initialized MCEF Modern browsers to Minecraft texture IDs. */
final class McefModernBridge {
    private static CompletableFuture<MCEFApi> initialization;
    private static final Map<MCEFBrowser, Identifier> textures = new IdentityHashMap<>();
    private static long nextTexture;

    private McefModernBridge() {}

    static boolean ready() {
        return initialization != null && initialization.isDone()
                && !initialization.isCompletedExceptionally() && !initialization.isCancelled();
    }

    static boolean initialize() {
        if (initialization == null) {
            initialization = MCEFApi.initialize().getFuture();
            initialization.whenComplete((api, error) -> {
                if (error != null) MpsqCameraClient.LOGGER.error("MCEF Modern konnte nicht starten", error);
            });
        }
        return ready();
    }

    static MCEFBrowser createBrowser(String url, boolean transparent) {
        if (!ready()) throw new IllegalStateException("MCEF Modern wird noch initialisiert");
        return initialization.join().createBrowser(url, transparent);
    }

    static Identifier texture(MCEFBrowser browser) {
        if (browser.getTextureView() == null) return null;
        return textures.computeIfAbsent(browser, value -> {
            Identifier id = Identifier.fromNamespaceAndPath("mpsqcamera", "browser/" + nextTexture++);
            Minecraft.getInstance().getTextureManager().register(id, new BrowserTexture(value));
            return id;
        });
    }

    static void close(MCEFBrowser browser) {
        Identifier id = textures.remove(browser);
        if (id != null) Minecraft.getInstance().getTextureManager().release(id);
        browser.close();
    }

    /** The browser owns its GPU allocation; Minecraft must not close it on texture reload. */
    private static final class BrowserTexture extends AbstractTexture {
        private final MCEFBrowser browser;
        private BrowserTexture(MCEFBrowser browser) { this.browser = browser; }
        @Override public GpuTexture getTexture() { return browser.getTexture(); }
        @Override public GpuTextureView getTextureView() { return browser.getTextureView(); }
        @Override public GpuSampler getSampler() {
            return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }
        @Override public void close() { /* Ownership remains with MCEFBrowser. */ }
    }
}
