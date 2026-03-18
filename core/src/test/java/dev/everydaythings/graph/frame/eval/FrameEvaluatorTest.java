package dev.everydaythings.graph.frame.eval;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.FrameBody;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.value.Operator;
import dev.everydaythings.graph.value.Function;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the unified FrameEvaluator.
 *
 * <p>All tests use hand-constructed FrameBody instances — no parser needed.
 * Each test verifies that the evaluator correctly resolves the predicate
 * and applies the right evaluation strategy.
 */
class FrameEvaluatorTest {

    private static Librarian librarian;
    private FrameEvaluator evaluator;
    private Scope scope;

    @BeforeAll
    static void initLibrarian() {
        librarian = Librarian.createInMemory();
    }

    @BeforeEach
    void setUp() {
        evaluator = new FrameEvaluator();
        scope = Scope.of(librarian);
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    /** Literal integer as a BindingTarget. */
    private static BindingTarget intLiteral(long value) {
        return Literal.ofInteger(value);
    }

    /** Literal text as a BindingTarget. */
    private static BindingTarget textLiteral(String value) {
        return Literal.ofText(value);
    }

    /** Literal boolean as a BindingTarget. */
    private static BindingTarget boolLiteral(boolean value) {
        return Literal.ofBoolean(value);
    }

    /** Create a THEME binding with a literal integer. */
    private static Binding theme(long value) {
        return new Binding(ThematicRole.Theme.IID, intLiteral(value));
    }

    /** Create a GOAL binding with a literal integer. */
    private static Binding goal(long value) {
        return new Binding(ThematicRole.Goal.IID, intLiteral(value));
    }

    /** Create a THEME binding with a nested frame. */
    private static Binding themeFrame(FrameBody nested) {
        return new Binding(ThematicRole.Theme.IID, new BindingTarget.FrameTarget(nested));
    }

    /** Create a GOAL binding with a nested frame. */
    private static Binding goalFrame(FrameBody nested) {
        return new Binding(ThematicRole.Goal.IID, new BindingTarget.FrameTarget(nested));
    }

    /** Create a RESULT binding with a nested frame. */
    private static Binding resultFrame(FrameBody nested) {
        return new Binding(ThematicRole.Result.IID, new BindingTarget.FrameTarget(nested));
    }

    // ==================================================================================
    // Arithmetic Operators
    // ==================================================================================

    @Nested
    class ArithmeticOperators {

        @Test
        void add_two_integers() {
            // 3 + 4 → ADD { THEME→3, GOAL→4 }
            FrameBody frame = new FrameBody(Operator.Add.IID, List.of(theme(3), goal(4)));
            Object result = evaluator.evaluate(frame, scope);
            assertThat(result).isEqualTo(7.0);
        }

        @Test
        void subtract() {
            // 10 - 3 → SUB { THEME→10, GOAL→3 }
            FrameBody frame = new FrameBody(Operator.Subtract.IID, List.of(theme(10), goal(3)));
            Object result = evaluator.evaluate(frame, scope);
            assertThat(result).isEqualTo(7.0);
        }

        @Test
        void multiply() {
            // 6 * 7 → MUL { THEME→6, GOAL→7 }
            FrameBody frame = new FrameBody(Operator.Multiply.IID, List.of(theme(6), goal(7)));
            Object result = evaluator.evaluate(frame, scope);
            assertThat(result).isEqualTo(42.0);
        }

        @Test
        void divide() {
            // 20 / 4 → DIV { THEME→20, GOAL→4 }
            FrameBody frame = new FrameBody(Operator.Divide.IID, List.of(theme(20), goal(4)));
            Object result = evaluator.evaluate(frame, scope);
            assertThat(result).isEqualTo(5.0);
        }

        @Test
        void division_by_zero_throws() {
            FrameBody frame = new FrameBody(Operator.Divide.IID, List.of(theme(10), goal(0)));
            assertThatThrownBy(() -> evaluator.evaluate(frame, scope))
                    .isInstanceOf(ArithmeticException.class)
                    .hasMessageContaining("Division by zero");
        }

        @Test
        void modulo() {
            // 17 % 5 → MOD { THEME→17, GOAL→5 }
            FrameBody frame = new FrameBody(Operator.Modulo.IID, List.of(theme(17), goal(5)));
            Object result = evaluator.evaluate(frame, scope);
            assertThat(result).isEqualTo(2.0);
        }

        @Test
        void power() {
            // 2 ^ 10 → POW { THEME→2, GOAL→10 }
            FrameBody frame = new FrameBody(Operator.Power.IID, List.of(theme(2), goal(10)));
            Object result = evaluator.evaluate(frame, scope);
            assertThat(result).isEqualTo(1024.0);
        }

        @Test
        void negate() {
            // -5 → NEG { THEME→5 }
            FrameBody frame = new FrameBody(Operator.Negate.IID, List.of(theme(5)));
            Object result = evaluator.evaluate(frame, scope);
            assertThat(result).isEqualTo(-5.0);
        }

        @Test
        void add_strings() {
            // "hello" + " world" → ADD { THEME→"hello", GOAL→" world" }
            FrameBody frame = new FrameBody(Operator.Add.IID, List.of(
                    new Binding(ThematicRole.Theme.IID, textLiteral("hello")),
                    new Binding(ThematicRole.Goal.IID, textLiteral(" world"))));
            Object result = evaluator.evaluate(frame, scope);
            assertThat(result).isEqualTo("hello world");
        }
    }

    // ==================================================================================
    // Comparison Operators
    // ==================================================================================

    @Nested
    class ComparisonOperators {

        @Test
        void equal_true() {
            FrameBody frame = new FrameBody(Operator.Equal.IID, List.of(theme(5), goal(5)));
            assertThat(evaluator.evaluate(frame, scope)).isEqualTo(true);
        }

        @Test
        void equal_false() {
            FrameBody frame = new FrameBody(Operator.Equal.IID, List.of(theme(5), goal(3)));
            assertThat(evaluator.evaluate(frame, scope)).isEqualTo(false);
        }

        @Test
        void not_equal() {
            FrameBody frame = new FrameBody(Operator.NotEqual.IID, List.of(theme(5), goal(3)));
            assertThat(evaluator.evaluate(frame, scope)).isEqualTo(true);
        }

        @Test
        void less_than() {
            FrameBody frame = new FrameBody(Operator.LessThan.IID, List.of(theme(3), goal(5)));
            assertThat(evaluator.evaluate(frame, scope)).isEqualTo(true);
        }

        @Test
        void greater_than() {
            FrameBody frame = new FrameBody(Operator.GreaterThan.IID, List.of(theme(5), goal(3)));
            assertThat(evaluator.evaluate(frame, scope)).isEqualTo(true);
        }

        @Test
        void less_or_equal() {
            FrameBody frame = new FrameBody(Operator.LessOrEqual.IID, List.of(theme(5), goal(5)));
            assertThat(evaluator.evaluate(frame, scope)).isEqualTo(true);
        }

        @Test
        void greater_or_equal() {
            FrameBody frame = new FrameBody(Operator.GreaterOrEqual.IID, List.of(theme(3), goal(5)));
            assertThat(evaluator.evaluate(frame, scope)).isEqualTo(false);
        }
    }

    // ==================================================================================
    // Logical Operators
    // ==================================================================================

    @Nested
    class LogicalOperators {

        @Test
        void and_true() {
            FrameBody frame = new FrameBody(Operator.And.IID, List.of(
                    new Binding(ThematicRole.Theme.IID, boolLiteral(true)),
                    new Binding(ThematicRole.Goal.IID, boolLiteral(true))));
            assertThat(evaluator.evaluate(frame, scope)).isEqualTo(true);
        }

        @Test
        void and_short_circuits_on_false() {
            // false && anything → false (GOAL is never evaluated)
            FrameBody frame = new FrameBody(Operator.And.IID, List.of(
                    new Binding(ThematicRole.Theme.IID, boolLiteral(false)),
                    new Binding(ThematicRole.Goal.IID, boolLiteral(true))));
            assertThat(evaluator.evaluate(frame, scope)).isEqualTo(false);
        }

        @Test
        void or_short_circuits_on_true() {
            // true || anything → true (GOAL is never evaluated)
            FrameBody frame = new FrameBody(Operator.Or.IID, List.of(
                    new Binding(ThematicRole.Theme.IID, boolLiteral(true)),
                    new Binding(ThematicRole.Goal.IID, boolLiteral(false))));
            assertThat(evaluator.evaluate(frame, scope)).isEqualTo(true);
        }

        @Test
        void or_false() {
            FrameBody frame = new FrameBody(Operator.Or.IID, List.of(
                    new Binding(ThematicRole.Theme.IID, boolLiteral(false)),
                    new Binding(ThematicRole.Goal.IID, boolLiteral(false))));
            assertThat(evaluator.evaluate(frame, scope)).isEqualTo(false);
        }

        @Test
        void not_true() {
            FrameBody frame = new FrameBody(Operator.Not.IID, List.of(
                    new Binding(ThematicRole.Theme.IID, boolLiteral(true))));
            assertThat(evaluator.evaluate(frame, scope)).isEqualTo(false);
        }

        @Test
        void not_false() {
            FrameBody frame = new FrameBody(Operator.Not.IID, List.of(
                    new Binding(ThematicRole.Theme.IID, boolLiteral(false))));
            assertThat(evaluator.evaluate(frame, scope)).isEqualTo(true);
        }
    }

    // ==================================================================================
    // Nested Frames (expression trees)
    // ==================================================================================

    @Nested
    class NestedFrames {

        @Test
        void nested_add_in_multiply() {
            // (3 + 4) * 2 → MUL { THEME → ADD { THEME→3, GOAL→4 }, GOAL → 2 }
            FrameBody inner = new FrameBody(Operator.Add.IID, List.of(theme(3), goal(4)));
            FrameBody outer = new FrameBody(Operator.Multiply.IID, List.of(themeFrame(inner), goal(2)));
            Object result = evaluator.evaluate(outer, scope);
            assertThat(result).isEqualTo(14.0);
        }

        @Test
        void deeply_nested_arithmetic() {
            // ((2 + 3) * (4 - 1)) → MUL { THEME → ADD{2,3}, GOAL → SUB{4,1} }
            FrameBody add = new FrameBody(Operator.Add.IID, List.of(theme(2), goal(3)));
            FrameBody sub = new FrameBody(Operator.Subtract.IID, List.of(theme(4), goal(1)));
            FrameBody mul = new FrameBody(Operator.Multiply.IID, List.of(themeFrame(add), goalFrame(sub)));
            Object result = evaluator.evaluate(mul, scope);
            assertThat(result).isEqualTo(15.0);
        }
    }

    // ==================================================================================
    // Control Flow
    // ==================================================================================

    @Nested
    class ControlFlow {

        @Test
        void conditional_true_branch() {
            // if true then 42 else 0
            FrameBody frame = new FrameBody(CoreVocabulary.Conditional.IID, List.of(
                    new Binding(ThematicRole.Theme.IID, boolLiteral(true)),
                    new Binding(ThematicRole.Result.IID, intLiteral(42)),
                    new Binding(ThematicRole.Goal.IID, intLiteral(0))));
            Object result = evaluator.evaluate(frame, scope);
            assertThat(result).isEqualTo(42L);
        }

        @Test
        void conditional_false_branch() {
            // if false then 42 else 0
            FrameBody frame = new FrameBody(CoreVocabulary.Conditional.IID, List.of(
                    new Binding(ThematicRole.Theme.IID, boolLiteral(false)),
                    new Binding(ThematicRole.Result.IID, intLiteral(42)),
                    new Binding(ThematicRole.Goal.IID, intLiteral(0))));
            Object result = evaluator.evaluate(frame, scope);
            assertThat(result).isEqualTo(0L);
        }

        @Test
        void conditional_with_nested_expressions() {
            // if (5 > 3) then (10 + 20) else 0
            FrameBody condition = new FrameBody(Operator.GreaterThan.IID, List.of(theme(5), goal(3)));
            FrameBody thenBranch = new FrameBody(Operator.Add.IID, List.of(theme(10), goal(20)));
            FrameBody frame = new FrameBody(CoreVocabulary.Conditional.IID, List.of(
                    themeFrame(condition),
                    resultFrame(thenBranch),
                    new Binding(ThematicRole.Goal.IID, intLiteral(0))));
            Object result = evaluator.evaluate(frame, scope);
            assertThat(result).isEqualTo(30.0);
        }

        @Test
        void sequence_returns_last() {
            // 1; 2; 3 → SEQUENCE { THEME→1, THEME→2, THEME→3 }
            FrameBody frame = new FrameBody(CoreVocabulary.Sequence.IID, List.of(
                    new Binding(ThematicRole.Theme.IID, intLiteral(1)),
                    new Binding(ThematicRole.Theme.IID, intLiteral(2)),
                    new Binding(ThematicRole.Theme.IID, intLiteral(3))));
            Object result = evaluator.evaluate(frame, scope);
            assertThat(result).isEqualTo(3L);
        }

        @Test
        void let_binding() {
            // let x = 5 in x + 3
            // LET { GOAL→"x", THEME→5, RESULT→ADD{RESOLVE{THEME→"x"}, GOAL→3} }
            FrameBody resolveX = new FrameBody(CoreVocabulary.Resolve.IID, List.of(
                    new Binding(ThematicRole.Theme.IID, textLiteral("x"))));
            FrameBody addExpr = new FrameBody(Operator.Add.IID, List.of(
                    themeFrame(resolveX),
                    goal(3)));
            FrameBody letFrame = new FrameBody(CoreVocabulary.Let.IID, List.of(
                    new Binding(ThematicRole.Goal.IID, textLiteral("x")),
                    new Binding(ThematicRole.Theme.IID, intLiteral(5)),
                    resultFrame(addExpr)));
            Object result = evaluator.evaluate(letFrame, scope);
            assertThat(result).isEqualTo(8.0);
        }

        @Test
        void resolve_from_scope() {
            // Set up scope with variable, then resolve it
            Scope withVar = scope.withVariable("y", 42);
            FrameBody frame = new FrameBody(CoreVocabulary.Resolve.IID, List.of(
                    new Binding(ThematicRole.Theme.IID, textLiteral("y"))));
            Object result = evaluator.evaluate(frame, withVar);
            assertThat(result).isEqualTo(42);
        }

        @Test
        void resolve_missing_variable_returns_null() {
            FrameBody frame = new FrameBody(CoreVocabulary.Resolve.IID, List.of(
                    new Binding(ThematicRole.Theme.IID, textLiteral("nonexistent"))));
            Object result = evaluator.evaluate(frame, scope);
            assertThat(result).isNull();
        }
    }

    // ==================================================================================
    // Functions
    // ==================================================================================

    @Nested
    class Functions {

        @Test
        void sqrt() {
            // sqrt(16) → SQRT { THEME→16 }
            FrameBody frame = new FrameBody(Function.Sqrt.IID, List.of(theme(16)));
            Object result = evaluator.evaluate(frame, scope);
            assertThat(result).isEqualTo(4.0);
        }

        @Test
        void abs_negative() {
            // abs(-7) → ABS { THEME → NEG{THEME→7} }
            FrameBody neg = new FrameBody(Operator.Negate.IID, List.of(theme(7)));
            FrameBody frame = new FrameBody(Function.Abs.IID, List.of(themeFrame(neg)));
            Object result = evaluator.evaluate(frame, scope);
            assertThat(result).isEqualTo(7.0);
        }
    }

    // ==================================================================================
    // Scope
    // ==================================================================================

    @Nested
    class ScopeTests {

        @Test
        void scope_variable_lookup() {
            Scope s = scope.withVariable("x", 10);
            assertThat(s.lookup("x")).contains(10);
        }

        @Test
        void scope_chain_inner_shadows_outer() {
            Scope outer = scope.withVariable("x", 10);
            Scope inner = outer.withVariable("x", 20);
            assertThat(inner.lookup("x")).contains(20);
        }

        @Test
        void scope_chain_inherits_from_parent() {
            Scope outer = scope.withVariable("x", 10);
            Scope inner = outer.withVariable("y", 20);
            assertThat(inner.lookup("x")).contains(10);
            assertThat(inner.lookup("y")).contains(20);
        }

        @Test
        void scope_missing_returns_empty() {
            assertThat(scope.lookup("missing")).isEmpty();
        }
    }

    // ==================================================================================
    // Error Cases
    // ==================================================================================

    @Nested
    class ErrorCases {

        @Test
        void unknown_predicate_throws() {
            ItemID unknown = ItemID.fromString("cg.test:unknown-predicate");
            FrameBody frame = new FrameBody(unknown, List.of(theme(1)));
            assertThatThrownBy(() -> evaluator.evaluate(frame, scope))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No implementation for predicate");
        }

        @Test
        void null_frame_throws() {
            assertThatThrownBy(() -> evaluator.evaluate(null, scope))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ==================================================================================
    // String Operators
    // ==================================================================================

    @Nested
    class StringOperators {

        @Test
        void concat() {
            FrameBody frame = new FrameBody(Operator.Concat.IID, List.of(
                    new Binding(ThematicRole.Theme.IID, textLiteral("foo")),
                    new Binding(ThematicRole.Goal.IID, textLiteral("bar"))));
            assertThat(evaluator.evaluate(frame, scope)).isEqualTo("foobar");
        }
    }
}
