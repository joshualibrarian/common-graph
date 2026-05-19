package dev.everydaythings.graph.operator.math;

import dev.everydaythings.graph.*;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.id.SchemaRef;
import dev.everydaythings.graph.value.Numeric;
import dev.everydaythings.graph.language.*;
import dev.everydaythings.graph.operator.BinaryArithmetic;
import dev.everydaythings.graph.operator.Operator;
import dev.everydaythings.graph.operator.OperatorNotation;

import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.language.ThematicRole;

/** The modulo (remainder) operator. Infix, left-associative, precedence 20. */
@Seed.Item(key = Modulo.KEY, head = Operator.KEY)
@Seed.Embodies(key = Modulo.KEY)
public class Modulo extends BinaryArithmetic {

    public static final String KEY = "cg.predicate:modulo";

    /** Arity — binary operator. */
    @Seed.Property(role = Operator.Arity.KEY)
    static final long arity = 2;

    /** Returns a Numeric — the result of the operation. */
    @Seed.Property(role = SchemaVocabulary.Returns.KEY)
    static final SchemaRef returns = SchemaRef.iid(Numeric.KEY);

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "the remainder after integer division";

    /** OperatorNotation lexeme — symbol with Infix qualifier plus Precedence and Associativity. */
    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                  qualifiers = {OperatorNotation.KEY, Operator.Infix.KEY}),
          bindings = {
                  @Seed.Binding(role = ThematicRole.Attribute.KEY,
                          qualifiers = {Operator.Precedence.KEY},
                          integer = 20),
                  @Seed.Binding(role = ThematicRole.Attribute.KEY,
                          qualifiers = {Operator.Associativity.KEY},
                          ref = Operator.Left.KEY)
          })
    static final String symbol = "%";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "modulo";

    public Modulo(ItemRef iid) { super(iid); }
    public Modulo(ItemRef iid, Librarian librarian) { super(iid, librarian); }

    @Override protected double applyDouble(double l, double r) { return l % r; }
    @Override protected long   applyLong(long l, long r) {
        if (r == 0L) throw new ArithmeticException("integer modulo by zero");
        return l % r;
    }
}
