package de.galaxushd.mpsqcamera;

import com.cinemamod.mcef.MCEF;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;
import org.cef.browser.CefBrowser;
import org.cef.handler.CefAudioHandler;
import org.cef.misc.CefAudioParameters;
import org.cef.misc.DataPointer;
import org.lwjgl.openal.AL10;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Bridges MCEF's float PCM callback to Minecraft's already-active OpenAL device. */
public final class CinemaAudioManager {
    /* A larger queue absorbs short render/tick stalls without audible gaps. */
    private static final int MAX_PENDING_PACKETS = 256;
    private static final int MAX_QUEUED_BUFFERS = 48;
    // Chromium's YouTube path sends 44.1 kHz PCM on the MCEF build that
    // reports params=null. Playing that at 48 kHz makes voices too high.
    private static final int FALLBACK_SAMPLE_RATE = 44_100;
    private static final Map<CefBrowser, AudioStream> STREAMS = new ConcurrentHashMap<>();
    private static final Map<CefBrowser, AudioRoute> BROWSER_ROUTES = new ConcurrentHashMap<>();
    private static volatile AudioRoute lastRegisteredRoute = AudioRoute.CINEMA;
    /* Some MCEF/JCEF builds call audio callbacks without the browser or parameters. */
    private static final AtomicReference<AudioStream> FALLBACK_STREAM = new AtomicReference<>();
    private static final AtomicLong LAST_AUDIBLE_AUDIO_NANOS = new AtomicLong();
    private static final long AUDIO_TAIL_NANOS = 500_000_000L;
    private static boolean initialized;

    private CinemaAudioManager() {
    }

    public static void initialize() {
        if (initialized || !MCEF.isInitialized()) {
            return;
        }

        try {
            MCEF.getClient().addAudioHandler(new BrowserAudioHandler());
            ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
            initialized = true;
        } catch (RuntimeException exception) {
            MpsqCameraClient.LOGGER.warn("MCEF-Audio konnte nicht eingerichtet werden", exception);
        }
    }

    public static void clear() {
        for (AudioStream stream : STREAMS.values()) {
            stream.close();
        }
        STREAMS.clear();
        BROWSER_ROUTES.clear();
        lastRegisteredRoute = AudioRoute.CINEMA;
        AudioStream fallback = FALLBACK_STREAM.getAndSet(null);
        if (fallback != null) fallback.close();
        LAST_AUDIBLE_AUDIO_NANOS.set(0L);
    }

    /** True while browser music is audible, including a short queue-drain tail. */
    public static boolean isMusicAudioActive() {
        long lastAudible = LAST_AUDIBLE_AUDIO_NANOS.get();
        long elapsed = System.nanoTime() - lastAudible;
        return lastAudible != 0L && elapsed >= 0L && elapsed < AUDIO_TAIL_NANOS;
    }
    /** Stops all MCEF audio immediately. Required for MCEF builds whose callback has no browser identity. */
    public static void stopAll() {
        clear();
    }

    /** Associates a hidden MCEF browser with the Minecraft volume category it should follow. */
    public static void registerBrowser(CefBrowser browser, AudioRoute route) {
        if (browser == null || route == null) return;
        BROWSER_ROUTES.put(browser, route);
        lastRegisteredRoute = route;
        AudioStream stream = STREAMS.get(browser);
        if (stream != null) stream.setRoute(route);
        AudioStream fallback = FALLBACK_STREAM.get();
        if (fallback != null) fallback.setRoute(route);
    }

    /** Removes a browser's route and stops its stream when MCEF supplied browser identity. */
    public static void unregisterBrowser(CefBrowser browser) {
        if (browser == null) return;
        BROWSER_ROUTES.remove(browser);
        AudioStream stream = STREAMS.remove(browser);
        if (stream != null) stream.close();
        if (BROWSER_ROUTES.isEmpty()) {
            lastRegisteredRoute = AudioRoute.CINEMA;
            AudioStream fallback = FALLBACK_STREAM.getAndSet(null);
            if (fallback != null) fallback.close();
        } else {
            lastRegisteredRoute = BROWSER_ROUTES.values().stream().reduce((first, second) -> second).orElse(AudioRoute.CINEMA);
            AudioStream fallback = FALLBACK_STREAM.get();
            if (fallback != null) fallback.setRoute(lastRegisteredRoute);
        }
    }

    private static void tick() {
        for (AudioStream stream : STREAMS.values()) {
            stream.pump();
        }
        // Some MCEF builds identify the browser as null; those packets go to the fallback stream.
        AudioStream fallback = FALLBACK_STREAM.get();
        if (fallback != null) {
            fallback.pump();
        }
    }

    private static final class BrowserAudioHandler implements CefAudioHandler {
        @Override
        public boolean getAudioParameters(CefBrowser browser, CefAudioParameters params) {
            return true;
        }

        @Override
        public void onAudioStreamStarted(CefBrowser browser, CefAudioParameters params, int channels) {
            int sampleRate = params == null ? FALLBACK_SAMPLE_RATE : params.sampleRate;
            AudioRoute route = browser == null ? lastRegisteredRoute : BROWSER_ROUTES.getOrDefault(browser, lastRegisteredRoute);
            AudioStream next = new AudioStream(sampleRate, channels, route);
            if (browser == null) {
                AudioStream previous = FALLBACK_STREAM.getAndSet(next);
                if (previous != null) previous.close();
                return;
            }
            AudioStream previous = STREAMS.put(browser, next);
            if (previous != null) {
                previous.close();
            }
        }

