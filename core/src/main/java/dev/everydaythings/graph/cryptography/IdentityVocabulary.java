package dev.everydaythings.graph.cryptography;


import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.GrammaticalFeature;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.language.*;
import dev.everydaythings.graph.ThematicRole;
import dev.everydaythings.graph.ref.TypeRef;

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
 *       (Next), the Delegator role, plus the cross-identity bridge
 *       predicates {@link Represents} (Signer ↔ social entity) and
 *       {@link Serves} (Signer ↔ Signer).</li>
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
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, dev.everydaythings.graph.PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "signing";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, dev.everydaythings.graph.PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "sign";
    }

    /**
     * The key-agreement track — primitives like X25519 ECDH or RSA-OAEP whose
     * purpose is to derive or wrap a content-encryption key, not to encrypt
     * content directly.  Long-term keypair lives on this Vault track; the
     * derived symmetric key is consumed by an AEAD content cipher (no
     * separate Vault track for AEAD — the symmetric key is ephemeral).
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
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, dev.everydaythings.graph.PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "key-agreement";
    }

    /**
     * The encryption track — primitives whose purpose is to encrypt content
     * directly (rather than to derive or wrap a key, which is
     * {@link KeyAgreement}).  Used to mark a keypair whose role is encrypting
     * material end-to-end for the holder (e.g., an OpenPGP encryption sub-key
     * paired with a separate signing sub-key under the same primary identity).
     *
     * <p>Distinct from {@link KeyAgreement} because some protocols separate the
     * key-derivation step from the encryption step at the protocol level (PGP
     * encryption sub-keys, hybrid encryption schemes); a single CG identity
     * may carry separate {@code KEY_AGREEMENT} and {@code ENCRYPTION} keypairs
     * for different protocol slots.
     */
    @Seed.Item(key = Encryption.KEY)
    public static final class Encryption {
        public static final String KEY = "cg.purpose:encryption";
        private Encryption() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the cryptographic encryption track — primitives that encrypt "
                        + "content directly (as distinct from key-agreement, which "
                        + "derives keys other algorithms use to encrypt)";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, dev.everydaythings.graph.PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "encryption";
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
     * <p>Body shape (resulting event): THEME→@identity, PURPOSE→@track,
     * INSTRUMENT[MULTIKEY]→keys, INSTRUMENT[NEXT]→digests,
     * ATTRIBUTE[THRESHOLD]→m-of-n, PARTNER[WITNESS]→witnesses (optional),
     * AGENT[DELEGATOR]→parent (optional), TIME→timestamp.
     *
     * <p>Command body shape (what the handler reads):
     * <pre>
     * INCEPTION
     *     AGENT → @signer                  # the signer to incept on (handler-routed)
     *     PURPOSE → @key-track             # which track to incept (signing, etc.)
     *     [optional] CAUSE → @reference   # structured reason
     *     [optional] ATTRIBUTE[COMMENT] → "text"   # free-form annotation
     * </pre>
     * The vault generates the keypair and produces the full event frame.
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
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, dev.everydaythings.graph.PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "inception";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, dev.everydaythings.graph.PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "incept";

        /** Which key-track to incept (signing / encryption / key-agreement). */
        @Seed.Property(schemaRole = ThematicRole.Purpose.KEY)
        static final TypeRef expectsPurpose = TypeRef.iid(Item.KEY);
    }

    /**
     * ROTATION — the next establishment event in an identity's KEL for one
     * key-track. Replaces the current key-set, reveals the preimage of the prior
     * pre-rotation commitment, publishes a fresh commitment for the next round.
     * Chain-anchored via {@code FOLLOWS → #prior-event-CID}; authority-asymmetric
     * (authorized by old keys, proven by new keys — both sign the body).
     *
     * <p>Body shape (resulting event): INCEPTION shape + FOLLOWS→#prior +
     * ATTRIBUTE[SEQUENCE]→n+1.
     *
     * <p>Command body shape (what the handler reads):
     * <pre>
     * ROTATION
     *     AGENT → @signer                  # the signer to rotate (handler-routed)
     *     PURPOSE → @key-track             # which track to rotate
     *     [optional] CAUSE → @reference   # structured reason (e.g. a breach incident)
     *     [optional] ATTRIBUTE[COMMENT] → "text"   # free-form annotation
     * </pre>
     * The vault reveals the pre-committed next key and commits a fresh next.
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
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, dev.everydaythings.graph.PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "rotation";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, dev.everydaythings.graph.PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "rotate";

        /** Which key-track to rotate. */
        @Seed.Property(schemaRole = ThematicRole.Purpose.KEY)
        static final TypeRef expectsPurpose = TypeRef.iid(Item.KEY);
    }

    /**
     * DELEGATION — a parent identity's authorization for a child identity to
     * operate under the parent's authority. Parent-issued (the child consumes,
     * doesn't issue); scope-bearing via optional PURPOSE multiset bindings;
     * revocable and optionally expirable via {@code ATTRIBUTE [EXPIRES]}.
     *
     * <p>Body shape (resulting event): AGENT→@parent, THEME→@child,
     * PURPOSE→scope (multiset), ATTRIBUTE[EXPIRES]→timestamp (optional),
     * TIME→when-issued.
     *
     * <p>Command body shape (what the handler reads):
     * <pre>
     * DELEGATION
     *     AGENT → @signer                  # the delegating signer (handler-routed)
     *     RECIPIENT → @delegate            # the party receiving authority
     *     PURPOSE → @key-track             # which track / scope is being delegated
     *     [optional] ATTRIBUTE[EXPIRES] → timestamp  # time-bounded delegation
     *     [optional] CAUSE → @reference   # structured reason
     *     [optional] ATTRIBUTE[COMMENT] → "text"   # free-form annotation
     * </pre>
     * The vault mints the delegation evidence.
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
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, dev.everydaythings.graph.PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "delegation";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, dev.everydaythings.graph.PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "delegate";

        /** The party receiving the delegation. */
        @Seed.Property(schemaRole = ThematicRole.Recipient.KEY)
        static final TypeRef expectsRecipient = TypeRef.iid(Item.KEY);

        /** Which track / scope is being delegated. */
        @Seed.Property(schemaRole = ThematicRole.Purpose.KEY)
        static final TypeRef expectsPurpose = TypeRef.iid(Item.KEY);
    }

    /**
     * REVOCATION — the generic "I take it back" — a withdrawal of any prior
     * assertion. Polymorphic via target type: identity IIDs (retire whole
     * identity), event CIDs (repudiate a specific event), delegation CIDs
     * (withdraw authorization), trust CIDs (withdraw trust), arbitrary frame CIDs
     * (retract claims). Authority via signer: the record signer must have
     * authority over the revoked thing.
     *
     * <p>Body shape (resulting event): THEME→polymorphic-target,
     * PURPOSE→reason-sememe (optional, e.g. Compromise/Retirement/Fraud/Mistake),
     * TIME→timestamp.
     *
     * <p>Command body shape (what the handler reads):
     * <pre>
     * REVOCATION
     *     AGENT → @signer                  # the revoking signer (handler-routed)
     *     THEME → @target                  # what's being revoked (identity, event, delegation, claim)
     *     [optional] CAUSE → @reason       # structured reason (Compromise/Retirement/Fraud/Mistake)
     *     [optional] ATTRIBUTE[COMMENT] → "text"   # free-form annotation
     * </pre>
     * The vault stops attesting to the target.
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
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, dev.everydaythings.graph.PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "revocation";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, dev.everydaythings.graph.PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "revoke";

        /** What's being revoked (identity, event, delegation, claim — polymorphic). */
        @Seed.Property(schemaRole = ThematicRole.Theme.KEY)
        static final TypeRef expectsTheme = TypeRef.iid(Item.KEY);
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
     * The key bytes carried by INSTRUMENT are inherently multikey-encoded (their
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

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "a third party's signed assertion binding a key (or other property) "
                        + "to a subject identity";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, dev.everydaythings.graph.PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "attestation";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, dev.everydaythings.graph.PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
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
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, dev.everydaythings.graph.PartOfSpeech.Adjective.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishAdjectiveLemma = "next";
    }

    /**
     * Current — the active one in a sequence; the present member of a
     * current/next pair.  Parallel to {@link Next}.  Used on key-bearing
     * compound bindings to disambiguate the active keypair from its
     * pre-committed successor, e.g., {@code (SIGNING, CURRENT)} vs
     * {@code (SIGNING, NEXT)}.
     *
     * <p>In CG's broader vocabulary {@code CURRENT} is often left implicit
     * (a bare {@code (SIGNING)} key reads as "the current one").  This
     * qualifier exists for cases where the schema benefits from being
     * explicit — vault entry archetypes declaring all key slots, e.g.
     */
    @Seed.Item(key = Current.KEY)
    public static final class Current {
        public static final String KEY = "cg.sememe:current";
        private Current() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the present or active one in a sequence";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, dev.everydaythings.graph.PartOfSpeech.Adjective.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishAdjectiveLemma = "current";
    }

    /**
     * Retained — kept for historical or fallback access after being
     * superseded.  Used on signed-pre-key bindings to mark older SPKs the
     * holder still keeps around so late-arriving X3DH initiations against
     * them can still be decrypted, e.g., {@code (SIGNED_PRE_KEY, RETAINED)}.
     *
     * <p>The contrast with {@link Current} is "no longer active but still
     * available" vs "active now."  Retained material is typically destroyed
     * after a holding window.
     */
    @Seed.Item(key = Retained.KEY)
    public static final class Retained {
        public static final String KEY = "cg.sememe:retained";
        private Retained() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "kept for historical or fallback access after being superseded";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, dev.everydaythings.graph.PartOfSpeech.Adjective.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishAdjectiveLemma = "retained";
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
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, dev.everydaythings.graph.PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "delegator";
    }

    // ==================================================================================
    // Pre-keys for asynchronous session opening
    // ==================================================================================

    /**
     * SIGNED_PRE_KEY — a signer's published X25519 pre-key for use in
     * asynchronous initial-key-agreement (X3DH).  Lets a sender open a
     * Double-Ratchet session to this signer without the signer being online.
     *
     * <p>A declaration predicate, structurally equivalent to {@link Inception}
     * but without chain semantics.  Same thematic-role pattern, fewer
     * bindings:
     *
     * <pre>
     * SIGNED_PRE_KEY
     *     THEME → @signer                                # whose pre-key
     *     INSTRUMENT [MULTIKEY] → x25519-multikey-bytes  # the pre-key itself
     *     PURPOSE → @key-agreement                       # which track
     *     ATTRIBUTE [VALIDITY_UNTIL] → instant           # expiry (optional)
     *     TIME → instant                                 # when published
     * </pre>
     *
     * <p>The record on this frame is signed by the signer's signing-track
     * key, attesting ownership of the pre-key (the binding between the
     * signer and the X25519 pubkey is what the signature commits to).
     *
     * <p>Pre-keys are periodically rotated.  Consumers fetch the most recent
     * unexpired SignedPreKey for a peer when opening a new session.  Once a
     * session is established the pre-key falls out of the picture — DR
     * continues from the root key seeded by X3DH.
     */
    @Seed.Item(key = SignedPreKey.KEY)
    public static final class SignedPreKey {
        public static final String KEY = "cg.predicate:signed-pre-key";
        private SignedPreKey() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "a signer's published X25519 pre-key, signed under the signer's "
                        + "signing key, used in X3DH initial-key-agreement to open a "
                        + "Double-Ratchet session asynchronously";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, dev.everydaythings.graph.PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "signed pre-key";
    }

    /**
     * ONE_TIME_PRE_KEY — a signer's published single-use X25519 pre-key for
     * use as the fourth DH input in X3DH.  Adds forward secrecy for the very
     * first message of a session: even if the signed pre-key is later
     * compromised, the one-time pre-key's private side has already been
     * destroyed by the consumer.
     *
     * <p>Body shape (same pattern as {@link SignedPreKey}):
     * <pre>
     * ONE_TIME_PRE_KEY
     *     THEME → @signer                                # whose key
     *     INSTRUMENT [MULTIKEY] → x25519-multikey-bytes  # the pre-key
     *     PURPOSE → @key-agreement                       # which track
     *     TIME → instant                                 # when published
     * </pre>
     *
     * <p>Lifecycle: publisher generates a batch (commonly ~100) and
     * publishes each as its own frame.  Senders fetch one per new session
     * and reference its pubkey in a {@code CONSUMED_PRE_KEY} binding on the
     * first message.  The publisher's vault destroys the private side on
     * first use; subsequent messages claiming the same pre-key fail to
     * decrypt.  When the publisher's published batch runs low, a new batch
     * is generated and published.
     */
    @Seed.Item(key = OneTimePreKey.KEY)
    public static final class OneTimePreKey {
        public static final String KEY = "cg.predicate:one-time-pre-key";
        private OneTimePreKey() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "a signer's published single-use X25519 pre-key; consumed by a sender "
                        + "as the fourth DH input in X3DH, providing forward secrecy "
                        + "even if the signed pre-key is later compromised";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, dev.everydaythings.graph.PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "one-time pre-key";
    }

    // ==================================================================================
    // Cross-identity bridge predicates — connect Signers to other identities
    // ==================================================================================

    /**
     * REPRESENTS — a Signer stands for a non-Signer social entity (a
     * Person, Group, Service, etc.).  The canonical Signer-to-social-entity
     * bridge.  Many-to-many: a social entity may be represented by multiple
     * Signers (rotation, multi-device, multi-sig); a Signer may represent
     * multiple social entities (a Service that stands for both its
     * operating Group and an individual operator).
     *
     * <p>For Signer-to-Signer relationships (one cryptographic identity
     * providing service to another), use {@link Serves} instead.
     *
     * <p>Shape:
     * <pre>
     *   predicate = REPRESENTS
     *   Agent     → the attester's Signer
     *   Theme     → the Signer doing the representing
     *   Goal      → the entity being represented
     *   Time      → when the assertion was made
     * </pre>
     */
    @Seed.Item(key = Represents.KEY)
    public static final class Represents {
        public static final String KEY = "cg.predicate:represents";
        private Represents() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "one entity stands for another; the canonical bridge from a signer "
                        + "(cryptographic identity with keys) to a social entity "
                        + "(person, group, service) in the world, and the alias "
                        + "relation between signers that share an operator";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, dev.everydaythings.graph.PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "represent";
    }

    /**
     * SERVES — one Signer provides service to another.  The canonical
     * Signer-to-Signer bridge.  Librarian SERVES User; device-Signer SERVES
     * user-Signer; session-Signer SERVES user-Signer.  Many-to-many across
     * federation and multi-device.
     *
     * <p>For Signer-to-non-Signer relationships use {@link Represents}.
     *
     * <p>Shape:
     * <pre>
     *   predicate = SERVES
     *   Agent     → the attester's Signer
     *   Theme     → the Signer doing the serving (e.g. the Librarian)
     *   Goal      → the Signer being served (e.g. the User)
     *   Time      → when the assertion was made
     * </pre>
     */
    @Seed.Item(key = Serves.KEY)
    public static final class Serves {
        public static final String KEY = "cg.predicate:serves";
        private Serves() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "one signer provides service to another; the canonical signer-to-"
                        + "signer bridge (librarian-to-user, device-to-user, session-to-"
                        + "user); trust derives from the served signer's KEL, many-to-"
                        + "many across federation and multi-device";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, dev.everydaythings.graph.PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "serve";
    }

}
