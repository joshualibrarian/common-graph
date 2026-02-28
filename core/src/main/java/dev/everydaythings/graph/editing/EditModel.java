package dev.everydaythings.graph.editing;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.CanonicalSchema;
import dev.everydaythings.graph.CanonicalSchema.FieldSchema;
import dev.everydaythings.graph.ui.scene.SceneModel;
import dev.everydaythings.graph.ui.scene.surface.SurfaceSchema;

import java.util.List;

/**
 * Stateful editing model for any {@link Canonical} object.
 *
 * <p>EditModel wraps a mutable Canonical instance and exposes its {@code @Canon}
 * fields for reading and writing. Events from the rendered surface flow back
 * through {@link #handleEvent(String, String)} to mutate the target.
 *
 * <h2>Event Convention</h2>
 * <p>Actions are prefixed with the operation type and suffixed with the field name,
 * separated by a colon:
 * <ul>
 *   <li>{@code "toggle:darkMode"} — flip a boolean field</li>
 *   <li>{@code "select:layout"} — set an enum field (target = constant name)</li>
 *   <li>{@code "set:title"} — set a string field (target = new value, future)</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * MyConfig config = new MyConfig();
 * EditModel model = new EditModel(config);
 * model.onChange(this::render);
 *
 * // Read
 * Object val = model.get("darkMode");
 *
 * // Write programmatically
 * model.set("darkMode", true);
 *
 * // Or via events (from rendered surface)
 * model.handleEvent("toggle:darkMode", "");
 * model.handleEvent("select:layout", "HORIZONTAL");
 * }</pre>
 */
public class EditModel extends SceneModel<SurfaceSchema> {

    private final Canonical target;
    private final CanonicalSchema schema;
    private final List<FieldSchema> fields;

    public EditModel(Canonical target) {
        this.target = target;
        this.schema = CanonicalSchema.of(target.getClass());
        this.fields = schema.fields();
    }

    // ==================== Accessors ====================

    /**
     * The mutable Canonical being edited.
     */
    public Canonical target() {
        return target;
    }

    /**
     * The schema describing the target's fields.
     */
    public CanonicalSchema schema() {
        return schema;
    }

    /**
     * Get the current value of a field by name.
     *
     * @param fieldName the @Canon field name
     * @return the field's current value, or null if not found
     */
    public Object get(String fieldName) {
        FieldSchema fs = findField(fieldName);
        return fs != null ? fs.getValue(target) : null;
    }

    /**
     * Set the value of a field by name.
     *
     * @param fieldName the @Canon field name
     * @param value     the new value
     */
    public void set(String fieldName, Object value) {
        FieldSchema fs = findField(fieldName);
        if (fs != null) {
            fs.setValue(target, value);
            changed();
        }
    }

    // ==================== Surface Generation ====================

    @Override
    public SurfaceSchema toSurface() {
        return new CanonicalEditorSurface(this);
    }

    // ==================== Event Handling ====================

    @Override
    public boolean handleEvent(String action, String target) {
        int colon = action.indexOf(':');
        if (colon < 0) return false;

        String op = action.substring(0, colon);
        String fieldName = action.substring(colon + 1);

        FieldSchema fs = findField(fieldName);
        if (fs == null) return false;

        switch (op) {
            case "toggle" -> {
                if (fs.isBoolean()) {
                    Object current = fs.getValue(this.target);
                    boolean val = current instanceof Boolean b ? b : false;
                    fs.setValue(this.target, !val);
                    changed();
                    return true;
                }
            }
            case "select" -> {
                if (fs.isEnum() && target != null) {
                    Object[] constants = fs.enumConstants();
                    if (constants != null) {
                        for (Object c : constants) {
                            if (((Enum<?>) c).name().equals(target)) {
                                fs.setValue(this.target, c);
                                changed();
                                return true;
                            }
                        }
                    }
                }
            }
            case "set" -> {
                if (fs.isString() && target != null) {
                    fs.setValue(this.target, target);
                    changed();
                    return true;
                }
            }
        }
        return false;
    }

    // ==================== Internals ====================

    private FieldSchema findField(String name) {
        for (FieldSchema fs : fields) {
            if (fs.name().equals(name)) return fs;
        }
        return null;
    }
}
