package dev.everydaythings.graph.operator.set;

import dev.everydaythings.graph.*;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.language.*;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.operator.Operator;
import dev.everydaythings.graph.operator.OperatorNotation;

import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.ref.SchemaRef;
import dev.everydaythings.graph.value.Bool;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.ThematicRole;

/**
 * The set-membership operator. Infix, non-associative, precedence 5. Tests whether
 * the left operand is a member of the right operand (a collection or container).
 */
@Seed.Item(key = In.KEY, head = Operator.KEY)
@Seed.Embodies(key = In.KEY)
public class In extends Operator {

    public static final String KEY = "cg.predicate:in";

    /** Arity — binary operator. */
    @Seed.Property(role = Operator.Arity.KEY)
    static final long arity = 2;

    /** Returns Bool. */
    @Seed.Property(role = SchemaVocabulary.Returns.KEY)
    static final SchemaRef returns = SchemaRef.iid(Bool.KEY);

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "set membership — true when the left operand is in the right collection";

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
    static final String symbol = "in";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Preposition.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishPrepositionLemma = "in";

    public In(ItemRef iid) { super(iid); }
    public In(ItemRef iid, Librarian librarian) { super(iid, librarian); }

    @Override
    protected Object evaluate(Frame frame) {
        Object element   = operandAt(frame, ThematicRole.Theme.KEY);
        Object container = operandAt(frame, ThematicRole.Goal.KEY);
        if (container == null) return null;
        if (container instanceof java.util.Collection<?> c) return c.contains(element);
        if (container instanceof Object[] arr) {
            for (Object item : arr) {
                if (element == null ? item == null : element.equals(item)) return true;
            }
            return false;
        }
        if (container instanceof String s && element instanceof String sub) return s.contains(sub);
        throw new IllegalArgumentException(
                "In: right operand must be a collection or string, got " + container);
    }
}
