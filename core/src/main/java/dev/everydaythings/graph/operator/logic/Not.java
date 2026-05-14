package dev.everydaythings.graph.operator.logic;

import dev.everydaythings.graph.*;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.language.*;
import dev.everydaythings.graph.operator.NotationVocabulary;
import dev.everydaythings.graph.operator.Operator;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.language.ThematicRole;

/** The logical-NOT operator. Prefix, right-associative, precedence 25. */
@Seed.Item(key = Not.KEY,
        head = Operator.KEY,
        bindings = {@Seed.Binding(role = NotationVocabulary.Arity.KEY, integer = 1)})
@Seed.Embodies(key = Not.KEY)
public class Not extends Operator {

    public static final String KEY = "cg.predicate:not";
    public static final ItemRef IID = ItemRef.fromString(KEY);

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "logical negation — true when the operand is false";

    /** Operator-form lexeme — bundles the symbol with its Fixity qualifier and ATTRIBUTE bindings for Precedence and Associativity. */
    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                  qualifiers = {NotationVocabulary.Prefix.KEY}),
          bindings = {
                  @Seed.Binding(role = ThematicRole.Attribute.KEY,
                          qualifiers = {NotationVocabulary.Precedence.KEY},
                          integer = 25),
                  @Seed.Binding(role = ThematicRole.Attribute.KEY,
                          qualifiers = {NotationVocabulary.Associativity.KEY},
                          ref = NotationVocabulary.Right.KEY)
          })
    static final String symbol = "!";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Adverb.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishAdverbLemma = "not";

    public Not(ItemRef iid) { super(iid); }
    public Not(ItemRef iid, Librarian librarian) { super(iid, librarian); }

    @Override
    public Object execute(Object... operands) {
        if (operands.length != 1) {
            throw new IllegalArgumentException(
                    "expects 1 operand, got " + operands.length);
        }
        Object operand = operands[0];
        return !toBoolean(operand);
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        if (value == null) return false;
        if (value instanceof Number n) return n.doubleValue() != 0.0;
        return true;
    }
}
