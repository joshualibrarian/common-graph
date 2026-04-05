package dev.everydaythings.graph.ui.text;

import dev.everydaythings.graph.ui.scene.SceneEvent;
import dev.everydaythings.graph.ui.scene.SceneNode;
import dev.everydaythings.graph.ui.scene.ScenePainter;

import java.util.ArrayList;
import java.util.List;

/**
 * ANSI terminal implementation of {@link ScenePainter}.
 *
 * <p>Builds ANSI-styled text from a resolved, laid-out SceneNode tree.
 * Output is a single string containing ANSI escape codes + Unicode
 * characters, suitable for writing directly to a terminal.
 *
 * <p>Also tracks hit regions for mouse event dispatch.
 */
public class AnsiSurfacePainter implements ScenePainter {

    private StringBuilder buffer;
    private int indentLevel;
    private int currentRow;
    private int currentCol;
    private final List<HitRegion> hitRegions = new ArrayList<>();

    @Override
    public void paint(SceneNode tree) {
        buffer = new StringBuilder();
        indentLevel = 0;
        currentRow = 0;
        currentCol = 0;
        hitRegions.clear();
        paintNode(tree, false);
    }

    @Override
    public void clear() {
        buffer = null;
        hitRegions.clear();
    }

    public String result() {
        return buffer != null ? buffer.toString() : "";
    }

    public List<HitRegion> hitRegions() { return hitRegions; }

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

    // =================================================================================
    // Tree Walking
    // =================================================================================

    private void paintNode(SceneNode node, boolean horizontal) {
        if (node == null) return;
        if (!node.isVisible()) return;

        switch (node.type()) {
            case CONTAINER -> paintContainer(node, horizontal);
            case TEXT -> paintText(node, horizontal);
            case BODY -> paintBody(node, horizontal);
            case null -> {}
        }
    }

    private void paintContainer(SceneNode node, boolean parentHorizontal) {
        String layout = node.layout() != null ? node.layout() : "vertical";
        boolean isVertical = "vertical".equals(layout);
        boolean isHorizontal = "horizontal".equals(layout);

        // Border opening
        boolean hasBorder = node.borderTopWidthFloat() > 0 || node.borderBottomWidthFloat() > 0
                || node.borderLeftWidthFloat() > 0 || node.borderRightWidthFloat() > 0;
        if (hasBorder) {
            appendIndent();
            buffer.append(ANSI.DIM).append("\u250C");
            for (int i = 0; i < 40; i++) buffer.append("\u2500");
            buffer.append("\u2510").append(ANSI.RESET).append("\n");
            currentRow++;
            currentCol = 0;
        }

        if (isVertical) indentLevel++;

        if (node.children() != null) {
            for (SceneNode child : node.children()) {
                paintNode(child, isHorizontal);
            }
        }

        if (isVertical) {
            indentLevel = Math.max(0, indentLevel - 1);
        } else if (isHorizontal) {
            if (currentCol > 0) {
                buffer.append("\n");
                currentRow++;
                currentCol = 0;
            }
        }
    }

    private void paintText(SceneNode node, boolean horizontal) {
        String content = node.text() != null ? node.text() : "";
        if (content.isEmpty()) return;

        int startRow = currentRow;
        int startCol = currentCol;

        StringBuilder styled = new StringBuilder();
        applyTextStyle(styled, node);
        styled.append(content);
        if (hasTextStyle(node)) styled.append(ANSI.RESET);

        if (horizontal) {
            buffer.append(styled).append(" ");
            currentCol += visibleLength(content) + 1;
        } else {
            appendIndent();
            buffer.append(styled).append("\n");
            int endCol = startCol + indentLevel * 2 + visibleLength(content);
            collectHitRegions(node, startRow, startCol, currentRow, endCol);
            currentRow++;
            currentCol = 0;
        }
    }

    private void paintBody(SceneNode node, boolean horizontal) {
        String display = node.glyph() != null ? node.glyph()
                : node.alt() != null ? node.alt()
                : "[body]";

        int startRow = currentRow;
        int startCol = currentCol;

        if (horizontal) {
            buffer.append(display).append(" ");
            currentCol += visibleLength(display) + 1;
        } else {
            appendIndent();
            buffer.append(display).append("\n");
            int endCol = startCol + indentLevel * 2 + visibleLength(display);
            collectHitRegions(node, startRow, startCol, currentRow, endCol);
            currentRow++;
            currentCol = 0;
        }
    }

    // =================================================================================
    // ANSI Styling
    // =================================================================================

    private void applyTextStyle(StringBuilder buf, SceneNode node) {
        String weight = node.fontWeight();
        Object fg = node.foreground();
        Object opacity = node.opacity();
        List<String> classes = node.classes();

        if ("bold".equals(weight)) buf.append(ANSI.BOLD);
        if ("dim".equals(opacity) || (classes != null && classes.contains("muted")))
            buf.append(ANSI.DIM);
        if (fg instanceof String fgStr && !fgStr.isEmpty()) {
            String ansi = colorToAnsi(fgStr);
            if (ansi != null) buf.append(ansi);
        } else if (fg instanceof Integer fgColor && fgColor != -1) {
            int r = (fgColor >> 16) & 0xFF, g = (fgColor >> 8) & 0xFF, b = fgColor & 0xFF;
            buf.append("\u001B[38;2;").append(r).append(";").append(g).append(";").append(b).append("m");
        }
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

    private boolean hasTextStyle(SceneNode node) {
        if ("bold".equals(node.fontWeight())) return true;
        if ("dim".equals(node.opacity())) return true;
        if (node.foreground() != null) return true;
        List<String> classes = node.classes();
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

    // =================================================================================
    // Hit Regions
    // =================================================================================

    public record HitRegion(int startRow, int startCol, int endRow, int endCol,
                            String eventType, String action, String target, String id) {
        public boolean contains(int row, int col) {
            if (row < startRow || row > endRow) return false;
            if (row == startRow && col < startCol) return false;
            if (row == endRow && col > endCol) return false;
            return true;
        }
    }

    private void collectHitRegions(SceneNode node, int startRow, int startCol,
                                   int endRow, int endCol) {
        if (node.events() == null) return;
        for (SceneEvent e : node.events()) {
            hitRegions.add(new HitRegion(startRow, startCol, endRow, endCol,
                    e.on(), e.action(), e.target(), node.id()));
        }
    }

    // =================================================================================
    // Helpers
    // =================================================================================

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

    // =================================================================================
    // ANSI Constants
    // =================================================================================

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
