package dev.everydaythings.graph.ui.scene;

import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for SceneCompiler — compile @Scene.* annotations and render to SceneRenderer.
 */
class SceneCompilerTest {

    @BeforeEach
    void clearCaches() {
        SceneCompiler.clearCache();
    }

    // ==================================================================================
    // Test Fixtures — annotated classes for compilation
    // ==================================================================================

    /** Simple vertical container with two text children. */
    @Scene.Container(direction = Scene.Direction.VERTICAL)
    static class SimpleLayout extends SceneSchema<Void> {

        @Scene.Text(content = "Hello")
        static class Greeting {}

        @Scene.Text(content = "World", style = {"muted"})
        static class Subtitle {}
    }

    /** Container with depth (3D depth attribute on 2D structure). */
    @Scene.Container(direction = Scene.Direction.HORIZONTAL, depth = "1cm")
    static class DepthContainer extends SceneSchema<Void> {

        @Scene.Text(content = "On top")
        static class Label {}
    }

    /** A shape with depth. */
    @Scene.Shape(type = "circle", fill = "#FF0000", depth = "5mm")
    static class DepthShape extends SceneSchema<Void> {}

    @Scene.Container(direction = Scene.Direction.HORIZONTAL)
    @Scene.Place(in = "board", anchor = "center", top = "0", left = "0", right = "100%")
    static class PlacedContainer extends SceneSchema<Void> {

        @Scene.Text(content = "Placed")
        static class Label {}
    }


    /** Body node (3D primitive geometry). */
    @Scene.Body(shape = "box", width = "2m", height = "1m", depth = "1m",
            color = 0x8B4513, opacity = 0.9, shading = "lit")
    static class BoxBody extends SceneSchema<Void> {}

    /** Body with mesh. */
    @Scene.Body(mesh = "model.glb", color = 0x00FF00)
    static class MeshBody extends SceneSchema<Void> {}

    /** Face wrapping 2D text on a body face. */
    @Scene.Face(value = "front", ppm = 1024)
    static class FrontFace extends SceneSchema<Void> {

        @Scene.Text(content = "Face text")
        static class Label {}
    }

    /** Transform node wrapping children. */
    @Scene.Transform(x = "1m", y = "2m", z = "3m", scaleX = 2, scaleY = 2, scaleZ = 2)
    static class TransformWrapper extends SceneSchema<Void> {

        @Scene.Body(shape = "sphere", color = 0xFF0000)
        static class Ball {}
    }

    /** Light node. */
    @Scene.Light(type = "directional", color = 0xFFFFFF, intensity = 0.8,
            z = "10m", dirZ = -1)
    static class DirectionalLight extends SceneSchema<Void> {}

    /** Audio node. */
    @Scene.Audio(src = "click.wav", x = "1m", y = "2m", z = "3m",
            volume = 0.8, loop = true, spatial = true, autoplay = true)
    static class SoundSource extends SceneSchema<Void> {}

    /** Environment node. */
    @Scene.Environment(background = 0x1A1A2E, ambient = 0x404040,
            fogNear = "10m", fogFar = "100m", fogColor = 0xC0C0C0)
    static class FoggyEnv extends SceneSchema<Void> {}

    /** Camera node. */
    @Scene.Camera(projection = "perspective", fov = 60,
            near = "0.1m", far = "1000m",
            y = "5m", z = "1.5m")
    static class PerspectiveCamera extends SceneSchema<Void> {}

    /** Complex: container + body + face + children (like a chess board). */
    @Scene.Container(direction = Scene.Direction.VERTICAL)
    @Scene.Body(shape = "box", width = "44cm", height = "0", depth = "44cm")
    static class BoardScene extends SceneSchema<Void> {

        @Scene.Text(content = "Board label")
        static class Label {}
    }

    /** No @Scene annotations at all. */
    static class BareClass extends SceneSchema<Void> {}

    // ==================================================================================
    // canCompile Tests
    // ==================================================================================

    @Test
    void canCompile_trueForContainer() {
        assertThat(SceneCompiler.canCompile(SimpleLayout.class)).isTrue();
    }

