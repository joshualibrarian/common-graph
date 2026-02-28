package dev.everydaythings.graph.surface;

import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.spatial.SpatialRenderer;
import dev.everydaythings.graph.ui.scene.spatial.SpatialSchema;
import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;
import dev.everydaythings.graph.ui.scene.spatial.ItemSpace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the @Scene annotation system and SpatialSchema.
 */
@DisplayName("Scene System")
class SpatialSchemaTest {

    // ==================================================================================
    // SpatialSchema Basics
    // ==================================================================================

    @Nested
    @DisplayName("SpatialSchema")
    class SchemaBasics {

        @Test
        @DisplayName("defaults to visible with unit scale")
        void defaults() {
            var schema = new TestScene();

            assertThat(schema.visible()).isTrue();
            assertThat(schema.scaleX()).isEqualTo(1.0);
            assertThat(schema.scaleY()).isEqualTo(1.0);
            assertThat(schema.scaleZ()).isEqualTo(1.0);
            assertThat(schema.id()).isNull();
            assertThat(schema.style()).isEmpty();
        }

        @Test
        @DisplayName("fluent setters return self")
        void fluentSetters() {
            var schema = new TestScene();
            var result = schema.id("test-id")
                    .style("scene-style")
                    .visible(false)
                    .scaleX(2.0)
                    .scaleY(0.5)
                    .scaleZ(3.0);

            assertThat(result).isSameAs(schema);
            assertThat(schema.id()).isEqualTo("test-id");
            assertThat(schema.style()).containsExactly("scene-style");
            assertThat(schema.visible()).isFalse();
            assertThat(schema.scaleX()).isEqualTo(2.0);
            assertThat(schema.scaleY()).isEqualTo(0.5);
            assertThat(schema.scaleZ()).isEqualTo(3.0);
        }

        @Test
        @DisplayName("uniform scale sets all three axes")
        void uniformScale() {
            var schema = new TestScene();
            schema.scale(2.5);

            assertThat(schema.scaleX()).isEqualTo(2.5);
            assertThat(schema.scaleY()).isEqualTo(2.5);
            assertThat(schema.scaleZ()).isEqualTo(2.5);
        }

        @Test
        @DisplayName("value can be set and retrieved")
        void valueBinding() {
            var schema = new TestScene();
            schema.value("hello");

            assertThat(schema.value()).isEqualTo("hello");
        }
    }

    // ==================================================================================
    // @Scene.Body and @Scene.* Annotations
    // ==================================================================================

    @Nested
    @DisplayName("@Scene.Body and @Scene.* annotations")
    class Annotations {

        @Test
        @DisplayName("@Scene.Body annotation has correct defaults")
        void bodyDefaults() {
            Scene.Body body = AnnotatedComponent.class.getAnnotation(Scene.Body.class);

            assertThat(body).isNotNull();
            assertThat(body.shape()).isEqualTo("box");
            assertThat(body.width()).isEqualTo("2m");
            assertThat(body.height()).isEqualTo("1m");
            assertThat(body.depth()).isEqualTo("1m");
            assertThat(body.color()).isEqualTo(0xFF0000);
            assertThat(body.opacity()).isEqualTo(1.0);
            assertThat(body.shading()).isEqualTo("lit");
        }

        @Test
        @DisplayName("@Scene.Transform annotation is readable")
        void placementReadable() {
            Scene.Transform transform = AnnotatedComponent.class.getAnnotation(Scene.Transform.class);

            assertThat(transform).isNotNull();
            assertThat(transform.x()).isEqualTo("1m");
            assertThat(transform.y()).isEqualTo("2m");
            assertThat(transform.z()).isEqualTo("3m");
            assertThat(transform.yaw()).isEqualTo(45.0);
        }

        @Test
        @DisplayName("@Scene.Light annotation is readable")
        void lightReadable() {
            Scene.Light light = AnnotatedSpace.class.getAnnotation(Scene.Light.class);

            assertThat(light).isNotNull();
            assertThat(light.type()).isEqualTo("point");
            assertThat(light.color()).isEqualTo(0xFFE4B5);
            assertThat(light.intensity()).isEqualTo(2.0);
            assertThat(light.y()).isEqualTo("8m");
        }

        @Test
        @DisplayName("@Scene.Camera annotation is readable")
        void cameraReadable() {
            Scene.Camera cam = AnnotatedSpace.class.getAnnotation(Scene.Camera.class);

            assertThat(cam).isNotNull();
            assertThat(cam.projection()).isEqualTo("perspective");
            assertThat(cam.fov()).isEqualTo(45.0);
            assertThat(cam.y()).isEqualTo("3m");
            assertThat(cam.z()).isEqualTo("8m");
        }

        @Test
        @DisplayName("@Scene.Environment annotation is readable")
        void environmentReadable() {
            Scene.Environment env = AnnotatedSpace.class.getAnnotation(Scene.Environment.class);

            assertThat(env).isNotNull();
            assertThat(env.background()).isEqualTo(0x2A2A3E);
            assertThat(env.ambient()).isEqualTo(0x505050);
            assertThat(env.fogNear()).isEqualTo("10m");
            assertThat(env.fogFar()).isEqualTo("100m");
        }

        @Test
        @DisplayName("@Scene.Face annotation has defaults")
        void faceDefaults() {
            Scene.Face face = FaceComponent.class.getAnnotation(Scene.Face.class);

            assertThat(face).isNotNull();
            assertThat(face.value()).isEqualTo("top");
            assertThat(face.ppm()).isEqualTo(512);
        }
    }

    // ==================================================================================
    // ItemSpace
    // ==================================================================================

