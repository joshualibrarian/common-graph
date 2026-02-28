package dev.everydaythings.graph.ui.paragraph;

import dev.everydaythings.graph.ui.scene.surface.primitive.ContainerSurface;
import dev.everydaythings.graph.ui.scene.surface.primitive.TextSpan;
import dev.everydaythings.graph.ui.scene.surface.primitive.TextSurface;
import dev.everydaythings.graph.ui.skia.*;

import java.util.List;

/**
 * Visual showcase: opens a Skia window demonstrating the Paragraph abstraction.
 *
 * <p>Shows:
 * <ul>
 *   <li>Plain text wrapping at different widths</li>
 *   <li>Rich text with mixed style spans</li>
 *   <li>Long text that wraps across many lines</li>
 *   <li>CJK / Unicode text</li>
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :ui:paragraphShowcase}
 *
 * <p>Press Escape to close. Scroll with mouse wheel.
 */
public class ParagraphShowcase {

    static ContainerSurface buildSurface() {
        ContainerSurface root = ContainerSurface.vertical().gap("16px");
        root.style("fill");

        // Title
        root.add(TextSurface.of("Paragraph Layout Showcase").style("heading"));

        // Section 1: Plain text wrapping
        root.add(TextSurface.of("1. Word Wrapping").style("heading"));
        root.add(TextSurface.of(
                "This is a long paragraph of plain text that should demonstrate word wrapping. "
                + "The layout engine measures text width and breaks at word boundaries when the "
                + "line exceeds the available container width. Previously, the MSDF path had no "
                + "wrapping at all — text just ran off the edge. Now both Skia and MSDF backends "
                + "share the same Paragraph abstraction with proper line breaking."));

        // Section 2: Narrow container wrapping
        root.add(TextSurface.of("2. Narrow Container (300px)").style("heading"));
        {
            ContainerSurface narrow = ContainerSurface.vertical();
            narrow.width("300px").background("#2A2A3A").padding("8px");
            narrow.add(TextSurface.of(
                    "Constrained to 300 pixels. This text must wrap within a narrow "
                    + "container, demonstrating that the paragraph respects its parent's "
                    + "allocated width during layout."));
            root.add(narrow);
        }

        // Section 3: Rich text with spans
        root.add(TextSurface.of("3. Rich Text Spans").style("heading"));
        {
            TextSurface rich = new TextSurface();
            rich.spans(List.of(
                    TextSpan.of("Common Graph ", "bold"),
                    TextSpan.of("replaces files, folders, web, and email with "),
                    TextSpan.of("Items", "code"),
                    TextSpan.of(" — verifiable, relatable, and discoverable by shared meaning. "),
                    TextSpan.of("Sememes ", "accent"),
                    TextSpan.of("are units of meaning, just as meters are units of measure.")
            ));
            rich.content("Common Graph replaces files, folders, web, and email with Items — verifiable, relatable, and discoverable by shared meaning. Sememes are units of meaning, just as meters are units of measure.");
            root.add(rich);
        }

        // Section 4: Multiple font sizes
        root.add(TextSurface.of("4. Font Profiles").style("heading"));
        {
            ContainerSurface col = ContainerSurface.vertical().gap("4px");
            col.add(TextSurface.of("Heading size text").style("heading"));
            col.add(TextSurface.of("Normal proportional text — the default for body content."));
            col.add(TextSurface.of("Monospace code-style text: fn main() { println!(\"Hello\"); }").style("code"));
            col.add(TextSurface.of("Small muted caption text").style("muted"));
            root.add(col);
        }

        // Section 5: Side-by-side narrow columns
        root.add(TextSurface.of("5. Side-by-Side Columns").style("heading"));
        {
            ContainerSurface row = ContainerSurface.horizontal().gap("16px");

            ContainerSurface left = ContainerSurface.vertical();
            left.width("250px").background("#2A2A3A").padding("8px");
            left.add(TextSurface.of("Left column: The Surface system declares rendering via annotations. "
                    + "Surfaces are CBOR-serializable models rendered by platform-specific renderers."));
            row.add(left);

            ContainerSurface right = ContainerSurface.vertical();
            right.width("250px").background("#2A2A3A").padding("8px");
            right.add(TextSurface.of("Right column: The Spatial system defines 3D scenes with @Scene.Body "
                    + "and @Scene.Environment annotations. Renderers interpret the DSL — it stays small and stable."));
            row.add(right);

            root.add(row);
        }

        // Section 6: Unicode / special characters
        root.add(TextSurface.of("6. Unicode Text").style("heading"));
        root.add(TextSurface.of("Arrows: → ← ↑ ↓  •  Symbols: ♔ ♛ ♞  •  Math: ∑ ∫ √ π  •  Emoji: 🎨 🔧 📦"));

        // Footer
        root.add(TextSurface.of("Press Escape to close • Scroll with mouse wheel").style("muted"));

        return root;
    }

    public static void main(String[] args) {
        ContainerSurface surface = buildSurface();

        // Build layout tree
        SkiaSurfaceRenderer renderer = new SkiaSurfaceRenderer();
        surface.render(renderer);
        LayoutNode.BoxNode layoutRoot = renderer.result();

        // Set up rendering pipeline
        FontCache fontCache = new FontCache(new dev.everydaythings.graph.ui.text.FontRegistry());
        LayoutEngine engine = new LayoutEngine(fontCache);
        SkiaPainter painter = new SkiaPainter(fontCache);

        // Scroll state
        final float[] scrollY = {0};

        // Open window
        SkiaWindow window = new SkiaWindow();
        window.init("Paragraph Showcase — Skia");
        window.show();

        window.onPaint(canvas -> {
            engine.layout(layoutRoot, window.width(), window.height());

            // Clamp scroll
            float contentH = layoutRoot.height();
            float maxScroll = Math.max(0, contentH - window.height());
            if (scrollY[0] > maxScroll) scrollY[0] = maxScroll;

            canvas.save();
            canvas.translate(0, -scrollY[0]);
            painter.paint(canvas, layoutRoot);
            canvas.restore();
        });

        window.onKey((key, scancode, action, mods) -> {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
                    && action == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
                org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose(window.handle(), true);
            }
        });

        window.onScroll((xOffset, yOffset) -> {
            scrollY[0] -= (float) yOffset * 40;
            if (scrollY[0] < 0) scrollY[0] = 0;
        });

        window.runLoop();
        fontCache.close();
        window.destroy();
    }
}
