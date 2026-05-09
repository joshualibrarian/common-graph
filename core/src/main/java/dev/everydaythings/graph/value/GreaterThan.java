package dev.everydaythings.graph.value;

import dev.everydaythings.graph.*;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.linguistics.Gloss;
import dev.everydaythings.graph.linguistics.Language;
import dev.everydaythings.graph.linguistics.Lexeme;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.semantics.ThematicRole;

/** Strict greater-than comparison. Infix, non-associative, precedence 5. */
@Seed.Item(key = GreaterThan.KEY, head = dev.everydaythings.graph.item.Item.Predicate.KEY)
@Seed.Embodies(key = GreaterThan.KEY)
public class GreaterThan extends Item {

    public static final String KEY = "cg.predicate:greater-than";
    public static final ItemID IID = ItemID.fromString(KEY);

    @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "true when the left operand is strictly greater than the right";

    @Seed.Frame(predicate = Lexeme.KEY)
    static final String symbol = ">";

    @Seed.Frame(predicate = NotationVocabulary.Fixity.KEY)
    static final ItemID fixity = NotationVocabulary.Infix.IID;

    @Seed.Frame(predicate = NotationVocabulary.Associativity.KEY)
    static final ItemID associativity = NotationVocabulary.NonAssociative.IID;

    @Seed.Frame(predicate = NotationVocabulary.Precedence.KEY)
    static final long precedence = 5L;

    public GreaterThan(ItemID iid) { super(iid); }
    public GreaterThan(ItemID iid, Librarian librarian) { super(iid, librarian); }

    public Object applyBinary(Object left, Object right) {
        if (left instanceof Number l && right instanceof Number r) {
            return l.doubleValue() > r.doubleValue();
        }
        throw new IllegalArgumentException(
                "GreaterThan.applyBinary: unsupported operand types " + left + " > " + right);
    }
}
