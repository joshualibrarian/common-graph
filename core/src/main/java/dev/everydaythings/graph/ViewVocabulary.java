package dev.everydaythings.graph;

import dev.everydaythings.graph.id.ItemID;
import dev.everydaythings.graph.linguistics.GrammaticalFeature;
import dev.everydaythings.graph.linguistics.Gloss;
import dev.everydaythings.graph.linguistics.Language;
import dev.everydaythings.graph.linguistics.Lexeme;
import dev.everydaythings.graph.linguistics.PartOfSpeech;
import dev.everydaythings.graph.semantics.ThematicRole;
import static dev.everydaythings.graph.Seed.*;

/**
 * View vocabulary — predicates for the view lifecycle on a {@link
 * dev.everydaythings.graph.runtime.Session}.
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
public final class ViewVocabulary {

    private ViewVocabulary() {}

    /**
     * ITEM_VIEW — a persistent view of an item within a session.
     *
     * <p>Body shape:
     * <pre>
     * ITEM_VIEW { THEME → item, LOCATION → session }
     * </pre>
     */
    @Seed.Item(key = ItemView.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class ItemView {
        public static final String KEY = "cg.predicate:item-view";
        public static final ItemID IID = ItemID.fromString(KEY);
        private ItemView() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "a persistent view of an item within a session";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishVerbLemma = "view";
    }

    /**
     * CLOSE — close a view of an item.
     *
     * <p>Removes the matching ITEM_VIEW frame from the session.
     */
    @Seed.Item(key = Close.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class Close {
        public static final String KEY = "cg.predicate:close";
        public static final ItemID IID = ItemID.fromString(KEY);
        private Close() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "close an open view of an item";

        @Frame(predicate = Lexeme.KEY,
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
    @Seed.Item(key = DisplayLayout.KEY, head = CoreVocabulary.Predicate.KEY)
    public static final class DisplayLayout {
        public static final String KEY = "cg.predicate:display-layout";
        public static final ItemID IID = ItemID.fromString(KEY);
        private DisplayLayout() {}

        @Frame(predicate = Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "placement of a display within a session's coordinate space";

        @Frame(predicate = Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "display-layout";
    }
}
