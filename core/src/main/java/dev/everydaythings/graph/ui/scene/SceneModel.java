package dev.everydaythings.graph.ui.scene;

import dev.everydaythings.graph.ui.input.KeyChord;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;

/**
 * Base class for stateful surface models.
 *
 * <p>SceneModel holds view state (selection, expansion, scroll position, etc.)
 * and produces Surface snapshots for rendering. Events from the rendered Surface
 * flow back to update the model, triggering re-renders.
 *
 * <h2>Pattern</h2>
 * <pre>
 * Model (state) → Surface (snapshot) → Render → Events → Model
 *       ↑                                          │
 *       └──────────── changed() ←──────────────────┘
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Create model
 * TreeModel model = new TreeModel(rootNode);
 * model.onChange(this::render);
 *
 * // Render
 * void render() {
 *     TreeSurface surface = model.toSurface();
 *     surface.render(output);
 * }
 *
 * // Handle events from surface
 * void handleEvent(String action, String target) {
 *     if (model.handleEvent(action, target)) {
 *         // Model handled it, will trigger onChange
 *     }
 * }
 *
 * // Handle keyboard
 * void handleKey(KeyChord chord) {
 *     if (model.handleKey(chord)) {
 *         // Model consumed the key
 *         return;
 *     }
 *     // Otherwise handle normally
 * }
 * }</pre>
 *
 * @param <S> The Surface type this model produces
 */
public abstract class SceneModel<S extends SurfaceSchema> {

    /** Listener notified when state changes. */
    private Runnable onChanged;

    /** Whether to batch changes (defer notification). */
    private boolean batching = false;

    /** Whether changes occurred during batch. */
    private boolean batchedChanges = false;

    // ==================================================================================
    // Abstract Methods
    // ==================================================================================

    /**
     * Build a Surface snapshot of current state.
     *
     * <p>This should be a pure function of the model's state - calling it
     * multiple times with the same state should produce equivalent surfaces.
     *
     * @return Surface representing current state
     */
    public abstract S toSurface();

    /**
     * Handle an event from the rendered surface.
     *
     * <p>Events are typically triggered by user interaction (click, etc.)
     * on elements rendered from this model's surface.
     *
     * @param action The action verb (e.g., "select", "toggle", "activate")
     * @param target The target identifier (e.g., node ID, or empty for self)
     * @return true if the event was handled and state changed
     */
    public abstract boolean handleEvent(String action, String target);

    // ==================================================================================
    // Keyboard Support (optional override)
    // ==================================================================================

    /**
     * Handle a keyboard event.
     *
     * <p>Override this to add keyboard navigation support. Return true if
     * the key was consumed (prevents further handling).
     *
     * @param chord The key chord pressed
     * @return true if handled, false to pass through
     */
    public boolean handleKey(KeyChord chord) {
        return false;
    }

    // ==================================================================================
    // Change Notification
    // ==================================================================================

    /**
     * Set the change listener.
     *
     * <p>Called whenever model state changes (unless batching).
     *
     * @param listener Runnable to call on change, typically triggers re-render
     */
    public void onChange(Runnable listener) {
        this.onChanged = listener;
    }

    /**
     * Notify that state has changed.
     *
     * <p>Call this from subclasses after modifying state. If batching,
     * notification is deferred until the batch ends.
     */
    protected void changed() {
        if (batching) {
            batchedChanges = true;
        } else if (onChanged != null) {
            onChanged.run();
        }
    }

    /**
     * Begin a batch of changes.
     *
     * <p>Changes within a batch only trigger a single notification when
     * {@link #endBatch()} is called. Useful for multiple state updates
     * that should result in one re-render.
     */
    public void beginBatch() {
        batching = true;
        batchedChanges = false;
    }

    /**
     * End a batch of changes.
     *
     * <p>If any changes occurred during the batch, notifies the listener.
     */
    public void endBatch() {
        batching = false;
        if (batchedChanges && onChanged != null) {
            batchedChanges = false;
            onChanged.run();
        }
    }

    /**
     * Execute a batch of changes.
     *
     * <p>Convenience method that wraps begin/end batch around a runnable.
     *
     * @param changes The changes to make
     */
    public void batch(Runnable changes) {
        beginBatch();
        try {
            changes.run();
        } finally {
            endBatch();
        }
    }
}
