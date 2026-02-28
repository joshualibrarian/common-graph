package dev.everydaythings.graph.surface.harness;

import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;
import dev.everydaythings.graph.ui.text.CliSurfaceRenderer;
import dev.everydaythings.graph.ui.text.TuiSurfaceRenderer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Wrapper for renderer output that provides unified access for assertions.
 *
 * <p>Captures the result of rendering a surface and provides helper methods
 * for common assertion patterns across different renderer types.
 */
@Getter
@Accessors(fluent = true)
public abstract class RenderResult {

    /**
     * Render a surface and capture the result.
     *
     * @param surface  The surface to render
     * @param renderer The renderer to use
     * @return A RenderResult appropriate for the renderer type
     */
    public static RenderResult capture(SurfaceSchema<?> surface, SurfaceRenderer renderer) {
        surface.render(renderer);

        if (renderer instanceof TuiSurfaceRenderer tui) {
            return new TextResult(tui.result(), RendererProvider.RendererType.TUI);
        } else if (renderer instanceof CliSurfaceRenderer cli) {
            return new TextResult(cli.result(), RendererProvider.RendererType.CLI);
        }
        throw new IllegalArgumentException("Unknown renderer: " + renderer.getClass());
    }

    /**
     * Result from text-based renderers (TUI, CLI).
     */
    @Getter
    @Accessors(fluent = true)
    @RequiredArgsConstructor
    public static class TextResult extends RenderResult {

        private final String output;
        private final RendererProvider.RendererType type;

        /**
         * Check if output contains the given text.
         */
        public boolean contains(String text) {
            return output != null && output.contains(text);
        }

        /**
         * Check if output contains an ANSI escape code.
         * Only meaningful for TUI renderer.
         */
        public boolean containsAnsi(String ansiCode) {
            return type == RendererProvider.RendererType.TUI && output != null && output.contains(ansiCode);
        }

        /**
         * Strip all ANSI escape codes from output.
         */
        public String stripAnsi() {
            if (output == null) return "";
            return output.replaceAll("\u001B\\[[0-9;]*m", "");
        }

        /**
         * Count the number of lines in output.
         */
        public int lineCount() {
            if (output == null || output.isEmpty()) return 0;
            return (int) output.lines().count();
        }

        /**
         * Check if this is a TUI result.
         */
        public boolean isTui() {
            return type == RendererProvider.RendererType.TUI;
        }

        /**
         * Check if this is a CLI result.
         */
        public boolean isCli() {
            return type == RendererProvider.RendererType.CLI;
        }

        @Override
        public String toString() {
            return "TextResult[" + type + "]: " + (output != null ? output.length() + " chars" : "null");
        }
    }
}
