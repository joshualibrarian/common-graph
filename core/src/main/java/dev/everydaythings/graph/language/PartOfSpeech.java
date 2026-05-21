package dev.everydaythings.graph.language;

import dev.everydaythings.graph.Seed;

/**
 * Part-of-speech sememes — grammatical category targets of {@code POS}-qualified
 * bindings on lexemes (and other linguistic frames).
 *
 * <p>The outer class is the part-of-speech meta-sememe ({@code cg.sememe:part-of-speech}),
 * usable as a qualifier on EXPECTS declaring "the target is a POS." Inner
 * classes are specific parts of speech.
 *
 * <p>Canonical-key prefix: {@code cg.pos:}.
 *
 * <p>The standard set: noun, verb, adjective, adverb, pronoun, conjunction,
 * interjection, preposition. Extensible (determiner, article, particle, etc.)
 * if WordNet or other imports require additions.
 *
 * <p>Each carries its CILI id (the WordNet "word class" sense) so cross-language
 * grammar imports merge cleanly into the same items.
 */
@Seed.Item(key = PartOfSpeech.KEY)
public final class PartOfSpeech {

    /** Canonical key for the part-of-speech meta-sememe. */
    public static final String KEY = "cg.sememe:part-of-speech";

    private PartOfSpeech() {}

    /** Noun — naming a person, place, thing, or idea. */
    @Seed.Item(key = Noun.KEY)
    @Seed.Cili("i69682")
    public static final class Noun {
        public static final String KEY = "cg.pos:noun";
        private Noun() {}
    }

    /** Verb — describing an action, event, or state. */
    @Seed.Item(key = Verb.KEY)
    @Seed.Cili("i69683")
    public static final class Verb {
        public static final String KEY = "cg.pos:verb";
        private Verb() {}
    }

    /** Adjective — modifying a noun. */
    @Seed.Item(key = Adjective.KEY)
    @Seed.Cili("i69688")
    public static final class Adjective {
        public static final String KEY = "cg.pos:adjective";
        private Adjective() {}
    }

    /** Adverb — modifying a verb, adjective, or another adverb. */
    @Seed.Item(key = Adverb.KEY)
    @Seed.Cili("i69689")
    public static final class Adverb {
        public static final String KEY = "cg.pos:adverb";
        private Adverb() {}
    }

    /** Pronoun — substituting for a noun. */
    @Seed.Item(key = Pronoun.KEY)
    @Seed.Cili("i69718")
    public static final class Pronoun {
        public static final String KEY = "cg.pos:pronoun";
        private Pronoun() {}
    }

    /** Conjunction — connecting clauses or phrases. */
    @Seed.Item(key = Conjunction.KEY)
    @Seed.Cili("i69721")
    public static final class Conjunction {
        public static final String KEY = "cg.pos:conjunction";
        private Conjunction() {}
    }

    /** Interjection — a standalone exclamatory utterance. */
    @Seed.Item(key = Interjection.KEY)
    @Seed.Cili("i74106")
    public static final class Interjection {
        public static final String KEY = "cg.pos:interjection";
        private Interjection() {}
    }

    /** Preposition — relating a noun phrase to another sentence element. */
    @Seed.Item(key = Preposition.KEY)
    @Seed.Cili("i69717")
    public static final class Preposition {
        public static final String KEY = "cg.pos:preposition";
        private Preposition() {}
    }
}
