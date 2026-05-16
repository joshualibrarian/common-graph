package dev.everydaythings.graph.value;

import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;

import static dev.everydaythings.graph.Seed.*;

/**
 * Bool — the {@link Value} archetype for boolean primitives.
 *
 * <p>Instances are wire literals: {@link java.lang.Boolean Boolean} true or
 * false.  No Java instance class extending {@link Value} — the boolean IS
 * the primitive.
 *
 * <p>Used as a type-slot for operator contracts.  Comparison and matcher-
 * producing operators declare {@code Returns = !Bool}.  Under the partial-
 * application rule, a Bool-returning operator in a binding-target with a
 * missing operand becomes a matcher over candidate values.
 *
 * <p>Named {@code Bool} rather than {@code Boolean} to avoid collision with
 * {@link java.lang.Boolean} in importer files.
 */
@Seed.Item(key = Bool.KEY, head = Value.KEY)
public final class Bool {
    public static final String KEY = "cg.value:bool";

    private Bool() {}

    @Frame(predicate = LexicalVocabulary.Gloss.KEY,
      field = @Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "the type-slot for boolean primitives — true or false; instances are wire "
                    + "literals, not body-shaped";

    @Frame(predicate = LexicalVocabulary.Lexeme.KEY,
      field = @Binding(role = ThematicRole.Value.KEY,
        qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "boolean";
}
