package dev.everydaythings.graph.operator.flow;

import dev.everydaythings.graph.*;
import dev.everydaythings.graph.id.ItemID;
import dev.everydaythings.graph.linguistics.GrammaticalFeature;
import dev.everydaythings.graph.linguistics.Gloss;
import dev.everydaythings.graph.linguistics.Language;
import dev.everydaythings.graph.linguistics.Lexeme;
import dev.everydaythings.graph.linguistics.PartOfSpeech;
import dev.everydaythings.graph.operator.NotationVocabulary;
import dev.everydaythings.graph.operator.Operator;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.semantics.ThematicRole;

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
        bindings = {@Seed.Binding(role = NotationVocabulary.Arity.KEY, integer = 2)})
@Seed.Embodies(key = Pipe.KEY)
public class Pipe extends Operator {

    public static final String KEY = "cg.predicate:pipe";
    public static final ItemID IID = ItemID.fromString(KEY);

    @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "pipe — feeds the left operand into the right (function application chain)";

    /** Operator-form lexeme — bundles the symbol with its Fixity qualifier and ATTRIBUTE bindings for Precedence and Associativity. */
    @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                  qualifiers = {NotationVocabulary.Infix.KEY}),
          bindings = {
                  @Seed.Binding(role = ThematicRole.Attribute.KEY,
                          qualifiers = {NotationVocabulary.Precedence.KEY},
                          integer = -10),
                  @Seed.Binding(role = ThematicRole.Attribute.KEY,
                          qualifiers = {NotationVocabulary.Associativity.KEY},
                          ref = NotationVocabulary.Left.KEY)
          })
    static final String symbol = "|>";

    @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "pipe";

    public Pipe(ItemID iid) { super(iid); }
    public Pipe(ItemID iid, Librarian librarian) { super(iid, librarian); }

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