    @Test
    void canCompile_trueForBody() {
        assertThat(SceneCompiler.canCompile(BoxBody.class)).isTrue();
    }

    @Test
    void canCompile_trueForLight() {
        assertThat(SceneCompiler.canCompile(DirectionalLight.class)).isTrue();
    }

    @Test
    void canCompile_trueForEnvironment() {
        assertThat(SceneCompiler.canCompile(FoggyEnv.class)).isTrue();
    }

    @Test
    void canCompile_trueForCamera() {
        assertThat(SceneCompiler.canCompile(PerspectiveCamera.class)).isTrue();
    }

    @Test
    void canCompile_falseForBareClass() {
        assertThat(SceneCompiler.canCompile(BareClass.class)).isFalse();
    }

    // ==================================================================================
    // Compilation (getCompiled) Tests
    // ==================================================================================

    @Test
    void compile_containerWithTextChildren() {
        ViewNode root = SceneCompiler.getCompiled(SimpleLayout.class);

        assertThat(root).isNotNull();
        assertThat(root.type).isEqualTo(ViewNode.NodeType.CONTAINER);
        assertThat(root.direction).isEqualTo(Scene.Direction.VERTICAL);
        assertThat(root.children).hasSize(2);

        ViewNode first = root.children.get(0);
        assertThat(first.type).isEqualTo(ViewNode.NodeType.TEXT);
        assertThat(first.textContent).isEqualTo("Hello");

        ViewNode second = root.children.get(1);
        assertThat(second.type).isEqualTo(ViewNode.NodeType.TEXT);
        assertThat(second.textContent).isEqualTo("World");
        assertThat(second.styles).containsExactly("muted");
    }

    @Test
    void compile_containerWithDepth() {
        ViewNode root = SceneCompiler.getCompiled(DepthContainer.class);

        assertThat(root).isNotNull();
        assertThat(root.type).isEqualTo(ViewNode.NodeType.CONTAINER);
        assertThat(root.depth).isEqualTo("1cm");
    }

    @Test
    void compile_containerWithPlaceMetadata() {
        ViewNode root = SceneCompiler.getCompiled(PlacedContainer.class);

        assertThat(root).isNotNull();
        assertThat(root.type).isEqualTo(ViewNode.NodeType.CONTAINER);
        assertThat(root.placeIn).isEqualTo("board");
        assertThat(root.placeAnchor).isEqualTo("center");
        assertThat(root.placeTop).isEqualTo("0");
        assertThat(root.placeLeft).isEqualTo("0");
        assertThat(root.placeRight).isEqualTo("100%");
    }


    @Test
    void compile_bodyNode() {
        ViewNode root = SceneCompiler.getCompiled(BoxBody.class);

        assertThat(root).isNotNull();
        assertThat(root.type).isEqualTo(ViewNode.NodeType.BODY);
        assertThat(root.bodyShape).isEqualTo("box");
        assertThat(root.bodyWidth).isEqualTo("2m");
        assertThat(root.bodyHeight).isEqualTo("1m");
        assertThat(root.bodyDepth).isEqualTo("1m");
        assertThat(root.bodyColor).isEqualTo(0x8B4513);
        assertThat(root.bodyOpacity).isEqualTo(0.9);
        assertThat(root.bodyShading).isEqualTo("lit");
    }

    @Test
    void compile_meshBody() {
        ViewNode root = SceneCompiler.getCompiled(MeshBody.class);

        assertThat(root).isNotNull();
        assertThat(root.type).isEqualTo(ViewNode.NodeType.BODY);
        assertThat(root.bodyMesh).isEqualTo("model.glb");
        assertThat(root.bodyColor).isEqualTo(0x00FF00);
    }

    @Test
    void compile_faceWithChildren() {
        ViewNode root = SceneCompiler.getCompiled(FrontFace.class);

        assertThat(root).isNotNull();
        assertThat(root.type).isEqualTo(ViewNode.NodeType.FACE);
        assertThat(root.faceName).isEqualTo("front");
        assertThat(root.facePpm).isEqualTo(1024);
        assertThat(root.children).hasSize(1);
        assertThat(root.children.get(0).type).isEqualTo(ViewNode.NodeType.TEXT);
    }

