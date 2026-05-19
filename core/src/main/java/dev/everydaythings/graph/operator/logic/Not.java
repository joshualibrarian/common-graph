package dev.everydaythings.graph.operator.logic;

import dev.everydaythings.graph.*;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.id.SchemaRef;
import dev.everydaythings.graph.value.Bool;
import dev.everydaythings.graph.language.*;
import dev.everydaythings.graph.operator.Operator;
import dev.everydaythings.graph.operator.OperatorNotation;

import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.language.ThematicRole;

/** The logical-NOT operator. Prefix, right-associative, precedence 25. */
@Seed.Item(key = Not.KEY, head = Operator.KEY)
@Seed.Embodies(key = Not.KEY)
@Seed.Cili("i71973")
public class Not extends Operator {

    public static final String KEY = "cg.predicate:not";

    /** Arity — unary operator. */
    @Seed.Property(role = Operator.Arity.KEY)
    static final long arity = 1;

    /** Returns Bool. */
    @Seed.Property(role = SchemaVocabulary.Returns.KEY)
    static final SchemaRef returns = SchemaRef.iid(Bool.KEY);

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "logical negation — true when the operand is false";

    /** OperatorNotation lexeme — symbol with Prefix qualifier plus Precedence and Associativity. */
    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                  qualifiers = {OperatorNotation.KEY, Operator.Prefix.KEY}),
          bindings = {
                  @Seed.Binding(role = ThematicRole.Attribute.KEY,
                          qualifiers = {Operator.Precedence.KEY},
                          integer = 25),
                  @Seed.Binding(role = ThematicRole.Attribute.KEY,
                          qualifiers = {Operator.Associativity.KEY},
                          ref = Operator.Right.KEY)
          })
    static final String symbol = "!";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Adverb.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishAdverbLemma = "not";

    public Not(ItemRef iid) { super(iid); }
    public Not(ItemRef iid, Librarian librarian) { super(iid, librarian); }

    @Override
    protected Object evaluate(Frame frame) {
        Object operand = operandAt(frame, ThematicRole.Theme.KEY);
        if (operand == null) return null;
        return !toBoolean(operand);
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n)  return n.doubleValue() != 0.0;
        return true;
    }
}
