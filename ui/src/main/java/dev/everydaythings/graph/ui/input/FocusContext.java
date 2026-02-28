package dev.everydaythings.graph.ui.input;

/**
 * The current focus context within an Item's view.
 *
 * <p>This affects how key chords are interpreted:
 * <ul>
 *   <li>{@link #PROMPT_TREE} - The "home" state where you're simultaneously
 *       in the prompt and navigating the tree. Plain arrows do text editing
 *       and history, Ctrl+arrows navigate the tree.</li>
 *   <li>{@link #PANEL} - Focus is in the panel content (forms, lists, etc.).
 *       Keys are passed through to the panel, except Escape which exits.</li>
 * </ul>
 */
public enum FocusContext {

    /**
     * Home state: prompt and tree navigation simultaneously.
     *
     * <p>In this state:
     * <ul>
     *   <li>Typing goes to the prompt</li>
     *   <li>Plain Up/Down = command history</li>
     *   <li>Plain Left/Right = cursor movement</li>
     *   <li>Ctrl+arrows = tree navigation</li>
     *   <li>Enter = execute (if text) or enter panel (if empty)</li>
     * </ul>
     */
    PROMPT_TREE,

    /**
     * Panel focus: interacting with panel content.
     *
     * <p>In this state:
     * <ul>
     *   <li>Keys are passed to the panel (Tab, arrows, typing, etc.)</li>
     *   <li>Escape returns to PROMPT_TREE</li>
     *   <li>Ctrl+arrows still navigate the tree</li>
     * </ul>
     */
    PANEL
}
