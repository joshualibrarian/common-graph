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

/** The modulo (remainder) operator. Infix, left-associative, precedence 20. */
@Seed.Item(key = Modulo.KEY, head = dev.everydaythings.graph.item.Item.Predicate.KEY)
@Seed.Embodies(key = Modulo.KEY)
public class Modulo extends Item {

    public static final String KEY = "cg.predicate:modulo";
    public static final ItemID IID = ItemID.fromString(KEY);

    @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "the remainder after integer division";

    @Seed.Frame(predicate = Lexeme.KEY)
    static final String symbol = "%";

    @Seed.Frame(predicate = NotationVocabulary.Fixity.KEY)
    static final ItemID fixity = NotationVocabulary.Infix.IID;

    @Seed.Frame(predicate = NotationVocabulary.Associativity.KEY)
    static final ItemID associativity = NotationVocabulary.Left.IID;

    @Seed.Frame(predicate = NotationVocabulary.Precedence.KEY)
    static final long precedence = 20L;

    @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "modulo";

    public Modulo(ItemID iid) { super(iid); }
    public Modulo(ItemID iid, Librarian librarian) { super(iid, librarian); }

    public Object applyBinary(Object left, Object right) {
        if (left instanceof Number l && right instanceof Number r) {
            if (left instanceof Double || right instanceof Double
                    || left instanceof Float || right instanceof Float) {
                return l.doubleValue() % r.doubleValue();
            }
            long rv = r.longValue();
            if (rv == 0L) throw new ArithmeticException("integer modulo by zero");
            return l.longValue() % rv;
        }
        throw new IllegalArgumentException(
                "Modulo.applyBinary: unsupported operand types " + left + " % " + right);
    }
}
