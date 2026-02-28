package dev.everydaythings.graph.surface;

import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.spatial.SpatialRenderer;
import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;
import dev.everydaythings.graph.ui.scene.surface.primitive.AudioSurface;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the spatial audio DSL — @Scene.Audio annotation, SpatialRenderer.audio(),
 * SurfaceRenderer.audio(), and AudioSurface.
 */
@DisplayName("Spatial Audio DSL")
class SpatialAudioTest {

    // ==================================================================================
    // @Scene.Audio Annotation
    // ==================================================================================

    @Nested
    @DisplayName("@Scene.Audio annotation")
    class AnnotationTest {

        @Test
        @DisplayName("annotation has correct defaults")
        void defaults() {
            Scene.Audio audio = AnnotatedWithDefaults.class.getAnnotation(Scene.Audio.class);
            assertThat(audio).isNotNull();
            assertThat(audio.src()).isEmpty();
            assertThat(audio.x()).isEqualTo("0");
            assertThat(audio.y()).isEqualTo("0");
            assertThat(audio.z()).isEqualTo("0");
            assertThat(audio.volume()).isEqualTo(1.0);
            assertThat(audio.pitch()).isEqualTo(1.0);
            assertThat(audio.loop()).isFalse();
            assertThat(audio.spatial()).isTrue();
            assertThat(audio.refDistance()).isEqualTo("1m");
            assertThat(audio.maxDistance()).isEqualTo("50m");
            assertThat(audio.autoplay()).isFalse();
        }

        @Test
        @DisplayName("annotation with custom values")
        void customValues() {
            Scene.Audio audio = AnnotatedWithCustom.class.getAnnotation(Scene.Audio.class);
            assertThat(audio).isNotNull();
            assertThat(audio.src()).isEqualTo("waterfall.wav");
            assertThat(audio.x()).isEqualTo("5m");
            assertThat(audio.y()).isEqualTo("0.5m");
            assertThat(audio.z()).isEqualTo("-3m");
            assertThat(audio.volume()).isEqualTo(0.7);
            assertThat(audio.pitch()).isEqualTo(1.2);
            assertThat(audio.loop()).isTrue();
            assertThat(audio.spatial()).isTrue();
            assertThat(audio.refDistance()).isEqualTo("2m");
            assertThat(audio.maxDistance()).isEqualTo("30m");
            assertThat(audio.autoplay()).isTrue();
        }

        @Test
        @DisplayName("non-spatial annotation")
        void nonSpatial() {
            Scene.Audio audio = AnnotatedNonSpatial.class.getAnnotation(Scene.Audio.class);
            assertThat(audio).isNotNull();
            assertThat(audio.src()).isEqualTo("music.ogg");
            assertThat(audio.spatial()).isFalse();
            assertThat(audio.loop()).isTrue();
        }

        @Scene.Audio
        static class AnnotatedWithDefaults {}

        @Scene.Audio(
                src = "waterfall.wav",
                x = "5m", y = "0.5m", z = "-3m",
                volume = 0.7, pitch = 1.2,
                loop = true, spatial = true,
                refDistance = "2m", maxDistance = "30m",
                autoplay = true
        )
        static class AnnotatedWithCustom {}

        @Scene.Audio(src = "music.ogg", spatial = false, loop = true)
        static class AnnotatedNonSpatial {}
    }

    // ==================================================================================
    // SpatialRenderer.audio() — default no-op
    // ==================================================================================

    @Nested
    @DisplayName("SpatialRenderer.audio()")
    class SpatialRendererAudioTest {

        @Test
        @DisplayName("default audio() is a no-op")
        void defaultIsNoOp() {
            // A minimal SpatialRenderer that only implements required methods
            SpatialRenderer minimal = new MinimalSpatialRenderer();
            // Should not throw
            minimal.audio("test.wav", 1, 2, 3, 0.5, 1.0, true, true, 1.0, 50.0, true);
        }

        @Test
        @DisplayName("recording renderer captures audio calls")
        void recordingCaptures() {
            RecordingSpatialRenderer recorder = new RecordingSpatialRenderer();
            recorder.audio("stream.ogg", 2.5, 1.0, -4.0, 0.8, 1.0, true, true, 1.5, 25.0, false);

            assertThat(recorder.audioCalls).hasSize(1);
            AudioCall call = recorder.audioCalls.get(0);
            assertThat(call.src).isEqualTo("stream.ogg");
            assertThat(call.x).isEqualTo(2.5);
            assertThat(call.y).isEqualTo(1.0);
            assertThat(call.z).isEqualTo(-4.0);
            assertThat(call.volume).isEqualTo(0.8);
            assertThat(call.pitch).isEqualTo(1.0);
            assertThat(call.loop).isTrue();
            assertThat(call.spatial).isTrue();
            assertThat(call.refDistance).isEqualTo(1.5);
            assertThat(call.maxDistance).isEqualTo(25.0);
            assertThat(call.autoplay).isFalse();
        }

        @Test
        @DisplayName("multiple audio sources are captured independently")
        void multipleAudioSources() {
            RecordingSpatialRenderer recorder = new RecordingSpatialRenderer();
            recorder.audio("birds.wav", 5, 3, 0, 0.5, 1.0, true, true, 2, 20, true);
            recorder.audio("wind.ogg", 0, 0, 0, 0.3, 1.0, true, false, 1, 50, true);

            assertThat(recorder.audioCalls).hasSize(2);
            assertThat(recorder.audioCalls.get(0).src).isEqualTo("birds.wav");
            assertThat(recorder.audioCalls.get(0).spatial).isTrue();
            assertThat(recorder.audioCalls.get(1).src).isEqualTo("wind.ogg");
            assertThat(recorder.audioCalls.get(1).spatial).isFalse();
        }
    }

