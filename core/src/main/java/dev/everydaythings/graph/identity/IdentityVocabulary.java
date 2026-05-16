package dev.everydaythings.graph.identity;


import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.language.*;
import dev.everydaythings.graph.language.ThematicRole;

/**
 * Identity vocabulary — the sememes and predicates whose meaning is tied to the
 * identity / key-management domain.
 *
 * <p>Two flavors of entries live here:
 * <ul>
 *   <li><b>Key-track purposes</b> — {@link Signing}, {@link Encryption}. The
 *       narrowings that scope a key-event chain (KEL) to a specific
 *       cryptographic purpose.</li>
 *   <li><b>Key-event predicates</b> — {@link Inception}, {@link Rotation},
 *       {@link Delegation}, {@link Revocation}. The frame heads that establish,
 *       advance, authorize, and retract identity-bearing assertions.</li>
 * </ul>
 *
 * <p>Each inner class is a bare seed declaration — KEY, IID, and {@code @Seed.Frame}
 * gloss/lexeme annotations. The predicates carry no behavior of their own;
 * body-reading helpers (extract committed keys, check self-attestation, read
 * sequence numbers, etc.) live on {@link Signer}, which is the natural home for
 * code that produces and consumes these frames.
 *
 * <p>General-purpose sememes used incidentally by identity frames
 * (Next/Threshold/Witness/Sequence/Expires, Compromise/Retirement/Fraud/Mistake,
 * Multikey) currently live in {@code semantics.CoreVocabulary} and the root
 * {@code CoreVocabulary} — they're applicable beyond identity even though they
 * happen to be exercised here. Migration into this file is a separate future
 * step if/when that scoping decision is revisited.
 */
public final class IdentityVocabulary {

    private IdentityVocabulary() {}

    // ==================================================================================
    // Key-track purposes
    // ==================================================================================

    /** The signing-key track — Ed25519-class keys used for signature attestation. */
    @Seed.Item(key = Signing.KEY)
    public static final class Signing {
        public static final String KEY = "cg.purpose:signing";
        private Signing() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the cryptographic signing-key track of an identity (e.g., Ed25519)";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "signing";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "sign";
    }

    /** The encryption-key track — X25519-class keys used for confidentiality. */
    @Seed.Item(key = Encryption.KEY)
    public static final class Encryption {
        public static final String KEY = "cg.purpose:encryption";
        private Encryption() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the cryptographic encryption-key track of an identity (e.g., X25519)";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "encryption";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "encrypt";
    }

    /**
     * The key-agreement / key-management track — primitives like X25519 ECDH or
     * RSA-OAEP whose purpose is to derive or wrap a content-encryption key, not
     * to encrypt content directly.  Distinct from {@link Encryption} (AEAD
     * ciphers that encrypt content) because the cryptographic shape and the
     * way they fit in a protocol differ.
     */
    @Seed.Item(key = KeyAgreement.KEY)
    public static final class KeyAgreement {
        public static final String KEY = "cg.purpose:key-agreement";
        private KeyAgreement() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the cryptographic key-agreement or key-wrap track — primitives that "
                        + "derive or wrap a content key (e.g., X25519 ECDH, RSA-OAEP), "
                        + "distinct from algorithms that encrypt content directly";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "key-agreement";
    }

    // ==================================================================================
    // Key-event predicates (KEL members)
    // ==================================================================================

