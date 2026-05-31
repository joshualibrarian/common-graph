package dev.everydaythings.graph.operator.math;

import dev.everydaythings.graph.*;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.language.*;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.operator.Operator;
import dev.everydaythings.graph.operator.OperatorNotation;

import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.ref.SchemaRef;
import dev.everydaythings.graph.value.Numeric;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.ThematicRole;

/**
 * The unary negation operator. Prefix, right-associative, precedence 25 (above
 * binary arithmetic). Surface form: {@code -5} = applies Negate to 5.
 */
@Seed.Item(key = Negate.KEY, head = Operator.KEY)
@Seed.Embodies(key = Negate.KEY)
public class Negate extends Operator {

    public static final String KEY = "cg.predicate:negate";

    /** Arity — unary operator. */
    @Seed.Property(role = Operator.Arity.KEY)
    static final long arity = 1;

    /** Returns a Numeric — the result of the operation. */
    @Seed.Property(role = SchemaVocabulary.Returns.KEY)
    static final SchemaRef returns = SchemaRef.iid(Numeric.KEY);

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "unary negation — flips the sign of a quantity";

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
    static final String symbol = "-";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishVerbLemma = "negate";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "negation";

    public Negate(ItemRef iid) { super(iid); }
    public Negate(ItemRef iid, Librarian librarian) { super(iid, librarian); }

    @Override
    protected Object evaluate(Frame frame) {
        Number operand = numberAt(frame, ThematicRole.Theme.KEY);
        if (operand == null) return null;
        if (operand instanceof Double || operand instanceof Float) {
            return -operand.doubleValue();
        }
        return -operand.longValue();
    }
}
