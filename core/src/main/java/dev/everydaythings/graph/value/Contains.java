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
 * The containment operator — inverse of {@link In}. {@code container contains element}
 * is the same as {@code element in container}. Infix, non-associative, precedence 5.
 */
@Seed.Item(key = Contains.KEY, head = dev.everydaythings.graph.item.Item.Predicate.KEY)
@Seed.Embodies(key = Contains.KEY)
public class Contains extends Item {

    public static final String KEY = "cg.predicate:contains";
    public static final ItemID IID = ItemID.fromString(KEY);

    @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "containment — true when the left collection contains the right operand";

    @Seed.Frame(predicate = Lexeme.KEY)
    static final String symbol = "contains";

    @Seed.Frame(predicate = NotationVocabulary.Fixity.KEY)
    static final ItemID fixity = NotationVocabulary.Infix.IID;

    @Seed.Frame(predicate = NotationVocabulary.Associativity.KEY)
    static final ItemID associativity = NotationVocabulary.NonAssociative.IID;

    @Seed.Frame(predicate = NotationVocabulary.Precedence.KEY)
    static final long precedence = 5L;

    @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishVerbLemma = "contain";

    public Contains(ItemID iid) { super(iid); }
    public Contains(ItemID iid, Librarian librarian) { super(iid, librarian); }

    public Object applyBinary(Object left, Object right) {
        if (left instanceof java.util.Collection<?> c) return c.contains(right);
        if (left instanceof Object[] arr) {
            for (Object item : arr) {
                if (right == null ? item == null : right.equals(item)) return true;
            }
            return false;
        }
        if (left instanceof String s && right instanceof String sub) return s.contains(sub);
        throw new IllegalArgumentException(
                "Contains.applyBinary: left operand must be a collection or string, got " + left);
    }
}
