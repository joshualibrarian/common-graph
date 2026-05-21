package dev.everydaythings.graph.value;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.quality.UnitVocabulary;

import java.util.Map;

/**
 * Time — a {@link Quantity} whose dimensional formula is the time dimension.
 *
 * <p>Body shape: {@code Body[head=Time, Value=N, @<TimeUnit>=1]}.
 */
@Seed.Item(key = Time.KEY, head = Quantity.KEY)
public final class Time extends Quantity {

    public static final String KEY = "cg.value:time";

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "a time-dimensioned quantity";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY,
        qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "time";

    private Time(long magnitude, ItemRef unit) {
        super(ItemRef.iid(KEY), magnitude, Map.of(unit, 1L));
    }

    public static Time seconds(long n) { return new Time(n, ItemRef.iid(UnitVocabulary.Second.KEY)); }
}
