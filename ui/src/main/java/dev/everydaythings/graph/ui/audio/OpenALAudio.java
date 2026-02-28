package dev.everydaythings.graph.ui.audio;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.openal.*;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.stb.STBVorbis.*;

/**
 * OpenAL spatial audio engine.
 *
 * <p>Manages the OpenAL device, context, listener, sources, and buffers.
 * The listener position tracks the camera, and sources are positioned
 * in 3D space with distance-based attenuation.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * try (OpenALAudio audio = new OpenALAudio()) {
 *     audio.init();
 *
 *     // Per-frame: update listener to match camera
 *     audio.updateListener(eyeX, eyeY, eyeZ, atX, atY, atZ, upX, upY, upZ);
 *
 *     // Create a spatial source
 *     int src = audio.createSource("waterfall.wav", 5, 0, -3,
 *             1.0, 1.0, true, true, 1.0, 50.0);
 *     audio.loadWav("waterfall.wav", wavBytes);
 *     audio.play(src);
 * }
 * }</pre>
 *
 * @see dev.everydaythings.graph.ui.scene.spatial.Body.Audio
 */
public class OpenALAudio implements AutoCloseable {

    private static final Logger log = LogManager.getLogger(OpenALAudio.class);

    private long device;
    private long context;
    private boolean initialized;

    /** Content reference -> OpenAL buffer ID. */
    private final Map<String, Integer> bufferCache = new HashMap<>();

    /** Active OpenAL sources (for cleanup). */
    private final List<Integer> sources = new ArrayList<>();

    /**
     * Initialize the OpenAL device and context.
     *
     * <p>Opens the default audio device, creates a context, and sets
     * the distance model to inverse-distance-clamped (realistic falloff).
     *
     * @return true if initialization succeeded, false if OpenAL is unavailable
     */
    public boolean init() {
        try {
            device = alcOpenDevice((ByteBuffer) null);
            if (device == 0L) {
                log.warn("No OpenAL device available — audio disabled");
                return false;
            }

            context = alcCreateContext(device, (IntBuffer) null);
            if (context == 0L) {
                log.warn("Failed to create OpenAL context — audio disabled");
                alcCloseDevice(device);
                device = 0;
                return false;
            }

            alcMakeContextCurrent(context);
            AL.createCapabilities(ALC.createCapabilities(device));

            // Use inverse distance clamped for realistic 3D falloff
            alDistanceModel(AL_INVERSE_DISTANCE_CLAMPED);

            initialized = true;
            log.info("OpenAL initialized: {}", alcGetString(device, ALC_DEVICE_SPECIFIER));
            return true;
        } catch (Exception e) {
            log.warn("OpenAL initialization failed — audio disabled", e);
            return false;
        }
    }

    /**
     * Whether OpenAL was successfully initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }

    // ==================== Listener ====================

    /**
     * Update the listener position and orientation to match the camera.
     *
     * @param x   Listener position X
     * @param y   Listener position Y
     * @param z   Listener position Z
     * @param atX Look-at direction X
     * @param atY Look-at direction Y
     * @param atZ Look-at direction Z
     * @param upX Up vector X
     * @param upY Up vector Y
     * @param upZ Up vector Z
     */
    public void updateListener(double x, double y, double z,
                               double atX, double atY, double atZ,
                               double upX, double upY, double upZ) {
        if (!initialized) return;

        alListener3f(AL_POSITION, (float) x, (float) y, (float) z);
        alListener3f(AL_VELOCITY, 0f, 0f, 0f);

        // OpenAL orientation: 6 floats = {atX, atY, atZ, upX, upY, upZ}
        float[] orientation = {
                (float) atX, (float) atY, (float) atZ,
                (float) upX, (float) upY, (float) upZ
        };
        alListenerfv(AL_ORIENTATION, orientation);
    }

    // ==================== Sources ====================

    /**
     * Create a positioned audio source.
     *
     * <p>The source is created but not playing. Call {@link #play(int)}
     * to start playback. If a buffer has been loaded for the given {@code src},
     * it is automatically attached.
     *
     * @param src         Content reference to audio asset
     * @param x           Position X (meters)
     * @param y           Position Y
     * @param z           Position Z
     * @param volume      Volume (0.0-1.0)
     * @param pitch       Playback speed multiplier
     * @param loop        Whether to loop
     * @param spatial     Whether to apply 3D spatialization
     * @param refDistance  Reference distance for attenuation
     * @param maxDistance  Maximum audible distance
     * @return OpenAL source ID, or -1 if not initialized
     */
    public int createSource(String src, double x, double y, double z,
                            double volume, double pitch, boolean loop,
                            boolean spatial, double refDistance,
                            double maxDistance) {
        if (!initialized) return -1;

        int source = alGenSources();
        sources.add(source);

        alSourcef(source, AL_GAIN, (float) volume);
        alSourcef(source, AL_PITCH, (float) pitch);
        alSourcei(source, AL_LOOPING, loop ? AL_TRUE : AL_FALSE);

        if (spatial) {
            alSource3f(source, AL_POSITION, (float) x, (float) y, (float) z);
            alSourcef(source, AL_REFERENCE_DISTANCE, (float) refDistance);
            alSourcef(source, AL_MAX_DISTANCE, (float) maxDistance);
            alSourcei(source, AL_SOURCE_RELATIVE, AL_FALSE);
        } else {
            // Non-spatial: relative to listener at origin, no distance attenuation
            alSourcei(source, AL_SOURCE_RELATIVE, AL_TRUE);
            alSource3f(source, AL_POSITION, 0f, 0f, 0f);
        }

        // Attach buffer if already loaded
        Integer bufferId = bufferCache.get(src);
        if (bufferId != null) {
            alSourcei(source, AL_BUFFER, bufferId);
        }

        return source;
    }