    @Test
    void compile_transformWithChildren() {
        ViewNode root = SceneCompiler.getCompiled(TransformWrapper.class);

        assertThat(root).isNotNull();
        assertThat(root.type).isEqualTo(ViewNode.NodeType.TRANSFORM);
        assertThat(root.transformX).isEqualTo("1m");
        assertThat(root.transformY).isEqualTo("2m");
        assertThat(root.transformZ).isEqualTo("3m");
        assertThat(root.transformScaleX).isEqualTo(2);
        assertThat(root.transformScaleY).isEqualTo(2);
        assertThat(root.transformScaleZ).isEqualTo(2);
        assertThat(root.children).hasSize(1);
        assertThat(root.children.get(0).type).isEqualTo(ViewNode.NodeType.BODY);
    }

    @Test
    void compile_lightNode() {
        ViewNode root = SceneCompiler.getCompiled(DirectionalLight.class);

        assertThat(root).isNotNull();
        assertThat(root.type).isEqualTo(ViewNode.NodeType.LIGHT);
        assertThat(root.lightType).isEqualTo("directional");
        assertThat(root.lightColor).isEqualTo(0xFFFFFF);
        assertThat(root.lightIntensity).isEqualTo(0.8);
        assertThat(root.lightZ).isEqualTo("10m");
        assertThat(root.lightDirZ).isEqualTo(-1);
    }

    @Test
    void compile_environmentNode() {
        ViewNode root = SceneCompiler.getCompiled(FoggyEnv.class);

        assertThat(root).isNotNull();
        assertThat(root.type).isEqualTo(ViewNode.NodeType.ENVIRONMENT);
        assertThat(root.envBackground).isEqualTo(0x1A1A2E);
        assertThat(root.envAmbient).isEqualTo(0x404040);
        assertThat(root.envFogNear).isEqualTo("10m");
        assertThat(root.envFogFar).isEqualTo("100m");
        assertThat(root.envFogColor).isEqualTo(0xC0C0C0);
    }

    @Test
    void compile_cameraNode() {
        ViewNode root = SceneCompiler.getCompiled(PerspectiveCamera.class);

        assertThat(root).isNotNull();
        assertThat(root.type).isEqualTo(ViewNode.NodeType.CAMERA);
        assertThat(root.cameraProjection).isEqualTo("perspective");
        assertThat(root.cameraFov).isEqualTo(60);
        assertThat(root.cameraNear).isEqualTo("0.1m");
        assertThat(root.cameraFar).isEqualTo("1000m");
        assertThat(root.cameraY).isEqualTo("5m");
        assertThat(root.cameraZ).isEqualTo("1.5m");
    }

    @Test
    void compile_containerWithBodySupplementary() {
        ViewNode root = SceneCompiler.getCompiled(BoardScene.class);

        assertThat(root).isNotNull();
        // Type is CONTAINER (primary), but body data is supplementary
        assertThat(root.type).isEqualTo(ViewNode.NodeType.CONTAINER);
        assertThat(root.bodyShape).isEqualTo("box");
        assertThat(root.bodyWidth).isEqualTo("44cm");
        assertThat(root.bodyDepth).isEqualTo("44cm");
        assertThat(root.children).hasSize(1);
    }

    // ==================================================================================
    // Caching Tests
    // ==================================================================================

    @Test
    void compile_cachesPerClass() {
        ViewNode first = SceneCompiler.getCompiled(SimpleLayout.class);
        ViewNode second = SceneCompiler.getCompiled(SimpleLayout.class);

        assertThat(first).isSameAs(second);
    }

    @Test
    void compile_clearCacheWorks() {
        ViewNode first = SceneCompiler.getCompiled(SimpleLayout.class);
        SceneCompiler.clearCache();
        ViewNode second = SceneCompiler.getCompiled(SimpleLayout.class);

        assertThat(first).isNotSameAs(second);
    }

