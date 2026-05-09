package dev.everydaythings.graph.value;

import dev.everydaythings.graph.*;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.linguistics.GrammaticalFeature;
import dev.everydaythings.graph.linguistics.Gloss;
import dev.everydaythings.graph.linguistics.Language;
import dev.everydaythings.graph.linguistics.Lexeme;
import dev.everydaythings.graph.linguistics.PartOfSpeech;
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
@Seed.Item(key = Pipe.KEY, head = dev.everydaythings.graph.item.Item.Predicate.KEY)
@Seed.Embodies(key = Pipe.KEY)
public class Pipe extends Item {

    public static final String KEY = "cg.predicate:pipe";
    public static final ItemID IID = ItemID.fromString(KEY);

    @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "pipe — feeds the left operand into the right (function application chain)";

    @Seed.Frame(predicate = Lexeme.KEY)
    static final String symbol = "|>";

    @Seed.Frame(predicate = NotationVocabulary.Fixity.KEY)
    static final ItemID fixity = NotationVocabulary.Infix.IID;

    @Seed.Frame(predicate = NotationVocabulary.Associativity.KEY)
    static final ItemID associativity = NotationVocabulary.Left.IID;

    @Seed.Frame(predicate = NotationVocabulary.Precedence.KEY)
    static final long precedence = -10L;

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
    public Object applyBinary(Object left, Object right) {
        return right;
    }
}
