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
 * The unary negation operator. Prefix, right-associative, precedence 25 (above
 * binary arithmetic). Surface form: {@code -5} = applies Negate to 5.
 */
@Seed.Item(key = Negate.KEY, head = dev.everydaythings.graph.item.Item.Predicate.KEY)
@Seed.Embodies(key = Negate.KEY)
public class Negate extends Item {

    public static final String KEY = "cg.predicate:negate";
    public static final ItemID IID = ItemID.fromString(KEY);

    @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "unary negation — flips the sign of a quantity";

    @Seed.Frame(predicate = Lexeme.KEY)
    static final String symbol = "-";

    @Seed.Frame(predicate = NotationVocabulary.Fixity.KEY)
    static final ItemID fixity = NotationVocabulary.Prefix.IID;

    @Seed.Frame(predicate = NotationVocabulary.Associativity.KEY)
    static final ItemID associativity = NotationVocabulary.Right.IID;

    @Seed.Frame(predicate = NotationVocabulary.Precedence.KEY)
    static final long precedence = 25L;

    @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishVerbLemma = "negate";

    @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "negation";

    public Negate(ItemID iid) { super(iid); }
    public Negate(ItemID iid, Librarian librarian) { super(iid, librarian); }

    public Object applyUnary(Object operand) {
        if (operand instanceof Number n) {
            if (operand instanceof Double || operand instanceof Float) {
                return -n.doubleValue();
            }
            return -n.longValue();
        }
        throw new IllegalArgumentException(
                "Negate.applyUnary: unsupported operand type " + operand);
    }
}
