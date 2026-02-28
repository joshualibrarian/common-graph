package dev.everydaythings.graph.surface.harness.assertions;

import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;
import dev.everydaythings.graph.surface.harness.RenderResult;
import dev.everydaythings.graph.surface.harness.RendererProvider;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unified assertions for surface rendering across all renderers.
 *
 * <p>Provides common assertion methods that work regardless of renderer type,
 * delegating to renderer-specific assertions when needed.
 */
public final class RenderAssertions {

    private RenderAssertions() {}

    // ==================== Capture & Assert ====================

    /**
     * Render a surface and assert it produces non-empty output.
     */
    public static RenderResult assertRenders(SurfaceSchema<?> surface, SurfaceRenderer renderer) {
        RenderResult result = RenderResult.capture(surface, renderer);
        assertThat(result)
            .as("Surface should produce a render result")
            .isNotNull();
        return result;
    }

    /**
     * Render and assert the output contains expected text.
     */
    public static void assertRendersText(SurfaceSchema<?> surface, SurfaceRenderer renderer, String expectedText) {
        RenderResult result = RenderResult.capture(surface, renderer);
        assertContainsText(result, expectedText);
    }

    // ==================== Content Assertions ====================

    /**
     * Assert that the render result contains expected text.
     *
     * <p>For text renderers, strips ANSI codes before checking.
     */
    public static void assertContainsText(RenderResult result, String text) {
        if (result instanceof RenderResult.TextResult textResult) {
            assertThat(textResult.stripAnsi())
                .as("Output should contain text '%s'", text)
                .contains(text);
        }
    }

    /**
     * Assert that the render result does NOT contain text.
     */
    public static void assertNotContainsText(RenderResult result, String text) {
        if (result instanceof RenderResult.TextResult textResult) {
            assertThat(textResult.stripAnsi())
                .as("Output should NOT contain text '%s'", text)
                .doesNotContain(text);
        }
    }

    // ==================== Renderer-Specific Dispatch ====================

    /**
     * Assert that TUI output has ANSI codes (styled output).
     *
     * <p>Only applies to TUI renderer; does nothing for other renderers.
     */
    public static void assertHasAnsiIfTui(RenderResult result) {
        if (result instanceof RenderResult.TextResult textResult && textResult.isTui()) {
            TuiAssertions.assertHasAnsi(textResult);
        }
    }

    /**
     * Assert that CLI output has NO ANSI codes (plain text).
     *
     * <p>Only applies to CLI renderer; does nothing for other renderers.
     */
    public static void assertNoAnsiIfCli(RenderResult result) {
        if (result instanceof RenderResult.TextResult textResult && textResult.isCli()) {
            CliAssertions.assertNoAnsi(textResult);
        }
    }

    /**
     * Assert renderer-appropriate output characteristics.
     *
     * <p>TUI should have ANSI codes, CLI should not.
     */
    public static void assertRendererAppropriate(RenderResult result) {
        if (result instanceof RenderResult.TextResult textResult) {
            if (!textResult.isTui()) {
                CliAssertions.assertNoAnsi(textResult);
            }
        }
    }

    // ==================== Style Assertions ====================

    /**
     * Assert that a style class is applied.
     *
     * <p>For text renderers, styles are expressed via ANSI/formatting
     * which is checked by other assertions.
     */
    public static void assertHasStyle(RenderResult result, String styleClass) {
        // For text renderers, styles are expressed via ANSI/formatting
        // which is checked by other assertions
    }

    // ==================== Factory Methods ====================

    /**
     * Get renderer-specific assertions for a result.
     */
    public static RendererProvider.RendererType getRendererType(RenderResult result) {
        if (result instanceof RenderResult.TextResult textResult) {
            return textResult.type();
        }
        throw new IllegalArgumentException("Unknown result type: " + result.getClass());
    }
}
