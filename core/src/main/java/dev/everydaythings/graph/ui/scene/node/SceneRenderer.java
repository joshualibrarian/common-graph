package dev.everydaythings.graph.ui.scene.node;

import dev.everydaythings.graph.ui.scene.SceneEvent;

import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The rendering interface for Node trees — with a built-in state runtime.
 *
 * <p>SceneRenderer is both an interface and a runtime. Platform renderers
 * implement the abstract {@code paint*} methods (how to draw). The default
 * methods provide everything else: tree walking, state management, binding
 * evaluation, action dispatch, and property resolution.
 *
 * <h2>State Model</h2>
 *
 * <p>State is declared on nodes and managed here — not in model objects.
 *
 * <p><b>Three layers</b> are available to conditions:
 * <ul>
 *   <li><b>Pseudo-state</b> — renderer tracks automatically: hover, focus, active.
 *       Updated via {@link #setPseudoState}/{@link #clearPseudoState}.</li>
 *   <li><b>Declared state</b> — nodes declare via {@link Node.StateDecl}, persists
 *       across re-renders in the state store. Mutated by actions.</li>
 *   <li><b>Environment</b> — {@link RenderEnvironment}: renderer type, viewport,
 *       breakpoint, capabilities. Evaluated by {@code when} conditions.</li>
 * </ul>
 *
 * <h2>Actions</h2>
 *
 * <p>State mutation actions are a closed set:
 * <ul>
 *   <li>{@code toggle:key} — flip boolean</li>
 *   <li>{@code set:key=value} — set to literal</li>
 *   <li>{@code unset:key} — reset to default</li>
 *   <li>{@code cycle:key=a,b,c} — advance to next value in list</li>
 * </ul>
 *
 * <p>Unrecognized actions bubble to {@link #onApplicationAction} for the
 * session/item layer to handle (verb dispatch, navigation, etc.).
 *
 * <h2>Renderer Primitives</h2>
 *
 * <p>Some interactive behaviors are renderer capabilities, not actions:
 * <ul>
 *   <li><b>Text editing</b> — nodes with {@code editable(true)}. The renderer
 *       captures keystrokes and manages value/cursor/selection in the state store.</li>
 *   <li><b>Scrolling</b> — nodes with {@code overflow("scroll")} or {@code overflow("auto")}.
 *       The renderer manages scroll offset.</li>
 *   <li><b>Focus</b> — nodes with {@code capturesFocus(true)}. The renderer
 *       manages the focus chain and keyboard routing.</li>
 * </ul>
 *
 * <h2>Implementation</h2>
 *
 * <p>A platform renderer implements 4 abstract methods and provides 2 accessors:
 * <pre>{@code
 * public class SkiaSceneRenderer implements SceneRenderer {
 *     private final Map<String, Map<String, Object>> store = new HashMap<>();
 *     private final RenderEnvironment env;
 *
 *     public Map<String, Map<String, Object>> stateStore() { return store; }
 *     public RenderEnvironment environment() { return env; }
 *
 *     public void paintContainer(Container c, ResolvedProps props) { ... }
 *     public void paintText(Text t, ResolvedProps props) { ... }
 *     public void paintBody(Body b, ResolvedProps props) { ... }
 *     public void endContainer() { ... }
 * }
 * }</pre>
 */
public interface SceneRenderer {

    // ==================================================================================
    // Abstract — each renderer provides these
    // ==================================================================================

    /**
     * The per-node state store. Persists across re-renders.
     * Outer key: node ID. Inner map: state key → typed value.
     */
    Map<String, Map<String, Object>> stateStore();

    /** The current rendering environment (viewport, renderer type, capabilities). */
    RenderEnvironment environment();

    /**
     * Stack of ancestor node IDs for state scope resolution.
     *
     * <p>During tree walking, nodes with IDs are pushed onto this stack.
     * When a child node references {@code $state.key}, it resolves against
     * the nearest ancestor with an ID — not necessarily the node itself.
     * This allows children to read their parent's declared state.
     */
    Deque<String> stateScopeStack();

    /** Paint a Container node. Called before children are rendered. */
    void paintContainer(Container container, ResolvedProps props);

    /** Paint a Text node (leaf). */
    void paintText(Text text, ResolvedProps props);

    /** Paint a Body node (leaf). */
    void paintBody(Body body, ResolvedProps props);

    /** Close the current container (after all children have been rendered). */
    void endContainer();

    // ==================================================================================
    // Tree Walking — default implementation
    // ==================================================================================

    /**
     * Render a node tree. Entry point.
     *
     * <p>Walks the tree, initializes declared state, evaluates visibility
     * and when-blocks, then delegates to the platform's paint methods.
     */
    default void render(Node node) {
        if (node == null) return;
        if (!evaluateVisible(node)) return;

        // Push state scope if this node has an ID
        boolean pushedScope = false;
        if (node.id() != null && !node.id().isEmpty()) {
            stateScopeStack().push(node.id());
            pushedScope = true;
        }

        initDeclaredState(node);
        ResolvedProps props = resolveProperties(node);

        try {
            if (node instanceof Container c) {
                paintContainer(c, props);
                if (c.children() != null) {
                    for (Node child : c.children()) {
                        render(child);
                    }
                }
                endContainer();
            } else if (node instanceof Text t) {
                paintText(t, props);
            } else if (node instanceof Body b) {
                paintBody(b, props);
            }
        } finally {
            if (pushedScope) {
                stateScopeStack().pop();
            }
        }
    }


    // ==================================================================================
    // State Store Management
    // ==================================================================================

    /**
     * Initialize declared state for a node. Called on every render pass.
     * Only sets defaults for keys that don't already exist in the store.
     */
    default void initDeclaredState(Node node) {
        if (node.id() == null || node.state() == null) return;
        Map<String, Object> nodeState = stateStore()
                .computeIfAbsent(node.id(), k -> new HashMap<>());
        for (Node.StateDecl decl : node.state()) {
            nodeState.putIfAbsent(decl.key(), parseDefault(decl.defaultValue()));
        }
    }

    /**
     * Get a state value for a node.
     */
    default Object getState(String nodeId, String key) {
        Map<String, Object> nodeState = stateStore().get(nodeId);
        return nodeState != null ? nodeState.get(key) : null;
    }

    /**
     * Set a state value for a node. Typed — accepts Boolean, Integer, String, etc.
     */
    default void setState(String nodeId, String key, Object value) {
        stateStore().computeIfAbsent(nodeId, k -> new HashMap<>()).put(key, value);
    }

    // ==================================================================================
    // Pseudo-State (hover, focus, active — renderer-tracked)
    // ==================================================================================

    /**
     * The set of pseudo-states active for each node.
     * Default implementation uses a field on the state store keyed by "$pseudo".
     */
    default Set<String> getPseudoStates(String nodeId) {
        Object val = getState(nodeId, "$pseudo");
        if (val instanceof Set<?> s) {
            @SuppressWarnings("unchecked")
            Set<String> result = (Set<String>) s;
            return result;
        }
        return Set.of();
    }

    /** Mark a pseudo-state as active on a node (e.g., "hover"). */
    default void setPseudoState(String nodeId, String pseudo) {
        Map<String, Object> nodeState = stateStore()
                .computeIfAbsent(nodeId, k -> new HashMap<>());
        Object existing = nodeState.get("$pseudo");
        java.util.Set<String> pseudos;
        if (existing instanceof java.util.Set<?> s) {
            @SuppressWarnings("unchecked")
            java.util.Set<String> cast = (java.util.Set<String>) s;
            pseudos = cast;
        } else {
            pseudos = new java.util.HashSet<>();
            nodeState.put("$pseudo", pseudos);
        }
        pseudos.add(pseudo);
    }

    /** Remove a pseudo-state from a node. */
    default void clearPseudoState(String nodeId, String pseudo) {
        Object existing = getState(nodeId, "$pseudo");
        if (existing instanceof java.util.Set<?> s) {
            @SuppressWarnings("unchecked")
            java.util.Set<String> cast = (java.util.Set<String>) s;
            cast.remove(pseudo);
        }
    }

    // ==================================================================================
    // Action Dispatch
    // ==================================================================================

    /**
     * Dispatch an action triggered by an event on a node.
     *
     * <p>State mutation actions are handled here. Unrecognized actions
     * bubble to {@link #onApplicationAction}.
     *
     * @param nodeId the node that triggered the action
     * @param action the action string (e.g., "toggle:expanded", "set:mode=edit")
     * @param target the action target (often empty or the node ID)
     * @return true if the action was handled
     */
    default boolean dispatch(String nodeId, String action, String target) {
        if (action == null || action.isEmpty()) return false;

        // toggle:key — flip boolean in renderer state, then also notify application
        if (action.startsWith("toggle:")) {
            String key = action.substring("toggle:".length());
            String effectiveId = resolveActionTarget(nodeId, target);
            Object current = getState(effectiveId, key);
            boolean val = isTruthy(current);
            setState(effectiveId, key, !val);
            // Also notify application — toggles often drive Java-side state
            // (e.g., tree rebuilding, detail panel routing)
            onApplicationAction(nodeId, action, target);
            return true;
        }

        // set:key=value — set literal
        if (action.startsWith("set:")) {
            String rest = action.substring("set:".length());
            int eq = rest.indexOf('=');
            if (eq > 0) {
                String key = rest.substring(0, eq);
                String value = rest.substring(eq + 1);
                String effectiveId = resolveActionTarget(nodeId, target);
                setState(effectiveId, key, parseValue(value));
                return true;
            }
        }

        // unset:key — remove from store (reverts to declareState default on next render)
        if (action.startsWith("unset:")) {
            String key = action.substring("unset:".length());
            String effectiveId = resolveActionTarget(nodeId, target);
            Map<String, Object> nodeState = stateStore().get(effectiveId);
            if (nodeState != null) nodeState.remove(key);
            return true;
        }

        // cycle:key=a,b,c — advance to next value
        if (action.startsWith("cycle:")) {
            String rest = action.substring("cycle:".length());
            int eq = rest.indexOf('=');
            if (eq > 0) {
                String key = rest.substring(0, eq);
                String[] values = rest.substring(eq + 1).split(",");
                if (values.length > 0) {
                    String effectiveId = resolveActionTarget(nodeId, target);
                    Object current = getState(effectiveId, key);
                    String currentStr = current != null ? current.toString() : "";
                    int idx = -1;
                    for (int i = 0; i < values.length; i++) {
                        if (values[i].equals(currentStr)) { idx = i; break; }
                    }
                    String next = values[(idx + 1) % values.length];
                    setState(effectiveId, key, parseValue(next));
                    return true;
                }
            }
        }

        // Unrecognized → application action
        return onApplicationAction(nodeId, action, target);
    }

    /**
     * Handle an application-level action (verb dispatch, navigation, etc.).
     * Override in your renderer to bridge to the session/item layer.
     *
     * @return true if the action was handled
     */
    default boolean onApplicationAction(String nodeId, String action, String target) {
        return false;
    }

    // ==================================================================================
    // Visibility Evaluation
    // ==================================================================================

    /**
     * Evaluate whether a node is visible.
     *
     * <p>If the node has no {@code visible} binding, it's visible.
     * Otherwise, the binding expression is evaluated against the state store.
     */
    default boolean evaluateVisible(Node node) {
        String expr = node.visible();
        if (expr == null || expr.isEmpty()) return true;

        // Negation
        boolean negate = false;
        if (expr.startsWith("!")) {
            negate = true;
            expr = expr.substring(1);
        }

        boolean result;
        if (expr.startsWith("$state.")) {
            // $state.key — lookup state on nearest ancestor with ID (scope stack)
            String key = expr.substring("$state.".length());
            result = isTruthy(getState(currentStateScope(), key));
        } else {
            // Literal truthy check
            result = isTruthy(expr);
        }

        return negate ? !result : result;
    }

    // ==================================================================================
    // Property Resolution (when-blocks)
    // ==================================================================================

    /**
     * Resolve the final properties for a node by applying matching when-blocks.
     *
     * <p>Starts with the node's base properties, then applies overrides
     * from each {@code when} block whose condition matches.
     */
    default ResolvedProps resolveProperties(Node node) {
        ResolvedProps.Builder builder = ResolvedProps.from(node);

        // Apply when-block overrides
        if (node.when() != null && !node.when().isEmpty()) {
            for (Map.Entry<String, Map<String, String>> entry : node.when().entrySet()) {
                if (evaluateCondition(node, entry.getKey())) {
                    builder.applyOverrides(entry.getValue());
                }
            }
        }

        // Resolve semantic text tokens to display text
        if (node instanceof Text t && t.tokens() != null && !t.tokens().isEmpty()) {
            String resolved = resolveSemanticTokens(t.tokens());
            if (resolved != null) {
                builder.put("text", resolved);
            }
        }

        return builder.build();
    }

    /**
     * Resolve semantic tokens to display text via the language system.
     *
     * <p>Each token's sememe is resolved through the librarian to its
     * display name. Multiple tokens are joined with spaces. The active
     * language from the environment determines which lexeme is selected.
     *
     * <p>When the full NLG pipeline is available, this method will handle
     * grammatical features, word order, morphology, and agreement per
     * the target language's rules.
     *
     * @param tokens the semantic tokens to resolve
     * @return the resolved display text, or null if resolution fails
     */
    default String resolveSemanticTokens(List<Text.SemanticToken> tokens) {
        RenderEnvironment env = environment();
        if (env == null || env.librarian() == null) return null;

        StringBuilder sb = new StringBuilder();
        for (Text.SemanticToken token : tokens) {
            if (token.sememe() == null) continue;

            String display = null;

            // Resolve via librarian — get the sememe item and its display token
            try {
                var sememe = env.librarian().get(token.sememe());
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

    /**
     * Evaluate a when-block condition against all three state layers.
     *
     * <p>Checks in order:
     * <ol>
     *   <li>Pseudo-states: "hover", "focus", "active"</li>
     *   <li>Declared state: "$state.key" or bare key name</li>
     *   <li>Environment: ":skia", "@lg", "mouse", "landscape"</li>
     * </ol>
     */
    default boolean evaluateCondition(Node node, String condition) {
        if (condition == null || condition.isEmpty()) return true;

        // Negation
        boolean negate = false;
        String expr = condition;
        if (expr.startsWith("!")) {
            negate = true;
            expr = expr.substring(1);
        }

        boolean result;
        String scopeId = currentStateScope();

        // Pseudo-state check (hover, focus, active)
        if (scopeId != null && isPseudoState(expr)) {
            result = getPseudoStates(scopeId).contains(expr);
        }
        // Explicit state reference: $state.key
        else if (expr.startsWith("$state.") && scopeId != null) {
            String key = expr.substring("$state.".length());
            result = isTruthy(getState(scopeId, key));
        }
        // Environment condition: :renderer, @breakpoint, capability
        else if (expr.startsWith(":") || expr.startsWith("@")
                || environment().matches(expr)) {
            result = environment().matches(expr);
        }
        // Bare name — check declared state on nearest ancestor scope
        else if (scopeId != null) {
            result = isTruthy(getState(scopeId, expr));
        }
        else {
            result = false;
        }

        return negate ? !result : result;
    }

    // ==================================================================================
    // Event Routing
    // ==================================================================================

    /**
     * Route an event from the platform to the appropriate action.
     *
     * <p>The platform renderer calls this when it detects an event (click,
     * hover, etc.) on a node. This method finds matching event handlers
     * on the node and dispatches their actions.
     *
     * @param nodeId   the node that received the event
     * @param node     the node (for reading event declarations)
     * @param eventType "click", "hover", "doubleClick", etc.
     * @return true if any action was dispatched
     */
    default boolean routeEvent(String nodeId, Node node, String eventType) {
        if (node.events() == null) return false;
        boolean handled = false;
        for (SceneEvent event : node.events()) {
            if (eventType.equals(event.on())) {
                handled |= dispatch(nodeId, event.action(), event.target());
            }
        }
        return handled;
    }

    // ==================================================================================
    // Key Event Dispatch
    // ==================================================================================

    /**
     * Dispatch a key event by matching it against @Scene.On declarations in the node tree.
     *
     * <p>Walks the node tree recursively, checking each node's events for a matching
     * key chord. When found, dispatches the action through the state runtime.
     * Returns true if any action was dispatched.
     *
     * @param root     the root of the current node tree
     * @param keyChord the key chord string (e.g., "F1", "Alt+Up", "Ctrl+S")
     * @return true if a matching event was found and dispatched
     */
    default boolean dispatchKeyEvent(Node root, String keyChord) {
        if (root == null || keyChord == null) return false;
        return dispatchKeyEventRecursive(root, keyChord);
    }

    private boolean dispatchKeyEventRecursive(Node node, String keyChord) {
        // Check this node's events
        if (node.events() != null) {
            for (SceneEvent event : node.events()) {
                if (keyChord.equals(event.on())) {
                    String nodeId = node.id() != null ? node.id() : "";
                    if (dispatch(nodeId, event.action(), event.target())) {
                        return true;
                    }
                }
            }
        }
        // Recurse into container children
        if (node instanceof Container c && c.children() != null) {
            for (Node child : c.children()) {
                if (dispatchKeyEventRecursive(child, keyChord)) return true;
            }
        }
        return false;
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    /**
     * Get the current state scope — the nearest ancestor node ID.
     * Returns null if no ancestor with an ID has been entered.
     */
    default String currentStateScope() {
        Deque<String> stack = stateScopeStack();
        return stack != null && !stack.isEmpty() ? stack.peek() : null;
    }

    /** Is this a renderer-tracked pseudo-state name? */
    private static boolean isPseudoState(String name) {
        return "hover".equals(name) || "focus".equals(name) || "active".equals(name);
    }

    /**
     * Resolve the target node ID for an action.
     * Empty or "self" → the triggering node. Otherwise use target as-is.
     */
    private static String resolveActionTarget(String nodeId, String target) {
        if (target == null || target.isEmpty() || "self".equals(target)) return nodeId;
        return target;
    }

    /** Parse a default value string to a typed Object. */
    private static Object parseDefault(String defaultValue) {
        return parseValue(defaultValue);
    }

    /** Parse a value string to a typed Object. */
    private static Object parseValue(String value) {
        if (value == null) return null;
        if ("true".equals(value)) return Boolean.TRUE;
        if ("false".equals(value)) return Boolean.FALSE;
        try { return Integer.parseInt(value); } catch (NumberFormatException ignored) {}
        try { return Double.parseDouble(value); } catch (NumberFormatException ignored) {}
        return value;
    }

    /** Is a value "truthy"? Null, false, 0, and empty string are falsy. */
    private static boolean isTruthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.doubleValue() != 0.0;
        if (value instanceof String s) return !s.isEmpty() && !"false".equals(s);
        return true;
    }
}
