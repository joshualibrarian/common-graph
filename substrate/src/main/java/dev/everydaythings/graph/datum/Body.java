package dev.everydaythings.graph.datum;

import dev.everydaythings.graph.ref.HashID;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.ref.SchemaRef;
import dev.everydaythings.graph.ref.TypeRef;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A Datum that asserts content. Has no signature slot.
 *
 * <p>The head is a reference into the IID space — one of {@link ItemRef}
 * (literal), {@link TypeRef} (query: match instances of this kind), or
 * {@link SchemaRef} (schema: declare what instances of this kind look like).
 * {@link dev.everydaythings.graph.ref.ContentRef} and
 * {@link dev.everydaythings.graph.ref.DatumRef} are rejected — a Body's head
 * always points at a sememe, never at content or datum hashes.
 *
 * <p>Two forms:
 *
 * <ul>
 *   <li><b>Structured</b> (the common form): head plus a list of bindings.
 *       Propositional frames, manifest bodies, multi-field value bodies
 *       (Color, PostalAddress).</li>
 *   <li><b>Atomic</b>: head plus a single leaf value (text, integer, instant,
 *       byte[], boolean, rational, ...).  Used for typed value-atoms whose
 *       content has no internal structure to bind — EmailAddress, ISBN,
 *       standalone numeric values, etc.</li>
 * </ul>
 *
 * <p>Both forms hash to deterministic DatumIDs via {@link
 * dev.everydaythings.graph.canonical.CanonWalker}'s structural walk, which
 * is independent of any wire format.  See the {@code encoding/} package
 * for concrete serialization (CG-CBOR is the reference encoding).
 *
 * <p>Construction is permissive — bodies may carry any bindings, including ones
 * beyond what the head sememe's EXPECTS strictly declares. Validation against
 * EXPECTS is a separate concern done at signing/commit time, not at construction.
 */
public non-sealed class Body extends Datum {

    /**
     * Atomic-form content: a single leaf value.  Non-null only for atomic
     * bodies; structured bodies have {@code null} here and use {@link #entries}
     * instead.
     */
    private final Object atomicContent;

    public Body(HashID head, List<? extends DatumNode> entries) {
        super(validateHead(head), entries);
        this.atomicContent = null;
    }

    /**
     * Atomic-form constructor: a Body whose content is a single leaf value
     * rather than a list of bindings.  Use for typed value-atoms
     * (EmailAddress, ISBN, standalone numeric values) where the body's
     * identity IS its head plus a canonical leaf.
     *
     * <p>The content must be a recognized leaf type: {@link String},
     * {@link Long}/{@link Integer}, {@link Boolean}, {@code byte[]},
     * {@link Instant}, {@link java.math.BigInteger}, {@link java.math.BigDecimal},
     * {@link dev.everydaythings.graph.value.Rational}, or {@link HashID}.
     *
     * @throws IllegalArgumentException if {@code atomicContent}'s runtime type
     *         isn't a recognized leaf
     */
    public Body(HashID head, Object atomicContent) {
        super(validateHead(head), List.of());
        Objects.requireNonNull(atomicContent, "atomicContent");
        validateAtomicType(atomicContent);
        this.atomicContent = atomicContent;
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

    private static void validateAtomicType(Object content) {
        if (content instanceof String) return;
        if (content instanceof Long) return;
        if (content instanceof Integer) return;
        if (content instanceof Boolean) return;
        if (content instanceof byte[]) return;
        if (content instanceof Instant) return;
        if (content instanceof java.math.BigInteger) return;
        if (content instanceof java.math.BigDecimal) return;
        if (content instanceof dev.everydaythings.graph.value.Rational) return;
        if (content instanceof HashID) return;
        throw new IllegalArgumentException(
                "Atomic body content must be a leaf-typed value (String, Long, Boolean, "
                        + "byte[], Instant, BigInteger, BigDecimal, Rational, HashID); got: "
                        + content.getClass().getName());
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
     * Create an atomic-form Body with the given head and leaf content.
     */
    public static Body ofAtomic(HashID head, Object atomicContent) {
        return new Body(head, atomicContent);
    }

    /**
     * Fluent entry point for building a bare Body (no signed records). For inline
     * expression bodies, nested-target bodies, and scene-graph nodes — anywhere a
     * body is data within a larger Datum, not an attested artifact.
     *
     * <p>For signed propositional bodies use {@link Frame#compose}; for
     * identity-bearing item bodies use {@code Manifest.compose}.
     */
    public static BodyBuilder compose(HashID head) {
        return new BodyBuilder(head);
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

    /**
     * True when this body is in atomic form (head plus a single leaf value).
     * Atomic bodies have no bindings.
     */
    public boolean isAtomic() {
        return atomicContent != null;
    }

    /**
     * The atomic-form content, if this body is atomic.  Returns
     * {@link Optional#empty()} for structured bodies.
     */
    public Optional<Object> atomicContent() {
        return Optional.ofNullable(atomicContent);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Body other)) return false;
        return head.equals(other.head)
                && entries.equals(other.entries)
                && atomicEquals(atomicContent, other.atomicContent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(head, entries, atomicHashCode(atomicContent));
    }

    @Override
    public String toString() {
        if (isAtomic()) return "Body[" + head + ", atomic=" + atomicSummary() + "]";
        return "Body[" + head + ", " + entries.size() + " entries]";
    }

    private String atomicSummary() {
        if (atomicContent instanceof byte[] bs) return "byte[" + bs.length + "]";
        String s = atomicContent.toString();
        return s.length() <= 32 ? s : s.substring(0, 29) + "...";
    }

    private static boolean atomicEquals(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a instanceof byte[] ab && b instanceof byte[] bb) return Arrays.equals(ab, bb);
        return a.equals(b);
    }

    private static int atomicHashCode(Object a) {
        if (a == null) return 0;
        if (a instanceof byte[] bs) return Arrays.hashCode(bs);
        return a.hashCode();
    }
}
