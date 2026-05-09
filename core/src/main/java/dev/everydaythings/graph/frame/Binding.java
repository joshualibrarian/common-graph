package dev.everydaythings.graph.frame;

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.Factory;
import dev.everydaythings.graph.Implements;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.id.CompoundKey.FrameToken;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.SememeGloss;
import dev.everydaythings.graph.language.ThematicRole;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
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
@Getter
@Implements(Binding.KEY)
@ItemSeed(key = Binding.KEY)
public final class Binding implements Canonical {

    public static final String KEY = "cg.structure:binding";
    public static final ItemID IID = ItemID.fromString(KEY);

    @ItemFrame(predicate = SememeGloss.KEY,
               fieldAs = @ItemFrame.Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
    static final String seedGloss = "a role binding within a frame — key→value with semantic function";

    // EXPECTS — array position 0, 1, 2, 3, 4 (declaration order = position)
    @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
               fieldAs = @ItemFrame.Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY}))
    static final ItemID expectRole = ItemID.fromString(ThematicRole.KEY);

    @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
               fieldAs = @ItemFrame.Bind(role = ThematicRole.Topic.KEY, qualifiers = {FrameBodyOld.TYPE_KEY, Qualifiers.KEY}))
    static final ItemID expectQualifiers = Qualifiers.IID;

    @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
               fieldAs = @ItemFrame.Bind(role = ThematicRole.Topic.KEY, qualifiers = {FrameBodyOld.TYPE_KEY, Target.KEY}))
    static final ItemID expectTarget = Target.IID;

    // TODO: @ItemFrame.Bind annotation still has identity/index properties; remove
    // them when the seed pipeline gets reworked for the new model.

    // Field-name sememes for array positions
    @ItemSeed(key = Qualifiers.KEY)
    static class Qualifiers {
        static final String KEY = "cg.structure:qualifiers";
        static final ItemID IID = ItemID.fromString(KEY);
    }

    @ItemSeed(key = Target.KEY)
    static class Target {
        static final String KEY = "cg.structure:target";
        static final ItemID IID = ItemID.fromString(KEY);
    }

    /** The semantic function — what KIND of binding (NAME, THEME, AGENT, ...). */
    private final ItemID role;

    /** Qualifiers — narrowing + constraints (sememes or literals, may be empty). */
    private final List<FrameToken> qualifiers;

    /** The bound value — CID, item ref, literal, or path. */
    private final BindingTarget target;

    /** Live decoded value (transient, runtime only). */
    private transient Object instance;

    // ==================================================================================
    // Constructors
    // ==================================================================================

    /**
     * Full constructor — role + qualifiers + target.
     *
     * <p>The qualifiers list is canonicalized by sorting on each qualifier's CBOR
     * encoding (lexicographic byte comparison). Qualifiers are a multiset — the
     * order callers happen to supply them in must not affect the binding's identity.
     */
    public Binding(ItemID role, List<FrameToken> qualifiers, BindingTarget target) {
        this.role = Objects.requireNonNull(role, "role");
        this.qualifiers = canonicalSortQualifiers(qualifiers);
        this.target = Objects.requireNonNull(target, "target");
    }

    private static List<FrameToken> canonicalSortQualifiers(List<FrameToken> qualifiers) {
        if (qualifiers == null || qualifiers.isEmpty()) return List.of();
        if (qualifiers.size() == 1) return List.copyOf(qualifiers);
        List<FrameToken> sorted = new ArrayList<>(qualifiers);
        sorted.sort((a, b) -> Arrays.compareUnsigned(
                a.toCbor().EncodeToBytes(),
                b.toCbor().EncodeToBytes()));
        return List.copyOf(sorted);
    }

    /**
     * Simple single-role binding (no qualifiers).
     */
    public Binding(ItemID role, BindingTarget target) {
        this(role, List.of(), target);
    }

    /**
     * No-arg constructor for Canonical decode support.
     */
    @SuppressWarnings("unused")
    private Binding() {
        this.role = null;
        this.qualifiers = List.of();
        this.target = null;
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
    // Key Accessors
    // ==================================================================================

    /**
     * Whether this is a simple (no qualifiers) binding.
     */
    public boolean isSimpleKey() {
        return qualifiers.isEmpty();
    }

    /**
     * Whether this binding has any qualifiers.
     */
    public boolean hasQualifiers() {
        return !qualifiers.isEmpty();
    }

    /**
     * Backward-compatible flat key — [role, sememe-qualifier₁, sememe-qualifier₂, ...].
     *
     * <p>Only includes sememe qualifiers (literal qualifiers are omitted since they
     * can't be represented as ItemIDs). Use {@link #qualifiers()} for the full list.
     */
    public List<ItemID> key() {
        if (qualifiers.isEmpty()) {
            return role != null ? List.of(role) : List.of();
        }
        List<ItemID> result = new ArrayList<>();
        if (role != null) result.add(role);
        for (FrameToken q : qualifiers) {
            if (q instanceof CompoundKey.Sememe s) result.add(s.id());
        }
        return List.copyOf(result);
    }

    /**
     * Whether this binding's role + qualifiers match the given sequence.
     *
     * <p>First argument matches the role. Subsequent arguments match qualifiers
     * (as Sememe tokens). All must be ItemIDs.
     */
    public boolean keyEquals(ItemID... parts) {
        if (parts == null || parts.length == 0) return false;
        if (!parts[0].equals(role)) return false;
        if (parts.length - 1 != qualifiers.size()) return false;
        for (int i = 1; i < parts.length; i++) {
            FrameToken q = qualifiers.get(i - 1);
            if (!(q instanceof CompoundKey.Sememe s) || !s.id().equals(parts[i])) return false;
        }
        return true;
    }

    // ==================================================================================
    // Target Convenience
    // ==================================================================================

    /**
     * If the target is an IidTarget, return the referenced ItemID.
     */
    public ItemID targetId() {
        return target instanceof BindingTarget.IidTarget iid ? iid.iid() : null;
    }

    // ==================================================================================
    // Factories
    // ==================================================================================

    /**
     * Create a binding with role, qualifiers, and target.
     */
    public static Binding qualified(ItemID role, List<FrameToken> qualifiers,
                                    BindingTarget target) {
        return new Binding(role, qualifiers, target);
    }

    /**
     * Create a binding referencing an item.
     */
    public static Binding ref(ItemID role, ItemID target) {
        return new Binding(role, BindingTarget.iid(target));
    }

    /**
     * Create a binding with a compound reference (item + frame key path).
     */
    public static Binding ref(ItemID role, dev.everydaythings.graph.item.id.Ref target) {
        return new Binding(role, BindingTarget.ref(target));
    }

    /**
     * Create a binding with a literal value.
     */
    public static Binding literal(ItemID role, BindingTarget target) {
        return new Binding(role, target);
    }

    /**
     * Create a binding with an inline nested frame.
     */
    public static Binding frame(ItemID role, FrameBodyOld body) {
        return new Binding(role, new BindingTarget.FrameTarget(body));
    }


    // ==================================================================================
    // CBOR Encoding
    // ==================================================================================

    /**
     * Custom CBOR encoding: [role_bytes, qualifiers_array, target]
     *
     * <p>Qualifiers encode as FrameTokens: Sememe → byte string, Literal → text string.
     */
    @Override
    public CBORObject toCborTree(Scope scope) {
        CBORObject arr = CBORObject.NewArray();
        arr.Add(CBORObject.FromByteArray(role.encodeBinary()));

        CBORObject quals = CBORObject.NewArray();
        for (FrameToken q : qualifiers) {
            quals.Add(q.toCbor());
        }
        arr.Add(quals);

        arr.Add(target.toCborTree(scope));
        return arr;
    }

    /**
     * Custom CBOR decoding.
     */
    @Factory
    public static Binding fromCborTree(CBORObject obj) {
        if (obj == null || obj.isNull()) return null;

        // Format: [role_bytes, qualifiers_array, target]
        ItemID role = new ItemID(obj.get(0).GetByteString());

        List<FrameToken> quals = new ArrayList<>();
        CBORObject qualsArr = obj.get(1);
        if (qualsArr != null && !qualsArr.isNull() && qualsArr.getType() == CBORType.Array) {
            for (int i = 0; i < qualsArr.size(); i++) {
                CBORObject q = qualsArr.get(i);
                if (q.getType() == CBORType.ByteString) {
                    quals.add(new CompoundKey.Sememe(new ItemID(q.GetByteString())));
                } else if (q.getType() == CBORType.TextString) {
                    quals.add(new CompoundKey.Literal(q.AsString()));
                }
            }
        }

        BindingTarget target = BindingTarget.fromCborTree(obj.get(2));

        return new Binding(role, quals, target);
    }

    // ==================================================================================
    // Display
    // ==================================================================================

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Binding{");
        if (role != null) {
            sb.append(role.displayAtWidth(12));
            if (!qualifiers.isEmpty()) sb.append(":").append(qualifiers.size());
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
        return Objects.equals(role, other.role)
                && Objects.equals(qualifiers, other.qualifiers)
                && Objects.equals(target, other.target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(role, qualifiers, target);
    }
}
