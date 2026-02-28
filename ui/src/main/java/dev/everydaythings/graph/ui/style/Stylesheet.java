package dev.everydaythings.graph.ui.style;

import dev.everydaythings.graph.ui.scene.RenderContext;
import dev.everydaythings.graph.ui.scene.Scene;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;

import java.util.*;

/**
 * A collection of style rules.
 *
 * <p>Stylesheet holds rules and provides methods for resolving
 * computed styles for elements based on matching rules and specificity.
 *
 * <h2>Cascade Order</h2>
 * <p>When multiple rules match an element, they are applied in order of:
 * <ol>
 *   <li>Specificity (higher wins)</li>
 *   <li>Source order (later wins for equal specificity)</li>
 * </ol>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Stylesheet stylesheet = Stylesheet.builder()
 *     .rule("TreeNode", props -> props.paddingLeft(2))
 *     .rule("TreeNode.chrome!tui", props -> props.visible().dim())
 *     .rule("TreeNode.chrome!gui", props -> props.hidden())
 *     .rule("TreeNode:selected", props -> props.reverse())
 *     .build();
 *
 * // Resolve styles for an element
 * Selector element = Selector.element("TreeNode", List.of("chrome"), "node-1");
 * RenderContext context = RenderContext.tui().withState("selected");
 * StyleProperties computed = stylesheet.resolve(element, context);
 * }</pre>
 */
public class Stylesheet {

    /** Empty stylesheet. */
    public static final Stylesheet EMPTY = new Stylesheet(List.of());

    private final List<StyleRule> rules;

    private Stylesheet(List<StyleRule> rules) {
        this.rules = List.copyOf(rules);
    }

    // ==================== Factories ====================

    public static Builder builder() {
        return new Builder();
    }

    public static Stylesheet of(StyleRule... rules) {
        return new Stylesheet(List.of(rules));
    }

    public static Stylesheet of(List<StyleRule> rules) {
        return new Stylesheet(rules);
    }

    // ==================== Resolution ====================

    /**
     * Resolve computed styles for an element in a context.
     *
     * <p>Finds all matching rules, sorts by specificity, and merges properties.
     *
     * @param element The element's selector
     * @param context The render context
     * @return Computed style properties
     */
    public StyleProperties resolve(Selector element, RenderContext context) {
        // Find matching rules
        List<StyleRule> matching = new ArrayList<>();
        for (StyleRule rule : rules) {
            if (rule.matches(element, context)) {
                matching.add(rule);
            }
        }

        if (matching.isEmpty()) {
            return StyleProperties.EMPTY;
        }

        // Sort by specificity (stable sort preserves source order for equal specificity)
        matching.sort(Comparator.comparingInt(StyleRule::specificity));

        // Merge properties (later/higher specificity overrides earlier/lower)
        StyleProperties result = StyleProperties.EMPTY;
        for (StyleRule rule : matching) {
            result = result.merge(rule.properties());
        }

        return result;
    }

    /**
     * Get all rules in this stylesheet.
     */
    public List<StyleRule> rules() {
        return rules;
    }

    /**
     * Check if this stylesheet is empty.
     */
    public boolean isEmpty() {
        return rules.isEmpty();
    }

    /**
     * Merge with another stylesheet.
     * Rules from the other stylesheet are added after this one's rules.
     */
    public Stylesheet merge(Stylesheet other) {
        if (other == null || other.isEmpty()) {
            return this;
        }
        if (this.isEmpty()) {
            return other;
        }

        List<StyleRule> merged = new ArrayList<>(this.rules);
        merged.addAll(other.rules);
        return new Stylesheet(merged);
    }

