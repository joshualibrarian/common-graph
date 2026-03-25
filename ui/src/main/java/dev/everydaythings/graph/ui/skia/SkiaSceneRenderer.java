package dev.everydaythings.graph.ui.skia;

import dev.everydaythings.graph.ui.scene.BoxBorder;
import dev.everydaythings.graph.ui.scene.RenderContext;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.SceneEvent;
import dev.everydaythings.graph.ui.scene.node.Body;
import dev.everydaythings.graph.ui.scene.node.Container;
import dev.everydaythings.graph.ui.scene.node.Embedded;
import dev.everydaythings.graph.ui.scene.node.Node;
import dev.everydaythings.graph.ui.scene.node.RenderEnvironment;
import dev.everydaythings.graph.ui.scene.node.ResolvedProps;
import dev.everydaythings.graph.ui.scene.node.SceneRenderer;
import dev.everydaythings.graph.ui.scene.node.Text;
import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Skia implementation of {@link SceneRenderer}.
 *
 * <p>Builds a {@link LayoutNode} tree from Node primitives with resolved
 * properties. The tree is then laid out by {@link LayoutEngine} and painted
 * by {@link SkiaPainter} — same pipeline as the old SkiaSurfaceRenderer, but
 * driven by the Node/SceneRenderer model instead of imperative SurfaceRenderer calls.
 *
 * <p>The state store persists across re-renders. Create one SkiaSceneRenderer
 * per window and reuse it — the state survives Node tree rebuilds.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * var renderer = new SkiaSceneRenderer(environment);
 * renderer.render(rootNode);
 * LayoutNode.BoxNode tree = renderer.result();
 * engine.layout(tree, width, height);
 * painter.paint(canvas, tree);
 * }</pre>
 */
public class SkiaSceneRenderer implements SceneRenderer {

    private final Map<String, Map<String, Object>> store = new HashMap<>();
    private final RenderEnvironment env;

    // Container stack for building the LayoutNode tree
    private final Deque<LayoutNode.BoxNode> containerStack = new ArrayDeque<>();
    private LayoutNode.BoxNode root;

    // Legacy bridge for Embedded nodes
    private SkiaSurfaceRenderer legacyRenderer;
    private RenderContext legacyContext;

    public SkiaSceneRenderer(RenderEnvironment env) {
        this.env = env;
    }

    /**
     * Set the legacy renderer for Embedded node support.
     * Required until all surfaces are migrated to Node trees.
     */
    public SkiaSceneRenderer withLegacyBridge(SkiaSurfaceRenderer legacy, RenderContext ctx) {
        this.legacyRenderer = legacy;
        this.legacyContext = ctx;
        return this;
    }

    // ==================================================================================
    // SceneRenderer — abstract accessors
    // ==================================================================================

    @Override
    public Map<String, Map<String, Object>> stateStore() { return store; }

    @Override
    public RenderEnvironment environment() { return env; }

    // ==================================================================================
    // Render entry point (overrides default to set up root)
    // ==================================================================================

    @Override
    public void render(Node node) {
        // Initialize root container
        root = new LayoutNode.BoxNode(Scene.Direction.VERTICAL, List.of());
        containerStack.clear();
        containerStack.push(root);

        // Delegate to default tree walk
        SceneRenderer.super.render(node);
    }

    /**
     * Get the built LayoutNode tree after rendering.
     */
    public LayoutNode.BoxNode result() {
        return root;
    }

    // ==================================================================================
    // SceneRenderer — paint methods
    // ==================================================================================

