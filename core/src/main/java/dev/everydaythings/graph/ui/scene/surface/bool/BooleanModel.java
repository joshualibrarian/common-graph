package dev.everydaythings.graph.ui.scene.surface.bool;

import dev.everydaythings.graph.ui.input.KeyChord;
import dev.everydaythings.graph.ui.input.SpecialKey;
import dev.everydaythings.graph.ui.scene.SceneModel;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;
import dev.everydaythings.graph.ui.scene.surface.primitive.ImageSurface;

/**
 * Model for boolean (on/off) state.
 *
 * <p>BooleanModel holds a single boolean value and can be rendered as
 * different surface types depending on the desired visual style:
 * <ul>
 *   <li>{@link CheckboxSurface} - Traditional checkbox (☐/☑)</li>
 *   <li>{@link ToggleSurface} - Toggle switch (⚪/🔵)</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Create model
 * BooleanModel model = BooleanModel.of("Enable notifications", false);
 * model.onChange(this::render);
 *
 * // Render as checkbox
 * CheckboxSurface surface = model.toCheckbox();
 *
 * // Or render as toggle
 * ToggleSurface surface = model.toToggle();
 *
 * // Handle events
 * model.handleEvent("toggle", "");
 * }</pre>
 */
public class BooleanModel extends SceneModel<SurfaceSchema> {

    /** The current value. */
    private boolean value;

    /** Optional label displayed next to the control. */
    private String label;

    /** Optional icon displayed with the label. */
    private ImageSurface icon;

    /** Optional ID for the control (for event targeting). */
    private String id;

    /** Whether the control is enabled. */
    private boolean enabled = true;

    // ==================== Construction ====================

    public BooleanModel() {}

    public BooleanModel(boolean value) {
        this.value = value;
    }

    public BooleanModel(String label, boolean value) {
        this.label = label;
        this.value = value;
    }

    public static BooleanModel of(boolean value) {
        return new BooleanModel(value);
    }

    public static BooleanModel of(String label, boolean value) {
        return new BooleanModel(label, value);
    }

    // ==================== Fluent Configuration ====================

    public BooleanModel label(String label) {
        this.label = label;
        return this;
    }

    public BooleanModel icon(ImageSurface icon) {
        this.icon = icon;
        return this;
    }

    public BooleanModel icon(String glyph) {
        this.icon = ImageSurface.of(glyph);
        return this;
    }

    public BooleanModel id(String id) {
        this.id = id;
        return this;
    }

    public BooleanModel enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    // ==================== State Access ====================

    public boolean value() {
        return value;
    }

    public boolean isOn() {
        return value;
    }

    public boolean isOff() {
        return !value;
    }

    public String label() {
        return label;
    }

    public ImageSurface icon() {
        return icon;
    }

    public String id() {
        return id;
    }

    public boolean enabled() {
        return enabled;
    }

    // ==================== State Modification ====================

    /**
     * Toggle the value.
     */
    public void toggle() {
        if (enabled) {
            this.value = !this.value;
            changed();
        }
    }

    /**
     * Set the value.
     */
    public void set(boolean value) {
        if (enabled && this.value != value) {
            this.value = value;
            changed();
        }
    }

    /**
     * Set to on/true.
     */
    public void on() {
        set(true);
    }

    /**
     * Set to off/false.
     */
    public void off() {
        set(false);
    }

    // ==================== Surface Generation ====================

    /**
     * Render as a checkbox.
     */
    public CheckboxSurface toCheckbox() {
        return CheckboxSurface.of(this);
    }

    /**
     * Render as a toggle switch.
     */
    public ToggleSurface toToggle() {
        return ToggleSurface.of(this);
    }

    @Override
    public SurfaceSchema toSurface() {
        // Default to checkbox
        return toCheckbox();
    }

    // ==================== Event Handling ====================

    @Override
    public boolean handleEvent(String action, String target) {
        if ("toggle".equals(action)) {
            toggle();
            return true;
        }
        if ("on".equals(action) || "check".equals(action)) {
            on();
            return true;
        }
        if ("off".equals(action) || "uncheck".equals(action)) {
            off();
            return true;
        }
        return false;
    }

    @Override
    public boolean handleKey(KeyChord chord) {
        if (chord.isKey(SpecialKey.ENTER) || chord.isChar(' ')) {
            toggle();
            return true;
        }
        return false;
    }
}
