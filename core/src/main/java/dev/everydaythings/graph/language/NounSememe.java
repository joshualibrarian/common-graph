package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.Type;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Librarian;

import java.util.List;
import java.util.Map;

/**
 * A noun sememe — an entity, concept, or thing.
 *
 * <p>Nouns serve as predicates in relations (e.g., AUTHOR, TITLE) and
 * as arguments to verbs (e.g., the thing being created).
 *
 * <p>This is the primary extension point for domain-specific noun types
 * that carry meaning beyond a plain noun:
 * <ul>
 *   <li>{@link dev.everydaythings.graph.value.Operator} — symbol, precedence, evaluation</li>
 *   <li>Function — arity, parameters, evaluation</li>
 *   <li>{@link dev.everydaythings.graph.value.Unit} — dimension, conversion factor</li>
 *   <li>{@link dev.everydaythings.graph.value.Dimension} — base unit</li>
 * </ul>
 *
 * <p>All domain nouns inherit glosses, tokens, symbols, and dictionary
 * registration from {@link Sememe}, making them discoverable through
 * the same vocabulary pipeline as any other sememe.
 */
@Type(value = NounSememe.KEY, glyph = "\uD83D\uDCA1", color = 0xF0C040)
public class NounSememe extends Sememe {

    public static final String KEY = "cg:type/noun-sememe";

    // ==================================================================================
    // SEED INSTANCES (core predicates)
    // ==================================================================================

    public static class Author {
        public static final String KEY = "cg.core:author";
        @Seed public static final NounSememe SEED = new NounSememe(KEY)
                .gloss(ENG, "the creator or originator of a work")
                .cili("i90183");
    }

    public static class CreatedAt {
        public static final String KEY = "cg.core:created-at";
        @Seed public static final NounSememe SEED = new NounSememe(KEY)
                .gloss(ENG, "the time at which something was created")
                .cili("i36666");
    }

    public static class ModifiedAt {
        public static final String KEY = "cg.core:modified-at";
        @Seed public static final NounSememe SEED = new NounSememe(KEY)
                .gloss(ENG, "the time at which something was last modified")
                .cili("i22389");
    }

    public static class Title {
        public static final String KEY = "cg.core:title";
        @Seed public static final NounSememe SEED = new NounSememe(KEY)
                .gloss(ENG, "the name or title of something")
                .cili("i69816")
                .indexWeight(1000);
    }

    public static class Description {
        public static final String KEY = "cg.core:description";
        @Seed public static final NounSememe SEED = new NounSememe(KEY)
                .gloss(ENG, "a textual description of something")
                .cili("i71841")
                .indexWeight(500);
    }

    public static class Slot {
        public static final String KEY = "cg.core:slot";
        @Seed public static final NounSememe SEED = new NounSememe(KEY)
                .gloss(ENG, "a position in a frame that expects a particular role")
                .cili("i69534")
                .word(LEMMA, ENG, "slot");
    }

    public static class LexemeSeed {
        public static final String KEY = "cg.core:lexeme";
        @Seed public static final NounSememe SEED = new NounSememe(KEY)
                .gloss(ENG, "a word-meaning mapping in a language's lexicon")
                .cili("i69622")
                .word(LEMMA, ENG, "lexeme");
    }

    public static class Frequency {
        public static final String KEY = "cg.core:frequency";
        @Seed public static final NounSememe SEED = new NounSememe(KEY)
                .gloss(ENG, "how often something occurs")
                .word(LEMMA, ENG, "frequency")
                .cili("i73785");
    }

    public static class Provenance {
        public static final String KEY = "cg.core:provenance";
        @Seed public static final NounSememe SEED = new NounSememe(KEY)
                .gloss(ENG, "the origin or source of information")
                .word(LEMMA, ENG, "provenance")
                .cili("i77490");
    }

    // ==================================================================================
    // CONSTRUCTORS
    // ==================================================================================

    /** Type seed constructor. */
    @SuppressWarnings("unused")
    protected NounSememe(ItemID typeId) {
        super(typeId);
    }

    /** Hydration constructor. */
    @SuppressWarnings("unused")
    protected NounSememe(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
    }

    /** Fluent seed constructor — use with chained .gloss(), .token(), .cili(), etc. */
    public NounSememe(String canonicalKey) {
        super(canonicalKey, PartOfSpeech.NOUN);
    }

    /** Seed constructor (no tokens). */
    public NounSememe(String canonicalKey,
                      Map<String, String> glosses, Map<String, String> sources) {
        super(canonicalKey, PartOfSpeech.NOUN, glosses, sources);
    }

    /** Seed constructor (with tokens). */
    public NounSememe(String canonicalKey,
                      Map<String, String> glosses, Map<String, String> sources,
                      List<String> tokens) {
        super(canonicalKey, PartOfSpeech.NOUN, glosses, sources, tokens);
    }

    /** Seed constructor (with symbols and tokens). */
    public NounSememe(String canonicalKey,
                      Map<String, String> glosses, Map<String, String> sources,
                      List<String> symbols, List<String> tokens) {
        super(canonicalKey, PartOfSpeech.NOUN, glosses, sources, symbols, tokens);
    }

    /** Runtime constructor (with librarian). */
    protected NounSememe(Librarian librarian, String canonicalKey,
                         Map<String, String> glosses, Map<String, String> sources) {
        super(librarian, canonicalKey, PartOfSpeech.NOUN, glosses, sources);
    }

    // ==================================================================================
    // COVARIANT OVERRIDES (fluent chaining returns NounSememe)
    // ==================================================================================

    @Override public NounSememe gloss(String lang, String text) { super.gloss(lang, text); return this; }
    @Override public NounSememe word(Sememe form, String lang, String surface) { super.word(form, lang, surface); return this; }
    @Override public NounSememe cili(String id) { super.cili(id); return this; }
    @Override public NounSememe symbol(String s) { super.symbol(s); return this; }
    @Override public NounSememe slot(Sememe role) { super.slot(role); return this; }
    @Override public NounSememe indexWeight(int weight) { super.indexWeight(weight); return this; }
}
