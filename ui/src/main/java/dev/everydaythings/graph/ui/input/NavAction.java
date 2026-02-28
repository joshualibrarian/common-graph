package dev.everydaythings.graph.ui.input;

/**
 * Logical navigation actions for keyboard-driven UI.
 *
 * <p>These are UI-agnostic actions that result from key bindings.
 * The UI layer translates physical key events to KeyChords,
 * the binding layer resolves KeyChords to NavActions,
 * and the UI layer executes the actions.
 *
 * <p>Actions are organized into two layers:
 * <ul>
 *   <li><b>Intra-item</b>: Navigation within a single Item's view
 *       (tree, panel, prompt). Default: Alt+Arrow</li>
 *   <li><b>Inter-item</b>: Navigation between Items in a workspace.
 *       Default: Alt+Shift+Arrow</li>
 * </ul>
 */
public enum NavAction {

    // ==========================================================================
    // INTRA-ITEM: Navigation within an Item's view (Alt+Arrow by default)
    // ==========================================================================

    // --- Tree navigation ---

    /** Move to previous visible item in tree. */
    TREE_UP,

    /** Move to next visible item in tree. */
    TREE_DOWN,

    /** Collapse current tree item. */
    TREE_COLLAPSE,

    /** Expand current tree item. */
    TREE_EXPAND,

    // --- Focus transitions ---

    /** Enter the panel (from prompt+tree state). */
    ENTER_PANEL,

    /** Exit panel and return to prompt+tree state. */
    EXIT_TO_PROMPT,

    // --- Prompt text editing ---

    /** Move cursor left in prompt. */
    CURSOR_LEFT,

    /** Move cursor right in prompt. */
    CURSOR_RIGHT,

    /** Move cursor to start of prompt. */
    CURSOR_HOME,

    /** Move cursor to end of prompt. */
    CURSOR_END,

    /** Previous command in history. */
    HISTORY_UP,

    /** Next command in history. */
    HISTORY_DOWN,

    /** Delete character before cursor. */
    DELETE_BACK,

    /** Delete character after cursor. */
    DELETE_FORWARD,

    /** Delete word before cursor. */
    DELETE_WORD,

    // --- Execution ---

    /** Execute the current prompt command. */
    EXECUTE,

    // ==========================================================================
    // VIEW MODE: Collection display mode switching (Cmd/Ctrl+1/2/3/4)
    // ==========================================================================

    /** Switch to table view mode. */
    VIEW_TABLE,

    /** Switch to tiles/grid view mode. */
    VIEW_TILES,

    /** Switch to list view mode. */
    VIEW_LIST,

    /** Switch to gallery view mode. */
    VIEW_GALLERY,

    /** Cycle to next view mode. */
    VIEW_CYCLE,

    // ==========================================================================
    // INTER-ITEM: Navigation between Items in workspace (Alt+Shift+Arrow default)
    // ==========================================================================

    /** Move to previous Item in workspace. */
    ITEM_PREV,

    /** Move to next Item in workspace. */
    ITEM_NEXT,

    /** Close/collapse current Item in workspace. */
    ITEM_CLOSE,

    /** Open/expand Item or show more detail. */
    ITEM_OPEN,

    /** Move current Item earlier in workspace order. */
    ITEM_MOVE_UP,

    /** Move current Item later in workspace order. */
    ITEM_MOVE_DOWN
}
