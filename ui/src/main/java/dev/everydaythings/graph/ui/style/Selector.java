package dev.everydaythings.graph.ui.style;

import dev.everydaythings.graph.ui.scene.RenderContext;

import java.util.*;

/**
 * A CSS-like selector for matching surface elements.
 *
 * <p>Selectors can match on:
 * <ul>
 *   <li><b>Type</b> - Item/component type (e.g., {@code Librarian}, {@code TreeNode})</li>
 *   <li><b>Classes</b> - Style classes (e.g., {@code .chrome}, {@code .selected})</li>
 *   <li><b>ID</b> - Element ID (e.g., {@code #root})</li>
 *   <li><b>Renderer</b> - Renderer type (e.g., {@code !tui}, {@code !gui}, {@code !space})</li>
 *   <li><b>Breakpoint</b> - Size breakpoint (e.g., {@code @sm}, {@code @md}, {@code @lg})</li>
 *   <li><b>State</b> - Pseudo-class (e.g., {@code :hover}, {@code :selected}, {@code :expanded})</li>
 * </ul>
 *
 * <h2>Syntax</h2>
 * <pre>{@code
 * Type              - matches item type
 * .class            - matches style class
 * #id               - matches element ID
 * !renderer         - matches renderer type (tui, gui, space)
 * @breakpoint       - matches size (sm, md, lg)
 * :state            - matches pseudo-class (hover, selected, expanded)
 * }</pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Build programmatically
 * Selector sel = Selector.builder()
 *     .type("TreeNode")
 *     .addClass("chrome")
 *     .renderer("tui")
 *     .state("expanded")
 *     .build();
 *
 * // Parse from string
 * Selector sel = Selector.parse("TreeNode.chrome!tui:expanded");
 *
 * // For emitting from Surface (element identity)
 * Selector element = Selector.element("TreeNode", List.of("chrome"), "node-42");
 * }</pre>
 */
public class Selector {

    private final String type;
    private final Set<String> classes;
    private final String id;
    private final String renderer;
    private final String breakpoint;
    private final Set<String> states;

    private Selector(Builder builder) {
        this.type = builder.type;
        this.classes = builder.classes.isEmpty() ? Set.of() : Set.copyOf(builder.classes);
        this.id = builder.id;
        this.renderer = builder.renderer;
        this.breakpoint = builder.breakpoint;
        this.states = builder.states.isEmpty() ? Set.of() : Set.copyOf(builder.states);
    }

    // ==================== Factories ====================

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a selector for an element (used when emitting from Surface).
     * This represents the element's identity, not a rule selector.
     */
    public static Selector element(String type, List<String> classes, String id) {
        return builder()
                .type(type)
                .classes(classes)
                .id(id)
                .build();
    }

    /**
     * Create a selector for just a type.
     */
    public static Selector ofType(String type) {
        return builder().type(type).build();
    }

    /**
     * Create a selector for just classes.
     */
    public static Selector ofClasses(String... classes) {
        return builder().classes(List.of(classes)).build();
    }

    /**
     * Create a selector for just classes (from list).
     */
    public static Selector ofClasses(List<String> classes) {
        return builder().classes(classes).build();
    }

    /**
     * Empty selector (matches everything).
     */
    public static Selector any() {
        return builder().build();
    }

    /**
     * Parse a selector from CSS-like string.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code TreeNode} - type</li>
     *   <li>{@code .chrome} - class</li>
     *   <li>{@code #root} - id</li>
     *   <li>{@code !tui} - renderer</li>
     *   <li>{@code @sm} - breakpoint</li>
     *   <li>{@code :hover} - state</li>
     *   <li>{@code TreeNode.chrome!tui:expanded} - combined</li>
     * </ul>
     */
    public static Selector parse(String selector) {
        if (selector == null || selector.isBlank()) {
            return any();
        }

        Builder builder = builder();
        StringBuilder current = new StringBuilder();
        char mode = 'T'; // T=type, .=class, #=id, !=renderer, @=breakpoint, :=state

        for (int i = 0; i <= selector.length(); i++) {
            char c = i < selector.length() ? selector.charAt(i) : '\0';

            if (c == '.' || c == '#' || c == '!' || c == '@' || c == ':' || c == '\0') {
                // Flush current token
                String token = current.toString().trim();
                if (!token.isEmpty()) {
                    switch (mode) {
                        case 'T' -> builder.type(token);
                        case '.' -> builder.addClass(token);
                        case '#' -> builder.id(token);
                        case '!' -> builder.renderer(token);
                        case '@' -> builder.breakpoint(token);
                        case ':' -> builder.addState(token);
                    }
                }
                current.setLength(0);
                mode = c == '\0' ? 'T' : c;
            } else {
                current.append(c);
            }
        }

        return builder.build();
    }

