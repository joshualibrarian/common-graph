package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.item.Type;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Librarian;

import java.util.List;
import java.util.Map;

/**
 * A pronoun sememe — a reference or variable placeholder.
 *
 * <p>Used for query pattern elements like ANY (wildcard) and WHAT (variable).
 */
@Type(value = PronounSememe.KEY, glyph = "\uD83D\uDCA1", color = 0xF0C040)
public final class PronounSememe extends Sememe {

    public static final String KEY = "cg:type/pronoun-sememe";

    // ==================================================================================
    // SEED INSTANCES (query pattern primitives)
    // ==================================================================================

    public static class Any {
        public static final String KEY = "cg.query:any";
        public static final PronounSememe SEED = new PronounSememe(KEY)
                .gloss(ENG, "matches anything; wildcard; any value")
                .cili("i61150")
                .symbol("*")
                .word(LEMMA, ENG, "wildcard").word(LEMMA, ENG, "anything");
    }

    public static class What {
        public static final String KEY = "cg.query:what";
        public static final PronounSememe SEED = new PronounSememe(KEY)
                .gloss(ENG, "the result being queried for; variable; unknown")
                .cili("i74896")
                .symbol("?")
                .word(LEMMA, ENG, "variable").word(LEMMA, ENG, "result");
    }

    // ==================================================================================
    // SEED INSTANCES (discourse references)
    // ==================================================================================

    public static class It {
        public static final String KEY = "cg.pronoun:it";
        public static final PronounSememe SEED = new PronounSememe(KEY)
                .gloss(ENG, "the most recently mentioned or created item")
                .word(LEMMA, ENG, "it").word(LEMMA, ENG, "that");
    }

    public static class This {
        public static final String KEY = "cg.pronoun:this";
        public static final PronounSememe SEED = new PronounSememe(KEY)
                .gloss(ENG, "the currently focused item")
                .word(LEMMA, ENG, "this");
    }

    public static class Last {
        public static final String KEY = "cg.pronoun:last";
        public static final PronounSememe SEED = new PronounSememe(KEY)
                .gloss(ENG, "the previously mentioned item")
                .word(LEMMA, ENG, "last").word(LEMMA, ENG, "previous");
    }

    // ==================================================================================
    // CONSTRUCTORS
    // ==================================================================================

    /** Type seed constructor. */
    @SuppressWarnings("unused")
    protected PronounSememe(ItemID typeId) {
        super(typeId);
    }

    /** Hydration constructor. */
    @SuppressWarnings("unused")
    protected PronounSememe(Librarian librarian, Manifest manifest) {
        super(librarian, manifest);
    }

    /** Fluent seed constructor. */
    public PronounSememe(String canonicalKey) {
        super(canonicalKey, PartOfSpeech.PRONOUN);
    }

    /** Seed constructor (no tokens). */
    public PronounSememe(String canonicalKey,
                         Map<String, String> glosses, Map<String, String> sources) {
        super(canonicalKey, PartOfSpeech.PRONOUN, glosses, sources);
    }

    /** Seed constructor (with tokens). */
    public PronounSememe(String canonicalKey,
                         Map<String, String> glosses, Map<String, String> sources,
                         List<String> tokens) {
        super(canonicalKey, PartOfSpeech.PRONOUN, glosses, sources, tokens);
    }

    /** Seed constructor (with symbols and tokens). */
    public PronounSememe(String canonicalKey,
                         Map<String, String> glosses, Map<String, String> sources,
                         List<String> symbols, List<String> tokens) {
        super(canonicalKey, PartOfSpeech.PRONOUN, glosses, sources, symbols, tokens);
    }

    /** Runtime constructor (with librarian). */
    protected PronounSememe(Librarian librarian, String canonicalKey,
                            Map<String, String> glosses, Map<String, String> sources) {
        super(librarian, canonicalKey, PartOfSpeech.PRONOUN, glosses, sources);
    }

    // ==================================================================================
    // COVARIANT OVERRIDES (fluent chaining returns PronounSememe)
    // ==================================================================================

    @Override public PronounSememe gloss(String lang, String text) { super.gloss(lang, text); return this; }
    @Override public PronounSememe word(Sememe form, String lang, String surface) { super.word(form, lang, surface); return this; }
    @Override public PronounSememe cili(String id) { super.cili(id); return this; }
    @Override public PronounSememe symbol(String s) { super.symbol(s); return this; }
    @Override public PronounSememe indexWeight(int weight) { super.indexWeight(weight); return this; }
}
