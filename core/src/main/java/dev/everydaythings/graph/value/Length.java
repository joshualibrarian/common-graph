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
 * Length — a {@link Quantity} whose dimensional formula is the length dimension.
 *
 * <p>Body shape: {@code Body[head=Length, Value=N, @<LengthUnit>=1]}.  The
 * underlying body is structurally a Quantity body — same magnitude + unit
 * exponent bindings — but the head signals "specifically a length," so callers
 * (and the eventual schema layer) can match on the typed shape without
 * inspecting unit dimensions.
 *
 * <p>Use the static factories ({@link #meters}, {@link #centimeters}, ...) to
 * mint length-valued instances.  Each factory's unit is length-dimensioned;
 * runtime unit-dimension validation lands later, alongside the EXPECTS rework.
 */
@Seed.Item(key = Length.KEY, head = Quantity.KEY)
public final class Length extends Quantity {

    public static final String KEY = "cg.value:length";

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "a length-dimensioned quantity";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
      field = @Seed.Binding(role = ThematicRole.Value.KEY,
        qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "length";

    private Length(long magnitude, ItemRef unit) {
        super(ItemRef.iid(KEY), magnitude, Map.of(unit, 1L));
    }

    public static Length meters(long n)      { return new Length(n, ItemRef.iid(UnitVocabulary.Meter.KEY)); }
    public static Length centimeters(long n) { return new Length(n, ItemRef.iid(UnitVocabulary.Centimeter.KEY)); }
    public static Length millimeters(long n) { return new Length(n, ItemRef.iid(UnitVocabulary.Millimeter.KEY)); }
    public static Length pixels(long n)      { return new Length(n, ItemRef.iid(UnitVocabulary.Pixel.KEY)); }
    public static Length em(long n)          { return new Length(n, ItemRef.iid(UnitVocabulary.Em.KEY)); }
}
