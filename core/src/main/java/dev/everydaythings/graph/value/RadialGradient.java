package dev.everydaythings.graph.value;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * RadialGradient — a {@link Gradient} radiating outward from a center.
 *
 * <p>Body shape: {@code Body[head=RadialGradient, @Shape=&lt;circle|ellipse&gt;,
 * @Value*=&lt;ColorStop&gt;]}.  The shape value declares whether the gradient is
 * circular or elliptical.  ColorStop bindings — repeated under the {@code Value}
 * role — define the color ramp from center to edge.  A center-position binding
 * (defaulting to the geometric center of the painted region) can be added later
 * when concrete consumers need positionable gradients.
 */
@Seed.Item(key = RadialGradient.KEY, head = Gradient.KEY)
public final class RadialGradient extends Gradient {

    public static final String KEY = "cg.archetype:radial-gradient";

    /** Radial shape: circle or ellipse. */
    public enum Shape {
        CIRCLE("circle"),
        ELLIPSE("ellipse");

        private final String wire;
        Shape(String wire) { this.wire = wire; }
        public String wire() { return wire; }
    }

    private static final ItemRef SHAPE_ROLE = ItemRef.iid("cg.quality:radial-shape");
    private static final ItemRef VALUE_ROLE = ItemRef.iid(ThematicRole.Value.KEY);

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "a gradient radiating outward from a center";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY,
        qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "radial gradient";

    public RadialGradient(Shape shape, List<ColorStop> stops) {
        super(ItemRef.iid(KEY), assemble(shape, stops));
    }

    private static List<Binding> assemble(Shape shape, List<ColorStop> stops) {
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(stops, "stops");
        List<Binding> bindings = new ArrayList<>(1 + stops.size());
        bindings.add(Binding.literal(SHAPE_ROLE, shape.wire()));
        long index = 0;
        for (ColorStop stop : stops) {
            Objects.requireNonNull(stop, "color stop");
            bindings.add(new Binding(VALUE_ROLE, List.of(), stop, index++));
        }
        return bindings;
    }

    /** Typed view over an existing Body whose head is the RadialGradient archetype. */
    public static RadialGradient from(Body body) {
        Objects.requireNonNull(body, "body");
        if (body instanceof RadialGradient rg) return rg;
        if (!ItemRef.iid(KEY).equals(body.headRef())) {
            throw new IllegalArgumentException(
                    "Body head is not the RadialGradient archetype: " + body.headRef());
        }
        throw new UnsupportedOperationException(
                "RadialGradient.from(Body) — typed reader not yet implemented");
    }
}
