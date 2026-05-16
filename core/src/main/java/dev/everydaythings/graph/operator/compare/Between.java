package dev.everydaythings.graph.operator.compare;

import dev.everydaythings.graph.*;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.id.SchemaRef;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.operator.Operator;
import dev.everydaythings.graph.runtime.librarian.Librarian;
import dev.everydaythings.graph.value.Bool;

/**
 * Range membership.  Fully applied (SOURCE, GOAL, THEME) returns true when
 * THEME lies in the inclusive closed range [SOURCE, GOAL].  Partially applied
 * — typically with no THEME — becomes a matcher over candidates.
 *
 * <p>Linguistically a preposition ({@code "between 0 and 255"}).  The CG
 * shape uses three bindings:
 * <ul>
 *   <li>SOURCE — the lower bound</li>
 *   <li>GOAL — the upper bound</li>
 *   <li>THEME — the candidate (optional; absent → partial application)</li>
 * </ul>
 *
 * <p>Manifest carries the basic contract:
 * <ul>
 *   <li>{@code Arity = 3}</li>
 *   <li>{@code Returns = !Bool}</li>
 * </ul>
 *
 * <p>Operand types are intentionally <b>not</b> constrained in the manifest.
 * BETWEEN is polymorphic: it works for Numerics, Quantities, Lengths, Times,
 * Colors-by-channel, and anything else with an ordering.  Expressing
 * "orderable" as a type constraint requires a capabilities/interface concept
 * the data model doesn't yet have — deferred.  Until then, the operator's
 * {@code execute()} does runtime dispatch.
 */
@Seed.Item(key = Between.KEY,
        head = Operator.KEY,
        bindings = {@Seed.Binding(role = Operator.Arity.KEY, integer = 3)})
@Seed.Embodies(key = Between.KEY)
public class Between extends Operator {

    public static final String KEY = "cg.predicate:between";

    /** Returns Bool — fully applied is true/false; partially applied becomes a matcher. */
    @Seed.Property(role = SchemaVocabulary.Returns.KEY)
    static final SchemaRef returnType = SchemaRef.iid(Bool.KEY);

    // Operand-type EXPECTS bindings (!Source / !Goal / !Theme) intentionally
    // omitted — BETWEEN is polymorphic across orderable types.  See the
    // class javadoc; a capabilities/interface concept is needed to express
    // "orderable" cleanly and is deferred.

    @Seed.Frame(predicate = LexicalVocabulary.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    static final String englishGloss =
            "range membership — true when THEME lies in the inclusive closed range [SOURCE, GOAL]; "
                    + "partial application (no THEME) yields a matcher over candidates";

    @Seed.Frame(predicate = LexicalVocabulary.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY,
                  qualifiers = {Language.English.KEY, PartOfSpeech.Preposition.KEY, GrammaticalFeature.Lemma.KEY}))
    static final String englishPrepositionLemma = "between";

    public Between(ItemRef iid) { super(iid); }
    public Between(ItemRef iid, Librarian librarian) { super(iid, librarian); }

    @Override
    public Object execute(Object... operands) {
        if (operands.length != 3) {
            throw new IllegalArgumentException(
                    "Between expects 3 operands (SOURCE, GOAL, THEME), got " + operands.length);
        }
        Object source = operands[0];
        Object goal   = operands[1];
        Object theme  = operands[2];
        if (source instanceof Number s && goal instanceof Number g && theme instanceof Number t) {
            double sd = s.doubleValue();
            double gd = g.doubleValue();
            double td = t.doubleValue();
            double lo = Math.min(sd, gd);
            double hi = Math.max(sd, gd);
            return td >= lo && td <= hi;
        }
        throw new IllegalArgumentException(
                "Between.execute: unsupported operand types (SOURCE=" + source
                        + ", GOAL=" + goal + ", THEME=" + theme + ")");
    }
}
