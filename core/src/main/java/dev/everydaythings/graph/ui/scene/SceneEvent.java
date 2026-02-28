package dev.everydaythings.graph.ui.scene;

import dev.everydaythings.graph.Canonical;

/**
 * A declarative event handler for Surface elements.
 *
 * <p>SceneEvent binds an event type to an action and target.
 * This is CBOR-serializable for remote rendering.
 *
 * <p>Event types:
 * <ul>
 *   <li>{@code click} - single click (button 1)</li>
 *   <li>{@code doubleClick} - double click</li>
 *   <li>{@code rightClick} - right click (button 2/3)</li>
 *   <li>{@code scrollUp} - scroll wheel up</li>
 *   <li>{@code scrollDown} - scroll wheel down</li>
 *   <li>{@code drag} - drag started</li>
 *   <li>{@code drop} - drop received</li>
 *   <li>{@code hover} - mouse enter</li>
 *   <li>{@code focus} - keyboard focus</li>
 * </ul>
 *
 * <p>Action is a verb/sememe that will be dispatched to the target.
 *
 * <p>Target references:
 * <ul>
 *   <li>{@code ""} or {@code "self"} - this element's item</li>
 *   <li>{@code "parent"} - parent item</li>
 *   <li>{@code "iid:xxx"} - explicit item by IID</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * SceneEvent.of("click", "navigate", "self")
 * SceneEvent.of("doubleClick", "edit")
 * SceneEvent.click("delete", targetIid)
 * }</pre>
 */
public record SceneEvent(
        @Canon(order = 0) String on,
        @Canon(order = 1) String action,
        @Canon(order = 2) String target
) implements Canonical {

    /**
     * Create an event with explicit target.
     */
    public static SceneEvent of(String on, String action, String target) {
        return new SceneEvent(on, action, target != null ? target : "");
    }

    /**
     * Create an event targeting self.
     */
    public static SceneEvent of(String on, String action) {
        return new SceneEvent(on, action, "");
    }

    // ==================================================================================
    // Convenience Factories
    // ==================================================================================

    public static SceneEvent click(String action) {
        return of("click", action);
    }

    public static SceneEvent click(String action, String target) {
        return of("click", action, target);
    }

    public static SceneEvent doubleClick(String action) {
        return of("doubleClick", action);
    }

    public static SceneEvent doubleClick(String action, String target) {
        return of("doubleClick", action, target);
    }

    public static SceneEvent drag(String action) {
        return of("drag", action);
    }

    public static SceneEvent drag(String action, String target) {
        return of("drag", action, target);
    }

    public static SceneEvent drop(String action) {
        return of("drop", action);
    }

    public static SceneEvent hover(String action) {
        return of("hover", action);
    }

    public static SceneEvent rightClick(String action) {
        return of("rightClick", action);
    }

    public static SceneEvent rightClick(String action, String target) {
        return of("rightClick", action, target);
    }

    public static SceneEvent scrollUp(String action) {
        return of("scrollUp", action);
    }

    public static SceneEvent scrollUp(String action, String target) {
        return of("scrollUp", action, target);
    }

    public static SceneEvent scrollDown(String action) {
        return of("scrollDown", action);
    }

    public static SceneEvent scrollDown(String action, String target) {
        return of("scrollDown", action, target);
    }

    // ==================================================================================
    // Common Actions
    // ==================================================================================

    /**
     * Navigate to an item.
     */
    public static SceneEvent navigate() {
        return click("navigate");
    }

    /**
     * Navigate to a specific item.
     */
    public static SceneEvent navigate(String targetIid) {
        return click("navigate", targetIid);
    }

    /**
     * Toggle a style class.
     */
    public static SceneEvent toggleStyle(String styleName) {
        return click("toggle:" + styleName);
    }

    /**
     * Invoke an action on self.
     */
    public static SceneEvent invoke(String actionName) {
        return click(actionName);
    }

    /**
     * Invoke an action on a target.
     */
    public static SceneEvent invoke(String actionName, String target) {
        return click(actionName, target);
    }
}
