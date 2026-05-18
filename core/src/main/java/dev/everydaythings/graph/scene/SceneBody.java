package dev.everydaythings.graph.scene;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.item.BodyBinder;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.quality.MediaVocabulary;
import dev.everydaythings.graph.quality.ShapeVocabulary;
import dev.everydaythings.graph.value.Color;
import dev.everydaythings.graph.value.Length;

import java.util.Objects;

/**
 * SceneBody — the visual primitive.  Carries one or more
 * representations of a visual element at different fidelities; the
 * renderer uses the highest one it supports.
 *
 * <p>The fidelity ramp:
 *
 * <ul>
 *   <li><b>3D</b> — {@link MediaVocabulary.Model Model} reference
 *       (GLB / glTF).  Used by spatial renderers.</li>
 *   <li><b>2D shape</b> — {@link ShapeVocabulary.Shape Shape} sememe
 *       (Line / Circle / Rectangle / Sphere / Box / Cylinder / Cone) +
 *       {@link ShapeVocabulary.Fill Fill}, {@link ShapeVocabulary.StrokeColor
 *       StrokeColor}, {@link ShapeVocabulary.StrokeWidth StrokeWidth},
 *       {@link ShapeVocabulary.Radius Radius}.</li>
 *   <li><b>2D image</b> — {@link MediaVocabulary.Image Image} reference
 *       (SVG / PNG / JPEG / WebP / GIF).</li>
 *   <li><b>Text fallback</b> — {@link MediaVocabulary.Glyph Glyph}
 *       (single Unicode character) for text renderers.</li>
 *   <li><b>Description</b> — {@link MediaVocabulary.Alt Alt}
 *       accessibility text, last-resort representation.</li>
 * </ul>
 *
 * <p>A SceneBody typically carries several of these; the renderer
 * picks the best one it supports.  Body transforms (rotation, scale)
 * from the inherited SceneNode surface apply to whichever
 * representation renders.
 *
 * <p>Named {@code SceneBody} to avoid colliding with
 * {@link dev.everydaythings.graph.datum.Body datum.Body} which it
 * extends structurally but is semantically distinct (this is a scene
 * primitive; that is the data-model unit).
 */
@Seed.Item(key = SceneBody.KEY, head = SceneNode.KEY)
public class SceneBody extends SceneNode {

    public static final String KEY = "cg.archetype:scene-body";

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "the visual scene primitive — carries representations across the fidelity "
                    + "spectrum (3D model, 2D shape, 2D image, text glyph, alt description)";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY,
        qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "scene body";

    // ==================================================================================
    // Representation slots — one per fidelity level.  A body declares as
    // many as the author wants the renderer to be able to use.
    // ==================================================================================

    /** 3D mesh reference (GLB / glTF) — used by spatial renderers. */
    @Seed.Property(role = MediaVocabulary.Model.KEY)
    protected ItemRef model;

    /** Geometric shape sememe — Line / Circle / Rectangle / Sphere / Box / Cylinder / Cone. */
    @Seed.Property(role = ShapeVocabulary.Shape.KEY)
    protected ItemRef shape;

    /** 2D image reference (SVG / PNG / JPEG / WebP / GIF). */
    @Seed.Property(role = MediaVocabulary.Image.KEY)
    protected ItemRef image;

    /** Single Unicode character used as a text-fallback representation. */
    @Seed.Property(role = MediaVocabulary.Glyph.KEY)
    protected String glyph;

    /** Accessibility / text-fallback description. */
    @Seed.Property(role = MediaVocabulary.Alt.KEY)
    protected String alt;

    // ==================================================================================
    // Shape-specific styling — used when this body renders as a shape.
    // ==================================================================================

    /** Interior color of the shape. */
    @Seed.Property(role = ShapeVocabulary.Fill.KEY)
    protected Color fill;

    /** Outline color of the shape. */
    @Seed.Property(role = ShapeVocabulary.StrokeColor.KEY)
    protected Color strokeColor;

    /** Outline thickness. */
    @Seed.Property(role = ShapeVocabulary.StrokeWidth.KEY)
    protected Length strokeWidth;

    /** Radius for radial shapes (Circle, Sphere, Cylinder, Cone). */
    @Seed.Property(role = ShapeVocabulary.Radius.KEY)
    protected Length radius;

    /** PBR material reference for 3D bodies. */
    @Seed.Property(role = ShapeVocabulary.Material.KEY)
    protected ItemRef material;

    // ==================================================================================
    // Construction.
    // ==================================================================================

    public SceneBody(Body body) {
        super(ItemRef.iid(KEY), body.bindings());
        BodyBinder.bind(this, body);
    }

    /** Typed view: dispatched from {@link SceneNode#from(Body)} on SceneBody-headed bodies. */
    public static SceneBody from(Body body) {
        Objects.requireNonNull(body, "body");
        if (body instanceof SceneBody sb) return sb;
        if (!ItemRef.iid(KEY).equals(body.headRef())) {
            throw new IllegalArgumentException(
                    "Body head is not the SceneBody archetype: " + body.headRef());
        }
        return new SceneBody(body);
    }
}
