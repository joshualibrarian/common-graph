package dev.everydaythings.graph.ui.style;

import dev.everydaythings.graph.ui.scene.RenderContext;

import java.util.Objects;

/**
 * A style rule - a selector paired with properties.
 *
 * <p>Rules are the building blocks of stylesheets. Each rule says
 * "elements matching this selector get these properties."
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * StyleRule rule = StyleRule.of(
 *     Selector.parse("TreeNode.chrome!tui"),
 *     StyleProperties.builder()
 *         .visible()
 *         .dim()
 *         .build()
 * );
 *
 * if (rule.matches(element, context)) {
 *     StyleProperties props = rule.properties();
 *     // apply props
 * }
 * }</pre>
 */
public record StyleRule(
        Selector selector,
        StyleProperties properties
) {

    public StyleRule {
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(properties, "properties");
    }

    // ==================== Factories ====================

    public static StyleRule of(Selector selector, StyleProperties properties) {
        return new StyleRule(selector, properties);
    }

    public static StyleRule of(String selector, StyleProperties properties) {
        return new StyleRule(Selector.parse(selector), properties);
    }

    /**
     * Create a rule that hides elements matching the selector.
     */
    public static StyleRule hidden(String selector) {
        return of(selector, StyleProperties.builder().hidden().build());
    }

    /**
     * Create a rule that shows elements matching the selector.
     */
    public static StyleRule visible(String selector) {
        return of(selector, StyleProperties.builder().visible().build());
    }

    // ==================== Matching ====================

    /**
     * Check if this rule matches an element in a context.
     */
    public boolean matches(Selector element, RenderContext context) {
        return selector.matches(element, context);
    }

    /**
     * Get the specificity of this rule's selector.
     */
    public int specificity() {
        return selector.specificity();
    }

    // ==================== Object Methods ====================

    @Override
    public String toString() {
        return selector + " " + properties;
    }
}
