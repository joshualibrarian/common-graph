package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Item.Seed;

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
    public static class ItemView {
        public static final String KEY = "cg.sememe:item-view";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "a persistent view of an item within a session")
                .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "item-view")
                .slot(ThematicRole.Theme.KEY)
                .slot(ThematicRole.Location.KEY);
    }

    /**
     * Predicate for display frames on a Host.
     *
     * <p>A DISPLAY frame on a Host represents a physical display device
     * attached to that host. THEME identifies the host.
     */
    public static class Display {
        public static final String KEY = "cg.sememe:display";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "a physical display device attached to a host")
                .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "display")
                .slot(ThematicRole.Theme.KEY);
    }

    /**
     * Predicate for display layout frames on a Session.
     *
     * <p>A DISPLAY_LAYOUT frame on a Session positions a display within the
     * session's unified coordinate space. THEME identifies the display,
     * LOCATION identifies the session.
     */
    public static class DisplayLayout {
        public static final String KEY = "cg.sememe:display-layout";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "placement of a display within a session's coordinate space")
                .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "display-layout")
                .slot(ThematicRole.Theme.KEY)
                .slot(ThematicRole.Location.KEY);
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
    public static class View {
        public static final String KEY = "cg.verb:view";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "open a persistent view of an item")
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "view")
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "open")
                .slot(ThematicRole.Theme.KEY);
    }

    /**
     * Close a view of an item.
     *
     * <p>Removes the ITEM_VIEW frame from the session and navigates back.
     */
    public static class Close {
        public static final String KEY = "cg.verb:close";
        @Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss(Sememe.ENG, "close an open view of an item")
                .word(PartOfSpeech.VERB, GrammaticalFeature.Lemma.SEED, Sememe.ENG, "close")
                .slot(ThematicRole.Theme.KEY);
    }
}
