package dev.everydaythings.graph.language;

import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.frame.ItemFrame.Bind;
import dev.everydaythings.graph.item.Implements;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.id.ItemID;

/**
 * View vocabulary seeds — predicates for the view lifecycle.
 *
 * <p>Each predicate is BOTH the frame type AND the command word.
 * "view chess" uses the ITEM_VIEW predicate — the English word "view"
 * is a lexeme pointing at the same sememe as the frame predicate.
 *
 * @see dev.everydaythings.graph.frame.ViewConfig
 */
public final class ViewVocabulary {

    private ViewVocabulary() {}

    // ==================================================================================
    // PREDICATES (which are also the "commands")
    // ==================================================================================

    /**
     * ITEM_VIEW — a persistent view of an item within a session.
     *
     * <p>The predicate for view frames on a Session, AND the word the user
     * types to create one. "view chess" = create an ITEM_VIEW frame with
     * THEME=chess. The session reacts by opening a window.
     */
    @Implements(ItemView.KEY)
    @ItemSeed(key = ItemView.KEY)
    public static class ItemView extends Sememe {
        public static final String KEY = "cg.sememe:item-view";
        public static final ItemID IID = ItemID.fromString(KEY);

        public ItemView() { super(KEY); }
        protected ItemView(ItemID iid) { super(iid); }
        protected ItemView(dev.everydaythings.graph.runtime.Librarian lib,
                           dev.everydaythings.graph.item.Manifest m) { super(lib, m); }  // cross-module refs required

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a persistent view of an item within a session";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"view", "open"};

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Location.KEY}))
        static final ItemID expectLocation = ThematicRole.Location.IID;

        // Behavior: Session handles this in its onFrameAssembled override.
        // The predicate itself doesn't need to act — the LOCATION target (session) does.
    }

    /**
     * CLOSE — close a view of an item.
     *
     * <p>Removes the ITEM_VIEW frame from the session.
     */
    @Implements(Close.KEY)
    @ItemSeed(key = Close.KEY)
    public static class Close extends Sememe {
        public static final String KEY = "cg.sememe:close";
        public static final ItemID IID = ItemID.fromString(KEY);

        public Close() { super(KEY); }
        protected Close(ItemID iid) { super(iid); }
        protected Close(dev.everydaythings.graph.runtime.Librarian lib,
                        dev.everydaythings.graph.item.Manifest m) { super(lib, m); }

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "close an open view of an item";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"close"};

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;

        // Behavior: Session handles this in its onFrameAssembled override.
    }

    /**
     * Predicate for display layout frames on a Session.
     *
     * <p>A DISPLAY_LAYOUT frame on a Session positions a display within the
     * session's unified coordinate space. THEME identifies the display,
     * LOCATION identifies the session.
     */
    @ItemSeed(key = DisplayLayout.KEY)
    public static class DisplayLayout {
        public static final String KEY = "cg.sememe:display-layout";
        public static final ItemID IID = ItemID.fromString(KEY);

        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "placement of a display within a session's coordinate space";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Value.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}, index = true))
        static final String[] words = {"display-layout"};

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Theme.KEY}))
        static final ItemID expectTheme = ThematicRole.Theme.IID;

        @ItemFrame(predicate = CoreVocabulary.Expects.KEY,
                   fieldAs = @Bind(role = ThematicRole.Topic.KEY, qualifiers = {ThematicRole.KEY, ThematicRole.Location.KEY}))
        static final ItemID expectLocation = ThematicRole.Location.IID;
    }

}
