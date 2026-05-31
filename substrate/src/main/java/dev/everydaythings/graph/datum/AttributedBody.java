package dev.everydaythings.graph.datum;

import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.HashID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Runtime aggregate of a {@link Body} with zero or more {@link Record}s attesting it.
 *
 * <p>An AttributedBody is a body together with its attributions (the records that
 * sign it). Two concrete subtypes:
 * <ul>
 *   <li>{@link Frame} — the general case. Any body + records.</li>
 *   <li>{@link Manifest} — body has an ITEM_ID binding and represents a version
 *       of an item; adds archetypal-flavored convenience methods.</li>
 * </ul>
 *
 * <p>Both subclasses inherit the body+records storage and the common accessors
 * defined here. Subclass-specific API lives on the subclasses themselves.
 *
 * <p>An AttributedBody is never serialized as a single object. Body and Records
 * are content-addressed and stored independently; AttributedBody is just the
 * in-memory grouping when you fetch them together.
 */
@Getter
@RequiredArgsConstructor
public abstract class AttributedBody {

    private final Body body;
    private final List<Record> records;

    /** The head reference of the body (sememe ItemRef). */
    public HashID head() {
        return body.head();
    }

    /** The body's bindings. */
    public List<Binding> bindings() {
        return body.bindings();
    }

    // bodyCID() deleted: ContentRef is encoder-specific and belongs at the
    // Library/codec boundary, not on a pure in-memory aggregate. Use
    // {@code ContentRef.of(encoder.encode(body))} at the call site, or look
    // up via the Library's DatumRef→ContentRef bridge.

    /** Find the first body binding whose (role, qualifiers) matches the given key. */
    public Optional<Binding> binding(CompoundKey key) {
        return body.binding(key);
    }

    /** Find all body bindings whose (role, qualifiers) match the given key. */
    public List<Binding> bindings(CompoundKey key) {
        return body.bindings(key);
    }

    /**
     * Downcast to {@link Manifest} if this AttributedBody is one.
     */
    public Optional<Manifest> asManifest() {
        return this instanceof Manifest m ? Optional.of(m) : Optional.empty();
    }

    /**
     * Whether the body's head sememe's EXPECTS includes ITEM_ID — i.e., whether
     * this body is structurally archetypal (an item-version body).
     *
     * <p>This implementation checks for the structural signal directly: presence
     * of an ITEM_ID binding in the body. A body without an ITEM_ID binding is
     * propositional (a regular frame); a body with one is archetypal (a manifest).
     */
    public boolean isArchetypal() {
        return body.binding(CompoundKey.of(Manifest.ITEM_ID)).isPresent();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AttributedBody other)) return false;
        return Objects.equals(body, other.body) && Objects.equals(records, other.records);
    }

    @Override
    public int hashCode() {
        return Objects.hash(body, records);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "[body=" + body + ", " + records.size() + " records]";
    }
}
