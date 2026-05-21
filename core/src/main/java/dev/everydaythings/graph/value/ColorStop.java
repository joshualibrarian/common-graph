package dev.everydaythings.graph.value;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;

import java.util.List;
import java.util.Objects;

/**
 * ColorStop — a single (color, offset) pair in a {@link Gradient}.
 *
 * <p>Body shape: {@code Body[head=ColorStop, @Value=&lt;Color&gt;, @Offset=&lt;0.0–1.0&gt;]}.
 * Offset is a fraction from 0 (start of the gradient) to 1 (end).  Multiple
 * stops in a gradient define a color ramp.
 */
@Seed.Item(key = ColorStop.KEY, head = Value.KEY)
public final class ColorStop extends Value {

    public static final String KEY = "cg.value:color-stop";

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "a single (color, offset) pair in a gradient — color at the given fractional "
                    + "position along the gradient's geometry";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY,
        qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "color stop";

    /** Role IID for the offset binding (a Quantity 0.0..1.0). */
    private static final ItemRef OFFSET_ROLE = ItemRef.iid("cg.quality:offset");
    private static final ItemRef VALUE_ROLE  = ItemRef.iid(ThematicRole.Value.KEY);

    public ColorStop(Color color, double offset) {
        super(ItemRef.iid(KEY), List.of(
                Binding.literal(VALUE_ROLE, Objects.requireNonNull(color, "color")),
                Binding.literal(OFFSET_ROLE, offset)));
    }
}
