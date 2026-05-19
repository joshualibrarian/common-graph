package dev.everydaythings.graph.bridges.keri;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.identity.algorithm.Hash;
import dev.everydaythings.graph.identity.algorithm.KeyAgreement;
import dev.everydaythings.graph.identity.algorithm.Signing;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;
import io.ipfs.multihash.Multihash;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CESR matter code — the textual prefix in KERI's "Composable Event Streaming
 * Representation" that identifies a cryptographic primitive's type and length.
 *
 * <p>This class plays three coordinated roles:
 *
 * <ul>
 *   <li><b>Predicate sememe</b> ({@code @Seed.Item}): the graph identity of
 *       "this algorithm has this matter code" assertions.</li>
 *   <li><b>Code registry</b>: public {@code String} constants for each known
 *       code ({@link #SHA2_256}, {@link #BLAKE3_256}, etc.) — these are the
 *       names callers reach for, and each is annotated with
 *       {@code @Seed.Frame(theme = algorithmKey)} so the bridge asserts the
 *       (algorithm, code) relationship into the graph at bootstrap.</li>
 *   <li><b>Codec helper</b>: companion accessors ({@link #rawLength},
 *       {@link #qb64Length}, {@link #algorithm}, {@link #multihashType},
 *       {@link #identify}) used by {@link Cesr} for the hot-path wire codec.
 *       Driven by a private metadata table keyed on the same constants.</li>
 * </ul>
 *
 * <h3>Sub-table layout (CESR specification)</h3>
 *
 * <ul>
 *   <li><b>1-char codes</b> ({@code A}..{@code Z}, {@code a}..{@code z}):
 *       fixed 44 base64-url chars total → 32 raw bytes.  E.g. {@code D} =
 *       Ed25519 verification key (transferable), {@code E} = Blake3-256.</li>
 *   <li><b>2-char codes</b> starting with {@code 0}: 88 base64-url chars total
 *       → 64 raw bytes.  E.g. {@code 0B} = Ed25519 signature.</li>
 *   <li><b>4-char codes</b> starting with {@code 1}: variable-length raw
 *       payloads (not yet exercised by this bridge).</li>
 * </ul>
 */
@Seed.Item(key = MatterCode.KEY, head = CoreVocabulary.Predicate.KEY)
public final class MatterCode {

    /** Canonical key of the matter-code predicate sememe. */
    public static final String KEY = "cg.bridge.keri:matter-code";

    private MatterCode() {}

    // ==================================================================================
    // Predicate vocabulary — gloss + lexeme for "matter code"
    // ==================================================================================

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "KERI's CESR matter code — the textual prefix identifying a "
                    + "cryptographic primitive's type and byte-length when "
                    + "encoded in CESR's qb64 (quad-base64) wire form";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "CESR matter code";

    // ==================================================================================
    // Code constants — each carries a @Seed.Frame asserting its (algorithm, code)
    // pairing into the graph, themed to the algorithm item.
    // ==================================================================================

    /** Ed25519 verification key, non-transferable (key IS the identifier; no rotation). */
    @Seed.Frame(predicate = MatterCode.KEY, theme = Signing.Ed25519.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY))
    public static final String ED25519_NT = "B";

    /** X25519 public encryption key. */
    @Seed.Frame(predicate = MatterCode.KEY, theme = KeyAgreement.X25519.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY))
    public static final String X25519 = "C";

    /** Ed25519 verification key, transferable (rotation-capable identifier). */
    @Seed.Frame(predicate = MatterCode.KEY, theme = Signing.Ed25519.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY))
    public static final String ED25519 = "D";

    /** Blake3-256 digest (KERI's default SAID algorithm). */
    @Seed.Frame(predicate = MatterCode.KEY, theme = Hash.Blake3.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY))
    public static final String BLAKE3_256 = "E";

    /** Blake2b-256 digest. */
    @Seed.Frame(predicate = MatterCode.KEY, theme = Hash.Blake2b_256.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY))
    public static final String BLAKE2B_256 = "F";

    /** SHA3-256 digest. */
    @Seed.Frame(predicate = MatterCode.KEY, theme = Hash.Sha3_256.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY))
    public static final String SHA3_256 = "H";

    /** SHA2-256 digest. */
    @Seed.Frame(predicate = MatterCode.KEY, theme = Hash.Sha256.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY))
    public static final String SHA2_256 = "I";

    /**
     * Ed25519 signature.  Same underlying algorithm as {@link #ED25519}; the
     * 2-char prefix encodes a 64-byte payload (signature) rather than a
     * 32-byte payload (key).  Wire-format role distinguishes the two.
     */
    @Seed.Frame(predicate = MatterCode.KEY, theme = Signing.Ed25519.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY))
    public static final String ED25519_SIG = "0B";

    // ==================================================================================
    // Per-code metadata — codec hot path
    // ==================================================================================

    /** Per-code metadata: raw payload length, the algorithm it identifies, and the
     *  multihash type that wraps its raw bytes when transposed into a CG ref. */
    public record Spec(int rawLength, ItemRef algorithm, Multihash.Type multihashType) {}

    private static final Map<String, Spec> SPECS;

    static {
        Map<String, Spec> m = new LinkedHashMap<>();
        m.put(ED25519_NT,  new Spec(32, ItemRef.iid(Signing.Ed25519.KEY),     Multihash.Type.id));
        m.put(X25519,      new Spec(32, ItemRef.iid(KeyAgreement.X25519.KEY), Multihash.Type.id));
        m.put(ED25519,     new Spec(32, ItemRef.iid(Signing.Ed25519.KEY),     Multihash.Type.id));
        m.put(BLAKE3_256,  new Spec(32, ItemRef.iid(Hash.Blake3.KEY),         Multihash.Type.blake3));
        m.put(BLAKE2B_256, new Spec(32, ItemRef.iid(Hash.Blake2b_256.KEY),    Multihash.Type.blake2b_256));
        m.put(SHA3_256,    new Spec(32, ItemRef.iid(Hash.Sha3_256.KEY),       Multihash.Type.sha3_256));
        m.put(SHA2_256,    new Spec(32, ItemRef.iid(Hash.Sha256.KEY),         Multihash.Type.sha2_256));
        m.put(ED25519_SIG, new Spec(64, ItemRef.iid(Signing.Ed25519.KEY),     null));
        SPECS = Map.copyOf(m);
    }

    /** Metadata for {@code code}, or {@code null} if the code is unknown. */
    public static Spec spec(String code) {
        return SPECS.get(code);
    }

    /** Raw byte length carried by {@code code}'s qb64 payload. */
    public static int rawLength(String code) {
        return require(code).rawLength;
    }

    /** Total qb64 string length for {@code code}: prefix + ceil(rawLength·4/3). */
    public static int qb64Length(String code) {
        return code.length() + ((require(code).rawLength * 4 + 2) / 3);
    }

    /** Algorithm item this code refers to (e.g. {@code cg.algorithm:sha2-256}). */
    public static ItemRef algorithm(String code) {
        return require(code).algorithm;
    }

    /**
     * Multihash type that semantically matches this code's raw bytes when
     * wrapped as a CG ref.  {@code null} for codes that don't have a
     * meaningful multihash representation (signatures, salts).
     */
    public static Multihash.Type multihashType(String code) {
        return require(code).multihashType;
    }

    /**
     * Identify the matter code prefixing a qb64 primitive string.  Codes
     * starting with {@code 0} are 2-char; codes starting with {@code 1} are
     * 4-char; everything else is 1-char.  Returns {@code null} if the prefix
     * doesn't match any known code.
     */
    public static String identify(String qb64) {
        if (qb64.isEmpty()) return null;
        char first = qb64.charAt(0);
        int prefixLen = first == '0' ? 2 : first == '1' ? 4 : 1;
        if (qb64.length() < prefixLen) return null;
        String prefix = qb64.substring(0, prefixLen);
        return SPECS.containsKey(prefix) ? prefix : null;
    }

    private static Spec require(String code) {
        Spec s = SPECS.get(code);
        if (s == null) throw new IllegalArgumentException("Unknown CESR matter code: " + code);
        return s;
    }
}
