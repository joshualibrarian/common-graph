package dev.everydaythings.graph.ui.scene.spatial;

import dev.everydaythings.graph.ui.scene.Scene;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for SpatialCompiler compile-once caching.
 */
class SpatialCompilerTest {

    @BeforeEach
    void clearCaches() {
        SpatialCompiler.clearCache();
    }

    // ==================================================================================
    // Test Fixtures — annotated classes for compilation
    // ==================================================================================

    @Scene.Environment(background = 0xF2F2F2, ambient = 0x808080)
    @Scene.Light(type = "directional", color = 0xFFFFFF, intensity = 0.8,
            z = "10m", dirZ = -1)
    @Scene.Camera(projection = "perspective", fov = 60, near = "0.1m", far = "1000m",
            y = "1.5m", z = "1m", targetZ = "1m")
    static class AnnotatedSpace extends SpatialSchema<Void> {
        @Override public void render(SpatialRenderer out) {}
    }

    @Scene.Environment(background = 0x1A1A2E, ambient = 0x404040,
            fogNear = "10m", fogFar = "100m", fogColor = 0xC0C0C0)
    static class FoggySpace extends SpatialSchema<Void> {
        @Override public void render(SpatialRenderer out) {}
    }

    static class BareSpace extends SpatialSchema<Void> {
        @Override public void render(SpatialRenderer out) {}
    }

    @Scene.Body(shape = "box", width = "2m", height = "1m", depth = "1m", color = 0x8B4513,
            opacity = 0.9, shading = "lit")
    @Scene.Transform(x = "1m", y = "0.5m", z = "-2m", scaleX = 2, scaleY = 0.5, scaleZ = 2)
    @Scene.Light(type = "point", color = 0xFFE4B5, intensity = 2.0, x = "0", y = "5m", z = "0")
    @Scene.Audio(src = "click.wav", x = "1m", y = "2m", z = "3m", volume = 0.8, loop = true,
            spatial = true, autoplay = true)
    static class FullyAnnotatedComponent {}

    @Scene.Body(shape = "sphere", color = 0xFF0000)
    static class SimpleShapeComponent {}

    @Scene.Body(mesh = "model.glb", color = 0x00FF00)
    static class MeshComponent {}

    @Scene.Body(as = TreeBody.class)
    static class CompoundComponent {}

    @Scene.Face(ppm = 1024)
    @Scene.Body(width = "2m", height = "1.5m")
    static class PanelComponent {}

    @Scene.Body(shape = "box", fontSize = "1cm")
    static class DerivedSizeComponent {}

    static class UnannotatedComponent {}

    // ==================================================================================
    // CompiledSpace Tests
    // ==================================================================================

    @Test
    void compileSpace_extractsEnvironment() {
        CompiledSpace space = SpatialCompiler.compileSpace(AnnotatedSpace.class);

        assertThat(space.hasEnvironment()).isTrue();
        assertThat(space.background()).isEqualTo(0xF2F2F2);
        assertThat(space.ambient()).isEqualTo(0x808080);
        assertThat(space.fogNear()).isEmpty();
        assertThat(space.fogFar()).isEmpty();
    }

    @Test
    void compileSpace_extractsEnvironmentWithFog() {
        CompiledSpace space = SpatialCompiler.compileSpace(FoggySpace.class);

        assertThat(space.hasEnvironment()).isTrue();
        assertThat(space.background()).isEqualTo(0x1A1A2E);
        assertThat(space.fogNear()).isEqualTo("10m");
        assertThat(space.fogFar()).isEqualTo("100m");
        assertThat(space.fogColor()).isEqualTo(0xC0C0C0);
    }

    @Test
    void compileSpace_extractsCamera() {
        CompiledSpace space = SpatialCompiler.compileSpace(AnnotatedSpace.class);

        assertThat(space.hasCamera()).isTrue();
        assertThat(space.projection()).isEqualTo("perspective");
        assertThat(space.fov()).isEqualTo(60);
        assertThat(space.near()).isEqualTo("0.1m");
        assertThat(space.far()).isEqualTo("1000m");
        assertThat(space.camY()).isEqualTo("1.5m");
        assertThat(space.camZ()).isEqualTo("1m");
        assertThat(space.targetZ()).isEqualTo("1m");
    }

    @Test
    void compileSpace_extractsLight() {
        CompiledSpace space = SpatialCompiler.compileSpace(AnnotatedSpace.class);

        assertThat(space.hasLight()).isTrue();
        assertThat(space.lightType()).isEqualTo("directional");
        assertThat(space.lightColor()).isEqualTo(0xFFFFFF);
        assertThat(space.lightIntensity()).isEqualTo(0.8);
        assertThat(space.lightZ()).isEqualTo("10m");
        assertThat(space.lightDirZ()).isEqualTo(-1);
    }