    /**
     * Start playing a source.
     */
    public void play(int sourceId) {
        if (!initialized || sourceId < 0) return;
        alSourcePlay(sourceId);
    }

    /**
     * Pause a source.
     */
    public void pause(int sourceId) {
        if (!initialized || sourceId < 0) return;
        alSourcePause(sourceId);
    }

    /**
     * Stop a source.
     */
    public void stop(int sourceId) {
        if (!initialized || sourceId < 0) return;
        alSourceStop(sourceId);
    }

    /**
     * Update a source's 3D position.
     */
    public void setSourcePosition(int sourceId, double x, double y, double z) {
        if (!initialized || sourceId < 0) return;
        alSource3f(sourceId, AL_POSITION, (float) x, (float) y, (float) z);
    }

    // ==================== Buffer Loading ====================

    /**
     * Load a WAV file into an OpenAL buffer.
     *
     * <p>Parses the WAV header to extract format, sample rate, and PCM data.
     * The buffer is cached by content reference for reuse.
     *
     * @param src     Content reference (cache key)
     * @param wavData Raw WAV file bytes
     * @return OpenAL buffer ID, or -1 on failure
     */
    public int loadWav(String src, byte[] wavData) {
        if (!initialized) return -1;

        Integer existing = bufferCache.get(src);
        if (existing != null) return existing;

        try {
            WavData wav = parseWav(wavData);
            int buffer = alGenBuffers();
            alBufferData(buffer, wav.format, wav.data, wav.sampleRate);
            bufferCache.put(src, buffer);
            log.debug("Loaded WAV buffer: {} ({}Hz, {} bytes)", src, wav.sampleRate, wavData.length);
            return buffer;
        } catch (Exception e) {
            log.error("Failed to load WAV: {}", src, e);
            return -1;
        }
    }

