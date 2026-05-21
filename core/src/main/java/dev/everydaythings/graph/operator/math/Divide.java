package dev.everydaythings.graph.operator.math;

import dev.everydaythings.graph.*;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.ref.SchemaRef;
import dev.everydaythings.graph.value.Numeric;
import dev.everydaythings.graph.language.*;
import dev.everydaythings.graph.operator.BinaryArithmetic;
import dev.everydaythings.graph.operator.Operator;
import dev.everydaythings.graph.operator.OperatorNotation;

import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.language.ThematicRole;

/** The division operator. Infix, left-associative, precedence 20. */
@Seed.Item(key = Divide.KEY, head = Operator.KEY)
@Seed.Embodies(key = Divide.KEY)
@Seed.Cili("i24946")
public class Divide extends BinaryArithmetic {

    public static final String KEY = "cg.predicate:divide";

    /** Arity — binary operator. */
    @Seed.Property(role = Operator.Arity.KEY)
    static final long arity = 2;

    /** Returns a Numeric — the result of the operation. */
    @Seed.Property(role = SchemaVocabulary.Returns.KEY)
    static final SchemaRef returns = SchemaRef.iid(Numeric.KEY);

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "the operation of partitioning a quantity into equal parts";

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
    static final String symbol = "/";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishVerbLemma = "divide";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "division";

    public Divide(ItemRef iid) { super(iid); }
    public Divide(ItemRef iid, Librarian librarian) { super(iid, librarian); }

    @Override protected double applyDouble(double l, double r) { return l / r; }
    @Override protected long   applyLong(long l, long r) {
        if (r == 0L) throw new ArithmeticException("integer division by zero");
        return l / r;
    }
}