    @Test
    void compileSpace_noAnnotations_returnsEmpty() {
        CompiledSpace space = SpatialCompiler.compileSpace(BareSpace.class);

        assertThat(space.hasEnvironment()).isFalse();
        assertThat(space.hasCamera()).isFalse();
        assertThat(space.hasLight()).isFalse();
        assertThat(space).isSameAs(CompiledSpace.EMPTY);
    }

    @Test
    void compileSpace_cachesPerClass() {
        CompiledSpace first = SpatialCompiler.compileSpace(AnnotatedSpace.class);
        CompiledSpace second = SpatialCompiler.compileSpace(AnnotatedSpace.class);

        assertThat(first).isSameAs(second);
        assertThat(SpatialCompiler.spaceCacheSize()).isEqualTo(1);
    }

    @Test
    void compileSpace_differentClassesCachedSeparately() {
        SpatialCompiler.compileSpace(AnnotatedSpace.class);
        SpatialCompiler.compileSpace(FoggySpace.class);
        SpatialCompiler.compileSpace(BareSpace.class);

        assertThat(SpatialCompiler.spaceCacheSize()).isEqualTo(3);
    }

    // ==================================================================================
    // CompiledBody Tests
    // ==================================================================================

    @Test
    void compileBody_extractsShape() {
        CompiledBody body = SpatialCompiler.compileBody(SimpleShapeComponent.class);

        assertThat(body.hasBody()).isTrue();
        assertThat(body.shape()).isEqualTo("sphere");
        assertThat(body.color()).isEqualTo(0xFF0000);
        assertThat(body.hasGeometry()).isTrue();
        assertThat(body.isCompound()).isFalse();
    }

    @Test
    void compileBody_extractsMesh() {
        CompiledBody body = SpatialCompiler.compileBody(MeshComponent.class);

        assertThat(body.hasBody()).isTrue();
        assertThat(body.mesh()).isEqualTo("model.glb");
        assertThat(body.color()).isEqualTo(0x00FF00);
        assertThat(body.hasGeometry()).isTrue();
    }

    @Test
    void compileBody_extractsFullAnnotations() {
        CompiledBody body = SpatialCompiler.compileBody(FullyAnnotatedComponent.class);

        // Body
        assertThat(body.hasBody()).isTrue();
        assertThat(body.shape()).isEqualTo("box");
        assertThat(body.width()).isEqualTo("2m");
        assertThat(body.height()).isEqualTo("1m");
        assertThat(body.depth()).isEqualTo("1m");
        assertThat(body.color()).isEqualTo(0x8B4513);
        assertThat(body.opacity()).isEqualTo(0.9);

        // Placement
        assertThat(body.hasPlacement()).isTrue();
        assertThat(body.placementX()).isEqualTo("1m");
        assertThat(body.placementY()).isEqualTo("0.5m");
        assertThat(body.placementZ()).isEqualTo("-2m");
        assertThat(body.scaleX()).isEqualTo(2);
        assertThat(body.scaleY()).isEqualTo(0.5);
        assertThat(body.scaleZ()).isEqualTo(2);

        // Body.Light
        assertThat(body.hasBodyLight()).isTrue();
        assertThat(body.bodyLightType()).isEqualTo("point");
        assertThat(body.bodyLightColor()).isEqualTo(0xFFE4B5);
        assertThat(body.bodyLightIntensity()).isEqualTo(2.0);

        // Audio
        assertThat(body.hasAudio()).isTrue();
        assertThat(body.audioSrc()).isEqualTo("click.wav");
        assertThat(body.audioVolume()).isEqualTo(0.8);
        assertThat(body.audioLoop()).isTrue();
        assertThat(body.audioAutoplay()).isTrue();
    }

    @Test
    void compileBody_extractsCompoundBody() {
        CompiledBody body = SpatialCompiler.compileBody(CompoundComponent.class);

        assertThat(body.hasBody()).isTrue();
        assertThat(body.isCompound()).isTrue();
        assertThat(body.as()).isEqualTo(TreeBody.class);
    }

    @Test
    void compileBody_extractsPanel() {
        CompiledBody body = SpatialCompiler.compileBody(PanelComponent.class);

        assertThat(body.hasPanel()).isTrue();
        assertThat(body.panelWidth()).isEqualTo("2m");
        assertThat(body.panelHeight()).isEqualTo("1.5m");
        assertThat(body.panelPpm()).isEqualTo(1024);
    }

    @Test
    void compileBody_extractsFontSize() {
        CompiledBody body = SpatialCompiler.compileBody(DerivedSizeComponent.class);

        assertThat(body.isDerivedSize()).isTrue();
        assertThat(body.fontSize()).isEqualTo("1cm");
        assertThat(body.hasGeometry()).isTrue();
        assertThat(body.shape()).isEqualTo("box");
    }

    @Test
    void compileBody_noFontSize_isNotDerived() {
        CompiledBody body = SpatialCompiler.compileBody(SimpleShapeComponent.class);

        assertThat(body.isDerivedSize()).isFalse();
        assertThat(body.fontSize()).isEmpty();
    }

