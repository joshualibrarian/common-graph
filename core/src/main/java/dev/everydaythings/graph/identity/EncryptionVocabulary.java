package dev.everydaythings.graph.identity;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;

/**
 * Encryption-flow vocabulary — the sememes and predicates whose meaning is
 * tied to <i>using</i> cryptographic primitives (encrypting content for
 * recipients, wrapping keys, declaring ephemeral material), rather than to
 * the identities and key-tracks themselves (which live in
 * {@link IdentityVocabulary}) or to the algorithms themselves (which live in
 * {@link AlgorithmVocabulary}).
 *
 * <p>The clean separation:
 * <ul>
 *   <li><b>{@link AlgorithmVocabulary}</b> — the algorithm sememes themselves
 *       (Ed25519, X25519, AES-GCM, etc.) plus their metadata.</li>
 *   <li><b>{@link IdentityVocabulary}</b> — identities, key tracks, key-event
 *       predicates (Inception / Rotation / Delegation / Revocation /
 *       Attestation), forward-reference qualifiers (Next).</li>
 *   <li><b>{@code EncryptionVocabulary}</b> (this file) — encryption-flow
 *       primitives: the ENCRYPT event predicate plus the qualifiers that
 *       narrow binding slots inside ENCRYPT bodies (Multikey, Keywrap,
 *       EphemeralPubkey).</li>
 * </ul>
 *
 * <p>Items here were extracted from {@link IdentityVocabulary} during the
 * 2026-05-17 crypto-vocabulary reorganization (see
 * {@code docs/crypto-vocabulary-assessment.md}).
 */
public final class EncryptionVocabulary {

    private EncryptionVocabulary() {}

    // ==================================================================================
    // ENCRYPT — the meaningful event that some bytes were encrypted for recipients
    // ==================================================================================

    /**
     * ENCRYPT — a meaningful event asserting that some bytes have been encrypted
     * for specific recipients using a specific algorithm. The cipher bytes
     * themselves are opaque content (typically Tag-10-wrapped); this body carries
     * all the metadata needed to decrypt: recipients, algorithm, key wraps,
     * ephemeral keys, time. A record signs this body normally.
     *
     * <p>The fact of encryption is meaningful (who encrypted what for whom, when);
     * the cipher bytes are pure mechanics. The body captures the meaningful event;
     * the bytes live alongside as a content-addressed opaque blob.
     *
     * <p>Body shape (typical):
     * <pre>
     * ENCRYPT
     *     [optional] AGENT → @signer-iid                  # omit for anonymous
     *     THEME → ~cipher-cid                              # the encrypted bytes
     *     BENEFICIARY → @recipient-iid                     # one per recipient (multiset)
     *     INSTRUMENT → @algorithm-suite-sememe             # which ciphersuite
     *     INSTRUMENT [EPHEMERAL_PUBKEY] → bytes            # sender's ephemeral X25519 pubkey
     *     INSTRUMENT [KEYWRAP, @recipient-iid] → bytes     # wrapped DEK per recipient
     *     TIME → timestamp                                 # when encrypted
     * </pre>
     *
     * <p>Granularity is a per-use choice — encrypt the smallest unit needed,
     * whether a single binding value (inline Tag-10), a whole frame, or an
     * entire item. The ENCRYPT body is the same primitive at all granularities.
     */
    @Seed.Item(key = Encrypt.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Encrypt {
        public static final String KEY = "cg.sememe:encrypt";
        private Encrypt() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "an event asserting that some bytes have been encrypted for specific recipients";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "encryption";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "encrypt";
    }

    // ==================================================================================
    // Key-material qualifiers — narrowings applied to bindings that carry key
    // bytes inside ENCRYPT bodies (and anywhere a key lives in data).
    // ==================================================================================

    /**
     * Qualifier marking a binding's bytes target as a multikey-encoded public key
     * (varint codec prefix + raw key material).
     *
     * <p>Used on {@code INSTRUMENT} bindings inside INCEPTION and ROTATION frames
     * to commit current keys, and anywhere a key lives in data. The target is
     * plain bytes; the qualifier identifies "these bytes are a multikey-format key."
     */
    @Seed.Item(key = Multikey.KEY)
    public static final class Multikey {
        public static final String KEY = "cg.value:multikey";
        private Multikey() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "qualifier marking a binding's bytes target as a multikey-encoded "
                        + "public key (varint codec prefix + raw key material)";
    }

    /**
     * Qualifier marking a binding's bytes target as a wrapped (encrypted) data
     * encryption key (DEK) for a specific recipient.
     *
     * <p>Used on {@code INSTRUMENT [Keywrap, @recipient-iid] → bytes} bindings
     * inside ENCRYPT bodies. The bytes are the DEK encrypted under a key
     * derived from ECDH between the sender's ephemeral key and the recipient's
     * long-term encryption-track pubkey. The recipient qualifier identifies
     * which keywrap is for whom.
     */
    @Seed.Item(key = Keywrap.KEY)
    public static final class Keywrap {
        public static final String KEY = "cg.value:keywrap";
        private Keywrap() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "qualifier marking a binding's bytes target as a wrapped data encryption key "
                        + "(DEK) for a specific recipient";
    }

    /**
     * Qualifier marking a binding's bytes target as a sender's ephemeral public
     * key for ECDH key agreement (X25519 or similar).
     *
     * <p>Used on a single {@code INSTRUMENT [EphemeralPubkey] → bytes} binding
     * per ENCRYPT body. Recipients combine this with their own long-term
     * private key to derive the shared secret that unwraps the DEK. Ephemeral
     * per-encryption-event keys give forward secrecy: compromise of long-term
     * keys later cannot decrypt past messages.
     */
    @Seed.Item(key = EphemeralPubkey.KEY)
    public static final class EphemeralPubkey {
        public static final String KEY = "cg.value:ephemeral-pubkey";
        private EphemeralPubkey() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "qualifier marking a binding's bytes target as a sender's ephemeral public "
                        + "key for ECDH key agreement (gives forward secrecy)";
    }
}