    @Override
    public void paintContainer(Container container, ResolvedProps props) {
        Scene.Direction dir = "horizontal".equals(props.layout())
                ? Scene.Direction.HORIZONTAL
                : Scene.Direction.VERTICAL;

        List<String> styles = props.classes();
        var box = new LayoutNode.BoxNode(dir, styles);

        // Identity
        if (props.id() != null) box.id(props.id());

        // Events from the original node
        applyEvents(box, container);

        // Gap
        if (props.gap() != null && !props.gap().isEmpty()) {
            box.gapSpec(props.gap());
        }

        // Overflow
        String overflow = props.overflow();
        if (overflow != null && !overflow.isEmpty()) {
            box.overflow(overflow);
        }

        // Border
        if (props.border() != null && !props.border().isEmpty()) {
            BoxBorder border = BoxBorder.parse(props.border());
            if (border != null && border.isVisible()) {
                box.border(border);
            }
        }

        // Background
        if (props.background() != null && !props.background().isEmpty()) {
            box.background(props.background());
        }

        // Sizing
        if (props.width() != null && !props.width().isEmpty()) {
            box.widthSpec(props.width());
        }
        if (props.height() != null && !props.height().isEmpty()) {
            box.heightSpec(props.height());
        }

        // Padding
        if (props.padding() != null && !props.padding().isEmpty()) {
            box.paddingSpec(props.padding());
        }

        // Corner radius
        if (props.corner() != null && !props.corner().isEmpty()) {
            box.shapeType("rectangle");
            try {
                box.borderRadius(Float.parseFloat(props.corner().replaceAll("[^0-9.]", "")));
            } catch (NumberFormatException ignored) {}
        }

        // Font
        if (props.fontFamily() != null) box.fontFamily(props.fontFamily());
        if (props.fontSize() != null) box.fontSizeSpec(props.fontSize());

        // Elevation
        if (props.elevation() != 0.0) {
            box.elevation((float) props.elevation());
            box.elevationSolid(true);
        }

        addToCurrentContainer(box);
        containerStack.push(box);
    }

    @Override
    public void paintText(Text text, ResolvedProps props) {
        String content = props.text() != null ? props.text() : "";
        List<String> styles = props.classes();

        var textNode = new LayoutNode.TextNode(content, styles);

        if (props.id() != null) textNode.id(props.id());
        if (props.fontFamily() != null) textNode.fontFamily(props.fontFamily());
        if (props.fontSize() != null) textNode.fontSizeSpec(props.fontSize());
        if (props.fontWeight() != null && "bold".equals(props.fontWeight())) {
            textNode.bold(true);
        }

        applyEvents(textNode, text);
        addToCurrentContainer(textNode);
    }

    @Override
    public void paintBody(Body body, ResolvedProps props) {
        List<String> styles = props.classes();

        // Shape rendering
        if (props.shape() != null) {
            var shapeNode = new LayoutNode.ShapeNode(
                    props.shape(),
                    props.corner() != null ? props.corner() : "",
                    props.fill() != null ? props.fill() : "",
                    props.stroke() != null ? props.stroke() : "",
                    props.strokeWidth() != null ? props.strokeWidth() : "",
                    "",
                    styles);
            if (props.id() != null) shapeNode.id(props.id());
            if (props.width() != null) shapeNode.widthSpec(props.width());
            if (props.height() != null) shapeNode.heightSpec(props.height());
            applyEvents(shapeNode, body);
            addToCurrentContainer(shapeNode);
            return;
        }

        // Image/glyph via fidelity chain
        String alt = props.glyph() != null ? props.glyph()
                : props.alt() != null ? props.alt()
                : "[body]";

        var imageNode = new LayoutNode.ImageNode(alt, props.image(), null, null, styles);
        if (props.id() != null) imageNode.id(props.id());

        // 3D model hint
        if (props.model() != null) {
            imageNode.modelResource(props.model());
            imageNode.modelColor(-1);
        }

        applyEvents(imageNode, body);
        addToCurrentContainer(imageNode);
    }

    @Override
    public void endContainer() {
        if (containerStack.size() > 1) {
            containerStack.pop();
        }
    }

    // ==================================================================================
    // Legacy bridge for Embedded nodes
    // ==================================================================================

    @Override
    public void renderLegacy(Embedded embedded) {
        if (embedded.surface() == null) return;

        // Render through the old SurfaceRenderer into a temporary LayoutNode tree,
        // then graft the result into our current container
        if (legacyRenderer != null) {
            // Create a fresh legacy renderer for this subtree
            var sub = new SkiaSurfaceRenderer(legacyContext);
            embedded.surface().render(sub);
            LayoutNode.BoxNode subtree = sub.result();

            // Graft all children from the subtree root into our current container
            for (LayoutNode child : subtree.children()) {
                addToCurrentContainer(child);
            }
        }
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private void addToCurrentContainer(LayoutNode node) {
        LayoutNode.BoxNode parent = containerStack.peek();
        if (parent != null) {
            parent.addChild(node);
        }
    }

    private void applyEvents(LayoutNode node, Node source) {
        if (source.events() != null) {
            for (SceneEvent event : source.events()) {
                node.events().add(new LayoutNode.PendingEvent(
                        event.on(), event.action(), event.target()));
            }
        }
    }
}