    // ==================== Object Methods ====================

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (StyleRule rule : rules) {
            sb.append(rule).append("\n");
        }
        return sb.toString();
    }

    // ==================== Builder ====================

    public static class Builder {
        private final List<StyleRule> rules = new ArrayList<>();

        /**
         * Add a rule with selector string and properties.
         */
        public Builder rule(String selector, StyleProperties properties) {
            rules.add(StyleRule.of(selector, properties));
            return this;
        }

        /**
         * Add a rule with selector string and property builder.
         */
        public Builder rule(String selector, java.util.function.Consumer<StyleProperties.Builder> propsBuilder) {
            StyleProperties.Builder builder = StyleProperties.builder();
            propsBuilder.accept(builder);
            rules.add(StyleRule.of(selector, builder.build()));
            return this;
        }

        /**
         * Add a rule with selector and properties.
         */
        public Builder rule(Selector selector, StyleProperties properties) {
            rules.add(StyleRule.of(selector, properties));
            return this;
        }

        /**
         * Add a pre-built rule.
         */
        public Builder rule(StyleRule rule) {
            rules.add(rule);
            return this;
        }

        /**
         * Add a rule that hides elements matching selector.
         */
        public Builder hidden(String selector) {
            rules.add(StyleRule.hidden(selector));
            return this;
        }

        /**
         * Add a rule that shows elements matching selector.
         */
        public Builder visible(String selector) {
            rules.add(StyleRule.visible(selector));
            return this;
        }

        public Stylesheet build() {
            return new Stylesheet(rules);
        }
    }

    // ==================== Classpath-Scanned Stylesheet ====================

    private static volatile Stylesheet classpathInstance;

    /**
     * Build a stylesheet by scanning {@code @Scene.Rule} annotations from the classpath.
     *
     * <p>Each class annotates itself with the style rules it owns. This method
     * uses ClassGraph to find all classes with {@link Scene.Rule} or
     * {@link Scene.Rules} annotations and assembles them into a single stylesheet.
     *
     * <p>The result is cached — subsequent calls return the same instance.
     */
    public static Stylesheet fromClasspath() {
        if (classpathInstance != null) return classpathInstance;
        synchronized (Stylesheet.class) {
            if (classpathInstance != null) return classpathInstance;
            classpathInstance = scanClasspath();
            return classpathInstance;
        }
    }

    private static Stylesheet scanClasspath() {
        Builder builder = builder();
        try (ScanResult scanResult = new ClassGraph()
                .acceptPackages("dev.everydaythings.graph")
                .enableClassInfo()
                .enableAnnotationInfo()
                .scan()) {

            for (ClassInfo classInfo : scanResult.getClassesWithAnnotation(Scene.Rule.class)) {
                addRulesFrom(classInfo.loadClass(), builder);
            }
            for (ClassInfo classInfo : scanResult.getClassesWithAnnotation(Scene.Rules.class)) {
                addRulesFrom(classInfo.loadClass(), builder);
            }
        }
        return builder.build();
    }

    private static void addRulesFrom(Class<?> clazz, Builder builder) {
        Scene.Rule[] rules = clazz.getAnnotationsByType(Scene.Rule.class);
        for (Scene.Rule rule : rules) {
            StyleProperties.Builder props = StyleProperties.builder();

            if (!rule.color().isEmpty()) {
                props.colorInt(parseHexColor(rule.color()));
            }
            if (!rule.background().isEmpty()) {
                if ("reverse".equals(rule.background())) {
                    props.reverse();
                } else {
                    props.backgroundColorInt(parseHexColor(rule.background()));
                }
            }
            if (!rule.fontSize().isEmpty()) {
                props.fontSizeRatio(Float.parseFloat(rule.fontSize()));
            }
            if (!rule.fontFamily().isEmpty()) {
                props.fontFamily(rule.fontFamily());
            }
            if (!rule.fontWeight().isEmpty()) {
                if ("bold".equals(rule.fontWeight())) {
                    props.bold();
                } else {
                    props.fontWeight(rule.fontWeight());
                }
            }
            if (!rule.display().isEmpty()) {
                props.display(rule.display());
            }
            if (!rule.opacity().isEmpty()) {
                props.opacity(rule.opacity());
            }
            if (!rule.rotation().isEmpty()) {
                props.set("rotation", rule.rotation());
            }

            builder.rule(rule.match(), props.build());
        }
    }

    /**
     * Parse a hex color string like "#89B4FA" or "#FF89B4FA" into an ARGB int.
     * If no alpha is specified, 0xFF is assumed.
     */
    static int parseHexColor(String hex) {
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.length() == 6) {
            return 0xFF000000 | Integer.parseInt(hex, 16);
        } else if (hex.length() == 8) {
            return (int) Long.parseLong(hex, 16);
        }
        throw new IllegalArgumentException("Invalid hex color: " + hex);
    }
}
