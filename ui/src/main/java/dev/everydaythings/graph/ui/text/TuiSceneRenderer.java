package dev.everydaythings.graph.ui.text;

import dev.everydaythings.graph.ui.scene.BoxBorder;
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
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * TUI (terminal) implementation of {@link SceneRenderer}.
 *
 * <p>Builds ANSI-styled text from Node trees. Output is a single string
 * containing ANSI escape codes + Unicode characters, suitable for writing
 * directly to a terminal.
 *
 * <p>Long-lived: create one per session, call {@link #render(Node)} on each
 * render pass. The state store persists across re-renders.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * var renderer = new TuiSceneRenderer();
 * renderer.environment(env);
 * renderer.render(rootNode);
 * terminal.writer().print(renderer.result());
 * }</pre>
 */
public class TuiSceneRenderer implements SceneRenderer {

    private final Map<String, Map<String, Object>> store = new HashMap<>();
    private final Deque<String> scopeStack = new ArrayDeque<>();
    private RenderEnvironment env;

    // Output buffer (reset each render)
    private StringBuilder buffer;
    private int indentLevel;
    private int currentRow;
    private int currentCol;

    // Container direction stack
    private final Deque<String> directionStack = new ArrayDeque<>();

    // Hit-testing support
    private final List<HitRegion> hitRegions = new ArrayList<>();
    private final List<PendingHitEvent> pendingHitEvents = new ArrayList<>();
    private int elementStartRow;
    private int elementStartCol;

    private BiFunction<String, String, Boolean> applicationHandler;

    public TuiSceneRenderer() {}

    public TuiSceneRenderer(RenderEnvironment env) { this.env = env; }

    // ==================================================================================
    // Configuration
    // ==================================================================================

    public void environment(RenderEnvironment env) { this.env = env; }

    public void onApplicationAction(BiFunction<String, String, Boolean> handler) {
        this.applicationHandler = handler;
    }

    // ==================================================================================
    // SceneRenderer — abstract accessors
    // ==================================================================================

    @Override public Map<String, Map<String, Object>> stateStore() { return store; }
    @Override public RenderEnvironment environment() { return env; }
    @Override public Deque<String> stateScopeStack() { return scopeStack; }

    // ==================================================================================
    // Application action bridge
    // ==================================================================================

    @Override
    public boolean onApplicationAction(String nodeId, String action, String target) {
        if (applicationHandler != null) return applicationHandler.apply(action, target);
        return false;
    }

    // ==================================================================================
    // Render entry point
    // ==================================================================================

    /**
     * Render a Node tree. Call this to start a new render pass.
     */
    public void renderTree(Node node) {
        buffer = new StringBuilder();
        indentLevel = 0;
        currentRow = 0;
        currentCol = 0;
        hitRegions.clear();
        directionStack.clear();
        directionStack.push("vertical");
        scopeStack.clear();
        render(node);
    }

    /** Get the rendered ANSI text. */
    public String result() {
        return buffer != null ? buffer.toString() : "";
    }

    /** Get hit regions for mouse event dispatch. */
    public List<HitRegion> hitRegions() { return hitRegions; }

    /** Hit test a position. Returns the innermost matching region, or null. */
    public HitRegion hitTest(int row, int col) {
        for (int i = hitRegions.size() - 1; i >= 0; i--) {
            HitRegion r = hitRegions.get(i);
            if (r.contains(row, col)) return r;
        }
        return null;
    }

    public HitRegion hitTest(int row, int col, String eventType) {
        for (int i = hitRegions.size() - 1; i >= 0; i--) {
            HitRegion r = hitRegions.get(i);
            if (r.contains(row, col) && eventType.equals(r.eventType())) return r;
        }
        return null;
    }

    // ==================================================================================
    // SceneRenderer — paint methods
    // ==================================================================================

    @Override
    public void paintContainer(Container container, ResolvedProps props) {
        String layout = props.layout() != null ? props.layout() : "vertical";
        directionStack.push(layout);

        // Emit border opening if present
        String border = props.border();
        if (border != null && !border.isEmpty()) {
            BoxBorder boxBorder = BoxBorder.parse(border);
            if (boxBorder != null && boxBorder.isVisible()) {
                // Simplified: just emit a top border line
                appendIndent();
                buffer.append(ANSI.DIM).append("\u250C");
                for (int i = 0; i < 40; i++) buffer.append("\u2500");
                buffer.append("\u2510").append(ANSI.RESET).append("\n");
                currentRow++;
                currentCol = 0;
            }
        }

        // Increase indent for vertical containers
        if ("vertical".equals(layout)) {
            indentLevel++;
        }

        collectHitEvents(container);
    }

    @Override
    public void paintText(Text text, ResolvedProps props) {
        String content = props.text() != null ? props.text() : "";
        if (content.isEmpty()) return;

        markElementStart();
        collectHitEvents(text);

        boolean isHorizontal = "horizontal".equals(directionStack.peek());

        StringBuilder styled = new StringBuilder();
        applyTextStyle(styled, props);
        styled.append(content);
        if (hasTextStyle(props)) styled.append(ANSI.RESET);

        if (isHorizontal) {
            buffer.append(styled).append(" ");
            currentCol += visibleLength(content) + 1;
        } else {
            appendIndent();
            buffer.append(styled).append("\n");
            createHitRegions(content);
            currentRow++;
            currentCol = 0;
        }
    }

