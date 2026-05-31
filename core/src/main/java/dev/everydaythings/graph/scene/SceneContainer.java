package dev.everydaythings.graph.scene;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.item.BodyBinder;
import dev.everydaythings.graph.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.PartOfSpeech;
import dev.everydaythings.graph.ThematicRole;
import dev.everydaythings.graph.quality.LayoutVocabulary;
import dev.everydaythings.graph.value.Bool;
import dev.everydaythings.graph.value.Numeric;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * SceneContainer — the structural primitive.  Has children plus layout
 * properties that arrange them.
 *
 * <p>Inherits SceneNode's universal property surface (background,
 * border, transform, opacity, ...) and adds container-specific bindings:
 * the {@link LayoutVocabulary.LayoutMode LayoutMode} that selects how
 * children flow (Vertical / Horizontal / Grid / Stack / Flex), the
 * {@link LayoutVocabulary.Justify Justify} and {@link
 * LayoutVocabulary.Alignment Alignment} that distribute and align them,
 * {@code Wrap}, {@code Columns}/{@code Rows} for grids, optional
 * {@link LayoutVocabulary.Handedness Handedness} for handed layouts,
 * and a {@code Repeat} expression for data-driven iteration.
 *
 * <p>Children themselves are bindings under the
 * {@link SceneVocabulary.Children Children} role, ordered by binding
 * index.  Their typed-view enumeration is intentionally not surfaced as
 * an {@code @Seed.Property} field — list-valued binding sets need their
 * own reader pattern (forthcoming alongside the SceneResolver work).
 */
@Seed.Item(key = SceneContainer.KEY, head = SceneNode.KEY)
public class SceneContainer extends SceneNode {

    public static final String KEY = "cg.archetype:scene-container";

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "the structural scene primitive — has children plus the layout properties that "
                    + "arrange them";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY,
        qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "scene container";

    // ==================================================================================
    // Layout slots — target an instance sememe (LayoutMode → Vertical /
    // Horizontal / Grid / Stack / Flex; Justify → JustifyStart / ... /
    // SpaceAround; etc.).  Field type ItemRef carries the bare sememe
    // reference; no auto-EXPECTS derivation (ItemRef has no `KEY`
    // constant), which is fine — the constraint that these slots take
    // one of an enumerated set of instances is a stronger constraint
    // than TypeRef can express today.
    // ==================================================================================

    /** Layout-mode sememe — Vertical / Horizontal / Grid / Stack / Flex. */
    @Seed.Property(role = LayoutVocabulary.LayoutMode.KEY) protected ItemRef layoutMode;

    /** Main-axis distribution — JustifyStart / JustifyCenter / JustifyEnd / SpaceBetween / SpaceAround. */
    @Seed.Property(role = LayoutVocabulary.Justify.KEY)    protected ItemRef justify;

    /** Cross-axis alignment — Start / Center / End / Stretch. */
    @Seed.Property(role = LayoutVocabulary.Alignment.KEY)  protected ItemRef alignment;

    /** Optional handedness for handed layouts — RightHanded / LeftHanded. */
    @Seed.Property(role = LayoutVocabulary.Handedness.KEY) protected ItemRef handedness;

    /** Whether children wrap to the next line when the current line is full. */
    @Seed.Property(role = LayoutVocabulary.Wrap.KEY)       protected Bool wrap;

    /** Grid-layout column count. */
    @Seed.Property(role = LayoutVocabulary.Columns.KEY)    protected Numeric columns;

    /** Grid-layout row count. */
    @Seed.Property(role = LayoutVocabulary.Rows.KEY)       protected Numeric rows;

    /** Enforced width:height aspect ratio. */
    @Seed.Property(role = LayoutVocabulary.AspectRatio.KEY) protected Numeric aspectRatio;

    // ==================================================================================
    // Construction.
    // ==================================================================================

    public SceneContainer(Body body) {
        super(ItemRef.iid(KEY), body.bindings());
        BodyBinder.bind(this, body);
    }

    /** Typed view: dispatched from {@link SceneNode#from(Body)} on SceneContainer-headed bodies. */
    public static SceneContainer from(Body body) {
        Objects.requireNonNull(body, "body");
        if (body instanceof SceneContainer sc) return sc;
        if (!ItemRef.iid(KEY).equals(body.headRef())) {
            throw new IllegalArgumentException(
                    "Body head is not the SceneContainer archetype: " + body.headRef());
        }
        return new SceneContainer(body);
    }

    // ==================================================================================
    // Children — typed-view enumeration of the Children bindings.
    //
    // Lazily-built and cached so every caller sees the same {@link SceneNode}
    // instances.  This is what makes layout-then-paint work: the Presenter
    // assigns {@link SceneNode#bounds(Bounds)} during layout, and the painter
    // walks via {@link #children()} on the next render-pipeline stage and
    // gets the same wrappers with bounds populated.
    //
    // The cache is per-instance; a SceneContainer constructed from a fresh
    // Body on the next render tick will rebuild its own children, which is
    // what we want — layout outputs are per-tick.
    // ==================================================================================

    private List<SceneNode> cachedChildren;

    /**
     * The container's children as typed {@link SceneNode}s in declaration /
     * index order.  Cached on first call so subsequent calls return the
     * same instances — important for layout-then-paint pipelines that
     * annotate the wrappers with computed {@link Bounds}.
     */
    public List<SceneNode> children() {
        if (cachedChildren == null) {
            ItemRef childrenRole = ItemRef.iid(SceneVocabulary.Children.KEY);
            List<Binding> matches = new ArrayList<>();
            for (Binding b : bindings()) {
                if (childrenRole.equals(b.role()) && b.target() instanceof Body) {
                    matches.add(b);
                }
            }
            matches.sort(Comparator.comparing(
                    b -> b.index() == null ? Long.MAX_VALUE : b.index(),
                    Comparator.nullsLast(Comparator.naturalOrder())));
            List<SceneNode> built = new ArrayList<>(matches.size());
            for (Binding b : matches) {
                built.add(SceneNode.from((Body) b.target()));
            }
            cachedChildren = List.copyOf(built);
        }
        return cachedChildren;
    }
}
