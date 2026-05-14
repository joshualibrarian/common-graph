package dev.everydaythings.graph.operator.compare;

import dev.everydaythings.graph.*;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.operator.NotationVocabulary;
import dev.everydaythings.graph.operator.Operator;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.language.ThematicRole;

/** The inequality comparison operator. Infix, non-associative, precedence 5. */
@Seed.Item(key = NotEqual.KEY,
        head = Operator.KEY,
        bindings = {@Seed.Binding(role = NotationVocabulary.Arity.KEY, integer = 2)})
@Seed.Embodies(key = NotEqual.KEY)
public class NotEqual extends Operator {

    public static final String KEY = "cg.predicate:not-equal";
    public static final ItemRef IID = ItemRef.fromString(KEY);

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "inequality test — true when operands differ";

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
    static final String symbol = "!=";

    public NotEqual(ItemRef iid) { super(iid); }
    public NotEqual(ItemRef iid, Librarian librarian) { super(iid, librarian); }

    @Override
    public Object execute(Object... operands) {
        if (operands.length != 2) {
            throw new IllegalArgumentException(
                    "expects 2 operands, got " + operands.length);
        }
        Object left = operands[0];
        Object right = operands[1];
        if (left instanceof Number l && right instanceof Number r) {
            return l.doubleValue() != r.doubleValue();
        }
        return left == null ? right != null : !left.equals(right);
    }
}
