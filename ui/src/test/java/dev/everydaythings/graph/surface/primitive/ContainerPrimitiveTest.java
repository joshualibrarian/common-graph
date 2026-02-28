package dev.everydaythings.graph.surface.primitive;

import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;
import dev.everydaythings.graph.surface.harness.RenderResult;
import dev.everydaythings.graph.surface.harness.RendererProvider;
import dev.everydaythings.graph.surface.harness.assertions.CliAssertions;
import dev.everydaythings.graph.ui.scene.surface.primitive.ContainerSurface;
import dev.everydaythings.graph.ui.scene.surface.primitive.TextSurface;
import dev.everydaythings.graph.ui.text.CliSurfaceRenderer;
import dev.everydaythings.graph.ui.text.TuiSurfaceRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ContainerSurface primitive across all renderers.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Empty containers</li>
 *   <li>Single child containers</li>
 *   <li>Multiple children with different directions</li>
 *   <li>Nested containers</li>
 *   <li>Style application to containers</li>
 * </ul>
 */
@DisplayName("ContainerSurface Primitive")
class ContainerPrimitiveTest {

    // ==================================================================================
    // Empty Container
    // ==================================================================================

    @Nested
    @DisplayName("Empty Container")
    class EmptyContainer {

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("renders empty vertical container")
        void rendersEmptyVertical(String rendererName, SurfaceRenderer renderer) {
            ContainerSurface container = ContainerSurface.vertical();

            RenderResult result = RenderResult.capture(container, renderer);

            // Empty container should not throw
            assertThat(result).isNotNull();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("renders empty horizontal container")
        void rendersEmptyHorizontal(String rendererName, SurfaceRenderer renderer) {
            ContainerSurface container = ContainerSurface.horizontal();

            RenderResult result = RenderResult.capture(container, renderer);

            assertThat(result).isNotNull();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("empty container has no children")
        void emptyContainerNoChildren(String rendererName, SurfaceRenderer renderer) {
            ContainerSurface container = ContainerSurface.vertical();

            assertThat(container.hasChildren()).isFalse();
            assertThat(container.children()).isEmpty();
        }
    }

    // ==================================================================================
    // Single Child
    // ==================================================================================

    @Nested
    @DisplayName("Single Child")
    class SingleChild {

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("renders single text child")
        void rendersSingleTextChild(String rendererName, SurfaceRenderer renderer) {
            ContainerSurface container = ContainerSurface.vertical()
                .add(TextSurface.of("Child Content"));

            RenderResult result = RenderResult.capture(container, renderer);

            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("Child Content");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("renders single text child via string convenience")
        void rendersSingleTextChildString(String rendererName, SurfaceRenderer renderer) {
            ContainerSurface container = ContainerSurface.horizontal()
                .add("Simple Text");

            RenderResult result = RenderResult.capture(container, renderer);

            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("Simple Text");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("container with one child has children")
        void containerWithOneChildHasChildren(String rendererName, SurfaceRenderer renderer) {
            ContainerSurface container = ContainerSurface.vertical()
                .add("Child");

            assertThat(container.hasChildren()).isTrue();
            assertThat(container.children()).hasSize(1);
        }
    }

    // ==================================================================================
    // Multiple Children
    // ==================================================================================

    @Nested
    @DisplayName("Multiple Children")
    class MultipleChildren {

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("renders multiple text children vertically")
        void rendersMultipleChildrenVertical(String rendererName, SurfaceRenderer renderer) {
            ContainerSurface container = ContainerSurface.vertical()
                .add("First")
                .add("Second")
                .add("Third");

            RenderResult result = RenderResult.capture(container, renderer);

            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("First");
            assertThat(textResult.stripAnsi()).contains("Second");
            assertThat(textResult.stripAnsi()).contains("Third");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("renders multiple text children horizontally")
        void rendersMultipleChildrenHorizontal(String rendererName, SurfaceRenderer renderer) {
            ContainerSurface container = ContainerSurface.horizontal()
                .add("Left")
                .add("Center")
                .add("Right");

            RenderResult result = RenderResult.capture(container, renderer);

            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("Left");
            assertThat(textResult.stripAnsi()).contains("Center");
            assertThat(textResult.stripAnsi()).contains("Right");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("addAll adds multiple children")
        void addAllAddsMultipleChildren(String rendererName, SurfaceRenderer renderer) {
            ContainerSurface container = ContainerSurface.vertical()
                .addAll(List.of(
                    TextSurface.of("Item A"),
                    TextSurface.of("Item B"),
                    TextSurface.of("Item C")
                ));

            assertThat(container.children()).hasSize(3);

            RenderResult result = RenderResult.capture(container, renderer);
            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("Item A");
            assertThat(textResult.stripAnsi()).contains("Item B");
            assertThat(textResult.stripAnsi()).contains("Item C");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("children() replaces existing children")
        void childrenReplacesExisting(String rendererName, SurfaceRenderer renderer) {
            ContainerSurface container = ContainerSurface.vertical()
                .add("Original")
                .children(List.of(
                    TextSurface.of("Replacement 1"),
                    TextSurface.of("Replacement 2")
                ));

            assertThat(container.children()).hasSize(2);

            RenderResult result = RenderResult.capture(container, renderer);
            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).doesNotContain("Original");
            assertThat(textResult.stripAnsi()).contains("Replacement 1");
            assertThat(textResult.stripAnsi()).contains("Replacement 2");
        }
    }

    // ==================================================================================
    // Nested Containers
    // ==================================================================================

    @Nested
    @DisplayName("Nested Containers")
    class NestedContainers {

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("renders nested containers")
        void rendersNestedContainers(String rendererName, SurfaceRenderer renderer) {
            ContainerSurface outer = ContainerSurface.vertical()
                .add(ContainerSurface.horizontal()
                    .add("Nested 1")
                    .add("Nested 2"))
                .add("After Nested");

            RenderResult result = RenderResult.capture(outer, renderer);

            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("Nested 1");
            assertThat(textResult.stripAnsi()).contains("Nested 2");
            assertThat(textResult.stripAnsi()).contains("After Nested");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("renders deeply nested containers")
        void rendersDeeplyNested(String rendererName, SurfaceRenderer renderer) {
            ContainerSurface level3 = ContainerSurface.horizontal().add("Deep");
            ContainerSurface level2 = ContainerSurface.vertical().add(level3);
            ContainerSurface level1 = ContainerSurface.horizontal().add(level2);
            ContainerSurface root = ContainerSurface.vertical().add(level1);

            RenderResult result = RenderResult.capture(root, renderer);

            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("Deep");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("alternating directions render correctly")
        void alternatingDirections(String rendererName, SurfaceRenderer renderer) {
            // V -> H -> V -> H pattern
            ContainerSurface container = ContainerSurface.vertical()
                .add(ContainerSurface.horizontal()
                    .add(ContainerSurface.vertical()
                        .add("A")
                        .add("B"))
                    .add(ContainerSurface.vertical()
                        .add("C")
                        .add("D")));

            RenderResult result = RenderResult.capture(container, renderer);

            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("A");
            assertThat(textResult.stripAnsi()).contains("B");
            assertThat(textResult.stripAnsi()).contains("C");
            assertThat(textResult.stripAnsi()).contains("D");
        }
    }

    // ==================================================================================
    // Direction
    // ==================================================================================

    @Nested
    @DisplayName("Direction")
    class Direction {

        @Test
        @DisplayName("default direction is vertical")
        void defaultDirectionIsVertical() {
            ContainerSurface container = new ContainerSurface();

            assertThat(container.direction()).isEqualTo(Scene.Direction.VERTICAL);
        }

        @Test
        @DisplayName("factory methods set correct direction")
        void factoryMethodsSetDirection() {
            assertThat(ContainerSurface.vertical().direction())
                .isEqualTo(Scene.Direction.VERTICAL);
            assertThat(ContainerSurface.horizontal().direction())
                .isEqualTo(Scene.Direction.HORIZONTAL);
        }

        @Test
        @DisplayName("direction can be changed fluently")
        void directionCanBeChanged() {
            ContainerSurface container = ContainerSurface.vertical()
                .direction(Scene.Direction.HORIZONTAL);

            assertThat(container.direction()).isEqualTo(Scene.Direction.HORIZONTAL);
        }
    }

    // ==================================================================================
    // Styled Containers
    // ==================================================================================

    @Nested
    @DisplayName("Styled Containers")
    class StyledContainers {

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("container with styles renders")
        void containerWithStylesRenders(String rendererName, SurfaceRenderer renderer) {
            ContainerSurface container = ContainerSurface.vertical()
                .add("Card Content");
            container.style("card", "elevated");

            RenderResult result = RenderResult.capture(container, renderer);

            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("Card Content");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("container with gap renders")
        void containerWithGapRenders(String rendererName, SurfaceRenderer renderer) {
            ContainerSurface container = ContainerSurface.vertical()
                .gap("1em")
                .add("Item 1")
                .add("Item 2");

            assertThat(container.gap()).isEqualTo("1em");

            RenderResult result = RenderResult.capture(container, renderer);
            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("Item 1");
            assertThat(textResult.stripAnsi()).contains("Item 2");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("capturesFocus property works")
        void capturesFocusPropertyWorks(String rendererName, SurfaceRenderer renderer) {
            ContainerSurface container = ContainerSurface.vertical()
                .capturesFocus(true)
                .add("Focusable Content");

            assertThat(container.capturesFocus()).isTrue();

            RenderResult result = RenderResult.capture(container, renderer);
            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("Focusable Content");
        }
    }

    // ==================================================================================
    // TUI-Specific
    // ==================================================================================

    @Nested
    @DisplayName("TUI Renderer Specific")
    class TuiSpecific {

        @Test
        @DisplayName("vertical container produces newlines between children")
        void verticalContainerProducesNewlines() {
            ContainerSurface container = ContainerSurface.vertical()
                .add("Line A")
                .add("Line B");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            container.render(renderer);
            String output = renderer.result();

            // Should have multiple lines
            long lineCount = output.lines().count();
            assertThat(lineCount).isGreaterThan(1);
        }
    }

    // ==================================================================================
    // CLI-Specific
    // ==================================================================================

    @Nested
    @DisplayName("CLI Renderer Specific")
    class CliSpecific {

        @Test
        @DisplayName("container output has no ANSI codes")
        void containerOutputNoAnsi() {
            ContainerSurface container = ContainerSurface.vertical()
                .add("Content");
            container.style("styled");

            CliSurfaceRenderer renderer = new CliSurfaceRenderer();
            container.render(renderer);
            String output = renderer.result();

            CliAssertions.assertNoAnsi(
                new RenderResult.TextResult(output, RendererProvider.RendererType.CLI));
        }
    }

    // ==================================================================================
    // Mixed Content
    // ==================================================================================

    @Nested
    @DisplayName("Mixed Content")
    class MixedContent {

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("container with text and nested containers")
        void mixedTextAndContainers(String rendererName, SurfaceRenderer renderer) {
            ContainerSurface container = ContainerSurface.vertical()
                .add("Header")
                .add(ContainerSurface.horizontal()
                    .add("Col 1")
                    .add("Col 2"))
                .add("Footer");

            RenderResult result = RenderResult.capture(container, renderer);

            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("Header");
            assertThat(textResult.stripAnsi()).contains("Col 1");
            assertThat(textResult.stripAnsi()).contains("Col 2");
            assertThat(textResult.stripAnsi()).contains("Footer");
        }
    }
}