    /**
     * INCEPTION — the founding key-state declaration for one identity's one
     * key-track. Self-anchored: the body's records must include at least one
     * signature from the keys committed in the body (see
     * {@link Signer#isSelfAttested(dev.everydaythings.graph.datum.Frame)}).
     * Forward-committing via optional {@code INSTRUMENT [NEXT]} bindings.
     *
     * <p>Body shape: THEME→@identity, PURPOSE→@track, INSTRUMENT[MULTIKEY]→keys,
     * INSTRUMENT[NEXT]→digests, ATTRIBUTE[THRESHOLD]→m-of-n,
     * PARTNER[WITNESS]→witnesses (optional), AGENT[DELEGATOR]→parent (optional),
     * TIME→timestamp.
     */
    @Seed.Item(key = Inception.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Inception {
        public static final String KEY = "cg.sememe:inception";
        private Inception() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the founding key-state declaration for one identity's one key-track";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "inception";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "incept";
    }

    /**
     * ROTATION — the next establishment event in an identity's KEL for one
     * key-track. Replaces the current key-set, reveals the preimage of the prior
     * pre-rotation commitment, publishes a fresh commitment for the next round.
     * Chain-anchored via {@code FOLLOWS → #prior-event-CID}; authority-asymmetric
     * (authorized by old keys, proven by new keys — both sign the body).
     *
     * <p>Body shape: INCEPTION shape + FOLLOWS→#prior + ATTRIBUTE[SEQUENCE]→n+1.
     */
    @Seed.Item(key = Rotation.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Rotation {
        public static final String KEY = "cg.sememe:rotation";
        private Rotation() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "evolving an identity's committed keys for one key-track, with pre-rotation reveal";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "rotation";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "rotate";
    }

    /**
     * DELEGATION — a parent identity's authorization for a child identity to
     * operate under the parent's authority. Parent-issued (the child consumes,
     * doesn't issue); scope-bearing via optional PURPOSE multiset bindings;
     * revocable and optionally expirable via {@code ATTRIBUTE [EXPIRES]}.
     *
     * <p>Body shape: AGENT→@parent, THEME→@child, PURPOSE→scope (multiset),
     * ATTRIBUTE[EXPIRES]→timestamp (optional), TIME→when-issued.
     */
    @Seed.Item(key = Delegation.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Delegation {
        public static final String KEY = "cg.sememe:delegation";
        private Delegation() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "a parent identity's authorization for a child identity to operate under its authority";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "delegation";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "delegate";
    }

    /**
     * REVOCATION — the generic "I take it back" — a withdrawal of any prior
     * assertion. Polymorphic via target type: identity IIDs (retire whole
     * identity), event CIDs (repudiate a specific event), delegation CIDs
     * (withdraw authorization), trust CIDs (withdraw trust), arbitrary frame CIDs
     * (retract claims). Authority via signer: the record signer must have
     * authority over the revoked thing.
     *
     * <p>Body shape: THEME→polymorphic-target,
     * PURPOSE→reason-sememe (optional, e.g. Compromise/Retirement/Fraud/Mistake),
     * TIME→timestamp, plus arbitrary contextual bindings (VALUE for free-text
     * reason, etc.).
     *
     * <p>Recovery / un-revocation: revocation is terminal. To "come back" after
     * identity revocation, incept a new identity.
     */
    @Seed.Item(key = Revocation.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Revocation {
        public static final String KEY = "cg.sememe:revocation";
        private Revocation() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the generic withdrawal of any prior assertion — \"I take it back\"";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "revocation";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "revoke";
    }

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
    // bytes or key-state-chain forward references.
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
     * Forward reference — the "next" of something committed by digest before reveal.
     *
     * <p>Used on {@code INSTRUMENT [NEXT]} bindings to mark a pre-rotation
     * commitment: the binding's target is the content-hash digest of the next
     * public key, revealed on the next ROTATION. Generic enough to apply
     * outside identity (any digest-before-reveal commitment), but currently
     * exercised in KEL events.
     */
    @Seed.Item(key = Next.KEY)
    public static final class Next {
        public static final String KEY = "cg.sememe:next";
        private Next() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the one immediately following in sequence";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Adjective.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishAdjectiveLemma = "next";
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
    // Reason sememes — formal causes for revocation/retraction. Used as PURPOSE
    // on REVOCATION bodies. Generic enough that they apply to non-identity
    // retractions too (a fraudulent claim, a mistaken assertion), but currently
    // the cleanest home is here alongside REVOCATION itself.
    // ==================================================================================

    /** Compromise — an exposure or breach (cryptographic, structural, or social). */
    @Seed.Item(key = Compromise.KEY)
    public static final class Compromise {
        public static final String KEY = "cg.sememe:compromise";
        private Compromise() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "an exposure or breach — cryptographic, structural, or social";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "compromise";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "compromise";
    }

    /** Retirement — routine cessation of use; no incident. */
    @Seed.Item(key = Retirement.KEY)
    public static final class Retirement {
        public static final String KEY = "cg.sememe:retirement";
        private Retirement() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "routine cessation of use or service; no incident, just no longer active";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "retirement";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "retire";
    }

    /** Fraud — deceit, intentional misrepresentation. */
    @Seed.Item(key = Fraud.KEY)
    public static final class Fraud {
        public static final String KEY = "cg.sememe:fraud";
        private Fraud() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "deceit; intentional misrepresentation";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "fraud";
    }

    /** Mistake — an honest error, no malice. */
    @Seed.Item(key = Mistake.KEY)
    public static final class Mistake {
        public static final String KEY = "cg.sememe:mistake";
        private Mistake() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "an honest error; no malice intended";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "mistake";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "mistake";
    }

    // ==================================================================================
    // Cryptographic algorithm suites — whole-suite sememes identifying a specific
    // ciphersuite bundle (KEM + AEAD + KDF). Whole-suite (not piece-by-piece) to
    // minimize misconfiguration surface. New suites added as separate inner
    // classes as needed.
    // ==================================================================================

    /**
     * X25519 ECDH key agreement + HKDF-SHA256 key derivation + AES-256-GCM AEAD.
     *
     * <p>The default ciphersuite for ENCRYPT bodies. Used as
     * {@code INSTRUMENT → @ItemRef.iid(X25519_AES256GCM_HKDF.KEY)} to name this suite.
     *
     * <p>Concretely: sender generates ephemeral X25519 keypair; for each
     * recipient performs ECDH against the recipient's long-term encryption-track
     * X25519 pubkey to derive a shared secret; runs HKDF-SHA256 to derive a key
     * wrapping key (KEK); encrypts the data encryption key (DEK) with that KEK;
     * encrypts the cleartext with the DEK using AES-256-GCM (which includes its
     * own AEAD authentication tag).
     */
    @Seed.Item(key = X25519_AES256GCM_HKDF.KEY)
    public static final class X25519_AES256GCM_HKDF {
        public static final String KEY = "cg.algo:x25519-aes256gcm-hkdf-sha256";
        private X25519_AES256GCM_HKDF() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "X25519 ECDH key agreement + HKDF-SHA256 key derivation + AES-256-GCM AEAD "
                        + "ciphersuite for hybrid encryption with per-recipient key wrap";
    }

    /** Delegator — one who delegates authority, responsibility, or a task. */
    @Seed.Item(key = Delegator.KEY)
    public static final class Delegator {
        public static final String KEY = "cg.sememe:delegator";
        private Delegator() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "one who delegates authority, responsibility, or a task";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "delegator";
    }

}
