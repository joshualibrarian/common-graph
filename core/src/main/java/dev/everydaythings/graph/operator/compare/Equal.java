package dev.everydaythings.graph.operator.compare;

import dev.everydaythings.graph.*;
import dev.everydaythings.graph.language.*;
import dev.everydaythings.graph.operator.Operator;

import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.language.ThematicRole;

/** The equality comparison operator. Infix, non-associative, precedence 5. */
@Seed.Item(key = Equal.KEY,
        head = Operator.KEY,
        bindings = {@Seed.Binding(role = Operator.Arity.KEY, integer = 2)})
@Seed.Embodies(key = Equal.KEY)
public class Equal extends Operator {

    public static final String KEY = "cg.predicate:equal";

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "equality test — true when operands are equal";

    /** Operator-form lexeme — bundles the symbol with its Fixity qualifier and ATTRIBUTE bindings for Precedence and Associativity. */
    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                  qualifiers = {Operator.Infix.KEY}),
          bindings = {
                  @Seed.Binding(role = ThematicRole.Attribute.KEY,
                          qualifiers = {Operator.Precedence.KEY},
                          integer = 5),
                  @Seed.Binding(role = ThematicRole.Attribute.KEY,
                          qualifiers = {Operator.Associativity.KEY},
                          ref = Operator.NonAssociative.KEY)
          })
    static final String symbol = "==";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Adjective.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishAdjectiveLemma = "equal";

    public Equal(ItemRef iid) { super(iid); }
    public Equal(ItemRef iid, Librarian librarian) { super(iid, librarian); }

    @Override
    public Object execute(Object... operands) {
        if (operands.length != 2) {
            throw new IllegalArgumentException(
                    "expects 2 operands, got " + operands.length);
        }
        Object left = operands[0];
        Object right = operands[1];
        if (left instanceof Number l && right instanceof Number r) {
            return l.doubleValue() == r.doubleValue();
        }
        return left == null ? right == null : left.equals(right);
    }
}