    @Test
    void compileBody_noAnnotation_returnsEmpty() {
        CompiledBody body = SpatialCompiler.compileBody(UnannotatedComponent.class);

        assertThat(body.hasBody()).isFalse();
        assertThat(body.hasPlacement()).isFalse();
        assertThat(body.hasPanel()).isFalse();
        assertThat(body.hasBodyLight()).isFalse();
        assertThat(body.hasAudio()).isFalse();
        assertThat(body).isSameAs(CompiledBody.EMPTY);
    }

    @Test
    void compileBody_cachesPerClass() {
        CompiledBody first = SpatialCompiler.compileBody(FullyAnnotatedComponent.class);
        CompiledBody second = SpatialCompiler.compileBody(FullyAnnotatedComponent.class);

        assertThat(first).isSameAs(second);
        assertThat(SpatialCompiler.bodyCacheSize()).isEqualTo(1);
    }

    // ==================================================================================
    // Emit Tests
    // ==================================================================================

    @Test
    void compiledSpace_emitsToRenderer() {
        CompiledSpace space = SpatialCompiler.compileSpace(AnnotatedSpace.class);
        RecordingSpatialRenderer recorder = new RecordingSpatialRenderer();

        space.emit(recorder);

        assertThat(recorder.environmentCalls).isEqualTo(1);
        assertThat(recorder.lightCalls).isEqualTo(1);
        assertThat(recorder.cameraCalls).isEqualTo(1);
    }

    @Test
    void compiledSpace_emptyDoesNotEmit() {
        RecordingSpatialRenderer recorder = new RecordingSpatialRenderer();

        CompiledSpace.EMPTY.emit(recorder);

        assertThat(recorder.environmentCalls).isEqualTo(0);
        assertThat(recorder.lightCalls).isEqualTo(0);
        assertThat(recorder.cameraCalls).isEqualTo(0);
    }

    @Test
    void compiledBody_emitsGeometry() {
        CompiledBody body = SpatialCompiler.compileBody(SimpleShapeComponent.class);
        RecordingSpatialRenderer recorder = new RecordingSpatialRenderer();

        body.emitGeometry(recorder, null);

        assertThat(recorder.bodyCalls).isEqualTo(1);
    }

    @Test
    void compiledBody_emitsMesh() {
        CompiledBody body = SpatialCompiler.compileBody(MeshComponent.class);
        RecordingSpatialRenderer recorder = new RecordingSpatialRenderer();

        body.emitGeometry(recorder, null);

        assertThat(recorder.meshBodyCalls).isEqualTo(1);
    }

    @Test
    void compiledBody_emitsLightAndAudio() {
        CompiledBody body = SpatialCompiler.compileBody(FullyAnnotatedComponent.class);
        RecordingSpatialRenderer recorder = new RecordingSpatialRenderer();

        body.emitBodyLight(recorder, null);
        body.emitAudio(recorder, null);

        assertThat(recorder.lightCalls).isEqualTo(1);
        assertThat(recorder.audioCalls).isEqualTo(1);
    }

    // ==================================================================================
    // Recording Renderer (test helper)
    // ==================================================================================

    /**
     * Minimal SpatialRenderer that counts calls for verification.
     */
    static class RecordingSpatialRenderer implements SpatialRenderer {
        int environmentCalls, lightCalls, cameraCalls;
        int bodyCalls, meshBodyCalls;
        int pushCalls, popCalls;
        int panelBeginCalls, panelEndCalls;
        int audioCalls;

        @Override public void environment(int bg, int ambient, double fogNear, double fogFar, int fogColor) { environmentCalls++; }
        @Override public void light(String type, int color, double intensity, double x, double y, double z, double dirX, double dirY, double dirZ) { lightCalls++; }
        @Override public void camera(String proj, double fov, double near, double far, double x, double y, double z, double tx, double ty, double tz) { cameraCalls++; }
        @Override public void body(String shape, double w, double h, double d, int color, double opacity, String shading, java.util.List<String> styles) { bodyCalls++; }
        @Override public void meshBody(String meshRef, int color, double opacity, String shading, java.util.List<String> styles) { meshBodyCalls++; }
        @Override public void pushTransform(double x, double y, double z, double qx, double qy, double qz, double qw, double sx, double sy, double sz) { pushCalls++; }
        @Override public void popTransform() { popCalls++; }
        @Override public void beginPanel(double w, double h, double ppm) { panelBeginCalls++; }
        @Override public void endPanel() { panelEndCalls++; }
        @Override public dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer panelRenderer() { return null; }
        @Override public void id(String id) {}
        @Override public void audio(String src, double x, double y, double z, double volume, double pitch, boolean loop, boolean spatial, double refDist, double maxDist, boolean autoplay) { audioCalls++; }
    }
}
