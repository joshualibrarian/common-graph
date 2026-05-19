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
                "an event asserting that some bytes are to be (or have been) encrypted for "
                        + "specific recipients; used both as a command verb (do this encryption) "
                        + "and as a record of one that has happened";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "encryption";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "encrypt";
    }

    /**
     * DECRYPT — the mirror of {@link Encrypt} as a command verb: an event
     * asserting that an encrypted frame is to be decrypted.  Dispatched to
     * the handler on the recipient's {@link
     * dev.everydaythings.graph.identity.Signer Signer}.
     *
     * <p>Body shape (command form):
     * <pre>
     * DECRYPT
     *     AGENT → @signer-iid       # the signer doing the decryption (their vault)
     *     THEME → @encrypted-ref    # the encrypted Frame (or its body) to decrypt
     *     TIME  → timestamp         # when the command was issued
     * </pre>
     *
     * <p>The handler reads the encrypted Frame, looks up the session with the
     * sender (auto-bootstrapping via INITIATOR_* bindings on first message),
     * and returns the recovered plaintext bytes through
     * {@link dev.everydaythings.graph.item.Item#receive Item.receive(Frame)}.
     * Plaintext is not republished as a body — the caller decides what to do
     * with it.
     */
    @Seed.Item(key = Decrypt.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Decrypt {
        public static final String KEY = "cg.sememe:decrypt";
        private Decrypt() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "a command asserting that an encrypted frame is to be decrypted by the "
                        + "named agent; mirror of encrypt as a command verb";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "decryption";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "decrypt";
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

    // ==================================================================================
    // Per-record encryption metadata
    //
    // When a body is wrapped in Opaque.Encrypted, the accompanying record's
    // bindings describe the encryption: which method was used, and (per
    // method) the parameters needed to decrypt.  The record's head points at
    // the ciphertext; the bindings here are the metadata.
    //
    // The bindings list itself is the AEAD authenticator's associated-data
    // source: AD = canonical hash of the bindings list, computed identically
    // on both sides via {@link dev.everydaythings.graph.canonical.HashTree
    // HashTree.hashOf}.  Any tampering with bindings changes the hash and
    // breaks AEAD verification.
    // ==================================================================================

    /**
     * METHOD — the encryption method used to produce a ciphertext.  Lives on a
     * binding inside an encryption-metadata record.  The value identifies
     * which scheme (e.g., {@link DoubleRatchetV1}); method-specific bindings
     * follow.
     */
    @Seed.Item(key = Method.KEY)
    public static final class Method {
        public static final String KEY = "cg.role:method";
        private Method() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the encryption method used to produce a ciphertext; identifies the scheme "
                        + "(e.g., double-ratchet-v1) and signals which other bindings to expect";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "method";
    }

    /**
     * DoubleRatchetV1 — the Signal-derived Double-Ratchet scheme as
     * implemented in {@link dev.everydaythings.graph.identity.DoubleRatchet}:
     * X25519 DH ratchet, AES-GCM-256 AEAD, HKDF-SHA-256 KDF, HMAC-SHA-256
     * symmetric chain step.  X3DH for initial-key-agreement (no OTPK in v1).
     */
    @Seed.Item(key = DoubleRatchetV1.KEY)
    public static final class DoubleRatchetV1 {
        public static final String KEY = "cg.method:double-ratchet-v1";
        private DoubleRatchetV1() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "Double-Ratchet v1: X25519 DH ratchet + AES-GCM-256 AEAD + HKDF-SHA-256, "
                        + "with X3DH for initial-key-agreement";
    }

    /**
     * SENDER_RATCHET_KEY — the sender's current DH ratchet public key (raw
     * X25519 multikey bytes).  Recipients use this to advance their receive
     * chain when the sender ratchets.
     */
    @Seed.Item(key = SenderRatchetKey.KEY)
    public static final class SenderRatchetKey {
        public static final String KEY = "cg.role:sender-ratchet-key";
        private SenderRatchetKey() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the sender's current DH ratchet public key on a Double-Ratchet message";
    }

    /**
     * PREVIOUS_CHAIN_LENGTH — the number of messages in the sender's
     * previous send chain.  Recipients use this to know how many message
     * keys to skip when the sender ratchets the DH key.
     */
    @Seed.Item(key = PreviousChainLength.KEY)
    public static final class PreviousChainLength {
        public static final String KEY = "cg.role:previous-chain-length";
        private PreviousChainLength() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the number of messages in the sender's previous send chain on a Double-Ratchet message";
    }

    /**
     * MESSAGE_NUMBER — the index of this message within the sender's
     * current send chain (zero-based, increments per message until the
     * sender DH-ratchets).
     */
    @Seed.Item(key = MessageNumber.KEY)
    public static final class MessageNumber {
        public static final String KEY = "cg.role:message-number";
        private MessageNumber() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the zero-based index of this message within the sender's current send chain";
    }

    /**
     * INITIATOR_IDENTITY_KEY — present on a Double-Ratchet message only when
     * the message bootstraps a new session via X3DH: the initiator's
     * long-term identity (X25519) public key as raw multikey bytes.  The
     * responder feeds this into X3DH's responder path to derive the same
     * shared secret the initiator did.
     */
    @Seed.Item(key = InitiatorIdentityKey.KEY)
    public static final class InitiatorIdentityKey {
        public static final String KEY = "cg.role:initiator-identity-key";
        private InitiatorIdentityKey() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the initiator's long-term identity public key on a Double-Ratchet "
                        + "session-bootstrap message; present only on the first message";
    }

    /**
     * INITIATOR_EPHEMERAL_KEY — present on a Double-Ratchet message only
     * when the message bootstraps a new session via X3DH: the initiator's
     * fresh ephemeral (X25519) public key as raw multikey bytes.  The
     * responder feeds this into X3DH's responder path.
     */
    @Seed.Item(key = InitiatorEphemeralKey.KEY)
    public static final class InitiatorEphemeralKey {
        public static final String KEY = "cg.role:initiator-ephemeral-key";
        private InitiatorEphemeralKey() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the initiator's freshly-minted ephemeral public key on a Double-Ratchet "
                        + "session-bootstrap message; present only on the first message";
    }

    /**
     * CONSUMED_PRE_KEY — present on a Double-Ratchet bootstrap message when
     * the initiator consumed one of the responder's published one-time
     * pre-keys in X3DH.  Names the specific OTPK (by its raw pubkey bytes)
     * so the responder can find the matching private key in their vault
     * and destroy it after use.
     */
    @Seed.Item(key = ConsumedPreKey.KEY)
    public static final class ConsumedPreKey {
        public static final String KEY = "cg.role:consumed-pre-key";
        private ConsumedPreKey() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the responder's one-time pre-key (X25519 pubkey bytes) that the "
                        + "initiator consumed in X3DH; present only on bootstrap messages "
                        + "where OTPK was used";
    }
}
