package dev.everydaythings.graph.ui.scene.node;

import dev.everydaythings.graph.ui.scene.BoxBorder;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.SceneEvent;
import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;

import java.util.List;

/**
 * Renders a Node tree by emitting {@link SurfaceRenderer} instructions.
 *
 * <p>This is the parallel rendering path for the new Node primitive system.
 * It walks a Node tree and translates each node into the corresponding
 * SurfaceRenderer calls that existing platform renderers already understand.
 *
 * <p>As renderers are migrated to consume Node trees directly, this class
 * shrinks and eventually disappears.
 */
public final class NodeRenderer {

    private NodeRenderer() {}

    /**
     * Render a node tree to the given SurfaceRenderer.
     */
    public static void render(Node node, SurfaceRenderer out) {
        if (node == null) return;

        if (node instanceof Container c) {
            renderContainer(c, out);
        } else if (node instanceof Text t) {
            renderText(t, out);
        } else if (node instanceof Body b) {
            renderBody(b, out);
        } else if (node instanceof Embedded e) {
            e.surface().render(out);
        }
    }

    // ==================================================================================
    // Container
    // ==================================================================================

    private static void renderContainer(Container c, SurfaceRenderer out) {
        emitBaseProperties(c, out);

        if (nonEmpty(c.gap())) out.gap(c.gap());
        if (nonEmpty(c.overflow())) out.overflow(c.overflow());

        Scene.Direction dir = "horizontal".equals(c.layout())
                ? Scene.Direction.HORIZONTAL
                : Scene.Direction.VERTICAL;

        List<String> styles = c.classes() != null ? c.classes() : List.of();

        BoxBorder border = nonEmpty(c.border()) ? BoxBorder.parse(c.border()) : null;
        boolean hasVisualProps = (border != null && border.isVisible())
                || nonEmpty(c.background())
                || nonEmpty(c.width())
                || nonEmpty(c.height())
                || nonEmpty(c.padding());

        if (hasVisualProps) {
            out.beginBox(dir, styles,
                    border != null ? border : BoxBorder.NONE,
                    orEmpty(c.background()),
                    orEmpty(c.width()),
                    orEmpty(c.height()),
                    orEmpty(c.padding()));
        } else {
            out.beginBox(dir, styles);
        }

        if (c.children() != null) {
            for (Node child : c.children()) {
                render(child, out);
            }
        }

        out.endBox();
    }

    // ==================================================================================
    // Text
    // ==================================================================================

    private static void renderText(Text t, SurfaceRenderer out) {
        emitBaseProperties(t, out);
        String content = t.text() != null ? t.text() : "";
        List<String> styles = t.classes() != null ? t.classes() : List.of();
        out.text(content, styles);
    }

    // ==================================================================================
    // Body
    // ==================================================================================

    private static void renderBody(Body b, SurfaceRenderer out) {
        emitBaseProperties(b, out);
        List<String> styles = b.classes() != null ? b.classes() : List.of();

        // Shape rendering (standalone geometric primitive)
        if (b.shape() != null) {
            if (nonEmpty(b.width()) || nonEmpty(b.height())) {
                out.shapeSize(orEmpty(b.width()), orEmpty(b.height()));
            }
            out.shape(b.shape(),
                    orEmpty(b.corner()),
                    orEmpty(b.fill()),
                    orEmpty(b.stroke()),
                    orEmpty(b.strokeWidth()),
                    "");
            return;
        }

        // Image/glyph rendering via fidelity fallback chain
        String alt = b.glyph() != null ? b.glyph()
                : b.alt() != null ? b.alt()
                : "[body]";

        if (b.model() != null) {
            out.model(b.model(), -1);
        }

        // Pass image path as resource, glyph as alt text
        if (b.image() != null) {
            out.image(alt, null, null, b.image(), null, null, styles);
        } else {
            out.image(alt, null, null, null, null, styles);
        }
    }

    // ==================================================================================
    // Base Properties (shared by all node types)
    // ==================================================================================

    private static void emitBaseProperties(Node node, SurfaceRenderer out) {
        if (nonEmpty(node.id())) {
            out.id(node.id());
        }
        if (node.editable()) {
            out.editable(true);
        }
        if (node.events() != null) {
            for (SceneEvent event : node.events()) {
                out.event(event.on(), event.action(), event.target());
            }
        }
        if (nonEmpty(node.fontFamily())) {
            out.textFontFamily(node.fontFamily());
        }
        if (nonEmpty(node.fontSize())) {
            out.textFontSize(node.fontSize());
        }
        if (node.elevation() != 0.0) {
            out.elevation(node.elevation(), true);
        }
        if (nonEmpty(node.rotation())) {
            try {
                out.rotation(Double.parseDouble(node.rotation()));
            } catch (NumberFormatException ignored) {}
        }
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private static boolean nonEmpty(String s) {
        return s != null && !s.isEmpty();
    }

    private static String orEmpty(String s) {
        return s != null ? s : "";
    }
}
