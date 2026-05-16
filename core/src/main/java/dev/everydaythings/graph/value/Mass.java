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
 * Mass — a {@link Quantity} whose dimensional formula is the mass dimension.
 *
 * <p>Body shape: {@code Body[head=Mass, Value=N, @<MassUnit>=1]}.
 */
@Seed.Item(key = Mass.KEY, head = Quantity.KEY)
public final class Mass extends Quantity {

    public static final String KEY = "cg.value:mass";

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "a mass-dimensioned quantity";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY,
        qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "mass";

    private Mass(long magnitude, ItemRef unit) {
        super(ItemRef.iid(KEY), magnitude, Map.of(unit, 1L));
    }

    public static Mass kilograms(long n) { return new Mass(n, ItemRef.iid(UnitVocabulary.Kilogram.KEY)); }
    public static Mass grams(long n)     { return new Mass(n, ItemRef.iid(UnitVocabulary.Gram.KEY)); }
}
