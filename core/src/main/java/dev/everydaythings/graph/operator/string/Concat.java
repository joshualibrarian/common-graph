package dev.everydaythings.graph.operator.string;

import dev.everydaythings.graph.*;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.language.*;
import dev.everydaythings.graph.operator.NotationVocabulary;
import dev.everydaythings.graph.operator.Operator;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.language.ThematicRole;

/** String concatenation operator. Infix, left-associative, precedence 10. */
@Seed.Item(key = Concat.KEY,
        head = Operator.KEY,
        bindings = {@Seed.Binding(role = NotationVocabulary.Arity.KEY, integer = 2)})
@Seed.Embodies(key = Concat.KEY)
public class Concat extends Operator {

    public static final String KEY = "cg.predicate:concat";
    public static final ItemRef IID = ItemRef.fromString(KEY);

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "string concatenation — joins two text values end to end";

    /** Operator-form lexeme — bundles the symbol with its Fixity qualifier and ATTRIBUTE bindings for Precedence and Associativity. */
    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                  qualifiers = {NotationVocabulary.Infix.KEY}),
          bindings = {
                  @Seed.Binding(role = ThematicRole.Attribute.KEY,
                          qualifiers = {NotationVocabulary.Precedence.KEY},
                          integer = 10),
                  @Seed.Binding(role = ThematicRole.Attribute.KEY,
                          qualifiers = {NotationVocabulary.Associativity.KEY},
                          ref = NotationVocabulary.Left.KEY)
          })
    static final String symbol = "++";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishVerbLemma = "concatenate";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "concatenation";

    public Concat(ItemRef iid) { super(iid); }
    public Concat(ItemRef iid, Librarian librarian) { super(iid, librarian); }

    @Override
    public Object execute(Object... operands) {
        if (operands.length != 2) {
            throw new IllegalArgumentException(
                    "expects 2 operands, got " + operands.length);
        }
        Object left = operands[0];
        Object right = operands[1];
        return String.valueOf(left) + String.valueOf(right);
    }
}
