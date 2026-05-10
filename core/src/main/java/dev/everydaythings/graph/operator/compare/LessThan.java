package dev.everydaythings.graph.operator.compare;

import dev.everydaythings.graph.*;
import dev.everydaythings.graph.operator.NotationVocabulary;
import dev.everydaythings.graph.operator.Operator;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.linguistics.Gloss;
import dev.everydaythings.graph.linguistics.Language;
import dev.everydaythings.graph.linguistics.Lexeme;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.semantics.ThematicRole;

/** Strict less-than comparison. Infix, non-associative, precedence 5. */
@Seed.Item(key = LessThan.KEY,
        head = Operator.KEY,
        bindings = {@Seed.Binding(role = NotationVocabulary.Arity.KEY, integer = 2)})
@Seed.Embodies(key = LessThan.KEY)
public class LessThan extends Operator {

    public static final String KEY = "cg.predicate:less-than";
    public static final ItemID IID = ItemID.fromString(KEY);

    @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "true when the left operand is strictly less than the right";

    /** Operator-form lexeme — bundles the symbol with its Fixity qualifier and ATTRIBUTE bindings for Precedence and Associativity. */
    @Seed.Frame(predicate = Lexeme.KEY,
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
    static final String symbol = "<";

    public LessThan(ItemID iid) { super(iid); }
    public LessThan(ItemID iid, Librarian librarian) { super(iid, librarian); }

    @Override
    public Object execute(Object... operands) {
        if (operands.length != 2) {
            throw new IllegalArgumentException(
                    "expects 2 operands, got " + operands.length);
        }
        Object left = operands[0];
        Object right = operands[1];
        if (left instanceof Number l && right instanceof Number r) {
            return l.doubleValue() < r.doubleValue();
        }
        throw new IllegalArgumentException(
                "LessThan.execute: unsupported operand types " + left + " < " + right);
    }
}
