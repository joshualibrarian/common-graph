package dev.everydaythings.graph.surface.composite;

import dev.everydaythings.graph.ui.scene.surface.ButtonSurface;
import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;
import dev.everydaythings.graph.surface.harness.RenderResult;
import dev.everydaythings.graph.surface.harness.RendererProvider;
import dev.everydaythings.graph.surface.harness.assertions.CliAssertions;
import dev.everydaythings.graph.ui.scene.surface.primitive.TextSurface;
import dev.everydaythings.graph.ui.text.CliSurfaceRenderer;
import dev.everydaythings.graph.ui.text.TuiSurfaceRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ButtonSurface composite across all renderers.
 *
 * <p>ButtonSurface is a clickable container that triggers an action.
 * Unlike ToggleSurface/CheckboxSurface, it extends ContainerSurface
 * and uses manual rendering (not StructureCompiler).
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Text buttons</li>
 *   <li>Icon buttons</li>
 *   <li>Icon + text buttons</li>
 *   <li>Button styles (primary, danger, ghost)</li>
 *   <li>Enabled/disabled states</li>
 *   <li>Action property</li>
 *   <li>Rich content buttons</li>
 * </ul>
 */
@DisplayName("ButtonSurface Composite")
class ButtonSurfaceTest {

    // ==================================================================================
    // Factory Methods
    // ==================================================================================

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethods {

        @Test
        @DisplayName("of(text, action) creates text button")
        void ofTextActionCreatesButton() {
            ButtonSurface button = ButtonSurface.of("Click me", "doAction");

            assertThat(button.action()).isEqualTo("doAction");
            assertThat(button.hasChildren()).isTrue();
        }

        @Test
        @DisplayName("icon(icon, action) creates icon button")
        void iconCreatesIconButton() {
            ButtonSurface button = ButtonSurface.icon("🗑️", "delete");

            assertThat(button.action()).isEqualTo("delete");
            assertThat(button.hasChildren()).isTrue();
        }

        @Test
        @DisplayName("of(icon, text, action) creates icon+text button")
        void ofIconTextActionCreatesButton() {
            ButtonSurface button = ButtonSurface.of("📤", "Submit", "submit");

            assertThat(button.action()).isEqualTo("submit");
            assertThat(button.children()).hasSize(2);
        }

        @Test
        @DisplayName("action(action) creates empty button with action")
        void actionCreatesEmptyButton() {
            ButtonSurface button = ButtonSurface.action("myAction");

            assertThat(button.action()).isEqualTo("myAction");
            assertThat(button.hasChildren()).isFalse();
        }
    }

    // ==================================================================================
    // Text Button Rendering
    // ==================================================================================

    @Nested
    @DisplayName("Text Button Rendering")
    class TextButtonRendering {

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("renders button text")
        void rendersButtonText(String rendererName, SurfaceRenderer renderer) {
            ButtonSurface button = ButtonSurface.of("Save Changes", "save");

            RenderResult result = RenderResult.capture(button, renderer);

            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("Save Changes");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("renders empty action button")
        void rendersEmptyActionButton(String rendererName, SurfaceRenderer renderer) {
            ButtonSurface button = ButtonSurface.action("empty");

            // Should not throw
            RenderResult result = RenderResult.capture(button, renderer);
            assertThat(result).isNotNull();
        }
    }

    // ==================================================================================
    // Icon Button Rendering
    // ==================================================================================

    @Nested
    @DisplayName("Icon Button Rendering")
    class IconButtonRendering {

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("renders icon button")
        void rendersIconButton(String rendererName, SurfaceRenderer renderer) {
            ButtonSurface button = ButtonSurface.icon("🗑️", "delete");

            RenderResult result = RenderResult.capture(button, renderer);

            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("🗑️");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("renders icon + text button")
        void rendersIconTextButton(String rendererName, SurfaceRenderer renderer) {
            ButtonSurface button = ButtonSurface.of("📤", "Upload", "upload");

            RenderResult result = RenderResult.capture(button, renderer);

            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("📤");
            assertThat(textResult.stripAnsi()).contains("Upload");
        }
    }

    // ==================================================================================
    // Button Styles
    // ==================================================================================

    @Nested
    @DisplayName("Button Styles")
    class ButtonStyles {

        @Test
        @DisplayName("primary() adds primary style")
        void primaryAddsStyle() {
            ButtonSurface button = ButtonSurface.of("Submit", "submit").primary();

            assertThat(button.style()).contains("primary");
        }

        @Test
        @DisplayName("danger() adds danger style")
        void dangerAddsStyle() {
            ButtonSurface button = ButtonSurface.of("Delete", "delete").danger();

            assertThat(button.style()).contains("danger");
        }

        @Test
        @DisplayName("ghost() adds ghost style")
        void ghostAddsStyle() {
            ButtonSurface button = ButtonSurface.of("Cancel", "cancel").ghost();

            assertThat(button.style()).contains("ghost");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("styled button renders without error")
        void styledButtonRenders(String rendererName, SurfaceRenderer renderer) {
            ButtonSurface button = ButtonSurface.of("Styled", "action").primary();

            RenderResult result = RenderResult.capture(button, renderer);

            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("Styled");
        }
    }

    // ==================================================================================
    // Enabled/Disabled State
    // ==================================================================================

    @Nested
    @DisplayName("Enabled/Disabled State")
    class EnabledDisabledState {

        @Test
        @DisplayName("buttons are enabled by default")
        void buttonsEnabledByDefault() {
            ButtonSurface button = ButtonSurface.of("Click", "action");

            assertThat(button.enabled()).isTrue();
        }

