package dev.everydaythings.graph.language;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.encoding.Canonical;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.frame.FrameBodyOld;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.Ref;

import java.text.Normalizer;
import java.util.*;

/**
 * A thin view over a frame binding in the token index.
 *
 * <p>Three fields: {@link FrameBodyOld}, binding index, weight. Everything
 * else is derived from the body's bindings:
 * <ul>
 *   <li><b>token</b> — the text literal in the indexed binding</li>
 *   <li><b>target</b> — the body's home item ({@link FrameBodyOld#homeId()})</li>
 *   <li><b>scope</b> — first qualifier of the binding (language), or null (universal)</li>
 *   <li><b>features</b> — remaining qualifiers (POS, morphology)</li>
 * </ul>
 *
 * <p>The {@link dev.everydaythings.graph.library.dictionary.TokenDictionary}
 * persists only the body hash and binding index; the full Posting is
 * reconstructed at query time by resolving the body from the object store.
 */
public final class Posting implements Canonical {

    private static final int WEIGHT_SCALE = 1000;

    private final FrameBodyOld body;
    private final int bindingIndex;
    private final float weight;

    // ==================================================================================
    // Constructor
    // ==================================================================================

    public Posting(FrameBodyOld body, int bindingIndex, float weight) {
        this.body = Objects.requireNonNull(body, "body");
        this.bindingIndex = bindingIndex;
        this.weight = weight;
    }

    /**
     * Create a posting from a frame body and binding index.
     */
    public static Posting fromFrame(FrameBodyOld body, int bindingIndex, float weight) {
        return new Posting(body, bindingIndex, weight);
    }

    /**
     * Create a posting with a different weight (for completion merging).
     */
    public static Posting withWeight(Posting posting, float newWeight) {
        return new Posting(posting.body, posting.bindingIndex, newWeight);
    }

    // ==================================================================================
    // Derived Accessors
    // ==================================================================================

    /** The binding this posting refers to. */
    public Binding binding() {
        return body.frameBindings().get(bindingIndex);
    }

    /** The normalized token string (from the binding's text literal). */
    public String token() {
        Binding b = binding();
        if (b.target() instanceof Literal lit && Literal.TYPE_TEXT.equals(lit.valueType())) {
            try {
                return normalize(lit.asText());
            } catch (Exception e) {
                return "";
            }
        }
        return "";
    }

    /** Target item this token resolves to (the body's home item). */
    public ItemID target() {
        return body.homeId();
    }

    /** Scope ItemID — first qualifier of the binding, or null (universal). */
    public ItemID scope() {
        var quals = binding().qualifiers();
        if (!quals.isEmpty() && quals.getFirst() instanceof CompoundKey.Sememe s) {
            return s.id();
        }
        return null;
    }

    /**
     * Grammatical features — remaining qualifiers after scope.
     *
     * <p>For {@code NAME:[ENGLISH, VERB, LEMMA] → "create"}, returns {VERB, LEMMA}.
     */
    public Set<ItemID> features() {
        var quals = binding().qualifiers();
        if (quals.size() <= 1) return Set.of();
        Set<ItemID> fs = new HashSet<>();
        for (int i = 1; i < quals.size(); i++) {
            if (quals.get(i) instanceof CompoundKey.Sememe s) fs.add(s.id());
        }
        return Set.copyOf(fs);
    }

    /** Relevance weight. */
    public float weight() { return weight; }

    /** The source frame body. */
    public FrameBodyOld body() { return body; }

    /** The binding index within the body. */
    public int bindingIndex() { return bindingIndex; }

    /** The body hash (ContentID). */
    public ContentID bodyHash() { return body.hash(); }

    // ==================================================================================
    // Queries
    // ==================================================================================

    /** Check if this posting is universal (no scope). */
    public boolean isUniversal() { return scope() == null; }

    /** Check if this posting is local to its target item. */
    public boolean isLocal() {
        ItemID s = scope();
        return s != null && s.equals(target());
    }

    // ==================================================================================
    // Display Methods
    // ==================================================================================

    public Ref ref() { return null; }
    public boolean isExpandable() { return false; }
    public String displayToken() { return token(); }

    public String displaySubtitle() {
        if (isLocal()) return "local";
        if (isUniversal()) return "universal";
        return "scoped";
    }

    public String colorCategory() { return "item"; }
    public ItemID targetId() { return target(); }

    public String displayDetail() {
        ItemID t = target();
        if (t == null) return "";
        String targetStr = t.encodeText();
        if (targetStr.length() > 50) {
            targetStr = targetStr.substring(0, 47) + "...";
        }
        return targetStr;
    }

    // ==================================================================================
    // Token Normalization
    // ==================================================================================

    public static String normalize(String token) {
        if (token == null) return null;
        String normalized = Normalizer.normalize(token, Normalizer.Form.NFC);
        return normalized.toLowerCase().trim().replaceAll("\\s+", " ");
    }

    // ==================================================================================
    // Canonical Encoding
    // ==================================================================================

    @Override
    public CBORObject toCborTree(Scope cborScope) {
        CBORObject arr = CBORObject.NewArray();
        arr.Add(body.toCborTree(Scope.RECORD));
        arr.Add(CBORObject.FromInt32(bindingIndex));
        arr.Add(CBORObject.FromInt32((int) (weight * WEIGHT_SCALE)));
        return arr;
    }

    public static Posting fromCborTree(CBORObject obj) {
        if (obj == null || obj.isNull()) return null;
        FrameBodyOld body = Canonical.fromCborTree(obj.get(0), FrameBodyOld.class, Scope.RECORD);
        int bindingIndex = obj.get(1).AsInt32();
        float weight = obj.get(2).AsInt32() / (float) WEIGHT_SCALE;
        return new Posting(body, bindingIndex, weight);
    }

    @SuppressWarnings("unused")
    private Posting() {
        this.body = null;
        this.bindingIndex = -1;
        this.weight = 0;
    }
}
