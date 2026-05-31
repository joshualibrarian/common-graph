package dev.everydaythings.graph.cryptography.algorithm;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.PartOfSpeech;
import dev.everydaythings.graph.ThematicRole;
import dev.everydaythings.graph.runtime.librarian.Librarian;

/**
 * Ciphersuite — the sub-archetype for whole-suite cryptographic bundles
 * combining a key-agreement step, a key-derivation step, and an AEAD content
 * cipher into one named primitive.
 *
 * <p>Whole-suite (not piece-by-piece) sememes minimize misconfiguration
 * surface: a single {@code INSTRUMENT → @suite-iid} binding on an ENCRYPT
 * body names the full composition.  Concrete suites are pure-data references
 * pointing at their component algorithm sememes (KeyAgreement, KDF, AEAD).
 *
 * <p>Phase-A: data declarations only.  The {@code encrypt} / {@code decrypt}
 * runtime operations on Ciphersuite (which dispatch through the components)
 * land when the content-encryption story comes online.
 */
@Seed.Item(key = Ciphersuite.KEY, head = Algorithm.KEY)
public abstract class Ciphersuite extends Algorithm {

    /** Canonical key for the ciphersuite sub-archetype. */
    public static final String KEY = "cg.archetype:ciphersuite";

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String suiteGloss =
            "a named composition of cryptographic primitives — key-agreement + KDF + AEAD — "
                    + "bundled as a single sememe to name a complete encryption recipe";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String suiteLemma = "ciphersuite";

    protected Ciphersuite(ItemRef iid) {
        super(iid);
    }

    protected Ciphersuite(ItemRef iid, Librarian librarian) {
        super(iid, librarian);
    }

    // ==================================================================================
    // Concrete ciphersuites.
    // ==================================================================================

    /**
     * X25519 ECDH key agreement + HKDF-SHA-256 key derivation + AES-256-GCM AEAD.
     *
     * <p>The default ciphersuite for ENCRYPT bodies (see
     * {@link dev.everydaythings.graph.cryptography.EncryptionVocabulary.Encrypt}).
     * Used as {@code INSTRUMENT → @ItemRef.iid(X25519_AesGcm256_Hkdf256.KEY)}
     * to name this suite.
     *
     * <p>Concretely: sender generates ephemeral X25519 keypair; for each
     * recipient performs ECDH against the recipient's long-term encryption-track
     * X25519 pubkey to derive a shared secret; runs HKDF-SHA256 to derive a key
     * wrapping key (KEK); encrypts the data encryption key (DEK) with that KEK;
     * encrypts the cleartext with the DEK using AES-256-GCM (which includes its
     * own AEAD authentication tag).
     */
    @Seed.Item(key = X25519_AesGcm256_Hkdf256.KEY, head = Ciphersuite.KEY)
    public static final class X25519_AesGcm256_Hkdf256 extends Ciphersuite {

        public static final String KEY = "cg.algorithm:x25519-aes256gcm-hkdf-sha256";

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "X25519 ECDH key agreement + HKDF-SHA256 key derivation + AES-256-GCM AEAD "
                        + "ciphersuite for hybrid encryption with per-recipient key wrap";

        public X25519_AesGcm256_Hkdf256()                         { super(ItemRef.iid(KEY)); }
        public X25519_AesGcm256_Hkdf256(Librarian librarian)      { super(ItemRef.iid(KEY), librarian); }
        public X25519_AesGcm256_Hkdf256(ItemRef iid, Librarian l) { super(iid, l); }
    }
}