        @Test
        @DisplayName("enabled(false) disables button")
        void enabledFalseDisables() {
            ButtonSurface button = ButtonSurface.of("Click", "action").enabled(false);

            assertThat(button.enabled()).isFalse();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("disabled button renders without error")
        void disabledButtonRenders(String rendererName, SurfaceRenderer renderer) {
            ButtonSurface button = ButtonSurface.of("Disabled", "action").enabled(false);

            RenderResult result = RenderResult.capture(button, renderer);
            assertThat(result).isNotNull();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("disabled button still renders text")
        void disabledButtonRendersText(String rendererName, SurfaceRenderer renderer) {
            ButtonSurface button = ButtonSurface.of("Disabled Button", "action").enabled(false);

            RenderResult result = RenderResult.capture(button, renderer);

            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("Disabled Button");
        }
    }

    // ==================================================================================
    // Rich Content Buttons
    // ==================================================================================

    @Nested
    @DisplayName("Rich Content Buttons")
    class RichContentButtons {

        @Test
        @DisplayName("can add custom content to action button")
        void canAddCustomContent() {
            ButtonSurface button = ButtonSurface.action("submit")
                .add(TextSurface.of("📤"))
                .add(TextSurface.of("Submit").style("bold"));

            assertThat(button.children()).hasSize(2);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("rich content button renders all content")
        void richContentButtonRendersAll(String rendererName, SurfaceRenderer renderer) {
            ButtonSurface button = ButtonSurface.action("submit")
                .add(TextSurface.of("🚀"))
                .add(TextSurface.of("Launch"));

            RenderResult result = RenderResult.capture(button, renderer);

            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("🚀");
            assertThat(textResult.stripAnsi()).contains("Launch");
        }
    }

    // ==================================================================================
    // Action Property
    // ==================================================================================

    @Nested
    @DisplayName("Action Property")
    class ActionProperty {

        @Test
        @DisplayName("action() returns the action")
        void actionReturnsAction() {
            ButtonSurface button = ButtonSurface.of("Click", "myAction");

            assertThat(button.action()).isEqualTo("myAction");
        }

        @Test
        @DisplayName("action can be null for visual-only buttons")
        void actionCanBeNull() {
            ButtonSurface button = ButtonSurface.action(null).add("No Action");

            assertThat(button.action()).isNull();
        }
    }

    // ==================================================================================
    // TUI-Specific
    // ==================================================================================

    @Nested
    @DisplayName("TUI Renderer Specific")
    class TuiSpecific {

        @Test
        @DisplayName("button renders in TUI without error")
        void buttonRendersInTui() {
            ButtonSurface button = ButtonSurface.of("TUI Button", "action");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            button.render(renderer);
            String output = renderer.result();

            assertThat(output).contains("TUI Button");
        }

        @Test
        @DisplayName("primary button renders in TUI")
        void primaryButtonRendersInTui() {
            ButtonSurface button = ButtonSurface.of("Primary", "submit").primary();

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            button.render(renderer);
            String output = renderer.result();

            assertThat(output).contains("Primary");
        }
    }

    // ==================================================================================
    // CLI-Specific
    // ==================================================================================

    @Nested
    @DisplayName("CLI Renderer Specific")
    class CliSpecific {

        @Test
        @DisplayName("button output has no ANSI codes")
        void buttonOutputNoAnsi() {
            ButtonSurface button = ButtonSurface.of("CLI Button", "action");

            CliSurfaceRenderer renderer = new CliSurfaceRenderer();
            button.render(renderer);
            String output = renderer.result();

            CliAssertions.assertNoAnsi(
                new RenderResult.TextResult(output, RendererProvider.RendererType.CLI));
        }

        @Test
        @DisplayName("button renders text in CLI")
        void buttonRendersTextInCli() {
            ButtonSurface button = ButtonSurface.of("CLI Action", "action");

            CliSurfaceRenderer renderer = new CliSurfaceRenderer();
            button.render(renderer);
            String output = renderer.result();

            assertThat(output).contains("CLI Action");
        }
    }

    // ==================================================================================
    // Fluent API
    // ==================================================================================

    @Nested
    @DisplayName("Fluent API")
    class FluentApi {

        @Test
        @DisplayName("style methods return same instance")
        void styleMethodsReturnSameInstance() {
            ButtonSurface button = ButtonSurface.of("Button", "action");

            // Note: primary(), danger(), ghost() call style() which doesn't return ButtonSurface
            // but the button itself is modified
            ButtonSurface primary = button.primary();
            assertThat(primary).isSameAs(button);
        }

        @Test
        @DisplayName("enabled() returns same instance")
        void enabledReturnsSameInstance() {
            ButtonSurface button = ButtonSurface.of("Button", "action");

            assertThat(button.enabled(false)).isSameAs(button);
        }

        @Test
        @DisplayName("method chaining works")
        void methodChainingWorks() {
            ButtonSurface button = ButtonSurface.of("Chained", "action")
                .primary()
                .enabled(true);

            assertThat(button.style()).contains("primary");
            assertThat(button.enabled()).isTrue();
        }
    }

    // ==================================================================================
    // Edge Cases
    // ==================================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("button with empty text renders")
        void buttonWithEmptyTextRenders(String rendererName, SurfaceRenderer renderer) {
            ButtonSurface button = ButtonSurface.of("", "action");

            RenderResult result = RenderResult.capture(button, renderer);
            assertThat(result).isNotNull();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("button with multiple styles renders")
        void buttonWithMultipleStylesRenders(String rendererName, SurfaceRenderer renderer) {
            ButtonSurface button = ButtonSurface.of("Multi-styled", "action");
            button.style("custom", "extra");
            button.primary(); // This adds another style

            RenderResult result = RenderResult.capture(button, renderer);

            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("Multi-styled");
        }
    }
}
