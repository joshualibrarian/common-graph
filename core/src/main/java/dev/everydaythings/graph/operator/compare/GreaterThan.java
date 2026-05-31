package dev.everydaythings.graph.operator.compare;

import dev.everydaythings.graph.*;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.operator.BinaryNumericComparison;
import dev.everydaythings.graph.operator.Operator;
import dev.everydaythings.graph.operator.OperatorNotation;

import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.ref.SchemaRef;
import dev.everydaythings.graph.value.Bool;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.ThematicRole;

/** Strict greater-than comparison. Infix, non-associative, precedence 5. */
@Seed.Item(key = GreaterThan.KEY, head = Operator.KEY)
@Seed.Embodies(key = GreaterThan.KEY)
public class GreaterThan extends BinaryNumericComparison {

    public static final String KEY = "cg.predicate:greater-than";

    /** Arity — binary operator. */
    @Seed.Property(role = Operator.Arity.KEY)
    static final long arity = 2;

    /** Returns Bool. */
    @Seed.Property(role = SchemaVocabulary.Returns.KEY)
    static final SchemaRef returns = SchemaRef.iid(Bool.KEY);

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "true when the left operand is strictly greater than the right";

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
    static final String symbol = ">";

    public GreaterThan(ItemRef iid) { super(iid); }
    public GreaterThan(ItemRef iid, Librarian librarian) { super(iid, librarian); }

    @Override protected Boolean compare(double l, double r) { return l > r; }
}
