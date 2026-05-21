package dev.everydaythings.graph.cryptography;


import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;

/**
 * Record vocabulary — sememes tied to records (signed attestations of bodies),
 * not to the bodies they attest.
 *
 * <p>A {@code Record} in CG carries a signature plus optional bindings that
 * describe <i>what the signer did to produce this signature</i>.  The most
 * common case ("I created this body and signed it") is the implicit default
 * and needs no metadata.  Less-common cases — encrypting, redacting,
 * compressing, or verifying an existing body — are marked with an {@link Act}
 * binding so the record's intent is legible.
 *
 * <h2>ACT vs body-level semantics</h2>
 *
 * <p>ACT lives at the signing-time layer: it describes the cryptographic
 * operation that produced this record.  Body-level claims (a witness's
 * assertion <i>about</i> another body, an endorsement, a counter-claim) live
 * in their own bodies whose predicates name those concepts; the records
 * attached to those bodies use the default ACT (Created).
 *
 * <p>The five operation ACTs map onto the cryptographic transformations
 * records can represent:
 *
 * <ul>
 *   <li>{@link Created} — default; "I authored this body."</li>
 *   <li>{@link Encrypted} — "I encrypted an existing body; this record signs
 *       the {@code Opaque.Encrypted} result."</li>
 *   <li>{@link Redacted} — "I redacted parts of an existing body; this record
 *       signs the {@code Opaque.Redacted} result."</li>
 *   <li>{@link Compressed} — "I compressed an existing body; this record
 *       signs the {@code Opaque.Compressed} result."</li>
 *   <li>{@link Verified} — "I verified an existing body's signatures and
 *       chain; this record signs the body unchanged as my attestation that
 *       the verification succeeded."  The anchor case for the attestation
 *       walker.</li>
 *   <li>{@link Materialized} — "I have materialized this body to physical
 *       storage; this record signs the body by its ContentRef (byte-level
 *       identity), not by its DatumRef."  Used to anchor on-disk persistence
 *       and to feed item-directory indexes that track which librarians hold
 *       which items at which byte-identities.</li>
 * </ul>
 */
public final class RecordVocabulary {

    private RecordVocabulary() {}

    // ==================================================================================
    // Role sememe
    // ==================================================================================

    /**
     * ACT — the role on a record binding declaring what cryptographic
     * operation the signer performed to produce this record.  Defaults to
     * {@link Created} when absent (the implicit case for almost all records).
     */
    @Seed.Item(key = Act.KEY)
    public static final class Act {
        public static final String KEY = "cg.role:act";
        private Act() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the cryptographic operation a record's signer performed in producing this "
                        + "record — created, encrypted, redacted, compressed, or verified";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "act";
    }

    // ==================================================================================
    // ACT value sememes — the operations a record signature can attest to.
    // ==================================================================================

    /**
     * Created — the signer authored this body from scratch.  The implicit
     * default ACT for records with no explicit {@code (ACT, …)} binding.
     */
    @Seed.Item(key = Created.KEY)
    public static final class Created {
        public static final String KEY = "cg.act:created";
        private Created() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the signer authored this body from scratch; the default record ACT, "
                        + "implicit when no ACT binding is present";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "create";
    }

    /**
     * Encrypted — the signer encrypted an existing body and this record signs
     * the resulting {@code Opaque.Encrypted} body.  The signer is the
     * encryption-doer, not the original body's author.
     */
    @Seed.Item(key = Encrypted.KEY)
    public static final class Encrypted {
        public static final String KEY = "cg.act:encrypted";
        private Encrypted() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the signer encrypted an existing body and this record signs the resulting "
                        + "Opaque.Encrypted body";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "encrypt";
    }

    /**
     * Redacted — the signer redacted parts of an existing body and this
     * record signs the resulting {@code Opaque.Redacted} body.  Used to
     * provide an audit trail for who performed the redaction.
     */
    @Seed.Item(key = Redacted.KEY)
    public static final class Redacted {
        public static final String KEY = "cg.act:redacted";
        private Redacted() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the signer redacted parts of an existing body and this record signs the "
                        + "resulting Opaque.Redacted body";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "redact";
    }

    /**
     * Compressed — the signer compressed an existing body and this record
     * signs the resulting {@code Opaque.Compressed} body.
     */
    @Seed.Item(key = Compressed.KEY)
    public static final class Compressed {
        public static final String KEY = "cg.act:compressed";
        private Compressed() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the signer compressed an existing body and this record signs the resulting "
                        + "Opaque.Compressed body";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "compress";
    }

    /**
     * Verified — the signer verified an existing body's signatures, chain, or
     * other integrity properties and this record signs the body unchanged as
     * an attestation that the verification succeeded.  Used as the anchor
     * shape for the attestation-chain walker: a {@code Verified} record from
     * a trusted identity on an attestation body lets the walker stop chain
     * traversal.
     */
    @Seed.Item(key = Verified.KEY)
    public static final class Verified {
        public static final String KEY = "cg.act:verified";
        private Verified() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the signer verified an existing body's signatures or chain and this record "
                        + "signs the body unchanged as an attestation that verification "
                        + "succeeded; used as the anchor shape for attestation-chain walking";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "verify";
    }

    /**
     * Materialized — the signer materialized an existing body to physical
     * storage (file system, hardware store, etc.) and this record signs the
     * body by its {@link dev.everydaythings.graph.ref.ContentRef ContentRef}
     * (byte-level identity), not by its
     * {@link dev.everydaythings.graph.ref.DatumRef DatumRef} (semantic
     * Merkle identity).
     *
     * <p>The commitment is to the EXACT bytes as stored — re-encoding or
     * redaction of the body changes the ContentRef, so a different
     * materialized form is a different MATERIALIZED record.  This is the
     * intended trade-off: materialization is a physical-storage statement,
     * not a semantic one, so byte-level identity is the correct
     * commitment.
     *
     * <p>Used to anchor on-disk persistence (e.g., a librarian's own
     * manifest on disk), and to feed future item-directory indexes that
     * track which librarian holds which item at which byte-identity.
     *
     * <p>Distinct from {@link Created} in two important ways:
     * <ul>
     *   <li>The TARGET is a {@link dev.everydaythings.graph.ref.ContentRef
     *       ContentRef}, not a {@link
     *       dev.everydaythings.graph.ref.DatumRef DatumRef}.</li>
     *   <li>The signer need not be the body's author — a librarian
     *       materializing a peer's body produces a MATERIALIZED record
     *       even though the body's {@link Created} record names a
     *       different signer.</li>
     * </ul>
     */
    @Seed.Item(key = Materialized.KEY)
    public static final class Materialized {
        public static final String KEY = "cg.act:materialized";
        private Materialized() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the signer materialized this body to physical storage; the record "
                        + "signs the body by its ContentRef (byte-level identity), not "
                        + "by its DatumRef (semantic identity)";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "materialize";
    }
}
