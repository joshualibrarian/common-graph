package dev.everydaythings.graph.operator.compare;

import dev.everydaythings.graph.*;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.operator.Operator;
import dev.everydaythings.graph.operator.OperatorNotation;

import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.id.SchemaRef;
import dev.everydaythings.graph.value.Bool;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.language.ThematicRole;

/** Inequality. Infix, non-associative, precedence 5. Numeric-aware (same as {@link Equal} negated). */
@Seed.Item(key = NotEqual.KEY, head = Operator.KEY)
@Seed.Embodies(key = NotEqual.KEY)
public class NotEqual extends Operator {

    public static final String KEY = "cg.predicate:not-equal";

    /** Arity — binary operator. */
    @Seed.Property(role = Operator.Arity.KEY)
    static final long arity = 2;

    /** Returns Bool. */
    @Seed.Property(role = SchemaVocabulary.Returns.KEY)
    static final SchemaRef returns = SchemaRef.iid(Bool.KEY);

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "inequality test — true when operands differ";

    /** OperatorNotation lexeme — symbol with Infix qualifier plus Precedence and Associativity. */
    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                  qualifiers = {OperatorNotation.KEY, Operator.Infix.KEY}),
          bindings = {
                  @Seed.Binding(role = ThematicRole.Attribute.KEY,
                          qualifiers = {Operator.Precedence.KEY},
                          integer = 5),
                  @Seed.Binding(role = ThematicRole.Attribute.KEY,
                          qualifiers = {Operator.Associativity.KEY},
                          ref = Operator.NonAssociative.KEY)
          })
    static final String symbol = "!=";

    public NotEqual(ItemRef iid) { super(iid); }
    public NotEqual(ItemRef iid, Librarian librarian) { super(iid, librarian); }

    @Override
    protected Object evaluate(Frame frame) {
        Object left  = operandAt(frame, ThematicRole.Theme.KEY);
        Object right = operandAt(frame, ThematicRole.Goal.KEY);
        if (left instanceof Number l && right instanceof Number r) {
            return l.doubleValue() != r.doubleValue();
        }
        return left == null ? right != null : !left.equals(right);
    }
}
