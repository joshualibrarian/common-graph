package dev.everydaythings.graph.operator.compare;

import dev.everydaythings.graph.*;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.ref.SchemaRef;
import dev.everydaythings.graph.GrammaticalFeature;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.LexicalVocabulary;
import dev.everydaythings.graph.PartOfSpeech;
import dev.everydaythings.graph.ThematicRole;
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

    /**
     * Pull SOURCE / GOAL / THEME from the incoming frame and check that THEME
     * lies in the inclusive closed range [SOURCE, GOAL].  Returns null when any
     * operand is absent (partial application — a matcher, not a value; matcher
     * orchestration lands separately).
     */
    @Override
    protected Object evaluate(Frame frame) {
        Number source = numberAt(frame, ThematicRole.Source.KEY);
        Number goal   = numberAt(frame, ThematicRole.Goal.KEY);
        Number theme  = numberAt(frame, ThematicRole.Theme.KEY);
        if (source == null || goal == null || theme == null) return null;
        double lo = Math.min(source.doubleValue(), goal.doubleValue());
        double hi = Math.max(source.doubleValue(), goal.doubleValue());
        return theme.doubleValue() >= lo && theme.doubleValue() <= hi;
    }
}
