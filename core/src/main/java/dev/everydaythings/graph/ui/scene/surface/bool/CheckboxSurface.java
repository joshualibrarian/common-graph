package dev.everydaythings.graph.ui.scene.surface.bool;

import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;

/**
 * Checkbox surface - visual representation of boolean state.
 *
 * <p>Renders as a traditional checkbox (square box with checkmark when checked)
 * with optional label. Uses declarative structure via @Surface annotations.
 *
 * <h2>Visual Structure</h2>
 * <pre>
 * UNCHECKED: [ ]  "Label"
 * CHECKED:   [✓]  "Label"
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Simple checkbox
 * CheckboxSurface checkbox = CheckboxSurface.of(true);
 *
 * // With label
 * CheckboxSurface checkbox = CheckboxSurface.of(false)
 *     .label("Accept terms")
 *     .editable(true);
 *
 * // Render
 * checkbox.render(renderer);
 * }</pre>
 *
 * <h2>Style Classes</h2>
 * <ul>
 *   <li>{@code checkbox} - The outer container</li>
 *   <li>{@code checkbox-box} - The square box</li>
 *   <li>{@code checkbox-check} - The checkmark icon</li>
 *   <li>{@code checked} - Added when value is true</li>
 *   <li>{@code unchecked} - Added when value is false</li>
 *   <li>{@code disabled} - Added when not editable</li>
 * </ul>
 */
@Scene.Container(direction = Scene.Direction.HORIZONTAL, style = "checkbox", gap = "0.5em")
@Scene.On(event = "click", action = "toggle", when = "$editable")
@Scene.State(style = {"disabled"}, when = "!$editable")
public class CheckboxSurface extends SurfaceSchema<Boolean> {

    /**
     * The checkbox box - square with rounded corners.
     */
    @Scene.Container(style = {"checkbox-box"}, size = "1.2em")
    @Scene.Shape(type = "rectangle", cornerRadius = "small")
    @Scene.State(style = {"checked"}, when = "value")
    @Scene.State(style = {"unchecked"}, when = "!value")
    static class Box {

        /**
         * The checkmark - visible only when checked.
         */
        @Scene.Image(alt = "✓", style = "checkbox-check", size = "1em")
        @Scene.If("value")
        static class Check {}
    }

    /**
     * Optional label text.
     */
    @Scene.Text(bind = "$label", style = "checkbox-label")
    @Scene.If("$label")
    static class Label {}

    // ==================== Factory Methods ====================

    /**
     * Create a checkbox with the given state.
     */
    public static CheckboxSurface of(boolean checked) {
        return new CheckboxSurface().value(checked);
    }

    /**
     * Create a checkbox with state and label.
     */
    public static CheckboxSurface of(boolean checked, String label) {
        return new CheckboxSurface().value(checked).label(label);
    }

    /**
     * Create from a BooleanModel (for backward compatibility).
     */
    public static CheckboxSurface of(BooleanModel model) {
        CheckboxSurface s = new CheckboxSurface();
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
     * Check if the checkbox is checked.
     */
    public boolean checked() {
        return Boolean.TRUE.equals(value());
    }

    /**
     * Set the checked state.
     */
    public CheckboxSurface checked(boolean checked) {
        return value(checked);
    }

    /**
     * Toggle the current state.
     */
    public CheckboxSurface toggle() {
        return value(!checked());
    }
}
