package dev.everydaythings.graph.surface.primitive;

import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;
import dev.everydaythings.graph.ui.scene.SceneCompiler;
import dev.everydaythings.graph.ui.scene.ViewNode;
import dev.everydaythings.graph.surface.harness.RenderResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for @Scene.Shape annotation primitive.
 *
 * <p>Shape is an annotation-based primitive (not a standalone SurfaceSchema class).
 * It's used in declarative surface definitions via @Scene.Shape on nested
 * static classes, and processed by SceneCompiler.
 *
 * <p>Shape can be:
 * <ul>
 *   <li>Standalone - a visual element with no children</li>
 *   <li>Combined with @Scene.Container - adds visible boundary to container</li>
 * </ul>
 *
 * <p>Tests cover:
 * <ul>
 *   <li>SceneCompiler recognition of @Scene.Shape</li>
 *   <li>Shape types: rectangle, circle, ellipse, line, path</li>
 *   <li>Shape properties: cornerRadius, fill, stroke</li>
 *   <li>Container + Shape composition</li>
 * </ul>
 */
@DisplayName("Shape Primitive (Annotation-Based)")
class ShapePrimitiveTest {

    // ==================================================================================
    // SceneCompiler Recognition
    // ==================================================================================

    @Nested
    @DisplayName("SceneCompiler Recognition")
    class CompilerRecognition {

        @Test
        @DisplayName("recognizes standalone @Scene.Shape")
        void recognizesStandaloneShape() {
            // StandaloneShapeSurface has @Scene.Shape on itself
            boolean canRender = SceneCompiler.canCompile(StandaloneShapeSurface.class);

            assertThat(canRender).isTrue();
        }

        @Test
        @DisplayName("recognizes Container + Shape composition")
        void recognizesContainerWithShape() {
            boolean canRender = SceneCompiler.canCompile(ContainerWithShapeSurface.class);

            assertThat(canRender).isTrue();
        }

        @Test
        @DisplayName("compiles shape to SHAPE node type")
        void compilesToShapeNodeType() {
            ViewNode node = SceneCompiler.getCompiled(StandaloneShapeSurface.class);

            assertThat(node).isNotNull();
            assertThat(node.type).isEqualTo(ViewNode.NodeType.SHAPE);
        }

        @Test
        @DisplayName("compiles container+shape to CONTAINER node type with shape properties")
        void compilesContainerShapeToContainer() {
            ViewNode node = SceneCompiler.getCompiled(ContainerWithShapeSurface.class);

            assertThat(node).isNotNull();
            assertThat(node.type).isEqualTo(ViewNode.NodeType.CONTAINER);
            assertThat(node.hasShape()).isTrue();
        }
    }

    // ==================================================================================
    // Shape Types
    // ==================================================================================

    @Nested
    @DisplayName("Shape Types")
    class ShapeTypes {

        @Test
        @DisplayName("rectangle shape type is captured")
        void rectangleShapeType() {
            ViewNode node = SceneCompiler.getCompiled(RectangleShapeSurface.class);

            assertThat(node.shapeType).isEqualTo("rectangle");
        }

        @Test
        @DisplayName("circle shape type is captured")
        void circleShapeType() {
            ViewNode node = SceneCompiler.getCompiled(CircleShapeSurface.class);

            assertThat(node.shapeType).isEqualTo("circle");
        }

        @Test
        @DisplayName("line shape type is captured")
        void lineShapeType() {
            ViewNode node = SceneCompiler.getCompiled(LineShapeSurface.class);

            assertThat(node.shapeType).isEqualTo("line");
        }
    }

    // ==================================================================================
    // Shape Properties
    // ==================================================================================

    @Nested
    @DisplayName("Shape Properties")
    class ShapeProperties {

        @Test
        @DisplayName("cornerRadius is captured")
        void cornerRadiusCaptured() {
            ViewNode node = SceneCompiler.getCompiled(RoundedRectangleSurface.class);

            assertThat(node.cornerRadius).isEqualTo("8px");
        }

        @Test
        @DisplayName("fill is captured")
        void fillCaptured() {
            ViewNode node = SceneCompiler.getCompiled(FilledShapeSurface.class);

            assertThat(node.shapeFill).isEqualTo("blue");
        }

        @Test
        @DisplayName("stroke is captured")
        void strokeCaptured() {
            ViewNode node = SceneCompiler.getCompiled(StrokedShapeSurface.class);

            assertThat(node.shapeStroke).isEqualTo("red");
        }

        @Test
        @DisplayName("strokeWidth is captured")
        void strokeWidthCaptured() {
            ViewNode node = SceneCompiler.getCompiled(StrokedShapeSurface.class);

            assertThat(node.shapeStrokeWidth).isEqualTo("2px");
        }

        @Test
        @DisplayName("pill cornerRadius is captured")
        void pillCornerRadius() {
            ViewNode node = SceneCompiler.getCompiled(PillShapeSurface.class);

            assertThat(node.cornerRadius).isEqualTo("pill");
        }
    }

