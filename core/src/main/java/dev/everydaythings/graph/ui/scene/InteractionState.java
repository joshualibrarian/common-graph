package dev.everydaythings.graph.ui.scene;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
/**
 * Per-session state store for scene interaction.
 *
 * <p>Owns declared state (toggle/set/unset/cycle) and pseudo-state
 * (hover, focus, active) for all nodes. Persists across re-renders —
 * owned by {@code Session} and shared by all rendering modes.
 *
 * <p>This is the state runtime extracted from the old {@code SceneRenderer}
 * interface, now a standalone class.
 *
 * <h2>State Layers</h2>
 * <ul>
 *   <li><b>Declared state</b> — nodes declare via
 *       {@link SceneNode.StateDecl}, initialized on first render,
 *       mutated by actions (toggle, set, unset, cycle).</li>
 *   <li><b>Pseudo-state</b> — renderer-tracked: hover, focus, active.
 *       Updated by the presenter from platform input.</li>
 * </ul>
 *
 * <h2>Actions</h2>
 * <ul>
 *   <li>{@code toggle:key} — flip boolean</li>
 *   <li>{@code set:key=value} — set to literal</li>
 *   <li>{@code unset:key} — reset to default</li>
 *   <li>{@code cycle:key=a,b,c} — advance to next value in list</li>
 * </ul>
 *
 * <p>Unrecognized actions bubble to the application action handler
 * (set by the session for verb dispatch, navigation, etc.).
 */
public class InteractionState {

    /** Outer key: node ID. Inner map: state key → typed value. */
    private final Map<String, Map<String, Object>> store = new HashMap<>();

    /** Handler for application-level actions not handled by the state runtime. */
    @FunctionalInterface
    public interface ApplicationActionHandler {
        boolean handle(String nodeId, String action, String target);
    }

    private ApplicationActionHandler applicationActionHandler;

    // =================================================================================
    // Application Action Handler
    // =================================================================================

    /**
     * Set the handler for application-level actions (verb dispatch, navigation).
     *
     * @param handler (nodeId, action, target) → handled
     */
    public void onApplicationAction(ApplicationActionHandler handler) {
        this.applicationActionHandler = handler;
    }

    // =================================================================================
    // State Access
    // =================================================================================

    public Object getState(String nodeId, String key) {
        Map<String, Object> nodeState = store.get(nodeId);
        return nodeState != null ? nodeState.get(key) : null;
    }

    public void setState(String nodeId, String key, Object value) {
        store.computeIfAbsent(nodeId, k -> new HashMap<>()).put(key, value);
    }

    /**
     * Initialize declared state for a node. Only sets defaults for keys
     * that don't already exist in the store (preserves across re-renders).
     */
    public void initDeclaredState(String nodeId, List<SceneNode.StateDecl> declarations) {
        if (nodeId == null || declarations == null) return;
        Map<String, Object> nodeState = store.computeIfAbsent(nodeId, k -> new HashMap<>());
        for (SceneNode.StateDecl decl : declarations) {
            nodeState.putIfAbsent(decl.key(), parseValue(decl.defaultValue()));
        }
    }

    // =================================================================================
    // Pseudo-State (hover, focus, active)
    // =================================================================================

    public Set<String> getPseudoStates(String nodeId) {
        Object val = getState(nodeId, "$pseudo");
        if (val instanceof Set<?> s) {
            @SuppressWarnings("unchecked")
            Set<String> result = (Set<String>) s;
            return result;
        }
        return Set.of();
    }

    public void setPseudoState(String nodeId, String pseudo) {
        Map<String, Object> nodeState = store.computeIfAbsent(nodeId, k -> new HashMap<>());
        Object existing = nodeState.get("$pseudo");
        Set<String> pseudos;
        if (existing instanceof Set<?> s) {
            @SuppressWarnings("unchecked")
            Set<String> cast = (Set<String>) s;
            pseudos = cast;
        } else {
            pseudos = new HashSet<>();
            nodeState.put("$pseudo", pseudos);
        }
        pseudos.add(pseudo);
    }

    public void clearPseudoState(String nodeId, String pseudo) {
        Object existing = getState(nodeId, "$pseudo");
        if (existing instanceof Set<?> s) {
            @SuppressWarnings("unchecked")
            Set<String> cast = (Set<String>) s;
            cast.remove(pseudo);
        }
    }

    public static boolean isPseudoState(String name) {
        return "hover".equals(name) || "focus".equals(name) || "active".equals(name);
    }

    // =================================================================================
    // Action Dispatch
    // =================================================================================

    /**
     * Dispatch an action triggered by a scene event.
     *
     * @param nodeId the node that triggered the action
     * @param action the action string (e.g., "toggle:expanded", "set:mode=edit")
     * @param target the action target (often empty or the node ID)
     * @return true if the action was handled
     */
    public boolean dispatch(String nodeId, String action, String target) {
        if (action == null || action.isEmpty()) return false;

        String effectiveId = resolveActionTarget(nodeId, target);

        if (action.startsWith("toggle:")) {
            String key = action.substring("toggle:".length());
            Object current = getState(effectiveId, key);
            setState(effectiveId, key, !isTruthy(current));
            // Also notify application — toggles often drive Java-side state
            notifyApplication(nodeId, action, target);
            return true;
        }

        if (action.startsWith("set:")) {
            String rest = action.substring("set:".length());
            int eq = rest.indexOf('=');
            if (eq > 0) {
                String key = rest.substring(0, eq);
                String value = rest.substring(eq + 1);
                setState(effectiveId, key, parseValue(value));
                return true;
            }
        }

        if (action.startsWith("unset:")) {
            String key = action.substring("unset:".length());
            Map<String, Object> nodeState = store.get(effectiveId);
            if (nodeState != null) nodeState.remove(key);
            return true;
        }

        if (action.startsWith("cycle:")) {
            String rest = action.substring("cycle:".length());
            int eq = rest.indexOf('=');
            if (eq > 0) {
                String key = rest.substring(0, eq);
                String[] values = rest.substring(eq + 1).split(",");
                if (values.length > 0) {
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
        return notifyApplication(nodeId, action, target);
    }

    private boolean notifyApplication(String nodeId, String action, String target) {
        if (applicationActionHandler != null) {
            return applicationActionHandler.handle(nodeId, action, target);
        }
        return false;
    }

    // =================================================================================
    // Helpers
    // =================================================================================

    private static String resolveActionTarget(String nodeId, String target) {
        if (target == null || target.isEmpty() || "self".equals(target)) return nodeId;
        return target;
    }

    /** Parse a value string to a typed Object. */
    public static Object parseValue(String value) {
        if (value == null) return null;
        if ("true".equals(value)) return Boolean.TRUE;
        if ("false".equals(value)) return Boolean.FALSE;
        try { return Integer.parseInt(value); } catch (NumberFormatException ignored) {}
        try { return Double.parseDouble(value); } catch (NumberFormatException ignored) {}
        return value;
    }

    /** Is a value "truthy"? Null, false, 0, and empty string are falsy. */
    public static boolean isTruthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.doubleValue() != 0.0;
        if (value instanceof String s) return !s.isEmpty() && !"false".equals(s);
        return true;
    }
}
