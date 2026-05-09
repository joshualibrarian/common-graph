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
 * The addition operator — predicate for {@code ADD { THEME → x, GOAL → y }} frames.
 *
 * <p>Per the white paper, ADD is structurally a transfer: one quantity (THEME) is
 * delivered to another (GOAL). "Add 5 to 3" — 5 is the Theme in motion, 3 is the
 * Goal destination. Surface infix form: {@code 5 + 3}. Verbose English form:
 * {@code "add 5 to 3"}.
 *
 * <p>Operator metadata is data — declared via {@code @Seed.Frame} annotations as frames
 * endorsed by Add's seed manifest:
 * <ul>
 *   <li>Universal Lexeme {@code "+"} (no Language qualifier) — surface symbol for infix rendering</li>
 *   <li>{@code Fixity → Infix} — operator appears between operands</li>
 *   <li>{@code Associativity → Left} — {@code 5+3+2} parses as {@code (5+3)+2}</li>
 *   <li>{@code Precedence → 10} — same tier as subtract; below multiply/divide</li>
 *   <li>English verb lemmas "add" / "sum" and noun lemma "addition" via Lexeme frames</li>
 * </ul>
 *
 * <p>Operator code (the actual computation) is the part that can't be data —
 * {@link #applyBinary} sums two numbers. Future runtimes (GraalVM polyglot, etc.)
 * may carry alternate implementations of the same contract.
 */
@Seed.Item(key = Add.KEY, head = dev.everydaythings.graph.item.Item.Predicate.KEY)
@Seed.Embodies(key = Add.KEY)
public class Add extends Item {

    /** Canonical key for the addition predicate. */
    public static final String KEY = "cg.predicate:add";

    /** Deterministic IID for the addition predicate. */
    public static final ItemID IID = ItemID.fromString(KEY);

    @Seed.Frame(predicate = Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss = "the operation of summing two quantities";

    /** Universal symbol — Lexeme with no Language qualifier. "+" works in any language. */
    @Seed.Frame(predicate = Lexeme.KEY)
    static final String symbol = "+";

    @Seed.Frame(predicate = NotationVocabulary.Fixity.KEY)
    static final ItemID fixity = NotationVocabulary.Infix.IID;

    @Seed.Frame(predicate = NotationVocabulary.Associativity.KEY)
    static final ItemID associativity = NotationVocabulary.Left.IID;

    /**
     * Precedence — higher values bind tighter. Addition is at the same level as
     * subtraction; multiplication/division are higher; exponentiation higher still.
     */
    @Seed.Frame(predicate = NotationVocabulary.Precedence.KEY)
    static final long precedence = 10L;

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

    /**
     * Apply addition to two numeric operands. Returns a {@code long} for integer
     * inputs and a {@code double} for floating inputs.
     *
     * <p>v1: minimal numeric handling. Future: dispatch to {@code Quantity} addition
     * (with unit conversion), {@code Decimal} addition, etc., through the runtime
     * adapter system.
     */
    public Object applyBinary(Object left, Object right) {
        if (left instanceof Number l && right instanceof Number r) {
            if (left instanceof Double || right instanceof Double
                    || left instanceof Float || right instanceof Float) {
                return l.doubleValue() + r.doubleValue();
            }
            return l.longValue() + r.longValue();
        }
        throw new IllegalArgumentException(
                "Add.applyBinary: unsupported operand types " + left + " + " + right);
    }
}
