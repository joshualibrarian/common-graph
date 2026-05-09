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

/** The logical-OR operator. Infix, left-associative, precedence 0 (lowest binary). */
@Seed.Item(key = Or.KEY, head = dev.everydaythings.graph.item.Item.Predicate.KEY)
@Seed.Embodies(key = Or.KEY)
public class Or extends Item {

    public static final String KEY = "cg.predicate:or";
    public static final ItemID IID = ItemID.fromString(KEY);

    @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "logical disjunction — true when at least one operand is true";

    @Seed.Frame(predicate = Lexeme.KEY)
    static final String symbol = "||";

    @Seed.Frame(predicate = NotationVocabulary.Fixity.KEY)
    static final ItemID fixity = NotationVocabulary.Infix.IID;

    @Seed.Frame(predicate = NotationVocabulary.Associativity.KEY)
    static final ItemID associativity = NotationVocabulary.Left.IID;

    @Seed.Frame(predicate = NotationVocabulary.Precedence.KEY)
    static final long precedence = 0L;

    @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Conjunction.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishConjunctionLemma = "or";

    public Or(ItemID iid) { super(iid); }
    public Or(ItemID iid, Librarian librarian) { super(iid, librarian); }

    public Object applyBinary(Object left, Object right) {
        return toBoolean(left) || toBoolean(right);
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        if (value == null) return false;
        if (value instanceof Number n) return n.doubleValue() != 0.0;
        return true;
    }
}