    @Nested
    @DisplayName("ItemSpace")
    class ItemSpaceTest {

        @Test
        @DisplayName("has @Scene.Environment annotation")
        void hasEnvironment() {
            Scene.Environment env = ItemSpace.class.getAnnotation(Scene.Environment.class);

            assertThat(env).isNotNull();
            assertThat(env.background()).isEqualTo(0xF2F2F2);
            assertThat(env.ambient()).isEqualTo(0x808080);
        }

        @Test
        @DisplayName("has @Scene.Light annotation")
        void hasLight() {
            Scene.Light light = ItemSpace.class.getAnnotation(Scene.Light.class);

            assertThat(light).isNotNull();
            assertThat(light.type()).isEqualTo("directional");
            assertThat(light.intensity()).isEqualTo(0.8);
        }

        @Test
        @DisplayName("has @Scene.Camera annotation")
        void hasCamera() {
            Scene.Camera cam = ItemSpace.class.getAnnotation(Scene.Camera.class);

            assertThat(cam).isNotNull();
            assertThat(cam.y()).isEqualTo("1.5m");
            assertThat(cam.z()).isEqualTo("1m");
            assertThat(cam.targetZ()).isEqualTo("1m");
        }

        @Test
        @DisplayName("renders environment, light, and camera")
        void rendersSceneSetup() {
            var scene = new ItemSpace();
            var recorder = new RecordingSpatialRenderer();

            scene.render(recorder);

            assertThat(recorder.calls).contains(
                    "environment(15921906,8421504,-1.0,-1.0,8421504)",
                    "light(directional,16777215,0.8,0.0,0.0,10.0,0.0,0.0,-1.0)",
                    "camera(perspective,60.0,0.1,1000.0,0.0,1.5,1.0,0.0,0.0,1.0)"
            );
        }
    }

    // ==================================================================================
    // SpatialRenderer
    // ==================================================================================

    @Nested
    @DisplayName("SpatialRenderer")
    class RendererTest {

        @Test
        @DisplayName("scene emits common properties before rendering")
        void emitsId() {
            var scene = new TestScene();
            scene.id("my-scene");

            var recorder = new RecordingSpatialRenderer();
            scene.render(recorder);

            assertThat(recorder.calls.get(0)).isEqualTo("id(my-scene)");
        }
    }

    // ==================================================================================
    // Test Fixtures
    // ==================================================================================

    /**
     * A minimal concrete SpatialSchema for testing.
     */
    static class TestScene extends SpatialSchema<String> {
        @Override
        public void render(SpatialRenderer out) {
            emitCommonProperties(out);
            out.body("box", 1, 1, 1, 0xCCCCCC, 1.0, "lit", List.of());
        }
    }

    @Scene.Body(shape = "box", width = "2m", color = 0xFF0000)
    @Scene.Transform(x = "1m", y = "2m", z = "3m", yaw = 45)
    static class AnnotatedComponent {}

    @Scene.Light(type = "point", color = 0xFFE4B5, intensity = 2.0, y = "8m")
    @Scene.Camera(fov = 45, y = "3m", z = "8m")
    @Scene.Environment(background = 0x2A2A3E, ambient = 0x505050, fogNear = "10m", fogFar = "100m")
    static class AnnotatedSpace {}

    @Scene.Face
    static class FaceComponent {}

    /**
     * A SpatialRenderer that records all calls for assertion.
     */
    static class RecordingSpatialRenderer implements SpatialRenderer {
        final List<String> calls = new ArrayList<>();

        @Override
        public void body(String shape, double w, double h, double d,
                          int color, double opacity, String shading, List<String> styles) {
            calls.add("body(%s,%.1f,%.1f,%.1f,%d,%.1f,%s)".formatted(shape, w, h, d, color, opacity, shading));
        }

        @Override
        public void meshBody(String meshRef, int color, double opacity, String shading, List<String> styles) {
            calls.add("meshBody(%s,%d,%.1f,%s)".formatted(meshRef, color, opacity, shading));
        }

        @Override
        public void pushTransform(double x, double y, double z,
                                   double qx, double qy, double qz, double qw,
                                   double sx, double sy, double sz) {
            calls.add("pushTransform(%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f)"
                    .formatted(x, y, z, qx, qy, qz, qw, sx, sy, sz));
        }

        @Override
        public void popTransform() {
            calls.add("popTransform()");
        }

        @Override
        public void beginPanel(double width, double height, double ppm) {
            calls.add("beginPanel(%.1f,%.1f,%.1f)".formatted(width, height, ppm));
        }

        @Override
        public SurfaceRenderer panelRenderer() {
            return null;
        }

        @Override
        public void endPanel() {
            calls.add("endPanel()");
        }

        @Override
        public void light(String type, int color, double intensity,
                           double x, double y, double z,
                           double dirX, double dirY, double dirZ) {
            calls.add("light(%s,%d,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f)"
                    .formatted(type, color, intensity, x, y, z, dirX, dirY, dirZ));
        }

        @Override
        public void camera(String projection, double fov, double near, double far,
                            double x, double y, double z,
                            double tx, double ty, double tz) {
            calls.add("camera(%s,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f)"
                    .formatted(projection, fov, near, far, x, y, z, tx, ty, tz));
        }

        @Override
        public void environment(int background, int ambient,
                                 double fogNear, double fogFar, int fogColor) {
            calls.add("environment(%d,%d,%.1f,%.1f,%d)"
                    .formatted(background, ambient, fogNear, fogFar, fogColor));
        }

        @Override
        public void id(String id) {
            calls.add("id(%s)".formatted(id));
        }
    }
}
