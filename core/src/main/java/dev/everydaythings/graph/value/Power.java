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
 * The exponentiation operator. Infix, <b>right-associative</b> (so {@code 2^3^2}
 * parses as {@code 2^(3^2)} = 512, not {@code (2^3)^2} = 64). Precedence 30 — above
 * multiplication.
 */
@Seed.Item(key = Power.KEY, head = dev.everydaythings.graph.item.Item.Predicate.KEY)
@Seed.Embodies(key = Power.KEY)
public class Power extends Item {

    public static final String KEY = "cg.predicate:power";
    public static final ItemID IID = ItemID.fromString(KEY);

    @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "exponentiation — raising one quantity to the power of another";

    @Seed.Frame(predicate = Lexeme.KEY)
    static final String symbol = "^";

    @Seed.Frame(predicate = NotationVocabulary.Fixity.KEY)
    static final ItemID fixity = NotationVocabulary.Infix.IID;

    @Seed.Frame(predicate = NotationVocabulary.Associativity.KEY)
    static final ItemID associativity = NotationVocabulary.Right.IID;

    @Seed.Frame(predicate = NotationVocabulary.Precedence.KEY)
    static final long precedence = 30L;

    @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String[] englishNounLemmas = {"power", "exponentiation"};

    public Power(ItemID iid) { super(iid); }
    public Power(ItemID iid, Librarian librarian) { super(iid, librarian); }

    public Object applyBinary(Object left, Object right) {
        if (left instanceof Number l && right instanceof Number r) {
            return Math.pow(l.doubleValue(), r.doubleValue());
        }
        throw new IllegalArgumentException(
                "Power.applyBinary: unsupported operand types " + left + " ^ " + right);
    }
}
