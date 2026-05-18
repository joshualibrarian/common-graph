package dev.everydaythings.graph.scene;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.quality.LayoutVocabulary;
import dev.everydaythings.graph.quality.SpatialVocabulary;
import dev.everydaythings.graph.quality.VisualVocabulary;
import dev.everydaythings.graph.value.Bool;
import dev.everydaythings.graph.value.Color;
import dev.everydaythings.graph.value.Length;
import dev.everydaythings.graph.value.Numeric;
import dev.everydaythings.graph.value.Value;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * SceneNode — the abstract parent archetype for all scene-tree nodes.
 *
 * <p>SceneNode is not directly instantiable.  Concrete scene primitives
 * are subarchetypes with their own role-specific bindings:
 *
 * <ul>
 *   <li><b>Container</b> — has {@code children} plus layout properties
 *       (LayoutMode, Justify, Alignment, Gap, Wrap, Columns, Rows, ...).</li>
 *   <li><b>Text</b> — has {@code text} (literal string) or {@code tokens}
 *       (sememe references the language layer resolves to displayable
 *       text) plus typography properties.</li>
 *   <li><b>SceneBody</b> — the visual primitive.  Carries {@code Shape},
 *       {@code Image}, {@code Model}, {@code Glyph}, or {@code Alt}
 *       across the fidelity spectrum.  Named {@code SceneBody} to avoid
 *       collision with {@link Body datum.Body} which it extends
 *       structurally but is semantically distinct.</li>
 * </ul>
 *
 * <p>Each subarchetype inherits the universal property surface — every
 * property in the CG quality vocabulary applies to every node type.  A
 * text node can have a background color, a body node can be rotated, a
 * container can have opacity.  Properties are carried as bindings using
 * existing qualities ({@code Width}, {@code Background},
 * {@code Padding}, ...) — no per-type schema; the renderer reads only
 * what's relevant for the node's kind.
 *
 * <p>The pattern parallels {@link dev.everydaythings.graph.value.Endpoint
 * Endpoint} → TcpEndpoint/UnixEndpoint and
 * {@link dev.everydaythings.graph.value.Gradient Gradient} → LinearGradient/RadialGradient:
 * an abstract value-archetype with no minted instances of its own,
 * refined by subarchetypes with their own shapes.
 *
 * <p>For the design rationale, the three-primitive philosophy, the
 * full property surface, and the pipeline (Resolver → Presenter →
 * Painter), see {@code docs/scene.md}.
 */
@Seed.Item(key = SceneNode.KEY, head = Value.KEY)
public abstract class SceneNode extends Value {

    public static final String KEY = "cg.archetype:scene-node";

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "the abstract archetype of scene-tree nodes — refined by Container, Text, "
                    + "and SceneBody subarchetypes that share a universal property surface "
                    + "(layout, background, typography, transform, interaction, etc.)";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY,
        qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "scene node";

    // ==================================================================================
    // Universal property slots.
    //
    // Each {@code @Seed.Property} instance field declares both:
    //   1. an EXPECTS on the SceneNode archetype's manifest (the field's
    //      Java type maps to a TypeRef constraint — Length → TypeRef(Length)
    //      etc.) — picked up by SeedProcessor at bootstrap;
    //   2. a runtime binding slot — populated from a body's matching
    //      binding by {@link dev.everydaythings.graph.item.BodyBinder
    //      BodyBinder} at instance construction.
    //
    // All slots are optional; an absent binding leaves the field null
    // (the cascade fills in defaults at the presenter stage).  Concrete
    // subarchetypes (Container, Text, SceneBody) inherit these and add
    // their own role-specific fields.
    //
    // Properties whose target type isn't cleanly a single value-archetype
    // (Border-per-side, Transform-per-axis, Events, State, When,
    // Animation/Transition multi-field bundles, Cursor/style sememe
    // instances) aren't declared here — they need either compound-binding
    // schema support or instance-of-archetype matchers, both pending.
    // ==================================================================================

    /** Foreground color of the node. */
    @Seed.Property(role = VisualVocabulary.Foreground.KEY) protected Color foreground;

    /** Background color of the node (gradients handled separately via a Gradient slot). */
    @Seed.Property(role = VisualVocabulary.Background.KEY) protected Color background;

    /** Width of the node. */
    @Seed.Property(role = SpatialVocabulary.Width.KEY)     protected Length width;

    /** Height of the node. */
    @Seed.Property(role = SpatialVocabulary.Height.KEY)    protected Length height;

