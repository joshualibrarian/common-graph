package dev.everydaythings.graph.identity.algorithm;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.identity.AlgorithmVocabulary;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.librarian.Librarian;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Kdf — the sub-archetype for key-derivation function algorithms.
 *
 * <p>A KDF takes a shared secret (typically from a {@link KeyAgreement}
 * step) plus an info/salt and produces a fixed-length symmetric key suitable
 * for an {@link Aead} content cipher.  Used as the middle component of
 * hybrid encryption ciphersuites: <i>key-agreement → KDF → AEAD</i>.
 *
 * <p>Phase-A leaves are data-only.  The {@code derive(ikm, salt, info, length)}
 * runtime operation lands alongside the content-encryption work.
 */
@Seed.Item(key = Kdf.KEY, head = Algorithm.KEY)
public abstract class Kdf extends Algorithm {

    /** Canonical key for the KDF sub-archetype. */
    public static final String KEY = "cg.archetype:kdf-algorithm";

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String kdfGloss =
            "a key-derivation function — expands a shared secret plus info/salt "
                    + "into a fixed-length symmetric key for downstream content encryption";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String kdfLemma = "key derivation function";

    protected Kdf(ItemRef iid) {
        super(iid);
    }

    protected Kdf(ItemRef iid, Librarian librarian) {
        super(iid, librarian);
    }

    // -- Operations --

    /**
     * Derive {@code length} bytes of output keying material from input key
     * material plus salt and info.  Standard KDF shape: {@code OKM = derive(IKM, salt, info, L)}.
     * Each concrete KDF implements the algorithm-specific extract-and-expand
     * (or equivalent) flow.
     */
    public abstract byte[] derive(byte[] ikm, byte[] salt, byte[] info, int length);

    // ==================================================================================
    // Concrete KDF algorithms.
    // ==================================================================================

    /**
     * HKDF-SHA-256 — HMAC-based Extract-and-Expand Key Derivation Function with
     * SHA-256 as the underlying hash (RFC 5869).  The default KDF for hybrid
     * encryption ciphersuites using SHA-256.
     */
    @Seed.Item(key = HkdfSha256.KEY, head = Kdf.KEY)
    public static final class HkdfSha256 extends Kdf {

        public static final String KEY = "cg.algorithm:hkdf-sha-256";

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String hkdfGloss =
                "HKDF-SHA-256 — HMAC-based Extract-and-Expand Key Derivation Function "
                        + "with SHA-256 as the underlying hash (RFC 5869)";

        public HkdfSha256()                         { super(ItemRef.iid(KEY)); }
        public HkdfSha256(Librarian librarian)      { super(ItemRef.iid(KEY), librarian); }
        public HkdfSha256(ItemRef iid, Librarian l) { super(iid, l); }

        private static final String HMAC = "HmacSHA256";
        private static final int HASH_LEN = 32;

        /**
         * RFC 5869 Extract-and-Expand.  Empty or null salt is treated as 32
         * zero bytes per the spec; null info is treated as empty.
         */
        @Override
        public byte[] derive(byte[] ikm, byte[] salt, byte[] info, int length) {
            if (length < 0) throw new IllegalArgumentException("length must be non-negative");
            int n = (length + HASH_LEN - 1) / HASH_LEN;
            if (n > 255) {
                throw new IllegalArgumentException(
                        "HKDF length " + length + " exceeds 255 * HashLen for SHA-256");
            }
            try {
                Mac mac = Mac.getInstance(HMAC);

                // Extract: PRK = HMAC(salt, IKM)
                byte[] saltKey = (salt == null || salt.length == 0) ? new byte[HASH_LEN] : salt;
                mac.init(new SecretKeySpec(saltKey, HMAC));
                byte[] prk = mac.doFinal(ikm);

                // Expand: T(1..n), OKM = T(1) || T(2) || ...
                byte[] okm = new byte[length];
                byte[] previous = new byte[0];
                int offset = 0;
                SecretKeySpec prkKey = new SecretKeySpec(prk, HMAC);
                byte[] infoOrEmpty = info == null ? new byte[0] : info;
                for (int i = 1; i <= n; i++) {
                    mac.init(prkKey);
                    mac.update(previous);
                    mac.update(infoOrEmpty);
                    mac.update((byte) i);
                    previous = mac.doFinal();
                    int copyLen = Math.min(previous.length, length - offset);
                    System.arraycopy(previous, 0, okm, offset, copyLen);
                    offset += copyLen;
                }
                return okm;
            } catch (Exception e) {
                throw new RuntimeException("HKDF-SHA-256 derive failed", e);
            }
        }

        /**
         * Librarian-less factory.  HKDF-SHA-256 carries no per-instance
         * tunables; the static {@code derive} could equally be used.  The
         * factory exists for symmetry with the other algorithm leaves and
         * for callers that want a typed runtime instance.
         */
        public static HkdfSha256 builtin() {
            return new HkdfSha256();
        }
    }
}
