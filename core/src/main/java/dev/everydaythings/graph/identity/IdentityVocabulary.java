package dev.everydaythings.graph.identity;


import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.language.*;
import dev.everydaythings.graph.language.ThematicRole;

/**
 * Identity vocabulary — the sememes and predicates whose meaning is tied to
 * the identity / key-management domain.
 *
 * <p>Sibling crypto-domain vocabularies:
 * <ul>
 *   <li>{@link AlgorithmVocabulary} — the algorithm sememes themselves
 *       (Ed25519, X25519, AES-GCM, ciphersuites) plus their metadata.</li>
 *   <li>{@link EncryptionVocabulary} — encryption-flow primitives (the
 *       ENCRYPT event + the qualifiers that narrow binding slots inside
 *       ENCRYPT bodies: Multikey, Keywrap, EphemeralPubkey).</li>
 *   <li>This file — identities, key tracks, key-event predicates (Inception,
 *       Rotation, Delegation, Revocation), forward-reference qualifiers
 *       (Next), the Delegator role.</li>
 * </ul>
 *
 * <p>Each inner class is a bare seed declaration — KEY, IID, and
 * {@code @Seed.Frame} gloss/lexeme annotations.  The predicates carry no
 * behavior of their own; body-reading helpers (extract committed keys,
 * check self-attestation, read sequence numbers, etc.) live on
 * {@link Signer}, which is the natural home for code that produces and
 * consumes these frames.
 *
 * <p>Reason sememes (Compromise / Retirement / Fraud / Mistake), historically
 * declared here for use by REVOCATION, now live in
 * {@link CoreVocabulary} since they apply equally well to non-identity
 * retractions.
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
     * ATTESTATION — a third party's signed assertion about another identity's
     * key binding.  The body declares subject + key + purpose + validity;
     * the record's signer is the <i>attester</i> rather than the subject.
     *
     * <p>Together, body + record form a {@link dev.everydaythings.graph.datum.Frame
     * Frame} that IS the certificate.  No separate cert archetype exists in
     * CG: an X.509 cert from PKI ingests as an Attestation frame whose
     * attester is the issuing CA; a CG-native peer attestation is an
     * Attestation frame whose attester is a peer; a self-signed AID cert
     * uses {@link Inception} (the self-attested key binding) instead.
     *
     * <p>Trust resolution walks Attestation records from a peer's pubkey
     * back to a trusted anchor (a directly-trusted IID, or a trusted root
     * cert ingested as an Attestation).
     *
     * <p>Body shape:
     * <pre>
     * ATTESTATION
     *     AGENT     → @attester-iid                # the signer (CA, peer, self for inception)
     *     THEME     → @subject-iid                 # whose key is being attested
     *     INSTRUMENT → key-bytes                   # subject's pubkey (multikey-encoded — self-describing)
     *     PURPOSE   → @signing / @encryption / ...  # the key's cryptographic purpose
     *     ATTRIBUTE [VALIDITY_FROM]  → instant     # lower bound (maps to X.509 notBefore)
     *     ATTRIBUTE [VALIDITY_UNTIL] → instant     # upper bound (maps to X.509 notAfter)
     *     TIME → timestamp                         # when issued
     * </pre>
     *
     * <p>{@code PURPOSE} is a top-level thematic role (see
     * {@link ThematicRole.Purpose}), not a qualifier inside INSTRUMENT.  The
     * key bytes carried by INSTRUMENT are inherently multikey-encoded (their
     * varint codec prefix self-describes the key type), so no Multikey
     * qualifier is needed for the single-INSTRUMENT case.  Inception, which
     * carries multiple INSTRUMENT bindings (current + pre-rotation next),
     * does use {@code INSTRUMENT [MULTIKEY]} / {@code [NEXT]} qualifiers
     * to distinguish them.
     *
     * <p>Difference from {@link Inception}: Inception is the founding
     * self-attested event on a KEL chain — sequence=1, FOLLOWS empty, record
     * signature MUST come from the committed keys themselves.  Attestation
     * is third-party — no chain semantics; the record is signed by AGENT
     * (the attester), normally distinct from THEME (the subject).
     *
     * <h2>X.509 ingestion modes (future-compatible)</h2>
     *
     * <p>The v1 ingestion path produces an Attestation Frame whose record is
     * the local Librarian's "I verified this at time T" attestation, having
     * verified the X.509 chain via standard PKIX (JSSE).  This loses the
     * issuer's cryptographic signature at the CG layer but keeps the body
     * shape clean.
     *
     * <p>The body shape <i>leaves room</i> for richer modes without breaking
     * change: an optional {@code SOURCE → ~original-der-cid} binding can
     * preserve the original X.509 DER bytes as an opaque payload; additional
     * Records on the same Frame can carry the original issuer's signature
     * over those preserved bytes.  Trust verification can then check either
     * the local Librarian's attestation, the original issuer's PKI
     * signature, or both — additive, not breaking.
     */
    @Seed.Item(key = Attestation.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Attestation {
        public static final String KEY = "cg.sememe:attestation";
        private Attestation() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "a third party's signed assertion binding a key (or other property) "
                        + "to a subject identity";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "attestation";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "attest";
    }

    // ==================================================================================
    // Validity-window qualifiers — used inside ATTESTATION bodies (and any
    // other binding whose temporal validity needs to be declared structurally
    // rather than left implicit).
    // ==================================================================================

    /**
     * Lower bound of a validity window.  Used as
     * {@code ATTRIBUTE [VALIDITY_FROM] → instant}.  Maps to X.509
     * {@code notBefore} when ingesting / emitting certs.
     */
    @Seed.Item(key = ValidityFrom.KEY)
    public static final class ValidityFrom {
        public static final String KEY = "cg.sememe:validity-from";
        private ValidityFrom() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "lower bound of a validity window (the instant from which the assertion holds)";
    }

    /**
     * Upper bound of a validity window.  Used as
     * {@code ATTRIBUTE [VALIDITY_UNTIL] → instant}.  Maps to X.509
     * {@code notAfter} when ingesting / emitting certs.
     */
    @Seed.Item(key = ValidityUntil.KEY)
    public static final class ValidityUntil {
        public static final String KEY = "cg.sememe:validity-until";
        private ValidityUntil() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "upper bound of a validity window (the instant after which the assertion no longer holds)";
    }

    // ==================================================================================
    // Forward-reference qualifier — used inside KEL events for pre-rotation
    // commitments.  Generic enough to apply to other digest-before-reveal
    // commitments but currently only exercised here.
    // ==================================================================================

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

    // ==================================================================================
    // Roles
    // ==================================================================================

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
