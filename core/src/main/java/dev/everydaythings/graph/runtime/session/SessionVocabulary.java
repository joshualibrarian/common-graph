package dev.everydaythings.graph.runtime.session;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.language.*;

import static dev.everydaythings.graph.Seed.*;

/**
 * View vocabulary — predicates for the view lifecycle on a {@link
 * Session}.
 *
 * <p>Each predicate is BOTH the frame type AND the command word a user types
 * to produce one. "view chess" creates an {@link ItemView} frame with
 * {@code THEME→chess}; the session reacts by opening a window. "close" creates
 * a {@link Close} frame; the session removes the view.
 *
 * <p>TODO: the OLD versions of {@link ItemView} and {@link Close} carried
 * behavior (an {@code onFrameAssembled} hook on the predicate's Sememe
 * subclass). In the new model that behavior lives on the Session item itself
 * via {@code @Handler} methods. This file currently carries only the seed
 * predicate data.
 */
public final class SessionVocabulary {

    private SessionVocabulary() {}

    /**
     * ITEM_VIEW — a persistent view of an item within a session.
     *
     * <p>Body shape:
     * <pre>
     * ITEM_VIEW { THEME → item, LOCATION → session }
     * </pre>
     */
    @Item(key = ItemView.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class ItemView {
        public static final String KEY = "cg.predicate:item-view";
        private ItemView() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "a persistent view of an item within a session";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "view";
    }

    /**
     * CLOSE — close a view of an item.
     *
     * <p>Removes the matching ITEM_VIEW frame from the session.
     */
    @Item(key = Close.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Close {
        public static final String KEY = "cg.predicate:close";
        private Close() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "close an open view of an item";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "close";
    }

    /**
     * DISPLAY_LAYOUT — positions a display within a session's unified
     * coordinate space.
     *
     * <p>Body shape:
     * <pre>
     * DISPLAY_LAYOUT { THEME → display, LOCATION → session }
     * </pre>
     */
    @Item(key = DisplayLayout.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class DisplayLayout {
        public static final String KEY = "cg.predicate:display-layout";
        private DisplayLayout() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "placement of a display within a session's coordinate space";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "display-layout";
    }
}
