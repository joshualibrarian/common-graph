package dev.everydaythings.graph.scene;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;

import static dev.everydaythings.graph.Seed.*;

/**
 * Scene vocabulary — sememes for the structural / identity / conditional
 * aspects of scene nodes that aren't covered by general-purpose vocabulary
 * (layout, visual, typography, animation).
 *
 * <p>Most scene-node properties are general-purpose qualities (Width,
 * Background, FontSize, Padding, ...) that live in their respective
 * {@code quality/} vocabularies.  This file holds the scene-specific
 * structural sememes:
 *
 * <ul>
 *   <li><b>Identity</b> — {@link Id}, {@link Classes} (selectors).</li>
 *   <li><b>Data binding</b> — {@link FrameRef} (data source binding for a node).</li>
 *   <li><b>State</b> — {@link State} (per-node declared state with defaults).</li>
 *   <li><b>Conditional cascade</b> — {@link When} blocks keyed by selectors.</li>
 *   <li><b>Selectors</b> — {@link ClassSelector}, {@link IdSelector},
 *       {@link StateSelector}, {@link InteractionSelector}.</li>
 * </ul>
 *
 * <p>SceneNode (the abstract value archetype) and its three concrete
 * subarchetypes (Container, Text, SceneBody) live alongside this file in
 * {@code scene/} and use these qualities + the broader CG vocabulary.
 */
public final class SceneVocabulary {

    private SceneVocabulary() {}

    // ==================================================================================
    // Identity — selectors and identifiers for nodes.
    // ==================================================================================

    /**
     * Id — a stable string identifier for a scene node.  Used by id
     * selectors in cascades, by anchor references, and by event-target
     * routing.  Target is a String.
     */
    @Seed.Item(key = Id.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Id {
        public static final String KEY = "cg.quality:scene-id";
        private Id() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "a stable string identifier for a scene node";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "id";
    }

    /**
     * Classes — a list of style-class names a scene node belongs to.
     * Used by class selectors in cascades to apply shared styling across
     * many nodes.  Target is a list of strings.
     */
    @Seed.Item(key = Classes.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Classes {
        public static final String KEY = "cg.quality:classes";
        private Classes() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "a list of style-class names a scene node belongs to; used by class "
                        + "selectors in cascades";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "classes";
    }

    /**
     * FrameRef — a binding-expression target identifying the data source
     * (a frame, a binding path, a query) this node's content is bound to.
     * Evaluated by the resolver against live data, producing concrete
     * resolved values that drive node content.
     *
     * <p>Java name {@code FrameRef} avoids collision with the structural
     * {@link dev.everydaythings.graph.datum.Frame Frame} concept; the
     * sememe key remains the natural {@code cg.quality:frame}.
     */
    @Seed.Item(key = FrameRef.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class FrameRef {
        public static final String KEY = "cg.quality:frame";
        private FrameRef() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "a binding-expression identifying the data source this node's content is "
                        + "bound to; evaluated by the resolver against live data";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "frame";
    }

    // ==================================================================================
    // State — per-node declared state slots with defaults.
    // ==================================================================================

    /**
     * State — a map of declared state slots (key → default value) for a
     * scene node.  Interaction handlers can mutate these slots ({@code
     * toggle:key}, {@code set:key=value}); {@link When} blocks use state
     * selectors to apply property overrides based on current values.
     */
    @Seed.Item(key = State.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class State {
        public static final String KEY = "cg.quality:state";
        private State() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "a map of declared state slots (key → default value) for a scene node";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "state";
    }

    // ==================================================================================
    // Conditional cascade — when-blocks with selector qualifiers.
    // ==================================================================================

    /**
     * When — a conditional override block on a scene node.  Each When
     * binding declares a selector (class, id, state, interaction) and the
     * property overrides that apply when the selector matches.  The
     * resolver evaluates item-state selectors; the presenter evaluates
     * interaction-state selectors.
     */
    @Seed.Item(key = When.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class When {
        public static final String KEY = "cg.quality:when";
        private When() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "a conditional override block — a selector plus the property overrides "
                        + "that apply when the selector matches";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Adverb.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishAdverbLemma = "when";
    }

    // ==================================================================================
    // Selectors — the kinds of conditions a When block can use.
    // ==================================================================================

    /**
     * ClassSelector — matches scene nodes that belong to a given style
     * class.  Target is a class name (string).
     */
    @Seed.Item(key = ClassSelector.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class ClassSelector {
        public static final String KEY = "cg.quality:class-selector";
        private ClassSelector() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "selector matching scene nodes that belong to a given style class";
    }

    /**
     * IdSelector — matches the scene node with a given id.  Target is an
     * id (string).
     */
    @Seed.Item(key = IdSelector.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class IdSelector {
        public static final String KEY = "cg.quality:id-selector";
        private IdSelector() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "selector matching the scene node with a given id";
    }

    /**
     * StateSelector — matches when a piece of item-side state is truthy
     * (or matches a literal value).  Evaluated by the resolver
     * (librarian-side) since item state is part of the data model, not
     * window-local.  Target is a state-key (with optional value).
     */
    @Seed.Item(key = StateSelector.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class StateSelector {
        public static final String KEY = "cg.quality:state-selector";
        private StateSelector() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "selector matching when a piece of item-side state is truthy or matches a "
                        + "literal value";
    }

    /**
     * InteractionSelector — matches when a window-side interaction state
     * is active (hover, selected, expanded, focused, pressed, ...).
     * Evaluated by the presenter (window-side) since interaction state is
     * per-window and never crosses the wire.  Target is a state-key.
     */
    @Seed.Item(key = InteractionSelector.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class InteractionSelector {
        public static final String KEY = "cg.quality:interaction-selector";
        private InteractionSelector() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "selector matching when a window-side interaction state is active "
                        + "(hover, selected, expanded, focused, pressed, ...)";
    }
}
