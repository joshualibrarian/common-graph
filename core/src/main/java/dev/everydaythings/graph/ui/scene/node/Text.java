package dev.everydaythings.graph.ui.scene.node;

import dev.everydaythings.graph.Canonical;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * The content primitive — displays text.
 *
 * <p>In 2D: renders as styled text within the layout.
 * In 3D: renders as a text panel or floating label.
 * In CLI: renders as plain text.
 *
 * <p>Text content can be a literal string or a bind expression
 * that resolves against the data context.
 */
@Getter
@Accessors(fluent = true)
@Canonical.Canonization
public class Text extends Node {

    /** The text content (literal or will be overridden by bind expression). */
    @Canon(order = 100)
    private String text;

    // ==================================================================================
    // Constructors
    // ==================================================================================

    public Text() {}

    public Text(String text) {
        this.text = text;
    }

    // ==================================================================================
    // Factories
    // ==================================================================================

    public static Text of(String text) {
        return new Text(text);
    }

    /** Text bound to an expression. */
    public static Text bound(String expression) {
        Text t = new Text();
        t.bind = expression;
        return t;
    }

    // ==================================================================================
    // Fluent Setters
    // ==================================================================================

    public Text text(String text) { this.text = text; return this; }
}