    // ==================================================================================
    // Container + Shape Shorthand
    // ==================================================================================

    @Nested
    @DisplayName("Container Shape Shorthand")
    class ContainerShapeShorthand {

        @Test
        @DisplayName("shape attribute on @Container is captured")
        void shapeAttributeOnContainer() {
            ViewNode node = SceneCompiler.getCompiled(ContainerWithShapeAttributeSurface.class);

            assertThat(node.type).isEqualTo(ViewNode.NodeType.CONTAINER);
            assertThat(node.shapeType).isEqualTo("rectangle");
        }

        @Test
        @DisplayName("cornerRadius on @Container is captured")
        void cornerRadiusOnContainer() {
            ViewNode node = SceneCompiler.getCompiled(ContainerWithShapeAttributeSurface.class);

            assertThat(node.cornerRadius).isEqualTo("medium");
        }
    }

    // ==================================================================================
    // Rendering
    // ==================================================================================

    @Nested
    @DisplayName("Rendering")
    class Rendering {

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("standalone shape renders without error")
        void standaloneShapeRenders(String rendererName, SurfaceRenderer renderer) {
            StandaloneShapeSurface surface = new StandaloneShapeSurface();

            // Should not throw
            RenderResult result = RenderResult.capture(surface, renderer);
            assertThat(result).isNotNull();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("container with shape renders without error")
        void containerWithShapeRenders(String rendererName, SurfaceRenderer renderer) {
            ContainerWithShapeSurface surface = new ContainerWithShapeSurface();

            RenderResult result = RenderResult.capture(surface, renderer);
            assertThat(result).isNotNull();
        }

        // TODO: More detailed rendering tests once SurfaceRenderer.shape()
        // implementation details are finalized for text renderers.
        // Currently, shapes are primarily visual and may not produce
        // visible output in text mode.
    }

    // ==================================================================================
    // Test Surface Definitions
    // ==================================================================================

    /**
     * Standalone shape (no container).
     */
    @Scene.Shape(type = "rectangle")
    static class StandaloneShapeSurface extends SurfaceSchema<Void> {
        @Override
        public void render(SurfaceRenderer out) {
            SceneCompiler.render(this, out);
        }
    }

    /**
     * Container with separate @Shape annotation.
     */
    @Scene.Container(direction = Scene.Direction.HORIZONTAL)
    @Scene.Shape(type = "rectangle", cornerRadius = "8px")
    static class ContainerWithShapeSurface extends SurfaceSchema<Void> {
        @Override
        public void render(SurfaceRenderer out) {
            SceneCompiler.render(this, out);
        }
    }

    /**
     * Container using shape attribute shorthand.
     */
    @Scene.Container(shape = "rectangle", cornerRadius = "medium")
    static class ContainerWithShapeAttributeSurface extends SurfaceSchema<Void> {
        @Override
        public void render(SurfaceRenderer out) {
            SceneCompiler.render(this, out);
        }
    }

    /**
     * Rectangle shape.
     */
    @Scene.Shape(type = "rectangle")
    static class RectangleShapeSurface extends SurfaceSchema<Void> {
        @Override
        public void render(SurfaceRenderer out) {
            SceneCompiler.render(this, out);
        }
    }

    /**
     * Circle shape.
     */
    @Scene.Shape(type = "circle")
    static class CircleShapeSurface extends SurfaceSchema<Void> {
        @Override
        public void render(SurfaceRenderer out) {
            SceneCompiler.render(this, out);
        }
    }

    /**
     * Line shape.
     */
    @Scene.Shape(type = "line")
    static class LineShapeSurface extends SurfaceSchema<Void> {
        @Override
        public void render(SurfaceRenderer out) {
            SceneCompiler.render(this, out);
        }
    }

    /**
     * Rounded rectangle with explicit cornerRadius.
     */
    @Scene.Shape(type = "rectangle", cornerRadius = "8px")
    static class RoundedRectangleSurface extends SurfaceSchema<Void> {
        @Override
        public void render(SurfaceRenderer out) {
            SceneCompiler.render(this, out);
        }
    }

    /**
     * Filled shape.
     */
    @Scene.Shape(type = "circle", fill = "blue")
    static class FilledShapeSurface extends SurfaceSchema<Void> {
        @Override
        public void render(SurfaceRenderer out) {
            SceneCompiler.render(this, out);
        }
    }

    /**
     * Stroked shape.
     */
    @Scene.Shape(type = "rectangle", stroke = "red", strokeWidth = "2px")
    static class StrokedShapeSurface extends SurfaceSchema<Void> {
        @Override
        public void render(SurfaceRenderer out) {
            SceneCompiler.render(this, out);
        }
    }

    /**
     * Pill-shaped (fully rounded rectangle).
     */
    @Scene.Shape(type = "rectangle", cornerRadius = "pill")
    static class PillShapeSurface extends SurfaceSchema<Void> {
        @Override
        public void render(SurfaceRenderer out) {
            SceneCompiler.render(this, out);
        }
    }
}