    @Override
    public void paintBody(Body body, ResolvedProps props) {
        // TUI fidelity: glyph → alt → "[body]"
        String display = props.glyph() != null ? props.glyph()
                : props.alt() != null ? props.alt()
                : "[body]";

        markElementStart();
        collectHitEvents(body);

        boolean isHorizontal = "horizontal".equals(directionStack.peek());

        if (isHorizontal) {
            buffer.append(display).append(" ");
            currentCol += visibleLength(display) + 1;
        } else {
            appendIndent();
            buffer.append(display).append("\n");
            createHitRegions(display);
            currentRow++;
            currentCol = 0;
        }
    }

    @Override
    public void endContainer() {
        String layout = directionStack.isEmpty() ? "vertical" : directionStack.pop();

        if ("vertical".equals(layout)) {
            indentLevel = Math.max(0, indentLevel - 1);
        } else if ("horizontal".equals(layout)) {
            // End horizontal line
            if (currentCol > 0) {
                buffer.append("\n");
                currentRow++;
                currentCol = 0;
            }
        }
    }

    // ==================================================================================
    // ANSI Styling
    // ==================================================================================

    private void applyTextStyle(StringBuilder buf, ResolvedProps props) {
        String weight = props.fontWeight();
        String fg = props.foreground();
        String opacity = props.opacity();
        List<String> classes = props.classes();

        if ("bold".equals(weight)) buf.append(ANSI.BOLD);
        if ("dim".equals(opacity) || (classes != null && classes.contains("muted")))
            buf.append(ANSI.DIM);
        if (fg != null && !fg.isEmpty()) {
            String ansi = colorToAnsi(fg);
            if (ansi != null) buf.append(ansi);
        }
        // Class-based coloring
        if (classes != null) {
            if (classes.contains("prompt")) buf.append(ANSI.BLUE);
            if (classes.contains("error")) buf.append(ANSI.RED);
            if (classes.contains("heading")) buf.append(ANSI.BOLD);
            if (classes.contains("completion-selected")) buf.append(ANSI.REVERSE);
            if (classes.contains("completion-indicator")) buf.append(ANSI.BLUE).append(ANSI.BOLD);
            if (classes.contains("verb-name") || classes.contains("param-name")
                    || classes.contains("token-name")) buf.append(ANSI.BOLD);
        }
    }

    private boolean hasTextStyle(ResolvedProps props) {
        if ("bold".equals(props.fontWeight())) return true;
        if ("dim".equals(props.opacity())) return true;
        if (props.foreground() != null && !props.foreground().isEmpty()) return true;
        List<String> classes = props.classes();
        if (classes != null && (classes.contains("muted") || classes.contains("prompt")
                || classes.contains("error") || classes.contains("heading")
                || classes.contains("completion-selected") || classes.contains("completion-indicator")
                || classes.contains("verb-name") || classes.contains("param-name")
                || classes.contains("token-name"))) return true;
        return false;
    }

    private String colorToAnsi(String color) {
        if (color.startsWith("#") && color.length() == 7) {
            try {
                int r = Integer.parseInt(color.substring(1, 3), 16);
                int g = Integer.parseInt(color.substring(3, 5), 16);
                int b = Integer.parseInt(color.substring(5, 7), 16);
                return "\u001B[38;2;" + r + ";" + g + ";" + b + "m";
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return switch (color) {
            case "red" -> ANSI.RED;
            case "green" -> ANSI.GREEN;
            case "yellow" -> ANSI.YELLOW;
            case "blue" -> ANSI.BLUE;
            case "magenta" -> ANSI.MAGENTA;
            case "cyan" -> ANSI.CYAN;
            case "white" -> ANSI.WHITE;
            default -> null;
        };
    }

    // ==================================================================================
    // Hit Regions
    // ==================================================================================

    public record HitRegion(int startRow, int startCol, int endRow, int endCol,
                            String eventType, String action, String target, String id) {
        public boolean contains(int row, int col) {
            if (row < startRow || row > endRow) return false;
            if (row == startRow && col < startCol) return false;
            if (row == endRow && col > endCol) return false;
            return true;
        }
    }

    private record PendingHitEvent(String on, String action, String target) {}

    private void collectHitEvents(Node node) {
        pendingHitEvents.clear();
        if (node.events() != null) {
            for (SceneEvent e : node.events()) {
                pendingHitEvents.add(new PendingHitEvent(e.on(), e.action(), e.target()));
            }
        }
    }

    private void markElementStart() {
        elementStartRow = currentRow;
        elementStartCol = currentCol;
    }

    private void createHitRegions(String content) {
        if (pendingHitEvents.isEmpty()) return;
        int endCol = elementStartCol + visibleLength(content);
        String nodeId = null; // could track from scope stack
        for (PendingHitEvent e : pendingHitEvents) {
            hitRegions.add(new HitRegion(elementStartRow, elementStartCol,
                    currentRow, endCol, e.on(), e.action(), e.target(), nodeId));
        }
        pendingHitEvents.clear();
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private void appendIndent() {
        for (int i = 0; i < indentLevel; i++) {
            buffer.append("  ");
            currentCol += 2;
        }
    }

    private static int visibleLength(String s) {
        if (s == null) return 0;
        return BoxDrawing.stripAnsiLength(s);
    }

    // ==================================================================================
    // ANSI Constants
    // ==================================================================================

    private static final class ANSI {
        static final String RESET = "\u001B[0m";
        static final String BOLD = "\u001B[1m";
        static final String DIM = "\u001B[2m";
        static final String REVERSE = "\u001B[7m";
        static final String RED = "\u001B[31m";
        static final String GREEN = "\u001B[32m";
        static final String YELLOW = "\u001B[33m";
        static final String BLUE = "\u001B[34m";
        static final String MAGENTA = "\u001B[35m";
        static final String CYAN = "\u001B[36m";
        static final String WHITE = "\u001B[37m";
    }
}
