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
 * LuminousIntensity — a {@link Quantity} whose dimensional formula is the
 * luminous-intensity dimension.
 *
 * <p>Body shape: {@code Body[head=LuminousIntensity, Value=N, @<LuminousUnit>=1]}.
 */
@Seed.Item(key = LuminousIntensity.KEY, head = Quantity.KEY)
public final class LuminousIntensity extends Quantity {

    public static final String KEY = "cg.value:luminous-intensity";

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "a luminous-intensity-dimensioned quantity";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY,
        qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "luminous intensity";

    private LuminousIntensity(long magnitude, ItemRef unit) {
        super(ItemRef.iid(KEY), magnitude, Map.of(unit, 1L));
    }

    public static LuminousIntensity candelas(long n) {
        return new LuminousIntensity(n, ItemRef.iid(UnitVocabulary.Candela.KEY));
    }
}
