package dev.everydaythings.graph.operator.math;

import dev.everydaythings.graph.*;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.id.SchemaRef;
import dev.everydaythings.graph.value.Numeric;
import dev.everydaythings.graph.language.*;
import dev.everydaythings.graph.operator.Operator;
import dev.everydaythings.graph.operator.OperatorNotation;

import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.language.ThematicRole;

/**
 * The exponentiation operator. Infix, <b>right-associative</b> (so {@code 2^3^2}
 * parses as {@code 2^(3^2)} = 512, not {@code (2^3)^2} = 64). Precedence 30 — above
 * multiplication. Always returns a double — no long path, since integer power
 * overflows quickly and the result is generally not integer-shaped.
 */
@Seed.Item(key = Power.KEY, head = Operator.KEY)
@Seed.Embodies(key = Power.KEY)
@Seed.Cili("i39979")
public class Power extends Operator {

    public static final String KEY = "cg.predicate:power";

    /** Arity — binary operator. */
    @Seed.Property(role = Operator.Arity.KEY)
    static final long arity = 2;

    /** Returns a Numeric — the result of the operation. */
    @Seed.Property(role = SchemaVocabulary.Returns.KEY)
    static final SchemaRef returns = SchemaRef.iid(Numeric.KEY);

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "exponentiation — raising one quantity to the power of another";

    /** OperatorNotation lexeme — symbol with Infix qualifier plus Precedence and Associativity. */
    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                  qualifiers = {OperatorNotation.KEY, Operator.Infix.KEY}),
          bindings = {
                  @Seed.Binding(role = ThematicRole.Attribute.KEY,
                          qualifiers = {Operator.Precedence.KEY},
                          integer = 30),
                  @Seed.Binding(role = ThematicRole.Attribute.KEY,
                          qualifiers = {Operator.Associativity.KEY},
                          ref = Operator.Right.KEY)
          })
    static final String symbol = "^";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String[] englishNounLemmas = {"power", "exponentiation"};

    public Power(ItemRef iid) { super(iid); }
    public Power(ItemRef iid, Librarian librarian) { super(iid, librarian); }

    @Override
    protected Object evaluate(Frame frame) {
        Number base = numberAt(frame, ThematicRole.Theme.KEY);
        Number exp  = numberAt(frame, ThematicRole.Goal.KEY);
        if (base == null || exp == null) return null;
        return Math.pow(base.doubleValue(), exp.doubleValue());
    }
}
