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

    // ==================================================================================
    // Record-binding roles — top-level scene declarations on an item's record.
    // ==================================================================================

    /**
     * Scene — the single sememe naming the scene system as a whole.
     *
     * <p>One sememe, used in two structurally-related ways:
     * <ul>
     *   <li><b>Binding role:</b> A {@code Scene → <body>} record binding
     *       attaches a scene-tree to the item the record points to.  The
     *       cascade walks records' Scene bindings; the qualifier (when
     *       present) selects the presentation form ({@code Scene[Handle]},
     *       {@code Scene[Aura]}, default Scene).</li>
     *   <li><b>Conceptual umbrella:</b> The Scene sememe is the family
     *       label for the scene system's archetypes — {@link SceneNode}
     *       (and its subarchetypes Container/Text/Body) and
     *       {@link SceneStyle}.  Both produce, in different shapes, the
     *       data that fills Scene-role bindings.</li>
     * </ul>
     *
     * <p>The dual usage is intentional, not a naming overload.  In both
     * cases we're referring to the same concept of "the scene system."
     * Resolved by {@link SceneCascade} walking the archetype chain looking
     * for the matching {@code Scene[qualifier]} record binding.
     */
    @Seed.Item(key = Scene.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Scene {
        public static final String KEY = "cg.role:scene";
        private Scene() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "record-binding role declaring the scene-tree to apply to the item this record points to";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "scene";
    }

    /**
     * Style — record-binding role naming a single style declaration (a
     * pattern + properties-to-apply) that the resolver merges onto matching
     * scene nodes during the cascade.  Multiple Style bindings per record
     * are supported (indexed for precedence when the matches overlap).
     *
     * <p>A style body has the {@link SceneStyle} archetype as its head, one
     * {@link Pattern} binding whose target is a query body (the match
     * pattern), and additional bindings that name the properties to apply.
     */
    @Seed.Item(key = Style.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Style {
        public static final String KEY = "cg.role:style";
        private Style() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "record-binding role declaring a style — a query pattern + the properties to apply to matches";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "style";
    }

    /**
     * SceneStyle — the archetype for style bodies.  Distinct from the
     * structural {@code SceneContainer}/{@code SceneText}/{@code SceneBody}
     * archetypes: style bodies don't get rendered themselves; they describe
     * what to apply to nodes that match their {@link Pattern} binding.
     *
     * <p>Style body shape:
     * <pre>
     * Body{
     *   head:    SceneStyle
     *   Pattern: Body{ head: ?SceneNode  ... match patterns ... }
     *   &lt;property bindings to apply to matches&gt;
     * }
     * </pre>
     */
    @Seed.Item(key = SceneStyle.KEY)
    public static final class SceneStyle {
        public static final String KEY = "cg.archetype:scene-style";
        private SceneStyle() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the archetype for style bodies — pattern + properties to apply to matching scene nodes";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "scene style";
    }

    /**
     * Pattern — binding role on a {@link SceneStyle} body whose target is the
     * query body to match against scene nodes.  Sibling property bindings
     * on the same SceneStyle are the assertions applied to each match.
     */
    @Seed.Item(key = Pattern.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Pattern {
        public static final String KEY = "cg.role:pattern";
        private Pattern() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "binding role on a scene-style body holding the query pattern to match against scene nodes";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "pattern";
    }

    // ==================================================================================
    // Scene-form qualifiers — disambiguate alternate presentations of an item.
    //
    // Bindings carry a compound key: role + qualifiers.  The default Scene
    // binding has no qualifiers and represents the item's primary / full
    // presentation.  A Scene[Handle] binding represents the compact form
    // (chip, badge, what appears inside a chain or list).  A Scene[Aura]
    // binding represents the per-item overlay framework (the styled template
    // for swarms, breadcrumbs, ambient activity rendered around the item).
    //
    // These qualifiers compose with each other and with device qualifiers
    // (when those land) via CompoundKey's multi-qualifier support, so
    // Scene[Handle, Mobile] is a real address future code can reach.
    // ==================================================================================

    /**
     * Handle — qualifier marking a scene as the compact / glanceable form
     * of an item.  Renders inside chains, lists, swarm dots, breadcrumbs —
     * anywhere the item needs to appear small but recognizable.  Distinct
     * from the default Scene which fills an ItemView at full presentation.
     */
    @Seed.Item(key = Handle.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Handle {
        public static final String KEY = "cg.qualifier:handle";
        private Handle() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "qualifier marking a scene as the compact glanceable form of an item — "
                        + "rendered in chains, lists, swarm dots, breadcrumbs";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "handle";
    }

    /**
     * Aura — qualifier marking a scene as the per-item overlay framework.
     * Each item's Aura scene is the styled template for the ambient
     * activity rendered around that item: notification swarms, navigation
     * breadcrumbs, hover-expanded chains, drag previews bound to it.  The
     * compositor activates an item's Aura when there's content to show
     * (a reaction arrived; a chain is open here); otherwise it stays
     * dormant and draws nothing.
     */
    @Seed.Item(key = Aura.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Aura {
        public static final String KEY = "cg.qualifier:aura";
        private Aura() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "qualifier marking a scene as the per-item overlay framework — the styled "
                        + "template for ambient activity rendered around the item (swarms, "
                        + "breadcrumbs, chains, drag previews)";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "aura";
    }

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

    // ==================================================================================
    // Container content.
    // ==================================================================================

    /**
     * Children — a contained scene node within a {@link
     * dev.everydaythings.graph.scene.SceneContainer SceneContainer}.
     * One Children binding per child; ordering via the binding's index
     * field.  Target is a nested Body (a SceneNode subarchetype).
     */
    @Seed.Item(key = Children.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Children {
        public static final String KEY = "cg.quality:children";
        private Children() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "a contained scene node within a container; one Children binding per child, "
                        + "ordering via the binding's index field";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "children";
    }

    // ==================================================================================
    // Text content.
    // ==================================================================================

    /**
     * Text — literal text content of a {@link
     * dev.everydaythings.graph.scene.SceneText SceneText} node.  Target
     * is a String.  Mutually-exclusive with {@link Tokens}; use Text for
     * literal strings (user content, code, debug output), Tokens for
     * semantic text that should resolve through the language layer.
     */
    @Seed.Item(key = Text.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Text {
        public static final String KEY = "cg.quality:text";
        private Text() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "literal text content of a SceneText node";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "text";
    }

    /**
     * Tokens — semantic text content of a {@link
     * dev.everydaythings.graph.scene.SceneText SceneText} node.  Each
     * Tokens binding's target is a sememe reference; the language layer
     * resolves it to a display string in the user's language at render
     * time.  Multiple Tokens bindings (ordered by index) compose a
     * sentence.
     */
    @Seed.Item(key = Tokens.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Tokens {
        public static final String KEY = "cg.quality:tokens";
        private Tokens() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "semantic text content of a SceneText node — sememe references resolved by "
                        + "the language layer to display strings in the user's language";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "tokens";
    }

    /**
     * Format — MIME-type sememe naming how literal text should be
     * interpreted ({@code text/plain}, {@code text/markdown}, {@code
     * application/json}, etc.).  Lets a SceneText node carry rich-format
     * content with the renderer applying the appropriate display
     * conventions.  Target is an Encoding sememe.
     */
    @Seed.Item(key = Format.KEY, head = CoreVocabulary.Quality.KEY)
    public static final class Format {
        public static final String KEY = "cg.quality:format";
        private Format() {}

        @Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "MIME-type sememe naming how literal text should be interpreted";

        @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Binding(role = ThematicRole.Value.KEY,
            qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "format";
    }
}
