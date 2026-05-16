package dev.everydaythings.graph.value;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.quality.UnitVocabulary;

import java.util.Map;

/**
 * ElectricCurrent — a {@link Quantity} whose dimensional formula is the
 * electric-current dimension.
 *
 * <p>Body shape: {@code Body[head=ElectricCurrent, Value=N, @<CurrentUnit>=1]}.
 */
@Seed.Item(key = ElectricCurrent.KEY, head = Quantity.KEY)
public final class ElectricCurrent extends Quantity {

    public static final String KEY = "cg.value:electric-current";

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "an electric-current-dimensioned quantity";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY,
        qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "electric current";

    private ElectricCurrent(long magnitude, ItemRef unit) {
        super(ItemRef.iid(KEY), magnitude, Map.of(unit, 1L));
    }

    public static ElectricCurrent amperes(long n) {
        return new ElectricCurrent(n, ItemRef.iid(UnitVocabulary.Ampere.KEY));
    }
}
