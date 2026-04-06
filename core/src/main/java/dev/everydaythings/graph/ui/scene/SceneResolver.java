package dev.everydaythings.graph.ui.scene;

import dev.everydaythings.graph.runtime.LibrarianHandle;
import dev.everydaythings.graph.ui.scene.SceneNode.SemanticToken;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves a SceneNode tree against live state on the librarian side.
 *
 * <p>The resolver evaluates the tree in place — no copies are made.
 * After resolution, the tree is ready for presentation (layout) or
 * wire transfer to the window.
 *
 * <h2>What the Resolver Does</h2>
 * <ul>
 *   <li><b>Binding expressions</b> evaluated:
 *       {@code "bind:value.typeName"} → resolved value</li>
 *   <li><b>Sememe references</b> resolved to lexemes in the user's language</li>
 *   <li><b>Visibility conditions</b> evaluated:
 *       {@code visible = "$item.piece"} → {@code true}</li>
 *   <li><b>When-block conditions</b> evaluated against item state and
 *       environment (NOT interaction state — that's the presenter's job)</li>
 *   <li><b>Declared state</b> initialized in the {@link InteractionState}</li>
 *   <li><b>Style cascade</b> applied: type defaults → item overrides
 *       → session overrides via {@code CONFIG:[PRESENTATION]} bindings</li>
 * </ul>
 *
 * <h2>What the Resolver Does NOT Do</h2>
 * <ul>
 *   <li>Resolve dimensional values ({@code "50%"}, {@code "1cm"})
 *       — needs viewport context (presenter)</li>
 *   <li>Evaluate interaction state (hover, selected, expanded)
 *       — lives on the window (presenter)</li>
 *   <li>Compute layout or assign bounds (presenter)</li>
 * </ul>
 *
 * @see SceneNode
 * @see InteractionState
 */
public class SceneResolver {

    // =================================================================================
    // Context
    // =================================================================================

    /**
     * Context for resolution — everything the resolver needs.
     */
    @lombok.Getter
    @lombok.experimental.Accessors(fluent = true)
    public static class ResolveContext {
        /** Handle for sememe resolution and config lookup. */
        private LibrarianHandle librarian;
        /** Active environment tags (":skia", "@lg", "mouse", etc.). */
        private Set<String> environmentTags;
        /** The per-session interaction state store. */
        private InteractionState state;

        public ResolveContext() {}

        public ResolveContext librarian(LibrarianHandle librarian) {
            this.librarian = librarian;
            return this;
        }

        public ResolveContext environmentTags(Set<String> environmentTags) {
            this.environmentTags = environmentTags;
            return this;
        }

        public ResolveContext state(InteractionState state) {
            this.state = state;
            return this;
        }
    }

    // =================================================================================
    // Resolution
    // =================================================================================

    /**
     * Resolve the given SceneNode tree in place.
     */
    public void resolve(SceneNode tree, ResolveContext ctx) {
        // Collect class-based styles from root's when-blocks (compiled from @Scene.Style)
        Map<String, Map<String, String>> classStyles = extractClassStyles(tree);
        resolveNode(tree, ctx, new ArrayDeque<>(), classStyles);
    }

    /**
     * Extract class-selector styles from a node's when-blocks.
     * These are entries where the key starts with "." (class selector).
     */
    private Map<String, Map<String, String>> extractClassStyles(SceneNode root) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        if (root.when() == null) return result;
        for (var entry : root.when().entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(".") || key.startsWith("#")) {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }

    private void resolveNode(SceneNode node, ResolveContext ctx, Deque<String> scopeStack,
                             Map<String, Map<String, String>> classStyles) {
        if (node == null) return;

        // Push state scope if this node has an ID
        boolean pushedScope = false;
        if (node.id() != null && !node.id().isEmpty()) {
            scopeStack.push(node.id());
            pushedScope = true;
        }

        try {
            String scopeId = scopeStack.isEmpty() ? null : scopeStack.peek();

            // Initialize declared state
            if (node.id() != null && node.state() != null) {
                ctx.state().initDeclaredState(node.id(), node.state());
            }

            // Apply class-based styles from @Scene.Style annotations
            applyClassStyles(node, classStyles);

            // Evaluate visibility
            if (!evaluateVisible(node, ctx, scopeId)) {
                node.visible(false);
                return;
            }

            // Evaluate when-blocks (item state + environment, NOT pseudo-state)
            applyWhenBlocks(node, ctx, scopeId);

            // Resolve semantic tokens to display text
            if (node.type() == SceneNode.NodeType.TEXT
                    && node.tokens() != null && !node.tokens().isEmpty()) {
                String resolved = resolveSemanticTokens(node.tokens(), ctx);
                if (resolved != null) {
                    node.text(resolved);
                }
            }

            // Recurse into children
            if (node.children() != null) {
                for (SceneNode child : node.children()) {
                    resolveNode(child, ctx, scopeStack, classStyles);
                }
            }

            // Recurse into body surfaces
            if (node.surfaces() != null) {
                for (SceneNode surface : node.surfaces().values()) {
                    resolveNode(surface, ctx, scopeStack, classStyles);
                }
            }

            // Recurse into child template
            if (node.childTemplate() != null) {
                resolveNode(node.childTemplate(), ctx, scopeStack, classStyles);
            }
        } finally {
            if (pushedScope) {
                scopeStack.pop();
            }
        }
    }

