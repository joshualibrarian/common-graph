package dev.everydaythings.graph.surface;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.component.SurfaceTemplateComponent;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.SceneCompiler;
import dev.everydaythings.graph.ui.scene.ViewNode;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;
import dev.everydaythings.graph.ui.scene.surface.primitive.ClockFace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Surface Template Storage")
class SurfaceTemplateTest {

    // ==================================================================================
    // Simple test surface for roundtrip testing
    // ==================================================================================

    @Scene.Container(direction = Scene.Direction.VERTICAL, padding = "8px", gap = "4px")
    static class SimpleTestSurface extends SurfaceSchema<Object> {

        @Scene.Text(content = "Hello", style = "heading")
        static class Title {}

        @Scene.Text(bind = "value.name", style = "body")
        static class Name {}
    }

    // ==================================================================================
    // ViewNode CBOR Roundtrip
    // ==================================================================================

    @Nested
    @DisplayName("ViewNode CBOR roundtrip")
    class ViewNodeRoundtrip {

        @Test
        @DisplayName("simple container with text children survives roundtrip")
        void simpleContainerRoundtrip() {
            ViewNode original = SceneCompiler.getCompiled(SimpleTestSurface.class);
            assertThat(original).isNotNull();
            assertThat(original.type).isEqualTo(ViewNode.NodeType.CONTAINER);

            // Encode
            byte[] bytes = original.encodeBinary(Canonical.Scope.RECORD);
            assertThat(bytes).isNotEmpty();

            // Decode
            ViewNode decoded = Canonical.decodeBinary(bytes, ViewNode.class, Canonical.Scope.RECORD);
            assertThat(decoded).isNotNull();
            assertThat(decoded.type).isEqualTo(ViewNode.NodeType.CONTAINER);
            assertThat(decoded.direction).isEqualTo(Scene.Direction.VERTICAL);
            assertThat(decoded.padding).isEqualTo("8px");
            assertThat(decoded.gap).isEqualTo("4px");

            // Children
            assertThat(decoded.children).hasSize(2);
            assertThat(decoded.children.get(0).type).isEqualTo(ViewNode.NodeType.TEXT);
            assertThat(decoded.children.get(0).textContent).isEqualTo("Hello");
            assertThat(decoded.children.get(1).type).isEqualTo(ViewNode.NodeType.TEXT);
            assertThat(decoded.children.get(1).textBind).isEqualTo("value.name");
        }

        @Test
        @DisplayName("ClockModel compiles to a multi-child STACK container")
        void clockSurfaceCompiles() {
            ViewNode clock = SceneCompiler.getCompiled(ClockFace.class);
            assertThat(clock).isNotNull();
            assertThat(clock.type).isEqualTo(ViewNode.NodeType.CONTAINER);
            assertThat(clock.direction).isEqualTo(Scene.Direction.STACK);
            assertThat(clock.children).isNotEmpty();
        }

        @Test
        @DisplayName("ClockModel survives SceneCompiler roundtrip")
        void clockSurfaceRoundtrip() {
            ViewNode original = SceneCompiler.getCompiled(ClockFace.class);
            assertThat(original).isNotNull();
            assertThat(original.type).isEqualTo(ViewNode.NodeType.CONTAINER);
            assertThat(original.children).isNotEmpty();
        }
    }

    // ==================================================================================
    // SurfaceTemplateComponent roundtrip
    // ==================================================================================

    @Nested
    @DisplayName("SurfaceTemplateComponent roundtrip")
    class TemplateComponentRoundtrip {

        @Test
        @DisplayName("compile from annotated class")
        void compileFromClass() {
            SurfaceTemplateComponent template = SurfaceTemplateComponent.compile(SimpleTestSurface.class);
            assertThat(template).isNotNull();
            assertThat(template.root()).isNotNull();
            assertThat(template.root().type).isEqualTo(ViewNode.NodeType.CONTAINER);
        }

        @Test
        @DisplayName("encode and decode preserves structure")
        void encodeDecodeRoundtrip() {
            SurfaceTemplateComponent original = SurfaceTemplateComponent.compile(SimpleTestSurface.class);
            assertThat(original).isNotNull();

            // Encode
            byte[] bytes = original.encodeBinary(Canonical.Scope.RECORD);
            assertThat(bytes).isNotEmpty();

            // Decode
            SurfaceTemplateComponent decoded = Canonical.decodeBinary(
                    bytes, SurfaceTemplateComponent.class, Canonical.Scope.RECORD);
            assertThat(decoded).isNotNull();
            assertThat(decoded.root()).isNotNull();
            assertThat(decoded.root().type).isEqualTo(ViewNode.NodeType.CONTAINER);
            assertThat(decoded.root().direction).isEqualTo(Scene.Direction.VERTICAL);
            assertThat(decoded.root().children).hasSize(2);
        }

        @Test
        @DisplayName("returns null for class without surface annotations")
        void nullForNonSurface() {
            SurfaceTemplateComponent template = SurfaceTemplateComponent.compile(String.class);
            assertThat(template).isNull();
        }
    }
}
