package dev.everydaythings.graph.datum;

import dev.everydaythings.graph.canonical.Layout;
import dev.everydaythings.graph.canonical.Order;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.CompoundKey.Qualifier;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.ref.HashID;

import java.util.List;
import java.util.Objects;

/**
 * A single role binding within a frame — the key→value unit of the frame primitive.
 *
 * <p>Each binding has these semantic parts:
 * <ul>
 *   <li><b>role</b> — the semantic function (NAME, THEME, AGENT, RESULT, etc.). Always a sememe.</li>
 *   <li><b>qualifiers</b> — narrowing + type constraints (ENGLISH, VERB, LEMMA, QUANTITY, etc.).
 *       Can be sememes or literals. May be empty for simple bindings.</li>
 *   <li><b>target</b> — the bound value (item ref, literal, content CID).</li>
 *   <li><b>index</b> — optional ordinal position; structural marker for ordered
 *       collections. When two bindings share the same compound key, a non-null
 *       index disambiguates their order. Null when the binding isn't part of an
 *       ordered group. See {@link #index()}.</li>
 * </ul>
 *
 * <p>The compound key {@code [role, qualifier₁, qualifier₂, ...]} is the binding's KEY.
 * The target is the binding's VALUE. Every binding is a key→value pair.
 *
 * <p>Bindings are pure data. Identity-bearing-ness is determined by which Datum the
 * binding lives in (Body = identity bindings, Record = non-identity bindings); whether
 * a binding gets indexed is a node-side policy decision applied at insertion time.
 * Neither concern lives on the Binding itself.
 *
 * <p>The {@code instance} field is the live decoded runtime value (transient).
 *
 * @see BindingTarget
 */
@Layout(Layout.Kind.ARRAY)
public final class Binding implements DatumNode {

    /**
     * The binding's key — head sememe plus qualifiers. The CompoundKey owns
     * canonicalization of the qualifier multiset; Binding just carries it.
     */
    @Order(0) private final CompoundKey key;

    /**
     * The bound value.  Any of:
     * <ul>
     *   <li>a {@link dev.everydaythings.graph.ref.HashID} (ItemRef / ContentRef / DatumRef)
     *       — reference to an item, content, or datum</li>
     *   <li>a {@link BindingTarget.RefTarget} — typed wrapper around a HashID</li>
     *   <li>a {@link BindingTarget.FrameTarget} or a {@link Body} — inline nested frame</li>
     *   <li>an {@link Opaque} stand-in ({@link Opaque.Redacted},
     *       {@link Opaque.Compressed}, {@link Opaque.Encrypted}) — Merkle-
     *       preserving stand-in for a hidden / compressed / encrypted subtree</li>
     *   <li>a primitive: {@link String}, {@link Long}, {@link Boolean}, {@code byte[]},
     *       {@link java.time.Instant}, {@link java.math.BigDecimal},
     *       {@link java.math.BigInteger}, {@link dev.everydaythings.graph.value.Rational}</li>
     * </ul>
     * The codec dispatches on runtime type; what the value <i>means</i>
     * (Java class name, multikey, IP address bytes, etc.) is carried by the
     * binding's qualifiers, not by the target itself.
     */
    @Order(1) private final Object target;

    /**
     * Optional ordinal position. When non-null, this binding occupies position
     * {@code index} within the ordered group of bindings sharing its compound
     * key. When null, the binding is unordered (set-like membership with its
     * siblings). The canonical encoding omits trailing-null fields, so an
     * absent index costs zero bytes. Two bindings with the same compound key
     * AND the same non-null index are malformed.
     */
    @Order(2) private final Long index;

    /** Live decoded value (transient, runtime only). */
    private transient Object instance;

    // ==================================================================================
    // Constructors
    // ==================================================================================

    /**
     * Primary constructor — a compound key, target, and optional index.
     *
     * <p>Target may be {@code null}.  A null-target binding asserts membership
     * by role alone: the binding's existence on a manifest is the assertion,
     * regardless of what it points at.  HANDLES with no target is the
     * canonical use case (an item declaring it handles its own frames).
     */
    public Binding(CompoundKey key, Object target, Long index) {
        this.key = Objects.requireNonNull(key, "key");
        this.target = target;  // nullable — see javadoc
        this.index = index;    // nullable
    }

    /**
     * Convenience: compound key + target, no index (unordered binding).
     */
    public Binding(CompoundKey key, Object target) {
        this(key, target, null);
    }

    /**
     * Convenience: build a binding from role + qualifiers + target.  The
     * qualifier list is canonicalized inside CompoundKey.  Role must be in
     * the IID family (ItemRef / TypeRef / SchemaRef).
     */
    public Binding(HashID role, List<Qualifier> qualifiers, Object target) {
        this(CompoundKey.of(role, qualifiers == null ? List.of() : qualifiers), target, null);
    }

