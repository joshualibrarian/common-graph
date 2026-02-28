package dev.everydaythings.graph.ui.scene.surface.primitive;

import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;

/**
 * Surface for audio playback controls.
 *
 * <p>AudioSurface renders as a play/pause control with optional label.
 * The actual audio playback is handled by the platform renderer.
 * Events emitted: "play", "pause", "seek".
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * AudioSurface.of("background.ogg").volume(0.5).loop(true)
 * }</pre>
 */
public class AudioSurface extends SurfaceSchema<Void> {

    /** Content reference to audio asset. */
    @Canon(order = 20)
    private String src;

    /** Volume (0.0 = silent, 1.0 = full). */
    @Canon(order = 21)
    private double volume = 1.0;

    /** Whether to loop playback. */
    @Canon(order = 22)
    private boolean loop = false;

    public AudioSurface() {}

    public static AudioSurface of(String src) {
        AudioSurface a = new AudioSurface();
        a.src = src;
        return a;
    }

    public AudioSurface volume(double volume) {
        this.volume = volume;
        return this;
    }

    public AudioSurface loop(boolean loop) {
        this.loop = loop;
        return this;
    }

    public String src() {
        return src;
    }

    public double volume() {
        return volume;
    }

    public boolean loop() {
        return loop;
    }

    @Override
    public void render(SurfaceRenderer out) {
        emitCommonProperties(out);
        out.audio(src, volume, loop, style());
    }
}
