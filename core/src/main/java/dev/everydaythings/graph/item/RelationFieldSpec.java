package dev.everydaythings.graph.item;

import dev.everydaythings.graph.item.id.ItemID;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.lang.reflect.Field;

/**
 * Specification for a field annotated with @Item.RelationField.
 *
 * <p>This captures all metadata from the annotation plus the field
 * reference for value access during commit.
 */
@Getter
@RequiredArgsConstructor
public class RelationFieldSpec {

    /** The annotated field. */
    @NonNull private final Field field;

    /** The relation predicate ID. */
    @NonNull private final ItemID predicate;

    /** Whether to include in manifest's relation list. */
    private final boolean canonical;

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
     * Check if the field type is iterable (can hold multiple relations).
     */
    public boolean isIterable() {
        return Iterable.class.isAssignableFrom(field.getType());
    }
}
