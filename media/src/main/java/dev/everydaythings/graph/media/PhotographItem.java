package dev.everydaythings.graph.media;

import dev.everydaythings.graph.frame.FrameBodyOld;
import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.frame.ItemFrame.Bind;
import dev.everydaythings.graph.item.Implements;
import dev.everydaythings.graph.item.ItemOld;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.ManifestOld;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.SememeGloss;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.LibrarianOld;

/**
 * A photograph — an item carrying image data with structured metadata.
 *
 * <p>Photographs are among the simplest content items: image data plus
 * structured assertions about what is depicted, where and when it was
 * captured, and what occasion it documents.  No game logic, no state
 * machine.  Just frames.
 *
 * <p>A single photograph item can carry multiple IMAGE frames (several
 * shots of the same subject, different formats, thumbnails), and the
 * accumulation surface is open: anyone can attach DEPICTS, reaction,
 * or annotation frames after creation.
 */
@Implements(PhotographItem.Photograph.KEY)
public class PhotographItem extends ItemOld {

    @ItemSeed(key = Photograph.KEY)
    public static class Photograph {
        public static final String KEY = "cg.media:photograph";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a photograph — one or more images capturing a moment";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"photograph", "photo", "picture"};

        // EXPECTS — required frames
        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY,
                                   qualifiers = {FrameBodyOld.TYPE_KEY, CoreVocabulary.Required.KEY, MediaVocabulary.Image.KEY}))
        static final ItemID expectImage = MediaVocabulary.Image.IID;

        // EXPECTS — optional frames
        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY,
                                   qualifiers = {FrameBodyOld.TYPE_KEY, MediaVocabulary.Depicts.KEY}))
        static final ItemID expectDepicts = MediaVocabulary.Depicts.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY,
                                   qualifiers = {FrameBodyOld.TYPE_KEY, CoreVocabulary.Place.KEY}))
        static final ItemID expectPlace = CoreVocabulary.Place.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY,
                                   qualifiers = {FrameBodyOld.TYPE_KEY, CoreVocabulary.Occasion.KEY}))
        static final ItemID expectOccasion = CoreVocabulary.Occasion.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY,
                                   qualifiers = {FrameBodyOld.TYPE_KEY, MediaVocabulary.Captured.KEY}))
        static final ItemID expectCaptured = MediaVocabulary.Captured.IID;
    }

    // ==================================================================================
    // Constructors
    // ==================================================================================

    /** Fresh photograph. */
    public PhotographItem(LibrarianOld librarian) {
        super(librarian);
    }

    /** Hydration constructor. */
    @SuppressWarnings("unused")
    protected PhotographItem(LibrarianOld librarian, ManifestOld manifest) {
        super(librarian, manifest);
    }
}
