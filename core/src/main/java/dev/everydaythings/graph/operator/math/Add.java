package dev.everydaythings.graph.operator.math;

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
 * The addition operator — predicate for {@code ADD { THEME → x, GOAL → y }} frames.
 *
 * <p>Per the white paper, ADD is structurally a transfer: one quantity (THEME) is
 * delivered to another (GOAL). "Add 5 to 3" — 5 is the Theme in motion, 3 is the
 * Goal destination. Surface infix form: {@code 5 + 3}. Verbose English form:
 * {@code "add 5 to 3"}.
 *
 * <p>Notation metadata rides on the operator-form Lexeme frame ({@code "+"} with an
 * Infix qualifier on the value binding plus ATTRIBUTE bindings for Precedence and
 * Associativity); semantic arity rides directly on the manifest body via
 * {@code @Seed.Item.bindings}.
 */
@Seed.Item(key = Add.KEY,
        head = Operator.KEY,
        bindings = {@Seed.Binding(role = NotationVocabulary.Arity.KEY, integer = 2)})
@Seed.Embodies(key = Add.KEY)
public class Add extends Operator {

    /** Canonical key for the addition predicate. */
    public static final String KEY = "cg.predicate:add";

    /** Deterministic IID for the addition predicate. */
    public static final ItemID IID = ItemID.fromString(KEY);

    @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "the operation of summing two quantities";

    /**
     * Operator-form lexeme — bundles the {@code "+"} symbol with its Fixity qualifier
     * (Infix) and ATTRIBUTE bindings for Precedence (10, same tier as subtract; below
     * multiply/divide) and Associativity (Left, so {@code 5+3+2} groups as
     * {@code (5+3)+2}).
     */
    @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                  qualifiers = {NotationVocabulary.Infix.KEY}),
          bindings = {
                  @Seed.Binding(role = ThematicRole.Attribute.KEY,
                          qualifiers = {NotationVocabulary.Precedence.KEY},
                          integer = 10),
                  @Seed.Binding(role = ThematicRole.Attribute.KEY,
                          qualifiers = {NotationVocabulary.Associativity.KEY},
                          ref = NotationVocabulary.Left.KEY)
          })
    static final String symbol = "+";

    @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Verb.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String[] englishVerbLemmas = {"add", "sum"};

    @Seed.Frame(predicate = Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishNounLemma = "addition";

    public Add(ItemID iid) {
        super(iid);
    }

    public Add(ItemID iid, Librarian librarian) {
        super(iid, librarian);
    }

    @Override
    public Object execute(Object... operands) {
        if (operands.length != 2) {
            throw new IllegalArgumentException(
                    "Add expects 2 operands, got " + operands.length);
        }
        Object left = operands[0];
        Object right = operands[1];
        if (left instanceof Number l && right instanceof Number r) {
            if (left instanceof Double || right instanceof Double
                    || left instanceof Float || right instanceof Float) {
                return l.doubleValue() + r.doubleValue();
            }
            return l.longValue() + r.longValue();
        }
        throw new IllegalArgumentException(
                "Add.execute: unsupported operand types " + left + " + " + right);
    }
}
