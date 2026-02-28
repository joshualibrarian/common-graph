package dev.everydaythings.graph.item.component;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.HandleID;
import dev.everydaythings.graph.item.id.ItemID;
import lombok.Getter;
import lombok.NonNull;

import java.lang.reflect.Field;

/**
 * Specification for a field annotated with @Item.ComponentField.
 *
 * <p>This captures all metadata from the annotation plus the field
 * reference for value access during commit and hydration.
 */
@Getter
public class ComponentFieldSpec {

    /** The annotated field. */
    @NonNull private final Field field;

    /** The component's handle ID. */
    @NonNull private final HandleID handle;

    /** The original string key for the handle (e.g., "library"). */
    @NonNull private final String handleKey;

    /** Human-facing alias (sememe token or literal). Empty if unset. */
    @NonNull private final String alias;

    /** The component's type ID (from field type or annotation). */
    @NonNull private final ItemID type;

    /** Mount path relative to item root (empty if not mounted). */
    private final String path;

    /** Whether to store as snapshot content. */
    private final boolean snapshot;

    /** Whether to store as stream content. */
    private final boolean stream;

    /** Whether this is a local-only component (no sync). */
    private final boolean localOnly;

    /** Whether this component contributes to version identity. */
    private final boolean identity;

    public ComponentFieldSpec(
            @NonNull Field field,
            @NonNull HandleID handle,
            @NonNull String handleKey,
            @NonNull String alias,
            @NonNull ItemID type,
            String path,
            boolean snapshot,
            boolean stream,
            boolean localOnly,
            boolean identity) {
        this.field = field;
        this.handle = handle;
        this.handleKey = handleKey;
        this.alias = alias;
        this.type = type;
        this.path = path != null ? path : "";
        this.snapshot = snapshot;
        this.stream = stream;
        this.localOnly = localOnly;
        this.identity = identity;
    }

    /**
     * Check if this component has a mount path.
     */
    public boolean hasMountPath() {
        return !path.isEmpty();
    }

    /**
     * Get the field value from an item instance.
     *
     * @param item The item to read from
     * @return The field value, or null if not accessible
     */
    public Object getValue(Item item) {
        try {
            field.setAccessible(true);
            return field.get(item);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    /**
     * Set the field value on an item instance.
     *
     * @param item The item to write to
     * @param value The value to set
     */
    public void setValue(Item item, Object value) {
        try {
            field.setAccessible(true);
            field.set(item, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to set field: " + field.getName(), e);
        }
    }

    /**
     * Check if the field type has a @Type annotation.
     */
    public boolean isAnnotatedType() {
        return field.getType().isAnnotationPresent(Type.class);
    }

    /**
     * Get the field's declared type.
     */
    public Class<?> fieldType() {
        return field.getType();
    }
}