    /** Minimum width — lower bound for layout. */
    @Seed.Property(role = SpatialVocabulary.MinWidth.KEY)  protected Length minWidth;

    /** Maximum width — upper bound for layout. */
    @Seed.Property(role = SpatialVocabulary.MaxWidth.KEY)  protected Length maxWidth;

    /** Minimum height — lower bound for layout. */
    @Seed.Property(role = SpatialVocabulary.MinHeight.KEY) protected Length minHeight;

    /** Maximum height — upper bound for layout. */
    @Seed.Property(role = SpatialVocabulary.MaxHeight.KEY) protected Length maxHeight;

    /** Spacing between children in flow layouts. */
    @Seed.Property(role = LayoutVocabulary.Gap.KEY)        protected Length gap;

    /** Elevation — signed; positive raises (drop shadow / Z displacement), negative recesses. */
    @Seed.Property(role = SpatialVocabulary.Elevation.KEY) protected Length elevation;

    /** Opacity — semantically 0..1; range constraint pending the matcher work. */
    @Seed.Property(role = VisualVocabulary.Opacity.KEY)    protected Numeric opacity;

    /** Visibility — whether the node renders. */
    @Seed.Property(role = VisualVocabulary.Visibility.KEY) protected Bool visibility;

    // ==================================================================================
    // Construction — subclass entry point.
    // ==================================================================================

    /**
     * Subclass constructor — each concrete SceneNode subarchetype calls
     * this with its own archetype IID as head and its node-specific
     * bindings (universal properties + role-specific content).
     */
    protected SceneNode(ItemRef head, List<Binding> bindings) {
        super(head, bindings);
    }

    // ==================================================================================
    // Universal property readers — the structurally-important ones.
    //
    // Most scene properties (background, font, transform, ...) are read
    // by the painter / presenter directly from bindings.  These two
    // readers (Id, Classes) appear here because cascade-selector logic
    // and event routing reference them universally, regardless of node
    // kind.
    //
    // Other universal readers can be added when concrete consumers
    // emerge that want them; until then, callers use the generic
    // Body.binding(...) lookup.
    // ==================================================================================

    /**
     * The stable string identifier for this node, or empty when none is
     * declared.  Used by id selectors in cascades, by anchor references,
     * and by event-target routing.
     */
    public Optional<String> id() {
        return readLiteral(SceneVocabulary.Id.KEY, String.class);
    }

    /**
     * The list of style-class names this node belongs to.  Empty when no
     * Classes binding is declared.  Used by class selectors in cascades
     * to apply shared styling across many nodes.
     */
    @SuppressWarnings("unchecked")
    public List<String> classes() {
        return readLiteral(SceneVocabulary.Classes.KEY, List.class)
                .map(list -> (List<String>) list)
                .orElseGet(List::of);
    }

    // ==================================================================================
    // Polymorphic from(Body) dispatcher.
    //
    // Stub for now — fleshed out as concrete Container / Text / SceneBody
    // subclasses land.
    // ==================================================================================

    /**
     * Typed view over an existing Body whose head is one of the SceneNode
     * subarchetypes.  Dispatches to the appropriate subclass's
     * {@code from(Body)} based on the body's head IID.
     */
    public static SceneNode from(Body body) {
        Objects.requireNonNull(body, "body");
        if (body instanceof SceneNode sn) return sn;
        ItemRef head = (body.headRef() instanceof ItemRef ir) ? ir : null;
        if (head == null) {
            throw new IllegalArgumentException(
                    "SceneNode body head must be an ItemRef, got " + body.headRef());
        }
        if (ItemRef.iid(SceneContainer.KEY).equals(head)) return SceneContainer.from(body);
        if (ItemRef.iid(SceneText.KEY).equals(head))      return SceneText.from(body);
        if (ItemRef.iid(SceneBody.KEY).equals(head))      return SceneBody.from(body);
        throw new IllegalArgumentException(
                "Body head is not a known SceneNode subarchetype: " + head);
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    /** Look up the first binding for a role and cast its literal target to the given type. */
    protected <T> Optional<T> readLiteral(String roleKey, Class<T> type) {
        ItemRef role = ItemRef.iid(roleKey);
        for (Binding b : bindings()) {
            if (role.equals(b.role()) && type.isInstance(b.target())) {
                return Optional.of(type.cast(b.target()));
            }
        }
        return Optional.empty();
    }
}
