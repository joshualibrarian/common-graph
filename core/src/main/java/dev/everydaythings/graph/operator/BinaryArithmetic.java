package dev.everydaythings.graph.operator;

import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.librarian.Librarian;

/**
 * Family base for binary arithmetic operators — Add, Subtract, Multiply,
 * Divide, Modulo. Pulls THEME (left) and GOAL (right) Number operands from
 * the incoming frame, promotes to {@code double} when either operand is a
 * floating-point type, otherwise stays in {@code long}, and dispatches to
 * the appropriate primitive-typed abstract method on the subclass.
 *
 * <p>Concrete operators implement just two short methods — the long path
 * and the double path. Type promotion, role extraction, and partial-
 * application (any operand missing → null result) are handled here.
 */
public abstract class BinaryArithmetic extends Operator {

    protected BinaryArithmetic(ItemRef iid) { super(iid); }
    protected BinaryArithmetic(ItemRef iid, Librarian librarian) { super(iid, librarian); }

    @Override
    protected final Object evaluate(Frame frame) {
        Number left  = numberAt(frame, ThematicRole.Theme.KEY);
        Number right = numberAt(frame, ThematicRole.Goal.KEY);
        if (left == null || right == null) return null;
        if (left instanceof Double || right instanceof Double
                || left instanceof Float || right instanceof Float) {
            return applyDouble(left.doubleValue(), right.doubleValue());
        }
        return applyLong(left.longValue(), right.longValue());
    }

    /** Compute with floating-point operands. */
    protected abstract double applyDouble(double left, double right);

    /** Compute with integer operands. May throw ({@link ArithmeticException}) on illegal
     *  operand combinations like integer division by zero. */
    protected abstract long applyLong(long left, long right);
}
