package dev.everydaythings.graph.editing;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.CanonicalSchema;
import dev.everydaythings.graph.CanonicalSchema.FieldSchema;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.surface.SurfaceRenderer;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Procedural surface that renders a form for editing a {@link Canonical} object.
 *
 * <p>Each {@code @Canon} field is rendered as a labeled row with a type-appropriate
 * widget. Currently supported:
 *
 * <table>
 *   <tr><th>Field type</th><th>Widget</th><th>Editable?</th></tr>
 *   <tr><td>boolean</td><td>Toggle track+thumb</td><td>Yes</td></tr>
 *   <tr><td>enum</td><td>Option list</td><td>Yes</td></tr>
 *   <tr><td>String</td><td>Text display</td><td>Display-only</td></tr>
 *   <tr><td>int/long/etc.</td><td>Text display</td><td>Display-only</td></tr>
 *   <tr><td>Canonical</td><td>Type name</td><td>Display-only</td></tr>
 *   <tr><td>List</td><td>Item count</td><td>Display-only</td></tr>
 *   <tr><td>Other</td><td>toString()</td><td>Display-only</td></tr>
 * </table>
 */
public class CanonicalEditorSurface extends SurfaceSchema<Void> {

    private final EditModel model;

    public CanonicalEditorSurface(EditModel model) {
        this.model = model;
    }

    @Override
    public void render(SurfaceRenderer out) {
        emitCommonProperties(out);

        // Outer vertical container for all field rows
        out.gap("0.5em");
        out.beginBox(Scene.Direction.VERTICAL, List.of("editor"));

        for (FieldSchema fs : model.schema().fields()) {
            renderField(out, fs);
        }

        out.endBox();
    }

    private void renderField(SurfaceRenderer out, FieldSchema fs) {
        Object value = fs.getValue(model.target());

        if (fs.isEnum()) {
            // Enum fields use vertical layout: label above options
            renderEnumField(out, fs, value);
        } else {
            // All other fields use horizontal layout: [label] [widget]
            out.gap("0.5em");
            out.beginBox(Scene.Direction.HORIZONTAL, List.of("editor-field"));

            // Label
            out.text(fs.displayName(), List.of("editor-label"));

            // Type-appropriate widget
            if (fs.isBoolean()) {
                renderToggle(out, fs, value);
            } else if (fs.isString()) {
                renderText(out, value);
            } else if (fs.isNumeric()) {
                renderText(out, value);
            } else if (fs.isCanonical()) {
                renderCanonicalRef(out, fs);
            } else if (fs.isCollection()) {
                renderCollection(out, value);
            } else {
                renderFallback(out, value);
            }

            out.endBox();
        }
    }

    /**
     * Render a toggle for a boolean field.
     *
     * <p>Emits a clickable toggle indicator as plain text within the field row.
     * GUI renderers can style the toggle-track class; text renderers see a clear on/off indicator.
     */
    private void renderToggle(SurfaceRenderer out, FieldSchema fs, Object value) {
        boolean on = value instanceof Boolean b && b;

        out.event("click", "toggle:" + fs.name(), "");
        out.text(on ? "ON" : "OFF", List.of("toggle-track", on ? "on" : "off"));
    }

    /**
     * Render an enum field as a vertical block: label + option list.
     *
     * <p>Each enum constant is a clickable row. The currently selected value
     * gets an additional "selected" style.
     */
    private void renderEnumField(SurfaceRenderer out, FieldSchema fs, Object value) {
        Object[] constants = fs.enumConstants();
        if (constants == null) return;

        out.gap("0.25em");
        out.beginBox(Scene.Direction.VERTICAL, List.of("editor-field", "editor-enum"));

        // Label
        out.text(fs.displayName(), List.of("editor-label"));

        String currentName = value instanceof Enum<?> e ? e.name() : "";

        for (Object c : constants) {
            String name = ((Enum<?>) c).name();
            List<String> optStyles = new ArrayList<>();
            optStyles.add("editor-option");
            if (name.equals(currentName)) {
                optStyles.add("selected");
            }

            out.event("click", "select:" + fs.name(), name);
            out.beginBox(Scene.Direction.HORIZONTAL, optStyles);
            out.text(name, List.of("editor-option-label"));
            out.endBox();
        }

        out.endBox();
    }

    private void renderText(SurfaceRenderer out, Object value) {
        String display = value != null ? value.toString() : "";
        out.text(display, List.of("editor-value"));
    }

    private void renderCanonicalRef(SurfaceRenderer out, FieldSchema fs) {
        out.text(fs.type().getSimpleName(), List.of("editor-value", "muted"));
    }

    private void renderCollection(SurfaceRenderer out, Object value) {
        int size = 0;
        if (value instanceof Collection<?> c) {
            size = c.size();
        } else if (value != null && value.getClass().isArray()) {
            size = Array.getLength(value);
        }
        out.text("[" + size + " items]", List.of("editor-value", "muted"));
    }

    private void renderFallback(SurfaceRenderer out, Object value) {
        String display = value != null ? value.toString() : "null";
        out.text(display, List.of("editor-value", "muted"));
    }
}
