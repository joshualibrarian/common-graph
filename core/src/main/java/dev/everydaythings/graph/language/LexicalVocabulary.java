package dev.everydaythings.graph.language;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.runtime.librarian.Librarian;

import static dev.everydaythings.graph.Seed.*;

/**
 * Lexical-relation vocabulary — the semantic relationships between concepts
 * that WordNet (and CILI) draw.
 *
 * <p>Three families:
 * <ul>
 *   <li><b>Taxonomic</b> — {@link Hypernym}, {@link Hyponym}, {@link InstanceOf}</li>
 *   <li><b>Mereological</b> — {@link Holonym}, {@link Meronym}</li>
 *   <li><b>Associative</b> — {@link Antonym}, {@link SimilarTo},
 *       {@link Derivation}, {@link Domain}, {@link Entails}, {@link Causes},
 *       {@link SeeAlso}</li>
 * </ul>
 *
 * <p>Each predicate carries its CILI id ({@link CiliId}) so cross-vocabulary
 * alignment stays stable across language editions.
 */
public final class LexicalVocabulary {

    private LexicalVocabulary() {}

    // ==================================================================================
    // TAXONOMIC
    // ==================================================================================

    @Seed.Item(key = Hypernym.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Hypernym {
        public static final String KEY = "cg.rel:hypernym";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private Hypernym() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "is a kind of; is a type of; is a subclass of";

        @Frame(predicate = CiliId.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String cili = "i69569";
    }

    @Seed.Item(key = Hyponym.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Hyponym {
        public static final String KEY = "cg.rel:hyponym";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private Hyponym() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "has subtype; has kind; is a superclass of";

        @Frame(predicate = CiliId.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String cili = "i69570";
    }

    @Seed.Item(key = InstanceOf.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class InstanceOf {
        public static final String KEY = "cg.rel:instance-of";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private InstanceOf() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "is an instance of; has type; is a member of class";

        @Frame(predicate = CiliId.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String cili = "i35284";
    }

    // ==================================================================================
    // MEREOLOGICAL
    // ==================================================================================

    @Seed.Item(key = Holonym.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Holonym {
        public static final String KEY = "cg.rel:holonym";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private Holonym() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "is a part of; is contained in";

        @Frame(predicate = CiliId.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String cili = "i69567";
    }

    @Seed.Item(key = Meronym.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Meronym {
        public static final String KEY = "cg.rel:meronym";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private Meronym() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "has as a part; contains";

        @Frame(predicate = CiliId.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String cili = "i69575";
    }

    // ==================================================================================
    // ASSOCIATIVE
    // ==================================================================================

    @Seed.Item(key = Antonym.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Antonym {
        public static final String KEY = "cg.rel:antonym";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private Antonym() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "is the opposite of; contrasts with";

        @Frame(predicate = CiliId.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String cili = "i69547";
    }

    @Seed.Item(key = SimilarTo.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class SimilarTo {
        public static final String KEY = "cg.rel:similar-to";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private SimilarTo() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "is similar to; resembles in meaning";

        @Frame(predicate = CiliId.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String cili = "i34992";
    }

    @Seed.Item(key = Derivation.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Derivation {
        public static final String KEY = "cg.rel:derivation";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private Derivation() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "is derivationally related to";

        @Frame(predicate = CiliId.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String cili = "i37467";
    }

    @Seed.Item(key = Domain.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Domain {
        public static final String KEY = "cg.rel:domain";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private Domain() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "belongs to domain; is in the category of";

        @Frame(predicate = CiliId.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String cili = "i68336";
    }

    @Seed.Item(key = Entails.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Entails {
        public static final String KEY = "cg.rel:entails";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private Entails() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "entails; necessarily implies";

        @Frame(predicate = CiliId.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String cili = "i34848";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "entails";
    }

    @Seed.Item(key = Causes.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Causes {
        public static final String KEY = "cg.rel:causes";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private Causes() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "causes; brings about";

        @Frame(predicate = CiliId.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String cili = "i29966";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "causes";
    }

    @Seed.Item(key = SeeAlso.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class SeeAlso {
        public static final String KEY = "cg.rel:see-also";
        public static final ItemRef IID = ItemRef.fromString(KEY);
        private SeeAlso() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "see also; is related to";

        @Frame(predicate = CiliId.KEY,
          field = @Binding(role = ThematicRole.Value.KEY))
        static final String cili = "i25271";
    }

    /**
     * The lexeme predicate — a word in a language that points at a sememe (meaning).
     *
     * <p>A LEXEME frame attaches a surface form (lemma, inflected form, idiom phrase)
     * to its endorsing sememe. The lemma+language+POS combination identifies "this
     * word in this language with this part-of-speech expresses this meaning."
     *
     * <p>Body shape (typically created via {@code @Bind} on a seed class):
     * <pre>
     * LEXEME
     *     VALUE [LANGUAGE, POS, FEATURE...] → "lemma-or-form"
     *     [+ optional context bindings]
     * </pre>
     *
     * <p>Lexeme qualifiers (on the VALUE binding) carry:
     * <ul>
     *   <li>Language (English, Spanish, ...)</li>
     *   <li>Part of Speech (Noun, Verb, ...)</li>
     *   <li>Grammatical features (Lemma for canonical form; Past, Plural, etc. for
     *       inflected forms)</li>
     * </ul>
     *
     * <p>The sememe-being-named is implicit via the endorsing seed manifest. Same
     * surface form in different languages = different LEXEME frames pointing at
     * the same sememe (synonyms across languages).
     *
     * <p>Polysemy: the same word with the same language + POS may map to multiple
     * sememes — handled via multiple LEXEME frames, each endorsed by a different
     * sememe.
     *
     * <p>Phase 1 keeps {@code onFrameAssembled} minimal — body persistence is enough.
     * Future: token-dictionary indexing for word→sememe lookup at parse time.
     */
    @Seed.Item(key = Lexeme.KEY, head = CoreVocabulary.Predicate.KEY)
    @Embodies(key = Lexeme.KEY)
    public static class Lexeme extends Item {

        /** Canonical key for the lexeme sememe. */
        public static final String KEY = "cg.sememe:lexeme";

        /** The deterministic IID for the lexeme sememe. */
        public static final ItemRef IID = ItemRef.fromString(KEY);

        public Lexeme(ItemRef iid, Librarian librarian) {
            super(iid, librarian);
        }
    }

    /**
     * The gloss predicate — a per-language definition of a sememe.
     *
     * <p>A GLOSS frame attaches a human-readable definition to its endorsing seed,
     * scoped by language qualifier. One sememe can have many glosses (one per
     * language); each gloss frame is independent and supersedable.
     *
     * <p>Body shape (typically created via {@code @Bind} on a seed class):
     * <pre>
     * GLOSS
     *     VALUE [LANGUAGE] → "the human-readable definition"
     *     [+ optional context bindings]
     * </pre>
     *
     * <p>The relationship to the sememe-being-defined is implicit: the seed manifest
     * has an ENDORSES binding to this gloss frame body. So "gloss for X" =
     * "GLOSS frame endorsed by X's manifest."
     *
     * <p>Multiple glosses per language are allowed (alternative definitions, edited
     * over time, contributed by different parties). Receivers' trust matrices and
     * presentation logic decide which to display.
     *
     * <p>Phase 1 keeps {@code onFrameAssembled} minimal — body persistence is enough.
     * Future: token-dictionary indexing for "find sememes whose gloss matches X."
     */
    @Seed.Item(key = Gloss.KEY, head = CoreVocabulary.Predicate.KEY)
    @Embodies(key = Gloss.KEY)
    public static class Gloss extends Item {

        /** Canonical key for the gloss sememe. */
        public static final String KEY = "cg.sememe:gloss";

        /** The deterministic IID for the gloss sememe. */
        public static final ItemRef IID = ItemRef.fromString(KEY);

        public Gloss(ItemRef iid, Librarian librarian) {
            super(iid, librarian);
        }
    }
}