    // ==================== Accessors ====================

    public String type() { return type; }
    public Set<String> classes() { return classes; }
    public String id() { return id; }
    public String renderer() { return renderer; }
    public String breakpoint() { return breakpoint; }
    public Set<String> states() { return states; }

    public boolean hasType() { return type != null && !type.isEmpty(); }
    public boolean hasClasses() { return !classes.isEmpty(); }
    public boolean hasId() { return id != null && !id.isEmpty(); }
    public boolean hasRenderer() { return renderer != null && !renderer.isEmpty(); }
    public boolean hasBreakpoint() { return breakpoint != null && !breakpoint.isEmpty(); }
    public boolean hasStates() { return !states.isEmpty(); }

    /**
     * Check if this selector is empty (matches everything).
     */
    public boolean isEmpty() {
        return !hasType() && !hasClasses() && !hasId()
            && !hasRenderer() && !hasBreakpoint() && !hasStates();
    }

    // ==================== Matching ====================

    /**
     * Check if this selector (as a rule) matches an element selector.
     *
     * @param element The element's selector (type, classes, id)
     * @param context The render context (renderer type, breakpoint, states)
     * @return true if this rule selector matches the element in this context
     */
    public boolean matches(Selector element, RenderContext context) {
        // Type must match (if specified)
        if (hasType() && !type.equals(element.type())) {
            return false;
        }

        // All rule classes must be present on element
        if (hasClasses() && !element.classes().containsAll(classes)) {
            return false;
        }

        // ID must match (if specified)
        if (hasId() && !id.equals(element.id())) {
            return false;
        }

        // Renderer must match context (if specified)
        if (hasRenderer() && !renderer.equals(context.renderer())) {
            return false;
        }

        // Breakpoint must match context (if specified)
        if (hasBreakpoint() && !breakpoint.equals(context.breakpoint())) {
            return false;
        }

        // All rule states must be active in context
        if (hasStates() && !context.states().containsAll(states)) {
            return false;
        }

        return true;
    }

    /**
     * Calculate specificity for cascade ordering.
     * Higher specificity wins when multiple rules match.
     *
     * <p>Specificity is calculated as (roughly following CSS):
     * <ul>
     *   <li>ID: 100</li>
     *   <li>Class, state, breakpoint, renderer: 10 each</li>
     *   <li>Type: 1</li>
     * </ul>
     */
    public int specificity() {
        int spec = 0;
        if (hasId()) spec += 100;
        spec += classes.size() * 10;
        spec += states.size() * 10;
        if (hasBreakpoint()) spec += 10;
        if (hasRenderer()) spec += 10;
        if (hasType()) spec += 1;
        return spec;
    }

    // ==================== Object Methods ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Selector that)) return false;
        return Objects.equals(type, that.type)
            && Objects.equals(classes, that.classes)
            && Objects.equals(id, that.id)
            && Objects.equals(renderer, that.renderer)
            && Objects.equals(breakpoint, that.breakpoint)
            && Objects.equals(states, that.states);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, classes, id, renderer, breakpoint, states);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (hasType()) sb.append(type);
        for (String cls : classes) sb.append('.').append(cls);
        if (hasId()) sb.append('#').append(id);
        if (hasRenderer()) sb.append('!').append(renderer);
        if (hasBreakpoint()) sb.append('@').append(breakpoint);
        for (String state : states) sb.append(':').append(state);
        return sb.length() > 0 ? sb.toString() : "*";
    }

    // ==================== Builder ====================

    public static class Builder {
        private String type;
        private final Set<String> classes = new LinkedHashSet<>();
        private String id;
        private String renderer;
        private String breakpoint;
        private final Set<String> states = new LinkedHashSet<>();

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder addClass(String className) {
            if (className != null && !className.isEmpty()) {
                this.classes.add(className);
            }
            return this;
        }

        public Builder classes(List<String> classes) {
            if (classes != null) {
                this.classes.addAll(classes);
            }
            return this;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder renderer(String renderer) {
            this.renderer = renderer;
            return this;
        }

        public Builder breakpoint(String breakpoint) {
            this.breakpoint = breakpoint;
            return this;
        }

        public Builder addState(String state) {
            if (state != null && !state.isEmpty()) {
                this.states.add(state);
            }
            return this;
        }

        public Builder states(List<String> states) {
            if (states != null) {
                this.states.addAll(states);
            }
            return this;
        }

        public Selector build() {
            return new Selector(this);
        }
    }
}
