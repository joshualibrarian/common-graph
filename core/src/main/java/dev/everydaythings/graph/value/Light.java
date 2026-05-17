package dev.everydaythings.graph.value;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.quality.LightVocabulary;
import dev.everydaythings.graph.quality.VisualVocabulary;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Light — a scene light source as a {@link Value}-shaped body.
 *
 * <p>Body shape:
 * <pre>
 * Body[head=Light,
 *      LightType   = @directional | @point | @spot,
 *      Position    = &lt;Point3D&gt;        (optional; default: scene-center for point/spot)
 *      Foreground  = &lt;Color&gt;          (key-light color)
 *      Ambient     = &lt;Color&gt;          (fill-light / shadow color)
 *      Intensity   = &lt;number&gt;         (strength multiplier; default 1.0)]
 * </pre>
 *
 * <p>The light contributes meaningfully across every fidelity level:
 * Filament-3D uses it as a real light source casting physical shadows;
 * 2D painters use it to derive drop-shadow direction + color from
 * elevation; text painters use it for depth hints.
 *
 * <p>{@code Foreground} reuses the existing
 * {@link VisualVocabulary.Foreground Foreground} quality (the key-light
 * color is conceptually "what's painted in front"); {@link
 * LightVocabulary.Ambient Ambient} carries the fill / shadow color.
 */
@Seed.Item(key = Light.KEY, head = Value.KEY)
public final class Light extends Value {

    public static final String KEY = "cg.value:light";

    private static final ItemRef LIGHT_TYPE = ItemRef.iid(LightVocabulary.LightType.KEY);
    private static final ItemRef FOREGROUND = ItemRef.iid(VisualVocabulary.Foreground.KEY);
    private static final ItemRef AMBIENT    = ItemRef.iid(LightVocabulary.Ambient.KEY);
    private static final ItemRef INTENSITY  = ItemRef.iid(LightVocabulary.Intensity.KEY);

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "a scene light source — type, key-light color, fill-light color, intensity; "
                    + "drives shadows in 2D, real lighting in 3D, depth hints in text";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY,
        qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "light";

    /**
     * Construct a Light with explicit bindings.  Most callers will use
     * the static factories ({@link #directional}, {@link #point},
     * {@link #spot}) instead.
     */
    public Light(List<Binding> bindings) {
        super(ItemRef.iid(KEY), bindings);
    }

    // ==================================================================================
    // Static factories — one per light type.
    // ==================================================================================

    /**
     * A directional light — parallel rays from an infinitely-distant
     * source (like sunlight).  No falloff with distance.
     */
    public static Light directional(Color keyColor, Color ambientColor) {
        Objects.requireNonNull(keyColor, "keyColor");
        Objects.requireNonNull(ambientColor, "ambientColor");
        List<Binding> bindings = new ArrayList<>(3);
        bindings.add(Binding.ref(LIGHT_TYPE, ItemRef.iid(LightVocabulary.Directional.KEY)));
        bindings.add(Binding.literal(FOREGROUND, keyColor));
        bindings.add(Binding.literal(AMBIENT, ambientColor));
        return new Light(bindings);
    }

    /**
     * A point light — radiating outward in all directions from a position
     * (like a bare bulb).  Falloff with distance.
     */
    public static Light point(Color keyColor, Color ambientColor, double intensity) {
        Objects.requireNonNull(keyColor, "keyColor");
        Objects.requireNonNull(ambientColor, "ambientColor");
        List<Binding> bindings = new ArrayList<>(4);
        bindings.add(Binding.ref(LIGHT_TYPE, ItemRef.iid(LightVocabulary.PointLight.KEY)));
        bindings.add(Binding.literal(FOREGROUND, keyColor));
        bindings.add(Binding.literal(AMBIENT, ambientColor));
        bindings.add(Binding.literal(INTENSITY, intensity));
        return new Light(bindings);
    }

    /**
     * A spot light — light cast in a cone from a position toward a
     * direction (like a spotlight).
     */
    public static Light spot(Color keyColor, Color ambientColor, double intensity) {
        Objects.requireNonNull(keyColor, "keyColor");
        Objects.requireNonNull(ambientColor, "ambientColor");
        List<Binding> bindings = new ArrayList<>(4);
        bindings.add(Binding.ref(LIGHT_TYPE, ItemRef.iid(LightVocabulary.Spot.KEY)));
        bindings.add(Binding.literal(FOREGROUND, keyColor));
        bindings.add(Binding.literal(AMBIENT, ambientColor));
        bindings.add(Binding.literal(INTENSITY, intensity));
        return new Light(bindings);
    }
}
