package dev.everydaythings.graph.ui.skia;

import dev.everydaythings.graph.ui.scene.BoxBorder;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.SceneEvent;
import dev.everydaythings.graph.ui.scene.node.Body;
import dev.everydaythings.graph.ui.scene.node.Container;
import dev.everydaythings.graph.ui.scene.node.Node;
import dev.everydaythings.graph.ui.scene.node.RenderEnvironment;
import dev.everydaythings.graph.ui.scene.node.ResolvedProps;
import dev.everydaythings.graph.ui.scene.node.SceneRenderer;
import dev.everydaythings.graph.ui.scene.node.Text;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Skia implementation of {@link SceneRenderer}.
 *
 * <p>Builds a {@link LayoutNode} tree from Node primitives with resolved
 * properties. The tree is then laid out by {@link LayoutEngine} and painted
 * by {@link SkiaPainter} — same pipeline as the old SkiaSurfaceRenderer, but
 * driven by the Node/SceneRenderer model instead of imperative SurfaceRenderer calls.
 *
 * <p>Long-lived: create one per window, call {@link #render(Node)} on each frame.
 * The state store persists across re-renders. Update the environment before each
 * render via {@link #environment(RenderEnvironment)}.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Create once per window
 * var renderer = new SkiaSceneRenderer();
 *
 * // Each frame:
 * renderer.environment(buildEnvironment(w, h, dpr));
 * renderer.render(rootNode);
 * LayoutNode.BoxNode tree = renderer.result();
 * engine.layout(tree, w, h);
 * painter.paint(canvas, tree);
 * }</pre>
 */
public class SkiaSceneRenderer implements SceneRenderer {

    private final Map<String, Map<String, Object>> store = new HashMap<>();
    private final Deque<String> scopeStack = new ArrayDeque<>();
    private RenderEnvironment env;

    // Container stack for building the LayoutNode tree (reset each render)
    private final Deque<LayoutNode.BoxNode> containerStack = new ArrayDeque<>();
    private LayoutNode.BoxNode root;

    // Application action handler — receives actions not handled by state runtime
    private BiFunction<String, String, Boolean> applicationHandler;

    public SkiaSceneRenderer() {}

    public SkiaSceneRenderer(RenderEnvironment env) {
        this.env = env;
    }

    // ==================================================================================
    // Configuration (called before each render)
    // ==================================================================================

    /** Update the environment (viewport, font metrics, capabilities). */
    public void environment(RenderEnvironment env) {
        this.env = env;
    }

    /**
     * Set the application action handler.
     *
     * <p>Called for actions not handled by the state runtime (toggle/set/unset/cycle).
     * The handler receives (action, target) and returns true if handled.
     */
    public void onApplicationAction(BiFunction<String, String, Boolean> handler) {
        this.applicationHandler = handler;
    }

    // ==================================================================================
    // SceneRenderer — abstract accessors
    // ==================================================================================

    @Override
    public Map<String, Map<String, Object>> stateStore() { return store; }

    @Override
    public RenderEnvironment environment() { return env; }

    @Override
    public Deque<String> stateScopeStack() { return scopeStack; }

    // ==================================================================================
    // Application action bridge
    // ==================================================================================

    @Override
    public boolean onApplicationAction(String nodeId, String action, String target) {
        if (applicationHandler != null) {
            return applicationHandler.apply(action, target);
        }
        return false;
    }

    // ==================================================================================
    // Render entry point (overrides default to set up root)
    // ==================================================================================

    /**
     * Render a Node tree. Call this to start a new render pass.
     *
     * <p>Resets the LayoutNode tree (state store persists).
     * The first Container becomes the root directly (no wrapper).
     */
    public void renderTree(Node node) {
        root = null;
        containerStack.clear();
        scopeStack.clear();
        render(node);
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

        if (props.id() != null) box.id(props.id());
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
                resolveBorder(box, border);
            }
        }

        // Background
        if (props.background() != null && !props.background().isEmpty()) {
            box.background(props.background());
        }

        // Sizing — "1fr" flows through; LayoutEngine handles fill distribution
        if (props.width() != null && !props.width().isEmpty()) {
            box.widthSpec(props.width());
        }
        if (props.height() != null && !props.height().isEmpty()) {
            box.heightSpec(props.height());
        }

        // Padding — resolve to pixels eagerly
        if (props.padding() != null && !props.padding().isEmpty()) {
            resolvePadding(box, props.padding());
        }

        // Corner radius
        if (props.corner() != null && !props.corner().isEmpty()) {
            box.shapeType("rectangle");
            if (env != null) {
                double px = env.resolveToPixels(props.corner());
                if (px >= 0) box.borderRadius((float) px);
            }
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
    // Helpers
    // ==================================================================================

    private void addToCurrentContainer(LayoutNode node) {
        if (containerStack.isEmpty()) {
            // First container becomes root (no wrapper)
            if (node instanceof LayoutNode.BoxNode box) {
                root = box;
            }
            return;
        }
        LayoutNode.BoxNode parent = containerStack.peek();
        if (parent != null) {
            parent.addChild(node);
        }
    }

    private void resolvePadding(LayoutNode.BoxNode box, String padding) {
        box.paddingSpec(padding);
        String[] parts = padding.trim().split("\\s+");
        try {
            if (parts.length == 1) {
                float p = resolvePixels(parts[0]);
                box.padding(p, p, p, p);
            } else if (parts.length == 2) {
                float v = resolvePixels(parts[0]);
                float h = resolvePixels(parts[1]);
                box.padding(v, h, v, h);
            } else if (parts.length == 4) {
                box.padding(
                    resolvePixels(parts[0]), resolvePixels(parts[1]),
                    resolvePixels(parts[2]), resolvePixels(parts[3]));
            }
        } catch (Exception ignored) {}
    }

    private float resolvePixels(String spec) {
        double px = env.resolveToPixels(spec);
        return px >= 0 ? (float) px : 0;
    }

    private void resolveBorder(LayoutNode.BoxNode box, BoxBorder border) {
        box.borderWidths(
                resolveBorderSideWidth(border.top()),
                resolveBorderSideWidth(border.right()),
                resolveBorderSideWidth(border.bottom()),
                resolveBorderSideWidth(border.left())
        );
        if (border.hasRadius() && env != null) {
            double px = env.resolveToPixels(border.radius());
            if (px >= 0) box.borderRadius((float) px);
        }
    }

    private float resolveBorderSideWidth(BoxBorder.BorderSide side) {
        if (!side.isVisible()) return 0;
        if (env != null) {
            double px = env.resolveToPixels(side.width());
            if (px >= 0) return (float) px;
        }
        return 1.0f;
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
