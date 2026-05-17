package dev.everydaythings.graph.quality;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;

import static dev.everydaythings.graph.Seed.*;

/**
 * Interaction vocabulary — sememes for the static interaction surface of
 * a scene node: which events it handles, whether it accepts keyboard
 * focus, whether its content is editable, and what cursor to show on hover.
 *
 * <p>Event predicates themselves (Click, Hover, KeyPress, PointerDown,
 * etc.) live in {@link InputVocabulary}; this file handles the node-side
 * <i>declaration</i> of what events are listened-to and how the node
 * presents itself to user input.
 */
public final class InteractionVocabulary {

    private InteractionVocabulary() {}

    // ==================================================================================
    // Interaction qualities.
    // ==================================================================================

    /**
     * Events — a declaration of which events a node handles, mapped to
     * action expressions.  Target is typically a map of event-predicate
     * IID → action expression, or a list of event-handler bindings.
     */
    @Seed.Item(key = Events.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Events {
        public static final String KEY = "cg.quality:events";
        private Events() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "a declaration of which events a node handles, mapped to action expressions";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "events";
    }

    /**
     * CapturesFocus — whether a node can receive keyboard focus and
     * participate in focus-traversal.  Boolean target.
     */
    @Seed.Item(key = CapturesFocus.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class CapturesFocus {
        public static final String KEY = "cg.quality:captures-focus";
        private CapturesFocus() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "whether a node can receive keyboard focus and participate in focus-traversal";
    }

    /**
     * Editable — whether a node's content can be edited by the user.
     * Boolean target.  For text-type nodes, enables direct text editing;
     * for other node types, semantics depend on the node kind.
     */
    @Seed.Item(key = Editable.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Editable {
        public static final String KEY = "cg.quality:editable";
        private Editable() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "whether a node's content can be edited by the user";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Adjective.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishAdjectiveLemma = "editable";
    }

    /**
     * Cursor — the cursor style shown when a pointer hovers over a node.
     * Target is one of the cursor sememes
     * ({@link DefaultCursor} / {@link PointerCursor} / {@link TextCursor}
     * / {@link WaitCursor} / {@link Crosshair} / {@link Grab} /
     * {@link Grabbing} / {@link NotAllowed} / {@link NoCursor}).
     */
    @Seed.Item(key = Cursor.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Cursor {
        public static final String KEY = "cg.quality:cursor";
        private Cursor() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the cursor style shown when a pointer hovers over a node";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "cursor";
    }

    // ==================================================================================
    // Cursor instances — the most common cursor styles.
    //
    // TODO: directional resize cursors (n-resize, s-resize, ew-resize,
    // nesw-resize, etc.) — add when concrete use cases emerge.
    // ==================================================================================

    /** The platform default cursor (typically an arrow). */
    @Seed.Item(key = DefaultCursor.KEY)
    public static final class DefaultCursor {
        public static final String KEY = "cg.cursor:default";
        private DefaultCursor() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "the platform default cursor (typically an arrow)";
    }

    /** A pointing hand — used to indicate a clickable element. */
    @Seed.Item(key = PointerCursor.KEY)
    public static final class PointerCursor {
        public static final String KEY = "cg.cursor:pointer";
        private PointerCursor() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "a pointing hand — used to indicate a clickable element";
    }

    /** An I-beam — used to indicate editable text. */
    @Seed.Item(key = TextCursor.KEY)
    public static final class TextCursor {
        public static final String KEY = "cg.cursor:text";
        private TextCursor() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "an I-beam — used to indicate editable text";
    }

    /** An hourglass / spinner — indicates the system is busy. */
    @Seed.Item(key = WaitCursor.KEY)
    public static final class WaitCursor {
        public static final String KEY = "cg.cursor:wait";
        private WaitCursor() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "an hourglass or spinner indicating the system is busy";
    }

    /** A crosshair — used for precise selection in drawing or image tools. */
    @Seed.Item(key = Crosshair.KEY)
    public static final class Crosshair {
        public static final String KEY = "cg.cursor:crosshair";
        private Crosshair() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "a crosshair — used for precise selection in drawing or image tools";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "crosshair";
    }

    /** An open hand — indicates the element can be grabbed/dragged. */
    @Seed.Item(key = Grab.KEY)
    public static final class Grab {
        public static final String KEY = "cg.cursor:grab";
        private Grab() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "an open hand indicating the element can be grabbed";
    }

    /** A closed hand — indicates an active grab/drag in progress. */
    @Seed.Item(key = Grabbing.KEY)
    public static final class Grabbing {
        public static final String KEY = "cg.cursor:grabbing";
        private Grabbing() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "a closed hand indicating an active grab in progress";
    }

    /** A no-entry sign — indicates an interaction that is not permitted. */
    @Seed.Item(key = NotAllowed.KEY)
    public static final class NotAllowed {
        public static final String KEY = "cg.cursor:not-allowed";
        private NotAllowed() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "a no-entry sign indicating an interaction is not permitted";
    }

    /** No cursor — the cursor is hidden over this element. */
    @Seed.Item(key = NoCursor.KEY)
    public static final class NoCursor {
        public static final String KEY = "cg.cursor:none";
        private NoCursor() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "no cursor — the cursor is hidden over this element";
    }
}
