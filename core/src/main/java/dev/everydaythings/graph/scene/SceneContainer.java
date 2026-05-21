package dev.everydaythings.graph.scene;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.item.BodyBinder;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.quality.LayoutVocabulary;
import dev.everydaythings.graph.value.Bool;
import dev.everydaythings.graph.value.Numeric;

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
}
