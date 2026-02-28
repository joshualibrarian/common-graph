package dev.everydaythings.graph.ui.scene.surface.bool;

import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.Transition;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;

/**
 * Toggle switch surface - visual representation of boolean state.
 *
 * <p>Renders as a pill-shaped track with a circular thumb that moves
 * left (off) or right (on). Uses declarative structure via @Surface annotations.
 *
 * <h2>Visual Structure</h2>
 * <pre>
 * OFF: [O    ]  "Label"
 * ON:  [    O]  "Label"
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Simple toggle
 * ToggleSurface toggle = ToggleSurface.of(true);
 *
 * // With label
 * ToggleSurface toggle = ToggleSurface.of(false)
 *     .label("Dark Mode")
 *     .editable(true);
 *
 * // Render
 * toggle.render(renderer);
 * }</pre>
 *
 * <h2>Style Classes</h2>
 * <ul>
 *   <li>{@code toggle} - The outer container</li>
 *   <li>{@code toggle-track} - The pill-shaped track</li>
 *   <li>{@code toggle-thumb} - The circular thumb</li>
 *   <li>{@code on} - Added when value is true</li>
 *   <li>{@code off} - Added when value is false</li>
 *   <li>{@code disabled} - Added when not editable</li>
 * </ul>
 */
@Scene.Container(direction = Scene.Direction.HORIZONTAL, style = "toggle", gap = "0.5em")
@Scene.On(event = "click", action = "toggle", when = "$editable")
@Scene.State(style = {"disabled"}, when = "!$editable")
public class ToggleSurface extends SurfaceSchema<Boolean> {

    /**
     * The toggle track - pill-shaped background.
     */
    @Scene.Container(
        direction = Scene.Direction.HORIZONTAL,
        style = {"toggle-track"},
        size = "2em",
        padding = "0.125em"
    )
    @Scene.Shape(type = "rectangle", cornerRadius = "pill")
    @Scene.State(style = {"on"}, when = "value")
    @Scene.State(style = {"off"}, when = "!value")
    static class Track {

        /**
         * The thumb - circular indicator that moves left/right.
         */
        @Scene.Container(style = {"toggle-thumb"}, size = "1em")
        @Scene.Shape(type = "circle")
        @Transition(property = "x", duration = 0.2, easing = "ease-out")
        static class Thumb {}
    }

    /**
     * Optional label text.
     */
    @Scene.Text(bind = "$label", style = "toggle-label")
    @Scene.If("$label")
    static class Label {}

    // ==================== Factory Methods ====================

    /**
     * Create a toggle with the given state.
     */
    public static ToggleSurface of(boolean on) {
        return new ToggleSurface().value(on);
    }

    /**
     * Create a toggle with state and label.
     */
    public static ToggleSurface of(boolean on, String label) {
        return new ToggleSurface().value(on).label(label);
    }

    /**
     * Create from a BooleanModel (for backward compatibility).
     */
    public static ToggleSurface of(BooleanModel model) {
        ToggleSurface s = new ToggleSurface();
        s.value(model.value());
        s.label(model.label());
        s.editable(model.enabled());
        if (model.id() != null) {
            s.id(model.id());
        }
        return s;
    }

    // ==================== Convenience Methods ====================

    /**
     * Check if the toggle is on.
     */
    public boolean on() {
        return Boolean.TRUE.equals(value());
    }

    /**
     * Set the toggle state.
     */
    public ToggleSurface on(boolean on) {
        return value(on);
    }

    /**
     * Toggle the current state.
     */
    public ToggleSurface toggle() {
        return value(!on());
    }
}