    /**
     * Load an OGG Vorbis file into an OpenAL buffer.
     *
     * <p>Uses stb_vorbis for decoding. The buffer is cached by content
     * reference for reuse.
     *
     * @param src     Content reference (cache key)
     * @param oggData Raw OGG file bytes
     * @return OpenAL buffer ID, or -1 on failure
     */
    public int loadOgg(String src, byte[] oggData) {
        if (!initialized) return -1;

        Integer existing = bufferCache.get(src);
        if (existing != null) return existing;

        ByteBuffer oggBuffer = null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            oggBuffer = MemoryUtil.memAlloc(oggData.length);
            oggBuffer.put(oggData).flip();

            IntBuffer errorBuf = stack.mallocInt(1);
            long decoder = stb_vorbis_open_memory(oggBuffer, errorBuf, null);
            if (decoder == 0L) {
                log.error("Failed to decode OGG: {} (error {})", src, errorBuf.get(0));
                return -1;
            }

            try (STBVorbisInfo info = STBVorbisInfo.malloc(stack)) {
                stb_vorbis_get_info(decoder, info);

                int channels = info.channels();
                int sampleRate = info.sample_rate();
                int totalSamples = stb_vorbis_stream_length_in_samples(decoder);

                ShortBuffer pcm = MemoryUtil.memAllocShort(totalSamples * channels);
                try {
                    stb_vorbis_get_samples_short_interleaved(decoder, channels, pcm);
                    stb_vorbis_close(decoder);

                    int format = (channels == 1) ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16;
                    int buffer = alGenBuffers();
                    alBufferData(buffer, format, pcm, sampleRate);
                    bufferCache.put(src, buffer);

                    log.debug("Loaded OGG buffer: {} ({}Hz, {}ch, {} samples)",
                            src, sampleRate, channels, totalSamples);
                    return buffer;
                } finally {
                    MemoryUtil.memFree(pcm);
                }
            }
        } catch (Exception e) {
            log.error("Failed to load OGG: {}", src, e);
            return -1;
        } finally {
            if (oggBuffer != null) {
                MemoryUtil.memFree(oggBuffer);
            }
        }
    }

    /**
     * Load raw PCM data directly into a buffer.
     *
     * @param src        Content reference (cache key)
     * @param pcmData    Raw PCM data
     * @param format     OpenAL format (e.g., AL_FORMAT_MONO16)
     * @param sampleRate Sample rate in Hz
     * @return OpenAL buffer ID, or -1 on failure
     */
    public int loadBuffer(String src, ByteBuffer pcmData, int format, int sampleRate) {
        if (!initialized) return -1;

        Integer existing = bufferCache.get(src);
        if (existing != null) return existing;

        int buffer = alGenBuffers();
        alBufferData(buffer, format, pcmData, sampleRate);
        bufferCache.put(src, buffer);
        return buffer;
    }

    /**
     * Check if a buffer is loaded for the given content reference.
     */
    public boolean hasBuffer(String src) {
        return bufferCache.containsKey(src);
    }

    /**
     * Attach a loaded buffer to an existing source.
     *
     * <p>Useful when the buffer is loaded after the source was created.
     */
    public void attachBuffer(int sourceId, String src) {
        if (!initialized || sourceId < 0) return;
        Integer bufferId = bufferCache.get(src);
        if (bufferId != null) {
            alSourcei(sourceId, AL_BUFFER, bufferId);
        }
    }

    // ==================== WAV Parsing ====================

    /**
     * Parse a WAV file into format, sample rate, and PCM data.
     */
    static WavData parseWav(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        // RIFF header
        int riff = buf.getInt();
        if (riff != 0x46464952) { // "RIFF"
            throw new IllegalArgumentException("Not a WAV file (missing RIFF header)");
        }
        buf.getInt(); // chunk size
        int wave = buf.getInt();
        if (wave != 0x45564157) { // "WAVE"
            throw new IllegalArgumentException("Not a WAV file (missing WAVE marker)");
        }

        // Find fmt and data chunks
        int audioFormat = 0;
        int numChannels = 0;
        int sampleRate = 0;
        int bitsPerSample = 0;
        ByteBuffer pcmData = null;

        while (buf.hasRemaining()) {
            int chunkId = buf.getInt();
            int chunkSize = buf.getInt();

            if (chunkId == 0x20746D66) { // "fmt "
                audioFormat = buf.getShort() & 0xFFFF;
                numChannels = buf.getShort() & 0xFFFF;
                sampleRate = buf.getInt();
                buf.getInt(); // byte rate
                buf.getShort(); // block align
                bitsPerSample = buf.getShort() & 0xFFFF;
                // Skip any extra fmt bytes
                int remaining = chunkSize - 16;
                if (remaining > 0) {
                    buf.position(buf.position() + remaining);
                }
            } else if (chunkId == 0x61746164) { // "data"
                byte[] pcmBytes = new byte[chunkSize];
                buf.get(pcmBytes);
                pcmData = ByteBuffer.allocateDirect(chunkSize).order(ByteOrder.nativeOrder());
                pcmData.put(pcmBytes).flip();
            } else {
                // Skip unknown chunk
                buf.position(buf.position() + chunkSize);
            }
        }

        if (pcmData == null) {
            throw new IllegalArgumentException("WAV file has no data chunk");
        }
        if (audioFormat != 1) {
            throw new IllegalArgumentException("Only PCM WAV format supported (got " + audioFormat + ")");
        }

        int format;
        if (numChannels == 1 && bitsPerSample == 8) {
            format = AL_FORMAT_MONO8;
        } else if (numChannels == 1 && bitsPerSample == 16) {
            format = AL_FORMAT_MONO16;
        } else if (numChannels == 2 && bitsPerSample == 8) {
            format = AL_FORMAT_STEREO8;
        } else if (numChannels == 2 && bitsPerSample == 16) {
            format = AL_FORMAT_STEREO16;
        } else {
            throw new IllegalArgumentException(
                    "Unsupported WAV format: " + numChannels + "ch " + bitsPerSample + "bit");
        }

        return new WavData(format, sampleRate, pcmData);
    }

    /**
     * Parsed WAV data ready for OpenAL.
     */
    record WavData(int format, int sampleRate, ByteBuffer data) {}

    // ==================== Cleanup ====================

    @Override
    public void close() {
        if (!initialized) return;

        // Stop and delete all sources
        for (int source : sources) {
            alSourceStop(source);
            alDeleteSources(source);
        }
        sources.clear();

        // Delete all buffers
        for (int buffer : bufferCache.values()) {
            alDeleteBuffers(buffer);
        }
        bufferCache.clear();

        // Destroy context and close device
        alcMakeContextCurrent(0L);
        if (context != 0L) {
            alcDestroyContext(context);
            context = 0;
        }
        if (device != 0L) {
            alcCloseDevice(device);
            device = 0;
        }

        initialized = false;
        log.info("OpenAL closed");
    }
}
