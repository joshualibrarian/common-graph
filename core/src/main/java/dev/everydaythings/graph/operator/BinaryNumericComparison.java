package dev.everydaythings.graph.operator;

import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.ThematicRole;
import dev.everydaythings.graph.runtime.librarian.Librarian;

/**
 * Family base for binary numeric comparisons — LessThan, LessOrEqual,
 * GreaterThan, GreaterOrEqual. Pulls THEME (left) and GOAL (right) Number
 * operands from the frame, converts to {@code double}, and dispatches to the
 * subclass's {@link #compare(double, double)}.
 *
 * <p>Returns null when either operand is missing or non-numeric — partial
 * application. {@link dev.everydaythings.graph.operator.compare.Equal Equal}
 * and {@link dev.everydaythings.graph.operator.compare.NotEqual NotEqual} are
 * not part of this family: they fall back to {@link Object#equals} for
 * non-numeric operands and so override {@link Operator#evaluate(Frame)}
 * directly.
 */
public abstract class BinaryNumericComparison extends Operator {

    protected BinaryNumericComparison(ItemRef iid) { super(iid); }
    protected BinaryNumericComparison(ItemRef iid, Librarian librarian) { super(iid, librarian); }

    @Override
    protected final Object evaluate(Frame frame) {
        Number left  = numberAt(frame, ThematicRole.Theme.KEY);
        Number right = numberAt(frame, ThematicRole.Goal.KEY);
        if (left == null || right == null) return null;
        return compare(left.doubleValue(), right.doubleValue());
    }

    /** Compare the two operands; return {@link Boolean#TRUE} or {@link Boolean#FALSE}. */
    protected abstract Boolean compare(double left, double right);
}
