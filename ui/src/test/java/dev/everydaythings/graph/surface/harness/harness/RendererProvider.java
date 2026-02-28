package dev.everydaythings.graph.surface.harness;

import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;
import dev.everydaythings.graph.ui.text.CliSurfaceRenderer;
import dev.everydaythings.graph.ui.text.TuiSurfaceRenderer;
import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;

/**
 * Provides renderer instances for parameterized tests.
 *
 * <p>Used with @MethodSource to test surfaces across all renderers.
 *
 * <p>Usage:
 * <pre>{@code
 * @ParameterizedTest(name = "{0}")
 * @MethodSource("dev.everydaythings.graph.surface.harness.RendererProvider#textRenderers")
 * void testSomething(String rendererName, SurfaceRenderer renderer) {
 *     // Test runs once per renderer
 * }
 * }</pre>
 */
public final class RendererProvider {

    private RendererProvider() {}

    /**
     * Text-based renderers.
     *
     * <p>Returns TUI and CLI renderers.
     */
    public static Stream<Arguments> textRenderers() {
        return Stream.of(
            Arguments.of("TUI", new TuiSurfaceRenderer()),
            Arguments.of("CLI", new CliSurfaceRenderer())
        );
    }

    /**
     * All renderers.
     */
    public static Stream<Arguments> allRenderers() {
        return Stream.of(
            Arguments.of("TUI", new TuiSurfaceRenderer()),
            Arguments.of("CLI", new CliSurfaceRenderer())
        );
    }

    /**
     * Renderer type classification.
     */
    public enum RendererType {
        TUI,
        CLI
    }

    /**
     * Determine the type of a renderer instance.
     */
    public static RendererType typeOf(SurfaceRenderer renderer) {
        if (renderer instanceof TuiSurfaceRenderer) return RendererType.TUI;
        if (renderer instanceof CliSurfaceRenderer) return RendererType.CLI;
        throw new IllegalArgumentException("Unknown renderer: " + renderer.getClass());
    }
}
