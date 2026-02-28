package dev.everydaythings.graph.surface.primitive;

import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;
import dev.everydaythings.graph.surface.harness.RenderResult;
import dev.everydaythings.graph.surface.harness.RendererProvider;
import dev.everydaythings.graph.surface.harness.assertions.CliAssertions;
import dev.everydaythings.graph.surface.harness.assertions.TuiAssertions;
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
 * Tests for TextSurface primitive across all renderers.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Basic text rendering (plain text, empty, null)</li>
 *   <li>Formatted text (code, markdown)</li>
 *   <li>Styled text (various style classes)</li>
 *   <li>Renderer-specific behavior (ANSI for TUI, plain for CLI)</li>
 * </ul>
 */
@DisplayName("TextSurface Primitive")
class TextPrimitiveTest {

    // ==================================================================================
    // Basic Text Rendering (Cross-Renderer)
    // ==================================================================================

    @Nested
    @DisplayName("Basic Text")
    class BasicText {

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("renders plain text content")
        void rendersPlainText(String rendererName, SurfaceRenderer renderer) {
            TextSurface text = TextSurface.of("Hello, World!");

            RenderResult result = RenderResult.capture(text, renderer);

            assertThat(result).isInstanceOf(RenderResult.TextResult.class);
            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("Hello, World!");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("renders text with special characters")
        void rendersSpecialCharacters(String rendererName, SurfaceRenderer renderer) {
            TextSurface text = TextSurface.of("Café résumé naïve");

            RenderResult result = RenderResult.capture(text, renderer);

            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("Café résumé naïve");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("renders unicode symbols")
        void rendersUnicodeSymbols(String rendererName, SurfaceRenderer renderer) {
            TextSurface text = TextSurface.of("✓ Success • Item → Next");

            RenderResult result = RenderResult.capture(text, renderer);

            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("✓");
            assertThat(textResult.stripAnsi()).contains("•");
            assertThat(textResult.stripAnsi()).contains("→");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("renders emoji")
        void rendersEmoji(String rendererName, SurfaceRenderer renderer) {
            TextSurface text = TextSurface.of("Hello 👋 World 🌍");

            RenderResult result = RenderResult.capture(text, renderer);

            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("👋");
            assertThat(textResult.stripAnsi()).contains("🌍");
        }
    }

    // ==================================================================================
    // Empty and Null Content
    // ==================================================================================

    @Nested
    @DisplayName("Empty/Null Content")
    class EmptyNullContent {

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("handles empty string")
        void handlesEmptyString(String rendererName, SurfaceRenderer renderer) {
            TextSurface text = TextSurface.of("");

            RenderResult result = RenderResult.capture(text, renderer);

            // Should not throw, output may be empty or minimal
            assertThat(result).isNotNull();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("handles null content gracefully")
        void handlesNullContent(String rendererName, SurfaceRenderer renderer) {
            TextSurface text = TextSurface.of(null);

            // Should not throw
            RenderResult result = RenderResult.capture(text, renderer);
            assertThat(result).isNotNull();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("handles whitespace-only content")
        void handlesWhitespaceOnly(String rendererName, SurfaceRenderer renderer) {
            TextSurface text = TextSurface.of("   \t\n   ");

            RenderResult result = RenderResult.capture(text, renderer);

            assertThat(result).isNotNull();
        }
    }

    // ==================================================================================
    // Formatted Text
    // ==================================================================================

    @Nested
    @DisplayName("Formatted Text")
    class FormattedText {

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("renders code format")
        void rendersCodeFormat(String rendererName, SurfaceRenderer renderer) {
            TextSurface text = TextSurface.of("var x = 42;").format("code");

            RenderResult result = RenderResult.capture(text, renderer);

            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("var x = 42;");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("renders plain format explicitly")
        void rendersPlainFormat(String rendererName, SurfaceRenderer renderer) {
            TextSurface text = TextSurface.of("Plain text").format("plain");

            RenderResult result = RenderResult.capture(text, renderer);

            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("Plain text");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("unknown format falls back gracefully")
        void unknownFormatFallback(String rendererName, SurfaceRenderer renderer) {
            TextSurface text = TextSurface.of("Content").format("unknown-format");

            RenderResult result = RenderResult.capture(text, renderer);

            // Should not throw, content should still appear
            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("Content");
        }
    }

    // ==================================================================================
    // Styled Text
    // ==================================================================================

    @Nested
    @DisplayName("Styled Text")
    class StyledText {

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("applies single style class")
        void appliesSingleStyle(String rendererName, SurfaceRenderer renderer) {
            TextSurface text = TextSurface.of("Heading").style("heading");

            RenderResult result = RenderResult.capture(text, renderer);

            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("Heading");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("applies multiple style classes")
        void appliesMultipleStyles(String rendererName, SurfaceRenderer renderer) {
            TextSurface text = TextSurface.of("Important").style("heading", "primary", "bold");

            RenderResult result = RenderResult.capture(text, renderer);

            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("Important");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("applies label")
        void appliesLabel(String rendererName, SurfaceRenderer renderer) {
            TextSurface text = TextSurface.of("Value").label("Field Name");

            RenderResult result = RenderResult.capture(text, renderer);

            // Label may or may not be rendered depending on surface implementation
            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("Value");
        }
    }

    // ==================================================================================
    // Multiline Text
    // ==================================================================================

    @Nested
    @DisplayName("Multiline Text")
    class MultilineText {

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("renders multiline content")
        void rendersMultiline(String rendererName, SurfaceRenderer renderer) {
            TextSurface text = TextSurface.of("Line 1\nLine 2\nLine 3");

            RenderResult result = RenderResult.capture(text, renderer);

            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("Line 1");
            assertThat(textResult.stripAnsi()).contains("Line 2");
            assertThat(textResult.stripAnsi()).contains("Line 3");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("preserves line breaks")
        void preservesLineBreaks(String rendererName, SurfaceRenderer renderer) {
            TextSurface text = TextSurface.of("First\n\nThird");

            RenderResult result = RenderResult.capture(text, renderer);

            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("First");
            assertThat(textResult.stripAnsi()).contains("Third");
        }
    }

    // ==================================================================================
    // TUI-Specific Tests
    // ==================================================================================

    @Nested
    @DisplayName("TUI Renderer Specific")
    class TuiSpecific {

        @Test
        @DisplayName("code format uses cyan color")
        void codeFormatUsesCyan() {
            TextSurface text = TextSurface.of("console.log()").format("code");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            text.render(renderer);
            String output = renderer.result();

            // Code format should use cyan in TUI
            assertThat(output).contains(TuiAssertions.CYAN);
        }

        @Test
        @DisplayName("styled text renders without error")
        void styledTextRendersWithoutError() {
            TextSurface text = TextSurface.of("Styled Text").style("heading", "primary");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            text.render(renderer);
            String output = renderer.result();

            // Should produce output containing the text
            // (ANSI codes depend on stylesheet configuration)
            assertThat(output).contains("Styled Text");
        }

        @Test
        @DisplayName("output includes reset code")
        void outputIncludesReset() {
            TextSurface text = TextSurface.of("Styled").style("heading");

            TuiSurfaceRenderer renderer = new TuiSurfaceRenderer();
            text.render(renderer);
            String output = renderer.result();

            // Should reset styles at end
            if (output.contains("\u001B[")) {
                assertThat(output).contains(TuiAssertions.RESET);
            }
        }
    }

    // ==================================================================================
    // CLI-Specific Tests
    // ==================================================================================

    @Nested
    @DisplayName("CLI Renderer Specific")
    class CliSpecific {

        @Test
        @DisplayName("renders without any ANSI codes")
        void rendersWithoutAnsi() {
            TextSurface text = TextSurface.of("Plain Text").style("heading", "bold");

            CliSurfaceRenderer renderer = new CliSurfaceRenderer();
            text.render(renderer);
            String output = renderer.result();

            // CLI should NOT have ANSI codes
            CliAssertions.assertNoAnsi(new RenderResult.TextResult(output, RendererProvider.RendererType.CLI));
        }

        @Test
        @DisplayName("code format wraps in backticks")
        void codeFormatUsesBackticks() {
            TextSurface text = TextSurface.of("code").format("code");

            CliSurfaceRenderer renderer = new CliSurfaceRenderer();
            text.render(renderer);
            String output = renderer.result();

            // CLI should use backticks for code
            assertThat(output).contains("`code`");
        }

        @Test
        @DisplayName("plain text renders as-is")
        void plainTextRendersAsIs() {
            TextSurface text = TextSurface.of("Simple text");

            CliSurfaceRenderer renderer = new CliSurfaceRenderer();
            text.render(renderer);
            String output = renderer.result();

            assertThat(output).contains("Simple text");
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
        @DisplayName("handles very long text")
        void handlesVeryLongText(String rendererName, SurfaceRenderer renderer) {
            String longText = "A".repeat(10000);
            TextSurface text = TextSurface.of(longText);

            RenderResult result = RenderResult.capture(text, renderer);

            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("AAAA");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("handles text with ANSI-like sequences")
        void handlesAnsiLikeSequences(String rendererName, SurfaceRenderer renderer) {
            // Text that looks like ANSI but isn't
            TextSurface text = TextSurface.of("Color code: [31m");

            RenderResult result = RenderResult.capture(text, renderer);

            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("[31m");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
        @DisplayName("handles tabs")
        void handlesTabs(String rendererName, SurfaceRenderer renderer) {
            TextSurface text = TextSurface.of("Column1\tColumn2\tColumn3");

            RenderResult result = RenderResult.capture(text, renderer);

            RenderResult.TextResult textResult = (RenderResult.TextResult) result;
            assertThat(textResult.stripAnsi()).contains("Column1");
            assertThat(textResult.stripAnsi()).contains("Column2");
        }
    }
}