    /**
     * Convenience: role + qualifiers + target + index.
     */
    public Binding(HashID role, List<Qualifier> qualifiers, Object target, Long index) {
        this(CompoundKey.of(role, qualifiers == null ? List.of() : qualifiers), target, index);
    }

    /**
     * Convenience: simple single-role binding (no qualifiers, no index).
     */
    public Binding(HashID role, Object target) {
        this(CompoundKey.of(role), target, null);
    }

    // ==================================================================================
    // Accessors
    // ==================================================================================

    /** The compound key (head + qualifiers). */
    public CompoundKey key() {
        return key;
    }

    /**
     * The semantic function — sugar for {@code key().head()}.  Always in the
     * IID family ({@link ItemRef} / {@link TypeRef} / {@link SchemaRef}).
     * Most call sites compare against {@code ItemRef.iid(KEY)}; that
     * comparison works because {@link HashID#equals} compares by full
     * ref-bytes (variant + multihash), so a literal {@code @KEY} doesn't
     * match a query {@code ?KEY} — which is correct.
     */
    public HashID role() {
        return key.head();
    }

    /**
     * The role as an {@link ItemRef}, asserting the variant.  Throws when the
     * role is a {@link TypeRef} or {@link SchemaRef}.  Use this when the
     * binding is known to be a literal role (not an expectation / query).
     */
    public ItemRef roleIid() {
        return key.headIid();
    }

    /** Qualifiers — sugar for {@code key().qualifiers()}. */
    public List<Qualifier> qualifiers() {
        return key.qualifiers();
    }

    /** The bound value. */
    public Object target() {
        return target;
    }

    /**
     * Ordinal position within an ordered group of same-compound-key siblings,
     * or {@code null} when unordered. Structural — not a qualifier on the
     * compound key.
     */
    public Long index() {
        return index;
    }

    /** Whether this binding carries an explicit ordinal position. */
    public boolean hasIndex() {
        return index != null;
    }

    // ==================================================================================
    // Instance Management
    // ==================================================================================

    /**
     * Set the live decoded instance for this binding.
     */
    public void setInstance(Object instance) {
        this.instance = instance;
    }

    /**
     * Get the live decoded instance, cast to the expected type.
     */
    @SuppressWarnings("unchecked")
    public <T> T instance(Class<T> type) {
        return type.isInstance(instance) ? (T) instance : null;
    }

    // ==================================================================================
    // Key Predicates
    // ==================================================================================

    /** Whether this is a simple (no qualifiers) binding. */
    public boolean isSimpleKey() {
        return qualifiers().isEmpty();
    }

    /** Whether this binding has any qualifiers. */
    public boolean hasQualifiers() {
        return !qualifiers().isEmpty();
    }

    // ==================================================================================
    // Target Convenience
    // ==================================================================================

    /**
     * If the target is a bare unpinned ItemRef, return it; otherwise null.
     * Useful for callers wanting "the item this binding points at" with no
     * version pin attached.
     */
    public ItemRef targetId() {
        if (target instanceof ItemRef ir && !ir.isPinned()) return ir;
        if (target instanceof BindingTarget.RefTarget rt && !rt.isCompound()) {
            return rt.asItemId();
        }
        return null;
    }

    // ==================================================================================
    // Factories
    // ==================================================================================

    /**
     * Create a binding with role, qualifiers, and target.
     */
    public static Binding qualified(ItemRef role, List<Qualifier> qualifiers,
                                    Object target) {
        return new Binding(role, qualifiers, target);
    }

    /**
     * Create a binding referencing an item.
     */
    public static Binding ref(ItemRef role, ItemRef target) {
        return new Binding(role, target);
    }

    /**
     * Create a binding wrapping a {@link HashID} as the target.
     */
    public static Binding ref(ItemRef role, HashID target) {
        return new Binding(role, target);
    }

    /**
     * Create a binding with a literal value.
     */
    public static Binding literal(ItemRef role, Object target) {
        return new Binding(role, target);
    }

    // ==================================================================================
    // Display
    // ==================================================================================

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Binding{");
        sb.append(key.head().displayAtWidth(12));
        if (!key.qualifiers().isEmpty()) sb.append(":").append(key.qualifiers().size());
        if (index != null) sb.append("[#").append(index).append("]");
        sb.append(" -> ").append(target);
        if (instance != null) sb.append(" [live]");
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Binding other)) return false;
        return Objects.equals(key, other.key)
                && Objects.equals(target, other.target)
                && Objects.equals(index, other.index);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, target, index);
    }
}