    // ==================================================================================
    // Rendering Tests
    // ==================================================================================

    @Test
    void render_containerWithText() {
        SimpleLayout schema = new SimpleLayout();
        RecordingSceneRenderer recorder = new RecordingSceneRenderer();

        schema.render(recorder);

        assertThat(recorder.beginBoxCalls).isEqualTo(1);
        assertThat(recorder.endBoxCalls).isEqualTo(1);
        assertThat(recorder.textCalls).isEqualTo(2);
        assertThat(recorder.texts).containsExactly("Hello", "World");
    }

    @Test
    void render_containerEmitsDepth() {
        DepthContainer schema = new DepthContainer();
        RecordingSceneRenderer recorder = new RecordingSceneRenderer();

        schema.render(recorder);

        assertThat(recorder.depthCalls).isEqualTo(1);
        assertThat(recorder.lastDepthMeters).isCloseTo(0.01, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void render_shapeEmitsDepth() {
        DepthShape schema = new DepthShape();
        RecordingSceneRenderer recorder = new RecordingSceneRenderer();

        schema.render(recorder);

        assertThat(recorder.depthCalls).isEqualTo(1);
        assertThat(recorder.lastDepthMeters).isCloseTo(0.005, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void render_body() {
        BoxBody schema = new BoxBody();
        RecordingSceneRenderer recorder = new RecordingSceneRenderer();

        schema.render(recorder);

        assertThat(recorder.bodyCalls).isEqualTo(1);
        assertThat(recorder.lastBodyShape).isEqualTo("box");
    }

    @Test
    void render_meshBody() {
        MeshBody schema = new MeshBody();
        RecordingSceneRenderer recorder = new RecordingSceneRenderer();

        schema.render(recorder);

        assertThat(recorder.meshBodyCalls).isEqualTo(1);
        assertThat(recorder.lastMeshRef).isEqualTo("model.glb");
    }

    @Test
    void render_face() {
        FrontFace schema = new FrontFace();
        RecordingSceneRenderer recorder = new RecordingSceneRenderer();

        schema.render(recorder);

        assertThat(recorder.beginFaceCalls).isEqualTo(1);
        assertThat(recorder.endFaceCalls).isEqualTo(1);
        assertThat(recorder.lastFaceName).isEqualTo("front");
        assertThat(recorder.lastFacePpm).isEqualTo(1024);
        // Children rendered between face calls
        assertThat(recorder.textCalls).isEqualTo(1);
    }

    @Test
    void render_transform() {
        TransformWrapper schema = new TransformWrapper();
        RecordingSceneRenderer recorder = new RecordingSceneRenderer();

        schema.render(recorder);

        assertThat(recorder.pushTransformCalls).isEqualTo(1);
        assertThat(recorder.popTransformCalls).isEqualTo(1);
        // Child body rendered inside transform
        assertThat(recorder.bodyCalls).isEqualTo(1);
    }

    @Test
    void render_light() {
        DirectionalLight schema = new DirectionalLight();
        RecordingSceneRenderer recorder = new RecordingSceneRenderer();

        schema.render(recorder);

        assertThat(recorder.lightCalls).isEqualTo(1);
        assertThat(recorder.lastLightType).isEqualTo("directional");
    }

    @Test
    void render_audio() {
        SoundSource schema = new SoundSource();
        RecordingSceneRenderer recorder = new RecordingSceneRenderer();

        schema.render(recorder);

        assertThat(recorder.audioCalls).isEqualTo(1);
        assertThat(recorder.lastAudioSrc).isEqualTo("click.wav");
    }

    @Test
    void render_environment() {
        FoggyEnv schema = new FoggyEnv();
        RecordingSceneRenderer recorder = new RecordingSceneRenderer();

        schema.render(recorder);

        assertThat(recorder.environmentCalls).isEqualTo(1);
        assertThat(recorder.lastEnvBackground).isEqualTo(0x1A1A2E);
    }

    @Test
    void render_camera() {
        PerspectiveCamera schema = new PerspectiveCamera();
        RecordingSceneRenderer recorder = new RecordingSceneRenderer();

        schema.render(recorder);

        assertThat(recorder.cameraCalls).isEqualTo(1);
        assertThat(recorder.lastCameraProjection).isEqualTo("perspective");
        assertThat(recorder.lastCameraFov).isEqualTo(60);
    }

    @Test
    void render_surfaceRendererDelegatesToScene() {
        SimpleLayout schema = new SimpleLayout();
        RecordingSceneRenderer recorder = new RecordingSceneRenderer();

        // Call the SurfaceRenderer overload — should route to SceneRenderer path
        schema.render((SurfaceRenderer) recorder);

        assertThat(recorder.beginBoxCalls).isEqualTo(1);
        assertThat(recorder.textCalls).isEqualTo(2);
    }

    // ==================================================================================
    // Repeat with Columns
    // ==================================================================================

    static class GridData {
        private final List<String> items;
        GridData(List<String> items) { this.items = items; }
        public List<String> items() { return items; }
    }

    @Scene.Container(direction = Scene.Direction.VERTICAL)
    static class ColumnsRepeatLayout extends SceneSchema<GridData> {

        @Scene.Repeat(bind = "value.items", columns = "3")
        @Scene.Container(direction = Scene.Direction.HORIZONTAL)
        static class GridCell {

            @Scene.Text(bind = "$item")
            static class Label {}
        }
    }

    @Scene.Container(direction = Scene.Direction.VERTICAL)
    static class RepeatFontLayout extends SceneSchema<GridData> {

        @Scene.Repeat(bind = "value.items")
        @Scene.Container(direction = Scene.Direction.HORIZONTAL,
                fontFamily = "Symbols Nerd Font Mono",
                fontSize = "80%")
        static class Row {

            @Scene.Text(bind = "$item")
            static class Label {}
        }
    }

    @Nested
    class RepeatColumns {

        @Test
        void wrapsItemsIntoHorizontalRows() {
            ColumnsRepeatLayout schema = new ColumnsRepeatLayout();
            schema.value(new GridData(List.of("A", "B", "C", "D", "E", "F")));
            RecordingSceneRenderer recorder = new RecordingSceneRenderer();

            schema.render(recorder);

            // 1 root VERTICAL + 2 auto row wrappers HORIZONTAL + 6 item containers = 9
            assertThat(recorder.beginBoxCalls).isEqualTo(9);
            assertThat(recorder.endBoxCalls).isEqualTo(9);
            assertThat(recorder.textCalls).isEqualTo(6);
            assertThat(recorder.texts).containsExactly("A", "B", "C", "D", "E", "F");
        }

        @Test
        void partialLastRow() {
            ColumnsRepeatLayout schema = new ColumnsRepeatLayout();
            schema.value(new GridData(List.of("A", "B", "C", "D")));
            RecordingSceneRenderer recorder = new RecordingSceneRenderer();

            schema.render(recorder);

            // 1 root + 2 auto rows (3+1) + 4 item containers = 7
            assertThat(recorder.beginBoxCalls).isEqualTo(7);
            assertThat(recorder.endBoxCalls).isEqualTo(7);
            assertThat(recorder.textCalls).isEqualTo(4);
            assertThat(recorder.texts).containsExactly("A", "B", "C", "D");
        }

        @Test
        void emptyCollection_noRows() {
            ColumnsRepeatLayout schema = new ColumnsRepeatLayout();
            schema.value(new GridData(List.of()));
            RecordingSceneRenderer recorder = new RecordingSceneRenderer();

            schema.render(recorder);

            // Just the root container, no rows
            assertThat(recorder.beginBoxCalls).isEqualTo(1);
            assertThat(recorder.endBoxCalls).isEqualTo(1);
            assertThat(recorder.textCalls).isEqualTo(0);
        }

        @Test
        void repeatPreservesContainerFontSettings() {
            RepeatFontLayout schema = new RepeatFontLayout();
            schema.value(new GridData(List.of("A", "B", "C")));
            RecordingSceneRenderer recorder = new RecordingSceneRenderer();

            schema.render(recorder);

            assertThat(recorder.textCalls).isEqualTo(3);
            assertThat(recorder.fontFamilies).contains("Symbols Nerd Font Mono");
            assertThat(recorder.fontSizes).contains("80%");
        }
    }

    // ==================================================================================
    // Recording SceneRenderer (test helper)
    // ==================================================================================

    /**
     * Minimal SceneRenderer that counts calls and captures last-seen values.
     */
    static class RecordingSceneRenderer implements SceneRenderer {
        int beginBoxCalls, endBoxCalls;
        int textCalls, formattedTextCalls;
        int imageCalls;
        int shapeCalls;
        int depthCalls;
        int bodyCalls, meshBodyCalls;
        int beginFaceCalls, endFaceCalls;
        int pushTransformCalls, popTransformCalls;
        int lightCalls;
        int audioCalls;
        int environmentCalls;
        int cameraCalls;

        List<String> texts = new ArrayList<>();
        List<String> fontFamilies = new ArrayList<>();
        List<String> fontSizes = new ArrayList<>();

        String lastBodyShape;
        String lastMeshRef;
        String lastFaceName;
        int lastFacePpm;
        String lastLightType;
        String lastAudioSrc;
        int lastEnvBackground;
        String lastCameraProjection;
        double lastCameraFov;
        double lastDepthMeters;

        // --- SurfaceRenderer abstract methods ---

        @Override public void text(String content, List<String> styles) {
            textCalls++;
            texts.add(content);
        }

        @Override public void formattedText(String content, String format, List<String> styles) {
            formattedTextCalls++;
        }

        @Override public void image(String alt, ContentID image, ContentID solid,
                                    String size, String fit, List<String> styles) {
            imageCalls++;
        }

        @Override public void beginBox(Scene.Direction direction, List<String> styles) {
            beginBoxCalls++;
        }

        @Override public void endBox() {
            endBoxCalls++;
        }

        @Override public void type(String type) {}
        @Override public void id(String id) {}
        @Override public void editable(boolean editable) {}
        @Override public void event(String on, String action, String target) {}
        @Override public void textFontFamily(String family) {
            fontFamilies.add(family);
        }
        @Override public void textFontSize(String spec) {
            fontSizes.add(spec);
        }

        // --- SceneRenderer methods ---

        @Override public void depth(double meters, boolean solid) {
            depthCalls++;
            lastDepthMeters = meters;
        }

        @Override public void body(String shape, double w, double h, double d,
                                   int color, double opacity, String shading, List<String> styles) {
            bodyCalls++;
            lastBodyShape = shape;
        }

        @Override public void meshBody(String meshRef, int color, double opacity,
                                       String shading, List<String> styles) {
            meshBodyCalls++;
            lastMeshRef = meshRef;
        }

        @Override public void beginFace(String face, int ppm) {
            beginFaceCalls++;
            lastFaceName = face;
            lastFacePpm = ppm;
        }

        @Override public void endFace() {
            endFaceCalls++;
        }

        @Override public void pushTransform(double x, double y, double z,
                                            double qx, double qy, double qz, double qw,
                                            double sx, double sy, double sz) {
            pushTransformCalls++;
        }

        @Override public void popTransform() {
            popTransformCalls++;
        }

        @Override public void light(String type, int color, double intensity,
                                    double x, double y, double z,
                                    double dirX, double dirY, double dirZ) {
            lightCalls++;
            lastLightType = type;
        }

        @Override public void audio3d(String src, double x, double y, double z,
                                      double volume, double pitch, boolean loop,
                                      boolean spatial, double refDistance, double maxDistance,
                                      boolean autoplay) {
            audioCalls++;
            lastAudioSrc = src;
        }

        @Override public void environment(int background, int ambient,
                                          double fogNear, double fogFar, int fogColor) {
            environmentCalls++;
            lastEnvBackground = background;
        }

        @Override public void camera(String projection, double fov, double near, double far,
                                     double x, double y, double z,
                                     double tx, double ty, double tz) {
            cameraCalls++;
            lastCameraProjection = projection;
            lastCameraFov = fov;
        }
    }
}