    // =================================================================================
    // Class-Based Style Application
    // =================================================================================

    /**
     * Apply class-based styles to a node. Matches the node's classes and ID
     * against the style selectors collected from @Scene.Style annotations.
     */
    private void applyClassStyles(SceneNode node, Map<String, Map<String, String>> classStyles) {
        if (classStyles.isEmpty()) return;

        List<String> nodeClasses = node.classes();
        String nodeId = node.id();

        for (var entry : classStyles.entrySet()) {
            String selector = entry.getKey();
            if (matchesSelector(selector, nodeClasses, nodeId)) {
                applyOverrides(node, entry.getValue());
            }
        }
    }

    /**
     * Check if a node matches a CSS-like selector.
     * ".heading" matches nodes with class "heading".
     * ".square.light" matches nodes with BOTH classes "square" and "light".
     * "#board" matches the node with id "board".
     */
    private boolean matchesSelector(String selector, List<String> classes, String id) {
        if (selector.startsWith("#")) {
            return selector.substring(1).equals(id);
        }
        if (selector.startsWith(".")) {
            // Split compound selectors: ".square.light" → ["square", "light"]
            String[] parts = selector.substring(1).split("\\.");
            if (classes == null) return false;
            for (String part : parts) {
                if (!classes.contains(part)) return false;
            }
            return true;
        }
        return false;
    }

    // =================================================================================
    // Visibility
    // =================================================================================

    private boolean evaluateVisible(SceneNode node, ResolveContext ctx, String scopeId) {
        Object vis = node.visible();
        if (vis == null) return true;
        if (vis instanceof Boolean b) return b;
        if (!(vis instanceof String expr) || expr.isEmpty()) return true;

        boolean negate = false;
        if (expr.startsWith("!")) {
            negate = true;
            expr = expr.substring(1);
        }

        boolean result;
        if (expr.startsWith("$state.")) {
            String key = expr.substring("$state.".length());
            result = InteractionState.isTruthy(
                    scopeId != null ? ctx.state().getState(scopeId, key) : null);
        } else {
            result = InteractionState.isTruthy(expr);
        }

        return negate ? !result : result;
    }

    // =================================================================================
    // When-Block Evaluation
    // =================================================================================

    private void applyWhenBlocks(SceneNode node, ResolveContext ctx, String scopeId) {
        Map<String, Map<String, String>> when = node.when();
        if (when == null || when.isEmpty()) return;

        for (Map.Entry<String, Map<String, String>> entry : when.entrySet()) {
            String condition = entry.getKey();

            // Skip pseudo-state conditions — those are for the presenter
            if (InteractionState.isPseudoState(condition)
                    || InteractionState.isPseudoState(
                    condition.startsWith("!") ? condition.substring(1) : condition)) {
                continue;
            }

            if (evaluateCondition(condition, ctx, scopeId)) {
                applyOverrides(node, entry.getValue());
            }
        }
    }

    private boolean evaluateCondition(String condition, ResolveContext ctx, String scopeId) {
        if (condition == null || condition.isEmpty()) return true;

        boolean negate = false;
        String expr = condition;
        if (expr.startsWith("!")) {
            negate = true;
            expr = expr.substring(1);
        }

        boolean result;

        // Explicit state reference: $state.key
        if (expr.startsWith("$state.") && scopeId != null) {
            String key = expr.substring("$state.".length());
            result = InteractionState.isTruthy(ctx.state().getState(scopeId, key));
        }
        // Environment condition: :renderer, @breakpoint, capability
        else if (expr.startsWith(":") || expr.startsWith("@")
                || (ctx.environmentTags() != null && ctx.environmentTags().contains(expr))) {
            result = ctx.environmentTags() != null && ctx.environmentTags().contains(expr);
        }
        // Bare name — check declared state on nearest ancestor scope
        else if (scopeId != null) {
            result = InteractionState.isTruthy(ctx.state().getState(scopeId, expr));
        }
        else {
            result = false;
        }

        return negate ? !result : result;
    }

    // =================================================================================
    // Property Overrides
    // =================================================================================

