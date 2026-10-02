package de.galaxushd.mpsqcamera;

import com.cinemamod.mcef.MCEF;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
import java.util.concurrent.atomic.AtomicReference;

/** Bridges MCEF's float PCM callback to Minecraft's already-active OpenAL device. */
public final class CinemaAudioManager {
    /* A larger queue absorbs short render/tick stalls without audible gaps. */
    private static final int MAX_PENDING_PACKETS = 256;
    private static final int MAX_QUEUED_BUFFERS = 24;
    private static final int MAX_QUEUED_AUDIO_MILLIS = 120;
    private static final int STARTUP_BUFFER_MILLIS = 80;
    private static final int MAX_STARTUP_WAIT_MILLIS = 500;
    private static final int MAX_PACKET_LATENESS_MILLIS = 40;
    // Chromium's YouTube path sends 44.1 kHz PCM on the MCEF build that
    // reports params=null. Playing that at 48 kHz makes voices too high.
    private static final int FALLBACK_SAMPLE_RATE = 44_100;
    private static final Map<CefBrowser, AudioStream> STREAMS = new ConcurrentHashMap<>();
    /* Some MCEF/JCEF builds call audio callbacks without the browser or parameters. */
    private static final AtomicReference<AudioStream> FALLBACK_STREAM = new AtomicReference<>();
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
        AudioStream fallback = FALLBACK_STREAM.getAndSet(null);
        if (fallback != null) fallback.close();
    }

    /** Stops all MCEF audio immediately. Required for MCEF builds whose callback has no browser identity. */
    public static void stopAll() {
        clear();
    }

    private static void tick() {
        for (AudioStream stream : STREAMS.values()) {
            stream.pump();
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
            AudioStream next = new AudioStream(sampleRate, channels);
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
                stream.accept(data, frames, pts);
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
        private final ConcurrentLinkedQueue<AudioPacket> pendingPackets = new ConcurrentLinkedQueue<>();
        private final Map<Integer, Integer> queuedBufferFrames = new ConcurrentHashMap<>();
        private volatile long firstPacketAtNanos;
        private int queuedFrames;
        private int sourceId;
        private boolean closed;
        private boolean started;

        private AudioStream(int sampleRate, int channels) {
            this.sampleRate = Math.max(8_000, sampleRate);
            this.channels = Math.max(1, channels);
        }

        private void accept(DataPointer data, int frames, long pts) {
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
                for (int index = 0; index < frames; index++) {
                    pcm.putShort(toPcm16(left.getFloat(index)));
                    pcm.putShort(toPcm16(right.getFloat(index)));
                }
                pcm.flip();
                long timestampMs = pts > 0L ? pts : System.currentTimeMillis();
                pendingPackets.offer(new AudioPacket(pcm, frames, timestampMs));
                if (firstPacketAtNanos == 0L) firstPacketAtNanos = System.nanoTime();
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
                    AL10.alSourcef(sourceId, AL10.AL_GAIN, Math.max(0.0f, Math.min(1.0f, ModConfig.volume)));
                    AL10.alSourcei(sourceId, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
                }

            int processed = AL10.alGetSourcei(sourceId, AL10.AL_BUFFERS_PROCESSED);
                while (processed-- > 0) {
                    int bufferId = AL10.alSourceUnqueueBuffers(sourceId);
                    queuedFrames -= queuedBufferFrames.getOrDefault(bufferId, 0);
                    queuedBufferFrames.remove(bufferId);
                    AL10.alDeleteBuffers(bufferId);
                }

                long maxQueuedFrames = (long) sampleRate * MAX_QUEUED_AUDIO_MILLIS / 1_000L;
                long nowMs = System.currentTimeMillis();
                while (AL10.alGetSourcei(sourceId, AL10.AL_BUFFERS_QUEUED) < MAX_QUEUED_BUFFERS
                        && queuedFrames < maxQueuedFrames) {
                    AudioPacket next = pendingPackets.peek();
                    if (next == null) break;
                    if (queuedFrames > 0 && queuedFrames + next.frames() > maxQueuedFrames) break;
                    AudioPacket packet = pendingPackets.poll();
                    if (packet == null) break;
                    long packetEndMs = packet.presentationTimeMs()
                            + packet.frames() * 1_000L / sampleRate;
                    // Do not replay stale chunks after a slow tick or video-specific startup stall.
                    if (packetEndMs < nowMs - MAX_PACKET_LATENESS_MILLIS) continue;
                    int bufferId = AL10.alGenBuffers();
                    AL10.alBufferData(bufferId, AL10.AL_FORMAT_STEREO16, packet.pcm(), sampleRate);
                    AL10.alSourceQueueBuffers(sourceId, bufferId);
                    queuedBufferFrames.put(bufferId, packet.frames());
                    queuedFrames += packet.frames();
                }

                int queued = AL10.alGetSourcei(sourceId, AL10.AL_BUFFERS_QUEUED);
                long bufferedNanos = queuedFrames * 1_000_000_000L / sampleRate;
                long firstPacketAt = firstPacketAtNanos;
                boolean startupBufferReady = bufferedNanos >= STARTUP_BUFFER_MILLIS * 1_000_000L
                        || (queuedFrames > 0 && firstPacketAt > 0L
                        && System.nanoTime() - firstPacketAt >= MAX_STARTUP_WAIT_MILLIS * 1_000_000L);
                if (queued > 0 && (started || startupBufferReady)
                        && AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                    AL10.alSourcePlay(sourceId);
                    started = true;
                }
            } catch (RuntimeException exception) {
                MpsqCameraClient.LOGGER.warn("Kino-Audio konnte nicht ausgegeben werden", exception);
                close();
            }

                // Keep the mod slider effective after the source was created.
                AL10.alSourcef(sourceId, AL10.AL_GAIN, Math.max(0.0f, Math.min(1.0f, ModConfig.volume)));
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

        private record AudioPacket(ByteBuffer pcm, int frames, long presentationTimeMs) { }
    }
}
