package dev.everydaythings.graph.ui.text;

import dev.everydaythings.graph.ui.scene.SceneNode;
import dev.everydaythings.graph.ui.scene.ScenePainter;

/**
 * Plain text implementation of {@link ScenePainter}.
 *
 * <p>Produces plain Unicode text — no ANSI codes, no colors, no hit testing.
 * The most minimal painter, suitable for piped output and basic terminals.
 */
public class PlainTextSurfacePainter implements ScenePainter {

    private StringBuilder buffer;
    private int indentLevel;
    private boolean lastWasHorizontal;

    @Override
    public void paint(SceneNode tree) {
        buffer = new StringBuilder();
        indentLevel = 0;
        lastWasHorizontal = false;
        paintNode(tree, false);
    }

    @Override
    public void clear() {
        buffer = null;
    }

    public String result() {
        return buffer != null ? buffer.toString() : "";
    }

    private void paintNode(SceneNode node, boolean horizontal) {
        if (node == null) return;
        if ("false".equals(node.visible())) return;

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

        if (isVertical) indentLevel++;

        if (node.children() != null) {
            for (SceneNode child : node.children()) {
                paintNode(child, isHorizontal);
            }
        }

        if (isVertical) {
            indentLevel = Math.max(0, indentLevel - 1);
        } else if (isHorizontal) {
            buffer.append("\n");
        }
    }

    private void paintText(SceneNode node, boolean horizontal) {
        String content = node.text() != null ? node.text() : "";
        if (content.isEmpty()) return;

        if (horizontal) {
            buffer.append(content).append(" ");
        } else {
            appendIndent();
            buffer.append(content).append("\n");
        }
    }

    private void paintBody(SceneNode node, boolean horizontal) {
        String display = node.glyph() != null ? node.glyph()
                : node.alt() != null ? node.alt() : "";
        if (display.isEmpty()) return;

        if (horizontal) {
            buffer.append(display).append(" ");
        } else {
            appendIndent();
            buffer.append(display).append("\n");
        }
    }

    private void appendIndent() {
        for (int i = 0; i < indentLevel; i++) buffer.append("  ");
    }
}
