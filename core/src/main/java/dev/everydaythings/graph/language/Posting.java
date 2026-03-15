package dev.everydaythings.graph.language;

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.Ref;
import lombok.Builder;
import lombok.Getter;

import java.text.Normalizer;
import java.util.*;

/**
 * An entry in the token index mapping a token to an item.
 *
 * <p>A posting is: text → item, with a scope. The DB key is {@code <scope><token>}.
 * When text is entered anywhere in the system (command line, search, expression),
 * it resolves to item references via postings matched against a scope chain.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@link #token} - The normalized lookup key</li>
 *   <li>{@link #target} - The item this token resolves to</li>
 *   <li>{@link #scope} - Any ItemID (language, item, user) or null for universal</li>
 *   <li>{@link #weight} - Relevance weight 0.0-1.0</li>
 * </ul>
 *
 * <p>The scope is just an ItemID — the TokenDictionary doesn't distinguish what
 * kind of item it is. The caller assembles a scope chain from context (active
 * languages, focused item, user, etc.) and gets back all matching postings.
 * Null scope means universal (language-neutral symbols like "m", "kg", "+").
 */
@Getter
@Builder
public final class Posting implements Canonical {

    @Canon(order = 0)
    private final String token;

    @Canon(order = 1)
    private final ItemID scope;  // null = global

    @Canon(order = 2)
    private final ItemID target;

    @Canon(order = 3)
    private final float weight; // Encoded as scaled int in CBOR (WEIGHT_SCALE = 1000)

    /**
     * Grammatical features of the word form that produced this posting.
     *
     * <p>POS is just another feature in the set. Examples:
     * <ul>
     *   <li>"run" (base verb) → {VERB}</li>
     *   <li>"ran" → {VERB, PAST}</li>
     *   <li>"running" → {VERB, PARTICIPLE, PRESENT}</li>
     *   <li>"cats" → {NOUN, PLURAL}</li>
     *   <li>"+" (universal symbol) → {} (empty)</li>
     * </ul>
     */
    @Canon(order = 4)
    private final Set<ItemID> features;

    public Posting(String token, ItemID scope, ItemID target, float weight) {
        this(token, scope, target, weight, Set.of());
    }

    public Posting(String token, ItemID scope, ItemID target, float weight, Set<ItemID> features) {
        this.token = Objects.requireNonNull(token, "token");
        this.scope = scope;  // null = universal
        this.target = Objects.requireNonNull(target, "target");
        this.weight = weight;
        this.features = features != null ? Set.copyOf(features) : Set.of();
    }

    /**
     * Create a universal posting (null scope, resolves for everyone).
     *
     * <p>Use for language-neutral symbols and operators ("m", "kg", "+", "USD").
     */
    public static Posting universal(String token, ItemID target) {
        return new Posting(normalize(token), null, target, 1.0f);
    }

    /**
     * Create a universal posting with custom weight.
     */
    public static Posting universal(String token, ItemID target, float weight) {
        return new Posting(normalize(token), null, target, weight);
    }

    /**
     * Create a universal posting with a single grammatical feature (typically POS).
     */
    public static Posting universal(String token, ItemID target, ItemID feature) {
        return new Posting(normalize(token), null, target, 1.0f, Set.of(feature));
    }

    /**
     * Create a universal posting with grammatical features.
     */
    public static Posting universal(String token, ItemID target, Set<ItemID> features) {
        return new Posting(normalize(token), null, target, 1.0f, features);
    }

    /**
     * Create a scoped posting. The scope is any ItemID — a language, an item, a user.
     */
    public static Posting scoped(String token, ItemID scope, ItemID target) {
        return new Posting(normalize(token), scope, target, 1.0f);
    }

    /**
     * Create a scoped posting with custom weight.
     */
    public static Posting scoped(String token, ItemID scope, ItemID target, float weight) {
        return new Posting(normalize(token), scope, target, weight);
    }

    /**
     * Create a scoped posting with a single grammatical feature (typically POS).
     */
    public static Posting scoped(String token, ItemID scope, ItemID target, ItemID feature) {
        return new Posting(normalize(token), scope, target, 1.0f, Set.of(feature));
    }

    /**
     * Create a scoped posting with grammatical features.
     */
    public static Posting scoped(String token, ItemID scope, ItemID target, float weight, Set<ItemID> features) {
        return new Posting(normalize(token), scope, target, weight, features);
    }

