package dev.everydaythings.graph.item;

import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.ItemID;
import lombok.Getter;
import lombok.NonNull;

import java.lang.reflect.Field;

/**
 * Specification for a field annotated with @ItemFrame.
 *
 * <p>Each frame binds the owning item via the {@code selfRole} (default THEME)
 * and the field value via the {@code valueRole} (default NAME). These roles
 * determine how the item and value participate semantically in the frame.
 *
 * <p>Endorsed frames (endorsed=true) are stored in the manifest and hydrated
 * as components. Unendorsed frames (endorsed=false) are stored as FrameRecord
 * envelopes and indexed for cross-item queries.
 */
@Getter
public class FrameFieldSpec {

    /** The annotated field (null for synthesized specs like @Implements). */
    private final Field field;

    /** The frame's semantic key — the primary address. */
    @NonNull private final FrameKey frameKey;

    /** The frame's type ID (from field type or derived). */
    @NonNull private final ItemID type;

    /** The thematic role binding the owning item to this frame (default THEME). */
    @NonNull private final ItemID selfRole;

    /** The thematic role binding the field value to this frame (default NAME). */
    @NonNull private final ItemID valueRole;

    /** Mount path relative to item root (empty if not mounted). */
    private final String path;

    /** Whether to store as stream content. */
    private final boolean stream;

    /** Whether this is a local-only frame (no sync). */
    private final boolean localOnly;

    /** Whether this frame contributes to version identity. */
    private final boolean identity;

    /** Whether this is an endorsed frame (in manifest). */
    private final boolean endorsed;

    /** Whether this is a static field (seed data, not instance data). */
    private final boolean staticField;

    public FrameFieldSpec(
            Field field,
            @NonNull FrameKey frameKey,
            @NonNull ItemID type,
            @NonNull ItemID selfRole,
            @NonNull ItemID valueRole,
            String path,
            boolean stream,
            boolean localOnly,
            boolean identity,
            boolean endorsed) {
        this.field = field;
        this.frameKey = frameKey;
        this.type = type;
        this.selfRole = selfRole;
        this.valueRole = valueRole;
        this.path = path != null ? path : "";
        this.stream = stream;
        this.localOnly = localOnly;
        this.identity = identity;
        this.endorsed = endorsed;
        this.staticField = field == null || java.lang.reflect.Modifier.isStatic(field.getModifiers());
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
        if (field == null) return frameKey.toCanonicalString();
        ItemFrame ann = field.getAnnotation(ItemFrame.class);
        if (ann != null && !ann.predicate().isEmpty()) {
            String firstKey = ann.predicate();
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
        if (field == null) return null;
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
        if (field == null) return;
        try {
            field.setAccessible(true);
            field.set(item, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to set field: " + field.getName(), e);
        }
    }

    /** Whether this is a synthesized spec (no backing field). */
    public boolean isSynthesized() {
        return field == null;
    }

    /** Check if the field type has an @Implements annotation. */
    public boolean isAnnotatedType() {
        return field != null && field.getType().isAnnotationPresent(Implements.class);
    }

    /** Check if the field type is iterable (can hold multiple values). */
    public boolean isIterable() {
        return field != null && Iterable.class.isAssignableFrom(field.getType());
    }

    /** Get the field's declared type. */
    public Class<?> fieldType() {
        return field != null ? field.getType() : null;
    }

}
