package dev.everydaythings.graph.frame;

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.Factory;
import dev.everydaythings.graph.item.Implements;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.id.FrameKey;
import dev.everydaythings.graph.item.id.FrameKey.FrameToken;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.SememeGloss;
import dev.everydaythings.graph.language.ThematicRole;
import lombok.Getter;

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
 * <p>Two orthogonal flags control behavior:
 * <ul>
 *   <li><b>identity</b> — does this binding contribute to the frame's body hash?</li>
 *   <li><b>index</b> — does this binding's target create a reverse-lookup entry?</li>
 * </ul>
 *
 * <p>The {@code instance} field is the live decoded runtime value (transient).
 *
 * @see FrameBody
 * @see BindingTarget
 */
@Getter
@Implements(Binding.KEY)
@ItemSeed(key = Binding.KEY)
public final class Binding implements Canonical {

    public static final String KEY = "cg.structure:binding";
    public static final ItemID IID = ItemID.fromString(KEY);

    @ItemFrame(predicate = SememeGloss.KEY,
               fieldAs = @ItemFrame.Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
    static final String seedGloss = "a role binding within a frame — key→value with semantic function";

    // EXPECTS — array position 0, 1, 2, 3, 4 (declaration order = position)
    @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
               fieldAs = @ItemFrame.Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY}))
    static final ItemID expectRole = ItemID.fromString(ThematicRole.KEY);

    @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
               fieldAs = @ItemFrame.Bind(role = ThematicRole.Topic.KEY, qualifiers = {Qualifiers.KEY}))
    static final ItemID expectQualifiers = Qualifiers.IID;

    @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
               fieldAs = @ItemFrame.Bind(role = ThematicRole.Topic.KEY, qualifiers = {Target.KEY}))
    static final ItemID expectTarget = Target.IID;

    @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
               fieldAs = @ItemFrame.Bind(role = ThematicRole.Topic.KEY, qualifiers = {Identity.KEY}))
    static final ItemID expectIdentity = Identity.IID;

    @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
               fieldAs = @ItemFrame.Bind(role = ThematicRole.Topic.KEY, qualifiers = {Index.KEY}))
    static final ItemID expectIndex = Index.IID;

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

    @ItemSeed(key = Identity.KEY)
    static class Identity {
        static final String KEY = "cg.structure:identity";
        static final ItemID IID = ItemID.fromString(KEY);
    }

    @ItemSeed(key = Index.KEY)
    static class Index {
        static final String KEY = "cg.structure:index";
        static final ItemID IID = ItemID.fromString(KEY);
    }

    /** The semantic function — what KIND of binding (NAME, THEME, AGENT, ...). */
    private final ItemID role;

    /** Qualifiers — narrowing + constraints (sememes or literals, may be empty). */
    private final List<FrameToken> qualifiers;

    /** The bound value — CID, item ref, literal, or path. */
    private final BindingTarget target;

    /** Does this binding contribute to the frame's body hash? */
    private final boolean identity;

    /** Does this binding's target create a reverse-lookup index entry? */
    private final boolean index;

    /** Live decoded value (transient, runtime only). */
    private transient Object instance;

    // ==================================================================================
    // Constructors
    // ==================================================================================

    /**
     * Full constructor — role + qualifiers + target + flags.
     */
    public Binding(ItemID role, List<FrameToken> qualifiers, BindingTarget target,
                   boolean identity, boolean index) {
        this.role = Objects.requireNonNull(role, "role");
        this.qualifiers = qualifiers != null ? List.copyOf(qualifiers) : List.of();
        this.target = Objects.requireNonNull(target, "target");
        this.identity = identity;
        this.index = index;
    }

    /**
     * Simple single-role binding with explicit flags (no qualifiers).
     */
    public Binding(ItemID role, BindingTarget target, boolean identity, boolean index) {
        this(role, List.of(), target, identity, index);
    }

    /**
     * Simple single-role binding with default flags (identity=true, index=false).
     */
    public Binding(ItemID role, BindingTarget target) {
        this(role, List.of(), target, true, false);
    }

    /**
     * Backward-compatible flat key constructor — splits key[0] as role, key[1:] as Sememe qualifiers.
     */
    public Binding(List<ItemID> key, BindingTarget target, boolean identity, boolean index) {
        this(
            key != null && !key.isEmpty() ? key.getFirst() : null,
            key != null && key.size() > 1
                ? key.subList(1, key.size()).stream()
                    .map(id -> (FrameToken) new FrameKey.Sememe(id))
                    .toList()
                : List.of(),
            target, identity, index
        );
    }

    /**
     * No-arg constructor for Canonical decode support.
     */
    @SuppressWarnings("unused")
    private Binding() {
        this.role = null;
        this.qualifiers = List.of();
        this.target = null;
        this.identity = true;
        this.index = false;
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
            if (q instanceof FrameKey.Sememe s) result.add(s.id());
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
            if (!(q instanceof FrameKey.Sememe s) || !s.id().equals(parts[i])) return false;
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
     * Create a binding with explicit role, qualifiers, and flags.
     */
    public static Binding qualified(ItemID role, List<FrameToken> qualifiers,
                                    BindingTarget target, boolean identity, boolean index) {
        return new Binding(role, qualifiers, target, identity, index);
    }

    /**
     * Create an identity binding referencing an item.
     */
    public static Binding ref(ItemID role, ItemID target) {
        return new Binding(role, BindingTarget.iid(target));
    }

    /**
     * Create an identity binding with a compound reference (item + frame key path).
     */
    public static Binding ref(ItemID role, dev.everydaythings.graph.item.id.Ref target) {
        return new Binding(role, BindingTarget.ref(target));
    }

    /**
     * Create an identity binding with a literal value.
     */
    public static Binding literal(ItemID role, BindingTarget target) {
        return new Binding(role, target);
    }

    /**
     * Create a binding with explicit flags.
     */
    public static Binding of(ItemID role, BindingTarget target, boolean identity, boolean index) {
        return new Binding(role, target, identity, index);
    }

    /**
     * Create a non-identity binding (doesn't affect body hash).
     */
    public static Binding nonIdentity(ItemID role, BindingTarget target) {
        return new Binding(role, target, false, false);
    }

    /**
     * Create an indexed binding (creates reverse-lookup entry).
     */
    public static Binding indexed(ItemID role, BindingTarget target) {
        return new Binding(role, target, true, true);
    }

    /**
     * Create an identity binding with an inline nested frame.
     */
    public static Binding frame(ItemID role, FrameBody body) {
        return new Binding(role, new BindingTarget.FrameTarget(body));
    }

    /**
     * Create a compound-key binding from a flat ItemID list.
     *
     * @deprecated Use {@link #qualified(ItemID, List, BindingTarget, boolean, boolean)} instead.
     */
    @Deprecated
    public static Binding compound(List<ItemID> key, BindingTarget target, boolean identity, boolean index) {
        return new Binding(key, target, identity, index);
    }

    // ==================================================================================
    // CBOR Encoding
    // ==================================================================================

    /**
     * Custom CBOR encoding: [role_bytes, qualifiers_array, target, identity, index]
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
        arr.Add(CBORObject.FromObject(identity));
        arr.Add(CBORObject.FromObject(index));
        return arr;
    }

    /**
     * Custom CBOR decoding.
     */
    @Factory
    public static Binding fromCborTree(CBORObject obj) {
        if (obj == null || obj.isNull()) return null;

        // Format: [role_bytes, qualifiers_array, target, identity, index]
        ItemID role = new ItemID(obj.get(0).GetByteString());

        List<FrameToken> quals = new ArrayList<>();
        CBORObject qualsArr = obj.get(1);
        if (qualsArr != null && !qualsArr.isNull() && qualsArr.getType() == CBORType.Array) {
            for (int i = 0; i < qualsArr.size(); i++) {
                CBORObject q = qualsArr.get(i);
                if (q.getType() == CBORType.ByteString) {
                    quals.add(new FrameKey.Sememe(new ItemID(q.GetByteString())));
                } else if (q.getType() == CBORType.TextString) {
                    quals.add(new FrameKey.Literal(q.AsString()));
                }
            }
        }

        BindingTarget target = BindingTarget.fromCborTree(obj.get(2));
        boolean identity = obj.size() > 3 && obj.get(3).AsBoolean();
        boolean index = obj.size() > 4 && obj.get(4).AsBoolean();

        return new Binding(role, quals, target, identity, index);
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
        if (!identity) sb.append(" [non-id]");
        if (index) sb.append(" [indexed]");
        if (instance != null) sb.append(" [live]");
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Binding other)) return false;
        return identity == other.identity
                && index == other.index
                && Objects.equals(role, other.role)
                && Objects.equals(qualifiers, other.qualifiers)
                && Objects.equals(target, other.target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(role, qualifiers, target, identity, index);
    }
}
