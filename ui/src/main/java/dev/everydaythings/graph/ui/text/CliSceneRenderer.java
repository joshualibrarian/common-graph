package dev.everydaythings.graph.ui.text;

import dev.everydaythings.graph.ui.scene.node.Body;
import dev.everydaythings.graph.ui.scene.node.Container;
import dev.everydaythings.graph.ui.scene.node.Node;
import dev.everydaythings.graph.ui.scene.node.RenderEnvironment;
import dev.everydaythings.graph.ui.scene.node.ResolvedProps;
import dev.everydaythings.graph.ui.scene.node.SceneRenderer;
import dev.everydaythings.graph.ui.scene.node.Text;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * CLI (plain text) implementation of {@link SceneRenderer}.
 *
 * <p>Produces plain Unicode text — no ANSI codes, no colors, no hit testing.
 * This is the most minimal renderer, suitable for piped output and basic terminals.
 */
public class CliSceneRenderer implements SceneRenderer {

    private final Map<String, Map<String, Object>> store = new HashMap<>();
    private final Deque<String> scopeStack = new ArrayDeque<>();
    private RenderEnvironment env;

    private StringBuilder buffer;
    private int indentLevel;
    private final Deque<String> directionStack = new ArrayDeque<>();

    public CliSceneRenderer() {}

    public void environment(RenderEnvironment env) { this.env = env; }

    @Override public Map<String, Map<String, Object>> stateStore() { return store; }
    @Override public RenderEnvironment environment() { return env; }
    @Override public Deque<String> stateScopeStack() { return scopeStack; }

    public void renderTree(Node node) {
        buffer = new StringBuilder();
        indentLevel = 0;
        directionStack.clear();
        directionStack.push("vertical");
        scopeStack.clear();
        render(node);
    }

    public String result() {
        return buffer != null ? buffer.toString() : "";
    }

    @Override
    public void paintContainer(Container container, ResolvedProps props) {
        String layout = props.layout() != null ? props.layout() : "vertical";
        directionStack.push(layout);
        if ("vertical".equals(layout)) indentLevel++;
    }

    @Override
    public void paintText(Text text, ResolvedProps props) {
        String content = props.text() != null ? props.text() : "";
        if (content.isEmpty()) return;

        boolean isHorizontal = "horizontal".equals(directionStack.peek());
        if (isHorizontal) {
            buffer.append(content).append(" ");
        } else {
            appendIndent();
            buffer.append(content).append("\n");
        }
    }

    @Override
    public void paintBody(Body body, ResolvedProps props) {
        String display = props.glyph() != null ? props.glyph()
                : props.alt() != null ? props.alt() : "";
        if (display.isEmpty()) return;

        boolean isHorizontal = "horizontal".equals(directionStack.peek());
        if (isHorizontal) {
            buffer.append(display).append(" ");
        } else {
            appendIndent();
            buffer.append(display).append("\n");
        }
    }

    @Override
    public void endContainer() {
        String layout = directionStack.isEmpty() ? "vertical" : directionStack.pop();
        if ("vertical".equals(layout)) {
            indentLevel = Math.max(0, indentLevel - 1);
        } else if ("horizontal".equals(layout)) {
            buffer.append("\n");
        }
    }

    private void appendIndent() {
        for (int i = 0; i < indentLevel; i++) buffer.append("  ");
    }
}
