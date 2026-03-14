package dev.everydaythings.graph.ui.input;

import dev.everydaythings.graph.parse.InputSnapshot;
import dev.everydaythings.graph.runtime.Eval;

/**
 * Interface for input renderers.
 *
 * <p>Renderers are "dumb" - they don't implement input logic, they just:
 * <ol>
 *   <li>Render the current {@link InputSnapshot}</li>
 *   <li>Convert physical key events to {@link InputAction}</li>
 * </ol>
 *
 * <p>All the smart logic lives in {@link dev.everydaythings.graph.parse.InputController}.
 *
 * <p>Implementations:
 * <ul>
 *   <li>GUI: Skia-based graphical rendering</li>
 *   <li>TUI: Lanterna-based terminal rendering</li>
 *   <li>CLI: Simple text with ANSI codes</li>
 *   <li>Eval: Single command with inline completions</li>
 * </ul>
 */
public interface InputRenderer {

    /**
     * Render the current input state.
     *
     * <p>Called by InputController whenever state changes.
     *
     * @param snapshot The current input state to render
     */
    void render(InputSnapshot snapshot);

    /**
     * Focus the input (make it ready to receive input).
     */
    default void focus() {}

    /**
     * Blur the input (remove focus).
     */
    default void blur() {}

    /**
     * Check if this renderer supports completions display.
     */
    default boolean supportsCompletions() {
        return true;
    }

    /**
     * Get the maximum number of completions to show.
     */
    default int maxCompletions() {
        return 10;
    }

    /**
     * Called when input is complete (user pressed Enter successfully).
     *
     * <p>Renderers can use this to update display, clear fields, etc.
     *
     * @param result The result of the completed input
     */
    default void onComplete(Eval.EvalResult result) {}

    /**
     * Called when the renderer should be destroyed.
     */
    default void dispose() {}
}
