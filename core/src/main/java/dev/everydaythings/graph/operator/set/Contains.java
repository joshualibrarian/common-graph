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
 * The containment operator — inverse of {@link In}. {@code container contains element}
 * is the same as {@code element in container}. Infix, non-associative, precedence 5.
 */
@Seed.Item(key = Contains.KEY, head = Operator.KEY)
@Seed.Embodies(key = Contains.KEY)
@Seed.Cili("i34820")
public class Contains extends Operator {

    public static final String KEY = "cg.predicate:contains";

    /** Arity — binary operator. */
    @Seed.Property(role = Operator.Arity.KEY)
    static final long arity = 2;

    /** Returns Bool. */
    @Seed.Property(role = SchemaVocabulary.Returns.KEY)
    static final SchemaRef returns = SchemaRef.iid(Bool.KEY);

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "containment — true when the left collection contains the right operand";

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
    static final String symbol = "contains";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishVerbLemma = "contain";

    public Contains(ItemRef iid) { super(iid); }
    public Contains(ItemRef iid, Librarian librarian) { super(iid, librarian); }

    @Override
    protected Object evaluate(Frame frame) {
        Object container = operandAt(frame, ThematicRole.Theme.KEY);
        Object element   = operandAt(frame, ThematicRole.Goal.KEY);
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
                "Contains: left operand must be a collection or string, got " + container);
    }
}
