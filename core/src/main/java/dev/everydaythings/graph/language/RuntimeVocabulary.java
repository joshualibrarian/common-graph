package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.id.ItemID;

/**
 * Seed vocabulary for programming language runtimes.
 *
 * <p>These sememes identify programming languages as implementation
 * targets for items. They appear as keys in the manifest's implementation
 * binding map, linking a language to its class/module implementation.
 *
 * <p>These are NOT natural languages (those use {@link Language},
 * e.g., {@code cg:language/eng}). These are runtime environments
 * that can execute item behavior.
 *
 * @see CoreVocabulary
 */
public final class RuntimeVocabulary {

    private RuntimeVocabulary() {}


    @ItemSeed(key = Java.KEY)
    public static class Java {
        public static final String KEY = "cg.language:java";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "Java programming language runtime";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "java";
    }

    @ItemSeed(key = Python.KEY)
    public static class Python {
        public static final String KEY = "cg.language:python";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "Python programming language runtime";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "python";
    }

    @ItemSeed(key = JavaScript.KEY)
    public static class JavaScript {
        public static final String KEY = "cg.language:javascript";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "JavaScript programming language runtime";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "javascript";
    }

    @ItemSeed(key = Rust.KEY)
    public static class Rust {
        public static final String KEY = "cg.language:rust";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemSeed.Frame(key = {SememeGloss.KEY, Language.ENGLISH_KEY})
        static final String gloss = "Rust programming language runtime";

        @ItemSeed.Word(lang = Language.ENGLISH_KEY, pos = PartOfSpeech.Noun.KEY, features = {GrammaticalFeature.Lemma.KEY})
        static final String noun1 = "rust";
    }
}
