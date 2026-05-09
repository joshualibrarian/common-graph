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
 * The set-membership operator. Infix, non-associative, precedence 5. Tests whether
 * the left operand is a member of the right operand (a collection or container).
 */
@Seed.Item(key = In.KEY, head = dev.everydaythings.graph.item.Item.Predicate.KEY)
@Seed.Embodies(key = In.KEY)
public class In extends Item {

    public static final String KEY = "cg.predicate:in";
    public static final ItemID IID = ItemID.fromString(KEY);

    @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "set membership — true when the left operand is in the right collection";

    @Seed.Frame(predicate = Lexeme.KEY)
    static final String symbol = "in";

    @Seed.Frame(predicate = NotationVocabulary.Fixity.KEY)
    static final ItemID fixity = NotationVocabulary.Infix.IID;

    @Seed.Frame(predicate = NotationVocabulary.Associativity.KEY)
    static final ItemID associativity = NotationVocabulary.NonAssociative.IID;

    @Seed.Frame(predicate = NotationVocabulary.Precedence.KEY)
    static final long precedence = 5L;

    @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Preposition.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishPrepositionLemma = "in";

    public In(ItemID iid) { super(iid); }
    public In(ItemID iid, Librarian librarian) { super(iid, librarian); }

    public Object applyBinary(Object left, Object right) {
        if (right instanceof java.util.Collection<?> c) return c.contains(left);
        if (right instanceof Object[] arr) {
            for (Object item : arr) {
                if (left == null ? item == null : left.equals(item)) return true;
            }
            return false;
        }
        if (right instanceof String s && left instanceof String sub) return s.contains(sub);
        throw new IllegalArgumentException(
                "In.applyBinary: right operand must be a collection or string, got " + right);
    }
}
