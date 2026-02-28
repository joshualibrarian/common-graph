package dev.everydaythings.graph.ui.text;

import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Plain-text CLI implementation of SurfaceRenderer.
 *
 * <p>Designed for simple terminals - uses unicode characters but no ANSI
 * escape codes (no colors, no bold, no cursor control). Just clean,
 * scrolling text output.
 *
 * <p>Uses only the three primitives: text, image, and box.
 */
public class CliSurfaceRenderer implements SurfaceRenderer {

    private final StringBuilder buffer = new StringBuilder();
    private int indentLevel = 0;
    private static final String INDENT = "    ";

    // Container context stack
    private final Deque<ContainerContext> contextStack = new ArrayDeque<>();

    // Current metadata for next element
    private String nextType;
    private String nextId;
    private boolean nextEditable;

    // Horizontal accumulator (for inline elements)
    private StringBuilder horizontalBuffer;

    private record ContainerContext(String type, Scene.Direction direction) {}

    public CliSurfaceRenderer() {}

    public String result() {
        return buffer.toString();
    }

    // ==================== Text Primitive ====================

    @Override
    public void text(String content, List<String> styles) {
        if (content == null || content.isEmpty()) return;

        if (isHorizontalContext()) {
            horizontalBuffer.append(content);
            horizontalBuffer.append("  ");
        } else {
            appendIndent();
            buffer.append(content);
            buffer.append("\n");
        }
    }

    @Override
    public void formattedText(String content, String format, List<String> styles) {
        if (content == null || content.isEmpty()) return;

        if (isHorizontalContext()) {
            if ("code".equals(format)) {
                horizontalBuffer.append("`").append(content).append("`");
            } else {
                horizontalBuffer.append(content);
            }
            horizontalBuffer.append("  ");
        } else {
            appendIndent();
            if ("code".equals(format)) {
                buffer.append("`").append(content).append("`");
            } else {
                buffer.append(content);
            }
            buffer.append("\n");
        }
    }

    // ==================== Image Primitive ====================

    @Override
    public void image(String alt, ContentID image, ContentID solid, String size, String fit, List<String> styles) {
        if (alt == null || alt.isEmpty()) return;

        if (isHorizontalContext()) {
            horizontalBuffer.append(alt).append(" ");
        } else {
            appendIndent();
            buffer.append(alt).append("\n");
        }
    }

    // ==================== Container Primitive ====================

    @Override
    public void beginBox(Scene.Direction direction, List<String> styles) {
        ContainerContext ctx = new ContainerContext("box", direction);
        contextStack.push(ctx);

        // Check styles for semantic hints
        boolean isTreeNode = styles != null && styles.contains("tree-node");
        boolean isTreeChildren = styles != null && styles.contains("tree-children");
        boolean isExpanded = styles != null && styles.contains("expanded");
        boolean hasChildren = styles != null && styles.contains("has-children");
        boolean isSelected = styles != null && styles.contains("selected");

        if (isTreeNode) {
            appendIndent();
            for (int i = 0; i < indentLevel; i++) {
                buffer.append("│   ");
            }
            if (isSelected) {
                buffer.append("▶ ");
            }
            if (hasChildren) {
                buffer.append(isExpanded ? "▼ " : "▷ ");
            } else {
                buffer.append("  ");
            }
            horizontalBuffer = new StringBuilder();
        } else if (isTreeChildren) {
            indentLevel++;
        } else if (direction == Scene.Direction.HORIZONTAL) {
            appendIndent();
            horizontalBuffer = new StringBuilder();
        }
    }

    @Override
    public void endBox() {
        ContainerContext ctx = contextStack.poll();
        if (ctx == null) return;

        if (ctx.direction == Scene.Direction.HORIZONTAL && horizontalBuffer != null) {
            // Strip only the trailing element separator (two spaces),
            // preserving intentional trailing spaces in content (e.g., prompt text)
            String content = horizontalBuffer.toString();
            if (content.endsWith("  ")) {
                content = content.substring(0, content.length() - 2);
            }
            buffer.append(content);
            buffer.append("\n");
            horizontalBuffer = null;
        }

        // Decrement indent for tree-children
        // (Would need to track this via context)
    }

    // ==================== Metadata ====================

    @Override
    public void type(String type) {
        this.nextType = type;
    }

    @Override
    public void id(String id) {
        this.nextId = id;
    }

    @Override
    public void editable(boolean editable) {
        this.nextEditable = editable;
    }

    // ==================== Events ====================

    @Override
    public void event(String on, String action, String target) {
        // CLI doesn't support interactive events, ignore
    }

    // ==================== Helpers ====================

    private boolean isHorizontalContext() {
        return horizontalBuffer != null;
    }

    private void appendIndent() {
        for (int i = 0; i < indentLevel; i++) {
            buffer.append(INDENT);
        }
    }
}
