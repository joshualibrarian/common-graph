package dev.everydaythings.graph.value;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;

import static dev.everydaythings.graph.Seed.*;

/**
 * Numeric — the {@link Value} archetype for numeric primitives.
 *
 * <p>Instances are wire literals: {@code Long}, {@link java.math.BigInteger
 * BigInteger}, {@link java.math.BigDecimal BigDecimal}, or {@link Rational}.
 * Unlike body-shaped Value archetypes (Color, Quantity, Length, ...), there
 * is no Java instance class extending {@link Value} — the number IS the
 * primitive.
 *
 * <p>Used as a type-slot for operator contracts.  An operator declaring
 * {@code Returns = !Numeric} produces a number; an EXPECTS binding
 * {@code !Theme = ?Numeric} says "this binding's target is any numeric."
 *
 * <p>Named {@code Numeric} rather than {@code Number} to avoid collision with
 * {@link java.lang.Number} in importer files.
 */
@Seed.Item(key = Numeric.KEY, head = Value.KEY)
public final class Numeric {
    public static final String KEY = "cg.value:numeric";

    private Numeric() {}

    @Frame(predicate = LexicalVocabulary.Gloss.KEY,
      field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "the type-slot for numeric primitives — Long, BigInteger, BigDecimal, "
                    + "Rational; instances are wire literals, not body-shaped";

    @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
      field = @Binding(role = ThematicRole.Value.KEY,
        qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "numeric";

    @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
      field = @Binding(role = ThematicRole.Value.KEY,
        qualifiers = {Language.English.KEY, PartOfSpeech.Adjective.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishAdjectiveLemma = "numeric";
}