    private void applyOverrides(SceneNode node, Map<String, String> overrides) {
        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            applyProperty(node, entry.getKey(), entry.getValue());
        }
    }

    /**
     * Apply a single property override to a SceneNode.
     */
    private void applyProperty(SceneNode node, String property, String value) {
        switch (property) {
            case "background" -> {  // shorthand — detects gradient syntax or sets color
                Gradient g = Gradient.parse(value);
                if (g != null) node.backgroundGradient(g);
                else node.backgroundColor(value);
            }
            case "backgroundColor" -> node.backgroundColor(value);
            case "backgroundImage" -> node.backgroundImage(value);
            case "backgroundSize" -> node.backgroundSize(value);
            case "foreground" -> node.foreground(value);
            case "border" -> node.border(value);
            case "borderTopWidth" -> node.borderTopWidth(value);
            case "borderRightWidth" -> node.borderRightWidth(value);
            case "borderBottomWidth" -> node.borderBottomWidth(value);
            case "borderLeftWidth" -> node.borderLeftWidth(value);
            case "borderTopStyle" -> node.borderTopStyle(value);
            case "borderRightStyle" -> node.borderRightStyle(value);
            case "borderBottomStyle" -> node.borderBottomStyle(value);
            case "borderLeftStyle" -> node.borderLeftStyle(value);
            case "borderTopColor" -> node.borderTopColor(value);
            case "borderRightColor" -> node.borderRightColor(value);
            case "borderBottomColor" -> node.borderBottomColor(value);
            case "borderLeftColor" -> node.borderLeftColor(value);
            case "transitionProperty" -> node.transitionProperty(value);
            case "transitionDuration" -> node.transitionDuration(value);
            case "transitionEasing" -> node.transitionEasing(value);
            case "transitionDelay" -> node.transitionDelay(value);
            case "padding" -> node.padding(value);
            case "margin" -> node.margin(value);
            case "width" -> node.width(value);
            case "height" -> node.height(value);
            case "gap" -> node.gap(value);
            case "corner" -> node.corner(value);
            case "fontSize" -> node.fontSize(value);
            case "fontFamily" -> node.fontFamily(value);
            case "fontWeight" -> node.fontWeight(value);
            case "fontStyle" -> node.fontStyle(value);
            case "textDecoration" -> node.textDecoration(value);
            case "textAlign" -> node.textAlign(value);
            case "lineHeight" -> node.lineHeight(value);
            case "letterSpacing" -> node.letterSpacing(value);
            case "textOverflow" -> node.textOverflow(value);
            case "whiteSpace" -> node.whiteSpace(value);
            case "minWidth" -> node.minWidth(value);
            case "maxWidth" -> node.maxWidth(value);
            case "minHeight" -> node.minHeight(value);
            case "maxHeight" -> node.maxHeight(value);
            case "opacity" -> node.opacity(value);
            case "overflow" -> node.overflow(value);
            case "visible" -> node.visible(value);
            case "cursor" -> node.cursor(value);
            case "fill" -> node.fill(value);
            case "strokeColor" -> node.strokeColor(value);
            case "strokeWidth" -> node.strokeWidth(value);
            case "pathData" -> node.pathData(value);
            case "anchorTop" -> node.anchorTop(value);
            case "anchorRight" -> node.anchorRight(value);
            case "anchorBottom" -> node.anchorBottom(value);
            case "anchorLeft" -> node.anchorLeft(value);
            case "animationDuration" -> node.animationDuration(value);
            case "animationIterationCount" -> node.animationIterationCount(value);
            case "animationDirection" -> node.animationDirection(value);
            case "animationEasing" -> node.animationEasing(value);
            case "animationDelay" -> node.animationDelay(value);
            case "animationFillMode" -> node.animationFillMode(value);
            case "animationPlayState" -> node.animationPlayState(value);
            case "rotation" -> node.rotation(value);  // shorthand → rotationZ
            case "rotationX" -> node.rotationX(value);
            case "rotationY" -> node.rotationY(value);
            case "rotationZ" -> node.rotationZ(value);
            case "scale" -> node.scale(value);
            case "scaleX" -> node.scaleX(value);
            case "scaleY" -> node.scaleY(value);
            case "scaleZ" -> node.scaleZ(value);
            case "transformOrigin" -> node.transformOrigin(value);
            case "align" -> node.align(value);
            case "justify" -> node.justify(value);
            case "layout" -> node.layout(value);
            case "text" -> node.text(value);
            case "image" -> node.image(value);
            case "glyph" -> node.glyph(value);
            // Ignore unknown properties silently
        }
    }

    // =================================================================================
    // Semantic Token Resolution
    // =================================================================================

    private String resolveSemanticTokens(List<SemanticToken> tokens, ResolveContext ctx) {
        if (ctx.librarian() == null) return null;

        StringBuilder sb = new StringBuilder();
        for (SemanticToken token : tokens) {
            if (token.sememe() == null) continue;

            String display = null;

            try {
                var sememe = ctx.librarian().get(token.sememe());
                if (sememe.isPresent()) {
                    display = sememe.get().displayToken();
                }
            } catch (Exception ignored) {}

            // Fallback: use the sememe key's last segment
            if (display == null) {
                String key = token.sememe().encodeText();
                int colon = key.lastIndexOf(':');
                display = colon >= 0 ? key.substring(colon + 1) : key;
            }

            if (!sb.isEmpty()) sb.append(" ");
            sb.append(display);
        }

        return sb.isEmpty() ? null : sb.toString();
    }
}
