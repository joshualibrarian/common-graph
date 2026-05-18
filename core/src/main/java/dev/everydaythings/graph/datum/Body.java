package dev.everydaythings.graph.datum;

import dev.everydaythings.graph.id.HashID;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.id.SchemaRef;
import dev.everydaythings.graph.id.TypeRef;
import java.util.List;
import java.util.Objects;

/**
 * A Datum that asserts content. Has no signature slot.
 *
 * <p>The head is a reference into the IID space — one of {@link ItemRef}
 * (literal), {@link TypeRef} (query: match instances of this kind), or
 * {@link SchemaRef} (schema: declare what instances of this kind look like).
 * {@link dev.everydaythings.graph.id.ContentRef} and
 * {@link dev.everydaythings.graph.id.DatumRef} are rejected — a Body's head
 * always points at a sememe, never at content or datum hashes.
 *
 * <p>Literal-headed bodies are the common case: propositional frames (head is
 * a Predicate), archetypal/manifest bodies (head is an Archetype), value
 * bodies (head is a Value archetype like Color or Quantity).  Query and
 * schema-headed bodies have the same wire shape — the head's reference
 * variant marks the operational mode.
 *
 * <p>CBOR encoding: 2-element array {@code [Tag-6(head), [bindings]]}.
 *
 * <p>Construction is permissive — bodies may carry any bindings, including ones
 * beyond what the head sememe's EXPECTS strictly declares. Validation against
 * EXPECTS is a separate concern done at signing/commit time, not at construction.
 */
public non-sealed class Body extends Datum {

    public Body(HashID head, List<? extends DatumNode> entries) {
        super(validateHead(head), entries);
    }

    /** Backwards-compat constructor; prefer {@link #Body(HashID, List)}. */
    public Body(ItemRef head, List<? extends DatumNode> entries) {
        super(head, entries);
    }

    /**
     * Validate that {@code head} is one of the IID-family reference variants
     * ({@link ItemRef}, {@link TypeRef}, {@link SchemaRef}).  ContentRefs and
     * DatumRefs reference different hash spaces and are not legal as Body
     * heads.
     */
    private static HashID validateHead(HashID head) {
        Objects.requireNonNull(head, "head");
        if (head instanceof ItemRef || head instanceof TypeRef || head instanceof SchemaRef) {
            return head;
        }
        throw new IllegalArgumentException(
                "Body head must be in the IID family (ItemRef/TypeRef/SchemaRef), got: "
                        + head.variant());
    }

    /**
     * Create a Body with the given head and bindings.
     */
    public static Body of(HashID head, List<? extends DatumNode> entries) {
        return new Body(head, entries);
    }

    /**
     * Create a Body with the given head and no bindings.
     */
    public static Body of(HashID head) {
        return new Body(head, List.of());
    }

    /**
     * Fluent entry point for building a bare Body (no signed records). For inline
     * expression bodies, nested-target bodies, and scene-graph nodes — anywhere a
     * body is data within a larger Datum, not an attested artifact.
     *
     * <p>For signed propositional bodies use {@link Frame#compose}; for
     * identity-bearing item bodies use {@code Manifest.compose}.
     */
    public static BodyComposer compose(HashID head) {
        return new BodyComposer(head);
    }

    /**
     * The head as an {@link ItemRef}.  Throws if this body's head is a
     * {@link TypeRef} (query) or {@link SchemaRef} (schema) — use
     * {@link #head()} for the generic HashID, or branch on
     * {@link #isLiteralBody()} / {@link #isQueryBody()} / {@link #isSchemaBody()}.
     */
    public ItemRef headRef() {
        if (head instanceof ItemRef ir) return ir;
        throw new ClassCastException(
                "Body head is " + head.variant() + ", not ITEM; use head() for the generic HashID");
    }

    /** True when this body's head is a literal {@link ItemRef} (instance / manifest / frame body). */
    public boolean isLiteralBody() { return head instanceof ItemRef; }

    /** True when this body's head is a {@link TypeRef} — the body is a query. */
    public boolean isQueryBody() { return head instanceof TypeRef; }

    /** True when this body's head is a {@link SchemaRef} — the body is a schema/expectation. */
    public boolean isSchemaBody() { return head instanceof SchemaRef; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Body other)) return false;
        return head.equals(other.head) && entries.equals(other.entries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(head, entries);
    }

    @Override
    public String toString() {
        return "Body[" + head + ", " + entries.size() + " entries]";
    }
}
