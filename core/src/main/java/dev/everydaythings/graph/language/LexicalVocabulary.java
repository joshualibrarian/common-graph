package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Item.Seed;

/**
 * Lexical relation vocabulary — semantic relationships between concepts.
 *
 * <p>These are the WordNet pointer types: taxonomic (hypernym/hyponym),
 * mereological (holonym/meronym), and associative (antonym, entailment,
 * derivation, etc.) relations. Each is anchored to a CILI identifier.
 *
 * @see CoreVocabulary for core predicates and action verbs
 */
public final class LexicalVocabulary {

    private LexicalVocabulary() {}

    public static class Hypernym {
        public static final String KEY = "cg.rel:hypernym";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "is a kind of; is a type of; is a subclass of")
                .cili("i69569")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY);
    }

    public static class Hyponym {
        public static final String KEY = "cg.rel:hyponym";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "has subtype; has kind; is a superclass of")
                .cili("i69570")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY);
    }

    public static class InstanceOf {
        public static final String KEY = "cg.rel:instance-of";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "is an instance of; has type; is a member of class")
                .cili("i35284")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY);
    }

    public static class Holonym {
        public static final String KEY = "cg.rel:holonym";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "is a part of; is contained in")
                .cili("i69567")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY);
    }

    public static class Meronym {
        public static final String KEY = "cg.rel:meronym";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "has as a part; contains")
                .cili("i69575")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY);
    }

    public static class Antonym {
        public static final String KEY = "cg.rel:antonym";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "is the opposite of; contrasts with")
                .cili("i69547")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY);
    }

    public static class SimilarTo {
        public static final String KEY = "cg.rel:similar-to";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "is similar to; resembles in meaning")
                .cili("i34992")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY);
    }

    public static class Derivation {
        public static final String KEY = "cg.rel:derivation";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "is derivationally related to")
                .cili("i37467")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY);
    }

    public static class Domain {
        public static final String KEY = "cg.rel:domain";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "belongs to domain; is in the category of")
                .cili("i68336")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY);
    }

    public static class Entails {
        public static final String KEY = "cg.rel:entails";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "entails; necessarily implies")
                .cili("i34848")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY);
    }

    public static class Causes {
        public static final String KEY = "cg.rel:causes";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "causes; brings about")
                .cili("i29966")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY);
    }

    public static class SeeAlso {
        public static final String KEY = "cg.rel:see-also";
        @Seed public static final Sememe SEED = new Sememe(KEY, PartOfSpeech.VERB)
                .gloss(Sememe.ENG, "see also; is related to")
                .cili("i25271")
                .slot(ThematicRole.Theme.KEY).slot(ThematicRole.Goal.KEY);
    }
}
