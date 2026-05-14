package dev.everydaythings.graph.operator.compare;

import dev.everydaythings.graph.*;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.operator.NotationVocabulary;
import dev.everydaythings.graph.operator.Operator;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.language.ThematicRole;

/** Less-than-or-equal comparison. Infix, non-associative, precedence 5. */
@Seed.Item(key = LessOrEqual.KEY,
        head = Operator.KEY,
        bindings = {@Seed.Binding(role = NotationVocabulary.Arity.KEY, integer = 2)})
@Seed.Embodies(key = LessOrEqual.KEY)
public class LessOrEqual extends Operator {

    public static final String KEY = "cg.predicate:less-or-equal";
    public static final ItemRef IID = ItemRef.fromString(KEY);

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "true when the left operand is less than or equal to the right";

    /** Operator-form lexeme — bundles the symbol with its Fixity qualifier and ATTRIBUTE bindings for Precedence and Associativity. */
    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                  qualifiers = {NotationVocabulary.Infix.KEY}),
          bindings = {
                  @Seed.Binding(role = ThematicRole.Attribute.KEY,
                          qualifiers = {NotationVocabulary.Precedence.KEY},
                          integer = 5),
                  @Seed.Binding(role = ThematicRole.Attribute.KEY,
                          qualifiers = {NotationVocabulary.Associativity.KEY},
                          ref = NotationVocabulary.NonAssociative.KEY)
          })
    static final String symbol = "<=";

    public LessOrEqual(ItemRef iid) { super(iid); }
    public LessOrEqual(ItemRef iid, Librarian librarian) { super(iid, librarian); }

    @Override
    public Object execute(Object... operands) {
        if (operands.length != 2) {
            throw new IllegalArgumentException(
                    "expects 2 operands, got " + operands.length);
        }
        Object left = operands[0];
        Object right = operands[1];
        if (left instanceof Number l && right instanceof Number r) {
            return l.doubleValue() <= r.doubleValue();
        }
        throw new IllegalArgumentException(
                "LessOrEqual.execute: unsupported operand types " + left + " <= " + right);
    }
}
