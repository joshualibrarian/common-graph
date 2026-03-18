package dev.everydaythings.graph.item;

import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.ItemID;
import lombok.Getter;
import lombok.NonNull;

import java.lang.reflect.Field;

/**
 * Specification for a field annotated with @Item.Frame.
 *
 * <p>Endorsed frames (endorsed=true) are stored in the manifest and hydrated
 * as components. Unendorsed frames (endorsed=false) are stored as FrameRecord
 * envelopes and indexed for cross-item queries.
 */
@Getter
public class FrameFieldSpec {

    /** The annotated field. */
    @NonNull private final Field field;

    /** The frame's semantic key — the primary address. */
    @NonNull private final FrameKey frameKey;

    /** The frame's type ID (from field type or derived). */
    @NonNull private final ItemID type;

    /** Mount path relative to item root (empty if not mounted). */
    private final String path;

    /** Whether to store as snapshot content. */
    private final boolean snapshot;

    /** Whether to store as stream content. */
    private final boolean stream;

    /** Whether this is a local-only frame (no sync). */
    private final boolean localOnly;

    /** Whether this frame contributes to version identity. */
    private final boolean identity;

    /** Whether this is an endorsed frame (in manifest). */
    private final boolean endorsed;

    public FrameFieldSpec(
            @NonNull Field field,
            @NonNull FrameKey frameKey,
            @NonNull ItemID type,
            String path,
            boolean snapshot,
            boolean stream,
            boolean localOnly,
            boolean identity,
            boolean endorsed) {
        this.field = field;
        this.frameKey = frameKey;
        this.type = type;
        this.path = path != null ? path : "";
        this.snapshot = snapshot;
        this.stream = stream;
        this.localOnly = localOnly;
        this.identity = identity;
        this.endorsed = endorsed;
    }

    /** Check if this frame has a mount path. */
    public boolean hasMountPath() {
        return !path.isEmpty();
    }

    /** Whether this frame has a semantic (non-literal) key. */
    public boolean isSemantic() {
        return frameKey.isSemantic();
    }

    /**
     * Get the predicate ItemID (head of the FrameKey).
     * Only meaningful for semantic keys.
     */
    public ItemID predicate() {
        FrameKey.FrameToken head = frameKey.head();
        if (head instanceof FrameKey.Sememe sememe) {
            return sememe.id();
        }
        // Literal keys don't have a predicate ItemID — derive one
        return ItemID.fromString(((FrameKey.Literal) head).value());
    }

    /**
     * The canonical string form of this frame's key.
     *
     * <p>For literal keys, this is the literal value (e.g., "vault").
     * For semantic keys, a deterministic slash-separated representation.
     */
    public String canonicalKeyString() {
        return frameKey.toCanonicalString();
    }

    /**
     * Human-readable display name for this frame's predicate.
     *
     * <p>Extracts the last path segment from the original annotation key string.
     * For example, {@code "cg.rel:hypernym"} → {@code "hypernym"}.
     */
    public String predicateDisplayName() {
        Item.Frame ann = field.getAnnotation(Item.Frame.class);
        if (ann != null && ann.key().length > 0) {
            String firstKey = ann.key()[0];
            int slash = firstKey.lastIndexOf('/');
            if (slash >= 0) return firstKey.substring(slash + 1);
            int colon = firstKey.lastIndexOf(':');
            if (colon >= 0) return firstKey.substring(colon + 1);
            return firstKey;
        }
        return field.getName();
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

    /** Check if the field type has an @Implements annotation. */
    public boolean isAnnotatedType() {
        return field.getType().isAnnotationPresent(Implements.class);
    }

    /** Check if the field type is iterable (can hold multiple values). */
    public boolean isIterable() {
        return Iterable.class.isAssignableFrom(field.getType());
    }

    /** Get the field's declared type. */
    public Class<?> fieldType() {
        return field.getType();
    }

}
