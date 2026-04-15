package dev.everydaythings.graph.media;

import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.frame.ItemFrame.Bind;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.SememeGloss;
import dev.everydaythings.graph.language.ThematicRole;

/**
 * Seed vocabulary for media predicates.
 *
 * <p>Contains predicates shared across media types: photographs, video,
 * audio, documents.  Discovered by SeedVocabulary via classpath scanning.
 */
public final class MediaVocabulary {

    private MediaVocabulary() {}

    // ==================================================================================
    // Media predicates
    // ==================================================================================

    /** Asserts that a media item depicts or shows a subject. */
    @ItemSeed(key = Depicts.KEY)
    public static class Depicts {
        public static final String KEY = "cg.media:depicts";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "asserts that a media item depicts or shows a subject";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"depicts", "shows"};

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY,
                                   qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY, CoreVocabulary.Required.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY,
                                   qualifiers = {ThematicRole.KEY, ThematicRole.Topic.KEY, CoreVocabulary.Required.KEY}))
        static final ItemID expectTopic = ThematicRole.Topic.IID;
    }

    /** Carries the actual image content in one or more formats. */
    @ItemSeed(key = Image.KEY)
    public static class Image {
        public static final String KEY = "cg.media:image";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "carries image content — the actual visual data in one or more formats";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"image"};

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY,
                                   qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY, CoreVocabulary.Required.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY,
                                   qualifiers = {ThematicRole.KEY, ThematicRole.Value.KEY, CoreVocabulary.Required.KEY}))
        static final ItemID expectValue = ThematicRole.Value.IID;
    }

    /** Records the capture event — when, with what instrument, under what conditions. */
    @ItemSeed(key = Captured.KEY)
    public static class Captured {
        public static final String KEY = "cg.media:captured";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "records the capture event — when, with what instrument, under what conditions";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"captured", "taken"};

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY,
                                   qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY, CoreVocabulary.Required.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;
    }
}
