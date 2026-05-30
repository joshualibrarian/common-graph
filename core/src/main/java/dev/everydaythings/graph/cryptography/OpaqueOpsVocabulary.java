package dev.everydaythings.graph.cryptography;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.ref.TypeRef;

/**
 * Opaque-producing command predicates — verbs whose handlers transform a body
 * into an {@link dev.everydaythings.graph.datum.Opaque Opaque} form.  Currently
 * holds {@link Elide} (redaction → {@code Opaque.Redacted}) and {@link Compress}
 * (compression → {@code Opaque.Compressed}).
 *
 * <p>{@link EncryptionVocabulary.Encrypt} also belongs here conceptually
 * (encryption → {@code Opaque.Encrypted}); kept in
 * {@link EncryptionVocabulary} for now to keep crypto-specific machinery
 * (key tracks, ratchet metadata) co-located.  Move when symmetry pressure
 * outweighs co-location pressure.
 *
 * <p>All three are handled on natural-home entities: encrypt on the signer
 * with the keys; elide and compress on the librarian (the steward of stored
 * content).
 */
public final class OpaqueOpsVocabulary {

    private OpaqueOpsVocabulary() {}

    // ==================================================================================
    // ELIDE — redact content from a body, preserving its structural identity.
    // ==================================================================================

    /**
     * ELIDE — a command asserting that a body is to be (or has been) reduced to
     * an {@link dev.everydaythings.graph.datum.Opaque.Redacted Opaque.Redacted}
     * form, discarding content while preserving its structural hash.  Same dual
     * command/record posture as {@link EncryptionVocabulary.Encrypt}.
     *
     * <p>Body shape (typical):
     * <pre>
     * ELIDE
     *     [optional] AGENT → @signer-iid           # omit for anonymous
     *     THEME → @body-ref or inline body         # what to elide
     *     TIME → timestamp                         # when elided
     * </pre>
     *
     * <p>(The verb "elide" supersedes "redact" — same meaning, cleaner word.
     * The {@link dev.everydaythings.graph.datum.Opaque.Redacted Opaque.Redacted}
     * Java class keeps its name for now and will be IntelliJ-renamed when the
     * sweep happens.)
     */
    @Seed.Item(key = Elide.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Elide {
        public static final String KEY = "cg.sememe:elide";
        private Elide() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "an event asserting that a body is to be (or has been) reduced to an "
                        + "Opaque.Redacted form, discarding content while preserving its structural hash; "
                        + "used both as a command verb (do this redaction) and as a record of one that has happened";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY,
                qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "elision";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY,
                qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "elide";

        /** What's being elided: an inline body or a reference to one. */
        @Seed.Property(schemaRole = ThematicRole.Theme.KEY)
        static final TypeRef expectsTheme = TypeRef.iid(Item.KEY);
    }

    // ==================================================================================
    // COMPRESS — deflate a body's bytes while preserving its structural hash.
    // ==================================================================================

    /**
     * COMPRESS — a command asserting that a body is to be (or has been) deflated
     * into an {@link dev.everydaythings.graph.datum.Opaque.Compressed
     * Opaque.Compressed} form.  The structural hash survives; the bytes are
     * compressed (gzip / brotli / etc.).
     *
     * <p>Body shape (typical):
     * <pre>
     * COMPRESS
     *     [optional] AGENT → @signer-iid          # omit for anonymous
     *     THEME → @body-ref or inline body        # what to compress
     *     [optional] INSTRUMENT → @algorithm      # which compression algorithm (default: gzip)
     *     TIME → timestamp                        # when compressed
     * </pre>
     */
    @Seed.Item(key = Compress.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Compress {
        public static final String KEY = "cg.sememe:compress";
        private Compress() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "an event asserting that a body is to be (or has been) deflated into an "
                        + "Opaque.Compressed form; used both as a command verb and as a record";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY,
                qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "compression";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY,
                qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "compress";

        /** What's being compressed: an inline body or a reference to one. */
        @Seed.Property(schemaRole = ThematicRole.Theme.KEY)
        static final TypeRef expectsTheme = TypeRef.iid(Item.KEY);

        /** Compression algorithm.  Optional; defaults to whatever the encoding registry provides. */
        @Seed.Property(schemaRole = ThematicRole.Instrument.KEY)
        static final TypeRef expectsInstrument = TypeRef.iid(Item.KEY);
    }

    // ==================================================================================
    // DECOMPRESS — the mirror of Compress, recovering a body from Opaque.Compressed.
    // ==================================================================================

    /**
     * DECOMPRESS — the mirror of {@link Compress} as a command verb: recovers
     * the original {@link dev.everydaythings.graph.datum.Body Body} from an
     * {@link dev.everydaythings.graph.datum.Opaque.Compressed Opaque.Compressed}
     * payload.
     *
     * <p>Body shape:
     * <pre>
     * DECOMPRESS
     *     [optional] AGENT → @signer-iid          # omit for anonymous
     *     THEME → @opaque-compressed-ref or inline opaque   # what to decompress
     *     TIME → timestamp                        # when decompressed
     * </pre>
     *
     * <p>Handled on the librarian — same natural home as compress.
     */
    @Seed.Item(key = Decompress.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Decompress {
        public static final String KEY = "cg.sememe:decompress";
        private Decompress() {}

        @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "an event recovering the original body from an Opaque.Compressed payload; "
                        + "the mirror of compress as a command verb";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY,
                qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "decompression";

        @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY,
                qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "decompress";

        /** What's being decompressed: an Opaque.Compressed or a reference to one. */
        @Seed.Property(schemaRole = ThematicRole.Theme.KEY)
        static final TypeRef expectsTheme = TypeRef.iid(Item.KEY);
    }
}
