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
 * Amount — a {@link Quantity} whose dimensional formula is the amount-of-substance
 * dimension.
 *
 * <p>Body shape: {@code Body[head=Amount, Value=N, @<AmountUnit>=1]}.
 */
@Seed.Item(key = Amount.KEY, head = Quantity.KEY)
public final class Amount extends Quantity {

    public static final String KEY = "cg.value:amount";

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "an amount-of-substance-dimensioned quantity";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY,
        qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "amount";

    private Amount(long magnitude, ItemRef unit) {
        super(ItemRef.iid(KEY), magnitude, Map.of(unit, 1L));
    }

    public static Amount moles(long n) {
        return new Amount(n, ItemRef.iid(UnitVocabulary.Mole.KEY));
    }
}
