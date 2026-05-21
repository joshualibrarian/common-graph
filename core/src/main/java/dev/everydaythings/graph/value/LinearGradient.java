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
 * LinearGradient — a {@link Gradient} along a straight line.
 *
 * <p>Body shape: {@code Body[head=LinearGradient, @Angle=&lt;degrees&gt;, @Value*=&lt;ColorStop&gt;]}.
 * The angle gives the direction of the gradient (in degrees from horizontal,
 * standard CSS convention: {@code 0deg} = bottom-to-top, {@code 90deg} =
 * left-to-right).  ColorStop bindings — repeated under the {@code Value}
 * role — define the color ramp.
 */
@Seed.Item(key = LinearGradient.KEY, head = Gradient.KEY)
public final class LinearGradient extends Gradient {

    public static final String KEY = "cg.archetype:linear-gradient";

    private static final ItemRef ANGLE_ROLE = ItemRef.iid("cg.quality:angle");
    private static final ItemRef VALUE_ROLE = ItemRef.iid(ThematicRole.Value.KEY);

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "a gradient along a straight line at a declared angle";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY,
        qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "linear gradient";

    public LinearGradient(double angleDegrees, List<ColorStop> stops) {
        super(ItemRef.iid(KEY), assemble(angleDegrees, stops));
    }

    private static List<Binding> assemble(double angleDegrees, List<ColorStop> stops) {
        Objects.requireNonNull(stops, "stops");
        List<Binding> bindings = new ArrayList<>(1 + stops.size());
        bindings.add(Binding.literal(ANGLE_ROLE, angleDegrees));
        long index = 0;
        for (ColorStop stop : stops) {
            Objects.requireNonNull(stop, "color stop");
            bindings.add(new Binding(VALUE_ROLE, List.of(), stop, index++));
        }
        return bindings;
    }

    /** Typed view over an existing Body whose head is the LinearGradient archetype. */
    public static LinearGradient from(Body body) {
        Objects.requireNonNull(body, "body");
        if (body instanceof LinearGradient lg) return lg;
        if (!ItemRef.iid(KEY).equals(body.headRef())) {
            throw new IllegalArgumentException(
                    "Body head is not the LinearGradient archetype: " + body.headRef());
        }
        // For now this is a thin marker view; full property readers land
        // when the resolver actually consumes them.
        throw new UnsupportedOperationException(
                "LinearGradient.from(Body) — typed reader not yet implemented");
    }
}
