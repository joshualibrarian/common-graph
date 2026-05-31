package dev.everydaythings.graph.value;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.PartOfSpeech;
import dev.everydaythings.graph.ThematicRole;
import dev.everydaythings.graph.quality.UnitVocabulary;

import java.util.Map;

/**
 * Temperature — a {@link Quantity} whose dimensional formula is the
 * thermodynamic-temperature dimension.
 *
 * <p>Body shape: {@code Body[head=Temperature, Value=N, @<TemperatureUnit>=1]}.
 */
@Seed.Item(key = Temperature.KEY, head = Quantity.KEY)
@Seed.Cili("i63340")
public final class Temperature extends Quantity {

    public static final String KEY = "cg.value:temperature";

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "a temperature-dimensioned quantity";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY,
        qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "temperature";

    private Temperature(long magnitude, ItemRef unit) {
        super(ItemRef.iid(KEY), magnitude, Map.of(unit, 1L));
    }

    public static Temperature kelvin(long n) {
        return new Temperature(n, ItemRef.iid(UnitVocabulary.Kelvin.KEY));
    }
}