    // ==================================================================================
    // AudioSurface — 2D play control widget
    // ==================================================================================

    @Nested
    @DisplayName("AudioSurface")
    class AudioSurfaceTest {

        @Test
        @DisplayName("factory creates surface with src")
        void factory() {
            AudioSurface audio = AudioSurface.of("track.ogg");
            assertThat(audio.src()).isEqualTo("track.ogg");
            assertThat(audio.volume()).isEqualTo(1.0);
            assertThat(audio.loop()).isFalse();
        }

        @Test
        @DisplayName("fluent setters work")
        void fluentSetters() {
            AudioSurface audio = AudioSurface.of("music.wav")
                    .volume(0.5)
                    .loop(true);
            assertThat(audio.src()).isEqualTo("music.wav");
            assertThat(audio.volume()).isEqualTo(0.5);
            assertThat(audio.loop()).isTrue();
        }

        @Test
        @DisplayName("render emits audio() call")
        void renderEmitsAudio() {
            AudioSurface audio = AudioSurface.of("track.ogg")
                    .volume(0.7)
                    .loop(true)
                    .style("player");

            RecordingSurfaceRenderer recorder = new RecordingSurfaceRenderer();
            audio.render(recorder);

            assertThat(recorder.audioCalls).hasSize(1);
            var call = recorder.audioCalls.get(0);
            assertThat(call.src).isEqualTo("track.ogg");
            assertThat(call.volume).isEqualTo(0.7);
            assertThat(call.loop).isTrue();
            assertThat(call.styles).containsExactly("player");
        }

        @Test
        @DisplayName("render with id emits id first")
        void renderWithId() {
            AudioSurface audio = AudioSurface.of("bgm.ogg").id("main-player");

            RecordingSurfaceRenderer recorder = new RecordingSurfaceRenderer();
            audio.render(recorder);

            assertThat(recorder.ids).contains("main-player");
            assertThat(recorder.audioCalls).hasSize(1);
        }
    }

    // ==================================================================================
    // Test Doubles
    // ==================================================================================

    record AudioCall(String src, double x, double y, double z,
                     double volume, double pitch, boolean loop,
                     boolean spatial, double refDistance, double maxDistance,
                     boolean autoplay) {}

    record SurfaceAudioCall(String src, double volume, boolean loop, List<String> styles) {}

    /**
     * Minimal SpatialRenderer — tests that default audio() doesn't throw.
     */
    static class MinimalSpatialRenderer implements SpatialRenderer {
        @Override
        public void body(String shape, double w, double h, double d,
                         int color, double opacity, String shading, List<String> styles) {}
        @Override
        public void meshBody(String meshRef, int color, double opacity,
                             String shading, List<String> styles) {}
        @Override
        public void pushTransform(double x, double y, double z,
                                  double qx, double qy, double qz, double qw,
                                  double sx, double sy, double sz) {}
        @Override
        public void popTransform() {}
        @Override
        public void beginPanel(double width, double height, double ppm) {}
        @Override
        public SurfaceRenderer panelRenderer() { return null; }
        @Override
        public void endPanel() {}
        @Override
        public void light(String type, int color, double intensity,
                          double x, double y, double z,
                          double dirX, double dirY, double dirZ) {}
        @Override
        public void camera(String projection, double fov, double near, double far,
                           double x, double y, double z,
                           double tx, double ty, double tz) {}
        @Override
        public void environment(int background, int ambient,
                                double fogNear, double fogFar, int fogColor) {}
        @Override
        public void id(String id) {}
    }

    /**
     * Recording SpatialRenderer that captures audio calls.
     */
    static class RecordingSpatialRenderer extends MinimalSpatialRenderer {
        final List<AudioCall> audioCalls = new ArrayList<>();

        @Override
        public void audio(String src, double x, double y, double z,
                          double volume, double pitch, boolean loop,
                          boolean spatial, double refDistance, double maxDistance,
                          boolean autoplay) {
            audioCalls.add(new AudioCall(src, x, y, z, volume, pitch, loop,
                    spatial, refDistance, maxDistance, autoplay));
        }
    }

    /**
     * Recording SurfaceRenderer that captures audio calls.
     */
    static class RecordingSurfaceRenderer implements SurfaceRenderer {
        final List<SurfaceAudioCall> audioCalls = new ArrayList<>();
        final List<String> ids = new ArrayList<>();

        @Override
        public void audio(String src, double volume, boolean loop, List<String> styles) {
            audioCalls.add(new SurfaceAudioCall(src, volume, loop, styles));
        }

        @Override
        public void text(String content, List<String> styles) {}
        @Override
        public void formattedText(String content, String format, List<String> styles) {}
        @Override
        public void image(String alt, dev.everydaythings.graph.item.id.ContentID image,
                          dev.everydaythings.graph.item.id.ContentID solid,
                          String size, String fit, List<String> styles) {}
        @Override
        public void beginBox(dev.everydaythings.graph.ui.scene.Scene.Direction direction,
                             List<String> styles) {}
        @Override
        public void endBox() {}
        @Override
        public void type(String type) {}
        @Override
        public void id(String id) { ids.add(id); }
        @Override
        public void editable(boolean editable) {}
        @Override
        public void event(String on, String action, String target) {}
    }
}
