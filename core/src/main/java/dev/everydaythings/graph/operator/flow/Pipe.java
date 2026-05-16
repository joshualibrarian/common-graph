package dev.everydaythings.graph.operator.flow;

import dev.everydaythings.graph.*;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.language.*;
import dev.everydaythings.graph.operator.Operator;

import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.language.ThematicRole;

/**
 * The pipe operator — feeds the left operand as input to the right operand
 * (typically a function). Infix, left-associative, very low precedence ({@code -10})
 * so it composes loosely. Surface form: {@code value |> function}.
 *
 * <p>The actual evaluation semantics (calling the right operand on the left) live in
 * the runtime/evaluator layer; {@code applyBinary} here is a placeholder that simply
 * returns the right operand to satisfy the binary-operator contract.
 */
@Seed.Item(key = Pipe.KEY,
        head = Operator.KEY,
        bindings = {@Seed.Binding(role = Operator.Arity.KEY, integer = 2)})
@Seed.Embodies(key = Pipe.KEY)
public class Pipe extends Operator {

    public static final String KEY = "cg.predicate:pipe";

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "pipe — feeds the left operand into the right (function application chain)";

    /** Operator-form lexeme — bundles the symbol with its Fixity qualifier and ATTRIBUTE bindings for Precedence and Associativity. */
    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                  qualifiers = {Operator.Infix.KEY}),
          bindings = {
                  @Seed.Binding(role = ThematicRole.Attribute.KEY,
                          qualifiers = {Operator.Precedence.KEY},
                          integer = -10),
                  @Seed.Binding(role = ThematicRole.Attribute.KEY,
                          qualifiers = {Operator.Associativity.KEY},
                          ref = Operator.Left.KEY)
          })
    static final String symbol = "|>";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "pipe";

    public Pipe(ItemRef iid) { super(iid); }
    public Pipe(ItemRef iid, Librarian librarian) { super(iid, librarian); }

    /**
     * v1: placeholder. Real pipe semantics (apply right to left) require an evaluator
     * that resolves callable items; this returns the right operand verbatim so the
     * binary-operator contract is satisfied.
     */
    @Override
    public Object execute(Object... operands) {
        if (operands.length != 2) {
            throw new IllegalArgumentException(
                    "expects 2 operands, got " + operands.length);
        }
        Object left = operands[0];
        Object right = operands[1];
        return right;
    }
}
