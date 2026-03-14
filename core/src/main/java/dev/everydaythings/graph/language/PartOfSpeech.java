package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;

/**
 * Parts of speech as items — each is a seed Sememe with a deterministic IID.
 *
 * <p>A part of speech is just a concept — a noun that names a grammatical
 * category. {@code PART_OF_SPEECH} is a predicate, and every sememe has a
 * {@code PART_OF_SPEECH} frame whose value is one of these concept sememes.
 *
 * <p>All POS values are nouns (the word "verb" is a noun). Noun's POS
 * points to itself (self-referential). All others point to {@link #NOUN}.
 *
 * <p>The static {@link ItemID} constants ({@link #VERB}, {@link #NOUN}, etc.)
 * provide the same API shape as the old enum, keeping call sites unchanged.
 */
public final class PartOfSpeech {

    private PartOfSpeech() {}

    // ==================================================================================
    // ItemID CONSTANTS — same names as the old enum values
    // ==================================================================================

    public static final ItemID NOUN         = ItemID.fromString("cg.pos:noun");
    public static final ItemID VERB         = ItemID.fromString("cg.pos:verb");
    public static final ItemID ADJECTIVE    = ItemID.fromString("cg.pos:adjective");
    public static final ItemID ADVERB       = ItemID.fromString("cg.pos:adverb");
    public static final ItemID PRONOUN      = ItemID.fromString("cg.pos:pronoun");
    public static final ItemID CONJUNCTION  = ItemID.fromString("cg.pos:conjunction");
    public static final ItemID INTERJECTION = ItemID.fromString("cg.pos:interjection");
    public static final ItemID PREPOSITION  = ItemID.fromString("cg.pos:preposition");

    // ==================================================================================
    // PREDICATE — the PART_OF_SPEECH predicate itself
    // ==================================================================================

    /**
     * The predicate "part-of-speech" — every sememe has a frame keyed by this
     * predicate, with one of the POS value seeds as its target.
     */
    public static class Predicate {
        public static final String KEY = "cg.core:part-of-speech";
        @Item.Seed public static final Sememe SEED = new Sememe(KEY, NOUN)
                .gloss(Sememe.ENG, "the grammatical category of a word")
                .word(Sememe.LEMMA, Sememe.ENG, "part-of-speech");
    }

    // ==================================================================================
    // POS VALUE SEEDS — all are nouns (the word "verb" is a noun)
    // ==================================================================================

    public static class Noun {
        public static final String KEY = "cg.pos:noun";
        @Item.Seed public static final Sememe SEED = new Sememe(KEY, NOUN)
                .gloss(Sememe.ENG, "a word that names a person, place, thing, or idea")
                .cili("i73935").word(Sememe.LEMMA, Sememe.ENG, "noun");
    }

    public static class Verb {
        public static final String KEY = "cg.pos:verb";
        @Item.Seed public static final Sememe SEED = new Sememe(KEY, NOUN)
                .gloss(Sememe.ENG, "a word that expresses an action or state")
                .cili("i73936").word(Sememe.LEMMA, Sememe.ENG, "verb");
    }

    public static class Adjective {
        public static final String KEY = "cg.pos:adjective";
        @Item.Seed public static final Sememe SEED = new Sememe(KEY, NOUN)
                .gloss(Sememe.ENG, "a word that modifies a noun")
                .cili("i73937").word(Sememe.LEMMA, Sememe.ENG, "adjective");
    }

    public static class Adverb {
        public static final String KEY = "cg.pos:adverb";
        @Item.Seed public static final Sememe SEED = new Sememe(KEY, NOUN)
                .gloss(Sememe.ENG, "a word that modifies a verb, adjective, or other adverb")
                .cili("i73938").word(Sememe.LEMMA, Sememe.ENG, "adverb");
    }

    public static class Pronoun {
        public static final String KEY = "cg.pos:pronoun";
        @Item.Seed public static final Sememe SEED = new Sememe(KEY, NOUN)
                .gloss(Sememe.ENG, "a word that substitutes for a noun")
                .cili("i73939").word(Sememe.LEMMA, Sememe.ENG, "pronoun");
    }

    public static class Conjunction {
        public static final String KEY = "cg.pos:conjunction";
        @Item.Seed public static final Sememe SEED = new Sememe(KEY, NOUN)
                .gloss(Sememe.ENG, "a word that connects clauses or sentences")
                .cili("i73940").word(Sememe.LEMMA, Sememe.ENG, "conjunction");
    }

    public static class Interjection {
        public static final String KEY = "cg.pos:interjection";
        @Item.Seed public static final Sememe SEED = new Sememe(KEY, NOUN)
                .gloss(Sememe.ENG, "a word expressing sudden feeling")
                .cili("i73941").word(Sememe.LEMMA, Sememe.ENG, "interjection");
    }

    public static class Preposition {
        public static final String KEY = "cg.pos:preposition";
        @Item.Seed public static final Sememe SEED = new Sememe(KEY, NOUN)
                .gloss(Sememe.ENG, "a word governing a noun or pronoun to express a relation")
                .cili("i73942").word(Sememe.LEMMA, Sememe.ENG, "preposition");
    }
}
