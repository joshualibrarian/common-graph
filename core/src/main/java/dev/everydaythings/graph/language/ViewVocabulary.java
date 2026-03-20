package dev.everydaythings.graph.language;

import dev.everydaythings.graph.frame.ItemFrame;
import dev.everydaythings.graph.frame.ItemFrame.Bind;
import dev.everydaythings.graph.item.ItemSeed;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;

/**
 * View vocabulary seeds — predicates and verbs for the ITEM_VIEW lifecycle.
 *
 * <p>Defines the semantic concepts needed for viewing items: the ITEM_VIEW
 * predicate (a frame on Session tracking an open view), and the view/close
 * verbs that create and remove those frames.
 *
 * @see dev.everydaythings.graph.frame.ViewConfig
 */
public final class ViewVocabulary {

    private ViewVocabulary() {}

    // ==================================================================================
    // PREDICATES
    // ==================================================================================

    /**
     * Predicate for view frames on a Session.
     *
     * <p>An ITEM_VIEW frame on a Session represents an open view of an item.
     * THEME identifies the viewed item; LOCATION identifies the session.
     */
    @ItemSeed(key = ItemView.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Location.KEY})
    public static class ItemView {
        public static final String KEY = "cg.sememe:item-view";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a persistent view of an item within a session";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"item-view"};
    }

    /**
     * Predicate for display frames on a Host.
     *
     * <p>A DISPLAY frame on a Host represents a physical display device
     * attached to that host. THEME identifies the host.
     */
    @ItemSeed(key = Display.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Display {
        public static final String KEY = "cg.sememe:display";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "a physical display device attached to a host";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"display"};
    }

    /**
     * Predicate for display layout frames on a Session.
     *
     * <p>A DISPLAY_LAYOUT frame on a Session positions a display within the
     * session's unified coordinate space. THEME identifies the display,
     * LOCATION identifies the session.
     */
    @ItemSeed(key = DisplayLayout.KEY, slots = {ThematicRole.Theme.KEY, ThematicRole.Location.KEY})
    public static class DisplayLayout {
        public static final String KEY = "cg.sememe:display-layout";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "placement of a display within a session's coordinate space";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"display-layout"};
    }

    // ==================================================================================
    // VERBS
    // ==================================================================================

    /**
     * Open a view of an item.
     *
     * <p>Creates an ITEM_VIEW frame on the session for the target item
     * and navigates into it.
     */
    @ItemSeed(key = View.KEY, slots = {ThematicRole.Theme.KEY})
    public static class View {
        public static final String KEY = "cg.verb:view";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "open a persistent view of an item";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"view", "open"};
    }

    /**
     * Close a view of an item.
     *
     * <p>Removes the ITEM_VIEW frame from the session and navigates back.
     */
    @ItemSeed(key = Close.KEY, slots = {ThematicRole.Theme.KEY})
    public static class Close {
        public static final String KEY = "cg.verb:close";
        public static final ItemID IID = ItemID.fromString(KEY);
        @ItemFrame(predicate = SememeGloss.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY, qualifiers = {Language.ENGLISH_KEY}))
        static final String gloss = "close an open view of an item";

        @ItemFrame(predicate = CoreVocabulary.Lexeme.KEY,
                   fieldAs = @Bind(role = ThematicRole.Name.KEY,
                                   qualifiers = {Language.ENGLISH_KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String[] words = {"close"};
    }
}