        @Override
        public void onAudioStreamPacket(CefBrowser browser, DataPointer data, int frames, long pts) {
            AudioStream stream = browser == null ? FALLBACK_STREAM.get() : STREAMS.get(browser);
            if (stream != null) {
                stream.accept(data, frames);
            }
        }

        @Override
        public void onAudioStreamStopped(CefBrowser browser) {
            AudioStream stream = browser == null ? FALLBACK_STREAM.getAndSet(null) : STREAMS.remove(browser);
            if (stream != null) {
                stream.close();
            }
        }

        @Override
        public void onAudioStreamError(CefBrowser browser, String text) {
            MpsqCameraClient.LOGGER.warn("Kino-Audiofehler: {}", text);
        }
    }

    private static final class AudioStream {
        private final int sampleRate;
        private final int channels;
        private volatile AudioRoute route;
        private final ConcurrentLinkedQueue<ByteBuffer> pendingPackets = new ConcurrentLinkedQueue<>();
        private int sourceId;
        private boolean closed;

        private AudioStream(int sampleRate, int channels, AudioRoute route) {
            this.sampleRate = Math.max(8_000, sampleRate);
            this.channels = Math.max(1, channels);
            this.route = route == null ? AudioRoute.CINEMA : route;
        }

        private void setRoute(AudioRoute route) {
            if (route != null) this.route = route;
        }

        private void accept(DataPointer data, int frames) {
            if (closed || frames <= 0 || pendingPackets.size() >= MAX_PENDING_PACKETS) {
                return;
            }

            try {
                DataPointer pointerArray = data.forCapacity(channels * Long.BYTES);
                DataPointer left = pointerArray.getData(0).forCapacity(frames * Float.BYTES).withAlignment(2);
                DataPointer right = channels > 1
                        ? pointerArray.getData(1).forCapacity(frames * Float.BYTES).withAlignment(2)
                        : left;

                ByteBuffer pcm = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder());
                boolean audible = false;
                for (int index = 0; index < frames; index++) {
                    float leftSample = left.getFloat(index);
                    float rightSample = right.getFloat(index);
                    audible |= Math.abs(leftSample) > 0.001f || Math.abs(rightSample) > 0.001f;
                    pcm.putShort(toPcm16(leftSample));
                    pcm.putShort(toPcm16(rightSample));
                }
                if (audible) LAST_AUDIBLE_AUDIO_NANOS.set(System.nanoTime());
                pcm.flip();
                pendingPackets.offer(pcm);
            } catch (RuntimeException exception) {
                MpsqCameraClient.LOGGER.warn("Kino-Audiodaten konnten nicht gelesen werden", exception);
            }
        }

        private void pump() {
            if (closed) {
                return;
            }

            try {
                if (sourceId == 0) {
                    sourceId = AL10.alGenSources();
                    AL10.alSourcei(sourceId, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
                }

            int processed = AL10.alGetSourcei(sourceId, AL10.AL_BUFFERS_PROCESSED);
                while (processed-- > 0) {
                    AL10.alDeleteBuffers(AL10.alSourceUnqueueBuffers(sourceId));
                }

                while (AL10.alGetSourcei(sourceId, AL10.AL_BUFFERS_QUEUED) < MAX_QUEUED_BUFFERS) {
                    ByteBuffer packet = pendingPackets.poll();
                    if (packet == null) {
                        break;
                    }
                    int bufferId = AL10.alGenBuffers();
                    AL10.alBufferData(bufferId, AL10.AL_FORMAT_STEREO16, packet, sampleRate);
                    AL10.alSourceQueueBuffers(sourceId, bufferId);
                }

                if (AL10.alGetSourcei(sourceId, AL10.AL_BUFFERS_QUEUED) > 0
                        && AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                    AL10.alSourcePlay(sourceId);
                }
            } catch (RuntimeException exception) {
                MpsqCameraClient.LOGGER.warn("Kino-Audio konnte nicht ausgegeben werden", exception);
                close();
            }

                // Raw MCEF/OpenAL audio bypasses Minecraft's mixer, so apply the
                // master and selected category sliders here on every client tick.
                AL10.alSourcef(sourceId, AL10.AL_GAIN, routeGain(route));
        }

        private void close() {
            closed = true;
            pendingPackets.clear();
            if (sourceId == 0) {
                return;
            }
            try {
                AL10.alSourceStop(sourceId);
                int queued = AL10.alGetSourcei(sourceId, AL10.AL_BUFFERS_QUEUED);
                while (queued-- > 0) {
                    AL10.alDeleteBuffers(AL10.alSourceUnqueueBuffers(sourceId));
                }
                AL10.alDeleteSources(sourceId);
            } catch (RuntimeException ignored) {
                // Minecraft may already have disposed its sound context during shutdown.
            }
            sourceId = 0;
        }

        private static short toPcm16(float sample) {
            float clamped = Math.max(-1.0f, Math.min(1.0f, sample));
            return (short) Math.round(clamped * Short.MAX_VALUE);
        }
    }

    private static float routeGain(AudioRoute route) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options == null) return 0.0f;
        float master = client.options.getSoundVolume(SoundCategory.MASTER);
        float category = switch (route) {
            case CINEMA -> ModConfig.volume;
            case AMBIENT -> client.options.getSoundVolume(SoundCategory.AMBIENT);
            case BLOCKS -> client.options.getSoundVolume(SoundCategory.BLOCKS);
        };
        return Math.max(0.0f, Math.min(1.0f, master * category));
    }

    public enum AudioRoute { CINEMA, AMBIENT, BLOCKS }
}