    /**
     * Create a posting local to an item (scope = target).
     */
    public static Posting local(String token, ItemID target) {
        return new Posting(normalize(token), target, target, 1.0f);
    }

    /**
     * Check if this posting is universal (no scope).
     */
    public boolean isUniversal() {
        return scope == null;
    }

    /**
     * Check if this posting is local to its target item.
     */
    public boolean isLocal() {
        return scope != null && scope.equals(target);
    }

    // ==================================================================================
    // Display Methods
    // ==================================================================================

    public Ref ref() {
        return null;  // Postings are index entries, not graph nodes
    }

    public boolean isExpandable() {
        return false;  // Postings are leaf values
    }

    public String displayToken() {
        return token;
    }

    public String displaySubtitle() {
        if (isLocal()) return "local";
        if (isUniversal()) return "universal";
        return "scoped";
    }

    public String colorCategory() {
        return "item";
    }

    public ItemID targetId() {
        return target;
    }

    public String displayDetail() {
        String targetStr = target.encodeText();
        if (targetStr.length() > 50) {
            targetStr = targetStr.substring(0, 47) + "...";
        }
        return targetStr;
    }

    /**
     * Normalize a token for indexing.
     *
     * <p>Normalization:
     * <ul>
     *   <li>NFC Unicode normalization</li>
     *   <li>Lowercase</li>
     *   <li>Trim whitespace</li>
     *   <li>Collapse internal whitespace to single space</li>
     * </ul>
     */
    public static String normalize(String token) {
        if (token == null) return null;
        String normalized = Normalizer.normalize(token, Normalizer.Form.NFC);
        return normalized.toLowerCase().trim().replaceAll("\\s+", " ");
    }

    // No-arg constructor for Canonical decoding
    @SuppressWarnings("unused")
    private Posting() {
        this.token = null;
        this.scope = null;
        this.target = null;
        this.weight = 0;
        this.features = Set.of();
    }

    // Weight scale: 1000 = 1.0f
    private static final int WEIGHT_SCALE = 1000;

    /**
     * Custom CBOR encoding to handle float weight as int.
     *
     * <p>CG-CBOR forbids IEEE 754 floats, so we encode weight as scaled int.
     * Features are encoded as a sorted array of ItemID bytes for deterministic encoding.
     */
    @Override
    public CBORObject toCborTree(Scope cborScope) {
        CBORObject arr = CBORObject.NewArray();
        arr.Add(CBORObject.FromString(token));
        arr.Add(scope != null ? CBORObject.FromByteArray(scope.encodeBinary()) : CBORObject.Null);
        arr.Add(CBORObject.FromByteArray(target.encodeBinary()));
        arr.Add(CBORObject.FromInt32((int) (weight * WEIGHT_SCALE)));
        // Features: sorted array of ItemID bytes (empty array if none)
        CBORObject featArr = CBORObject.NewArray();
        features.stream()
                .sorted(Comparator.comparing(ItemID::encodeText))
                .forEach(f -> featArr.Add(CBORObject.FromByteArray(f.encodeBinary())));
        arr.Add(featArr);
        return arr;
    }

    /**
     * Custom CBOR decoding to handle int weight as float.
     * Features element can be: absent, null, a single ItemID byte string (legacy), or an array of ItemID byte strings.
     */
    public static Posting fromCborTree(CBORObject obj) {
        if (obj == null || obj.isNull()) return null;

        String token = obj.get(0).AsString();

        ItemID scope = null;
        CBORObject scopeObj = obj.get(1);
        if (scopeObj != null && !scopeObj.isNull()) {
            scope = new ItemID(scopeObj.GetByteString());
        }

        ItemID target = new ItemID(obj.get(2).GetByteString());
        float weight = obj.get(3).AsInt32() / (float) WEIGHT_SCALE;

        Set<ItemID> features = Set.of();
        if (obj.size() > 4) {
            CBORObject featObj = obj.get(4);
            if (featObj != null && !featObj.isNull()) {
                if (featObj.getType() == CBORType.ByteString) {
                    // Legacy: single ItemID byte string (old partOfSpeech field)
                    features = Set.of(new ItemID(featObj.GetByteString()));
                } else if (featObj.getType() == CBORType.Array && featObj.size() > 0) {
                    var set = new HashSet<ItemID>();
                    for (int i = 0; i < featObj.size(); i++) {
                        set.add(new ItemID(featObj.get(i).GetByteString()));
                    }
                    features = Set.copyOf(set);
                }
            }
        }

        return new Posting(token, scope, target, weight, features);
    }
}
