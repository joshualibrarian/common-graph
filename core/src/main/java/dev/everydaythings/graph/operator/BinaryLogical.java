package dev.everydaythings.graph.operator;

import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.librarian.Librarian;

/**
 * Family base for binary boolean operators — And, Or. Pulls THEME (left) and
 * GOAL (right) operands from the frame, coerces each to a primitive boolean
 * (numbers compare against zero, non-null non-bool non-number treated as
 * truthy), and dispatches to the subclass.
 *
 * <p>Returns null when either operand is missing (partial application).
 */
public abstract class BinaryLogical extends Operator {

    protected BinaryLogical(ItemRef iid) { super(iid); }
    protected BinaryLogical(ItemRef iid, Librarian librarian) { super(iid, librarian); }

    @Override
    protected final Object evaluate(Frame frame) {
        Object left  = operandAt(frame, ThematicRole.Theme.KEY);
        Object right = operandAt(frame, ThematicRole.Goal.KEY);
        if (left == null || right == null) return null;
        return apply(coerceBool(left), coerceBool(right));
    }

    /** Compute over coerced boolean operands. */
    protected abstract Boolean apply(boolean left, boolean right);

    /** Lenient boolean coercion: Boolean direct, Number → nonzero, anything else → true. */
    private static boolean coerceBool(Object v) {
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n)  return n.doubleValue() != 0.0;
        return true;
    }
}
