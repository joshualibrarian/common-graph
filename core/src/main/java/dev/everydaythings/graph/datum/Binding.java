package dev.everydaythings.graph.datum;

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;
import dev.everydaythings.graph.canonical.Factory;
import dev.everydaythings.graph.canonical.Layout;
import dev.everydaythings.graph.canonical.Order;
import dev.everydaythings.graph.id.CompoundKey;
import dev.everydaythings.graph.id.CompoundKey.Qualifier;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.id.HashID;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A single role binding within a frame — the key→value unit of the frame primitive.
 *
 * <p>Each binding has three semantic parts:
 * <ul>
 *   <li><b>role</b> — the semantic function (NAME, THEME, AGENT, RESULT, etc.). Always a sememe.</li>
 *   <li><b>qualifiers</b> — narrowing + type constraints (ENGLISH, VERB, LEMMA, QUANTITY, etc.).
 *       Can be sememes or literals. May be empty for simple bindings.</li>
 *   <li><b>target</b> — the bound value (item ref, literal, content CID).</li>
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
public final class Binding {

    /**
     * The binding's key — head sememe plus qualifiers. The CompoundKey owns
     * canonicalization of the qualifier multiset; Binding just carries it.
     */
    @Order(0) private final CompoundKey key;

    /**
     * The bound value. Any of:
     * <ul>
     *   <li>a {@link dev.everydaythings.graph.id.HashID} (ItemRef / ContentRef / DatumRef)
     *       — reference to an item, content, or datum</li>
     *   <li>a {@link Body} — inline nested frame</li>
     *   <li>a {@link BindingTarget.RedactedTarget} — Merkle elision marker</li>
     *   <li>a primitive: {@link String}, {@link Long}, {@link Boolean}, {@code byte[]},
     *       {@link java.time.Instant}, {@link java.math.BigDecimal},
     *       {@link java.math.BigInteger}, {@link dev.everydaythings.graph.value.Rational}</li>
     * </ul>
     * The codec dispatches on runtime type; what the value <i>means</i>
     * (Java class name, multikey, IP address bytes, etc.) is carried by the
     * binding's qualifiers, not by the target itself.
     */
    @Order(1) private final Object target;

    /** Live decoded value (transient, runtime only). */
    private transient Object instance;

    // ==================================================================================
    // Constructors
    // ==================================================================================

    /**
     * Primary constructor — a compound key and its target.
     */
    public Binding(CompoundKey key, Object target) {
        this.key = Objects.requireNonNull(key, "key");
        this.target = Objects.requireNonNull(target, "target");
    }

    /**
     * Convenience: build a binding from role + qualifiers + target. The
     * qualifier list is canonicalized inside CompoundKey.
     */
    public Binding(ItemRef role, List<Qualifier> qualifiers, Object target) {
        this(CompoundKey.of(role, qualifiers == null ? List.of() : qualifiers), target);
    }

    /**
     * Convenience: simple single-role binding (no qualifiers).
     */
    public Binding(ItemRef role, Object target) {
        this(CompoundKey.of(role), target);
    }

    /**
     * No-arg constructor for legacy Canonical decode support.
     */
    @SuppressWarnings("unused")
    private Binding() {
        this.key = null;
        this.target = null;
    }

    // ==================================================================================
    // Accessors
    // ==================================================================================

    /** The compound key (head + qualifiers). */
    public CompoundKey key() {
        return key;
    }

    /** The semantic function — sugar for {@code key().head()}. */
    public ItemRef role() {
        return key == null ? null : key.head();
    }

    /** Qualifiers — sugar for {@code key().qualifiers()}. */
    public List<Qualifier> qualifiers() {
        return key == null ? List.of() : key.qualifiers();
    }

    /** The bound value. */
    public Object target() {
        return target;
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
    // CBOR decoding (legacy entry point — codec calls this for polymorphic dispatch)
    // ==================================================================================

    /**
     * Custom CBOR decoding. Wire format:
     * {@code [CompoundKey, BindingTarget]} — a 2-element array where the
     * first element is a CompoundKey's CBOR ({@code [Tag6(head), [quals]]})
     * and the second is the binding target.
     */
    @Factory
    public static Binding fromCborTree(CBORObject obj) {
        if (obj == null || obj.isNull()) return null;
        if (obj.getType() != CBORType.Array || obj.size() != 2) {
            throw new IllegalArgumentException(
                    "Binding requires a 2-element CBOR array [key, target], got "
                            + obj.getType() + (obj.getType() == CBORType.Array
                                    ? " of size " + obj.size() : ""));
        }
        CompoundKey key = dev.everydaythings.graph.encoding.CgCbor.decodeCompoundKey(obj.get(0));
        Object target = BindingTarget.fromCborTree(obj.get(1));
        return new Binding(key, target);
    }

    // ==================================================================================
    // Display
    // ==================================================================================

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Binding{");
        if (key != null) {
            sb.append(key.head().displayAtWidth(12));
            if (!key.qualifiers().isEmpty()) sb.append(":").append(key.qualifiers().size());
        }
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
                && Objects.equals(target, other.target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, target);
    }
}
