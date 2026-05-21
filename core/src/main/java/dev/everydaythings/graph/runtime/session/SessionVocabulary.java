package dev.everydaythings.graph.runtime.session;

import dev.everydaythings.graph.CoreVocabulary;
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
     * ITEM_VIEW {
     *   Theme                    → item        // what's being viewed
     *   Location                 → session     // back-pointer to the session this view lives on
     *   Location[<device-iid>]   → Point       // handle anchor on that device's display
     *   Attribute[Expanded]      → Bool        // collapsed (handle-only) vs expanded
     *   Attribute[<Size-archetype>] → Size     // expanded box dimensions
     * }
     * </pre>
     *
     * <p>Theme is the item being viewed (the assertion is "about" the item;
     * "what's open in session X" is a reverse-lookup over the Location back-
     * pointer via FRAME_BY_TARGET).  Location does double duty — one binding
     * names the session, additional bindings carry a device-IID qualifier to
     * say WHERE on that device the view's handle is anchored.  Multi-display
     * mirroring uses multiple Location[device]:Point bindings.
     *
     * <p>{@link Expanded} is a {@code Quality} sememe used as the qualifier on
     * an {@code Attribute} binding whose target is a {@code Bool}.  Size is
     * the {@code GeometryVocabulary.Size} archetype.
     *
     * <p>Position coordinates and Size dimensions are {@link
     * dev.everydaythings.graph.value.Length Length} values, which carry their
     * own units (px, em, rem, ...).  Viewport-relative units (vh, vw, %, ...)
     * arrive when the layout engine's variable-resolution system is wired
     * (see UnitVocabulary's TODO).
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
     * Expanded — the expansion-state qualifier for an {@link ItemView}.
     *
     * <p>Used as the qualifier on an {@code Attribute[Expanded]: Bool} binding.
     * {@code true} = the window's handle plus content scene are both shown;
     * {@code false} = handle-only (collapsed).  The handle is always drawn
     * when the window exists; expansion just controls whether the content
     * scene is drawn below it.
     */
    @Item(key = Expanded.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Expanded {
        public static final String KEY = "cg.quality:expanded";
        private Expanded() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "whether a window is showing its content scene or only its handle";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Adjective.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishAdjectiveLemma = "expanded";
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
