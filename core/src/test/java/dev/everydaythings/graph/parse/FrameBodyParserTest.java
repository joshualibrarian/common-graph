package dev.everydaythings.graph.parse;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.FrameBody;
import dev.everydaythings.graph.frame.eval.FrameEvaluator;
import dev.everydaythings.graph.frame.eval.Scope;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.Librarian;
import dev.everydaythings.graph.value.Function;
import dev.everydaythings.graph.value.Operator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the FrameBodyParser.
 *
 * <p>Verifies that text expressions are parsed into correct FrameBody trees,
 * and then round-trips through FrameEvaluator to verify end-to-end correctness.
 */
class FrameBodyParserTest {

    private static Librarian librarian;
    private FrameEvaluator evaluator;
    private Scope scope;
    private ExpressionLexer.SymbolResolver resolver;

    @BeforeAll
    static void initLibrarian() {
        librarian = Librarian.createInMemory();
    }

    @BeforeEach
    void setUp() {
        evaluator = new FrameEvaluator();
        scope = Scope.of(librarian);

        // Build symbol resolver — bridge using Operator.fromSymbol() until
        // TokenDictionary properly indexes and disambiguates operator symbols.
        // See design-lexer-symbol-resolution.md for the full plan.
        resolver = symbol -> {
            Operator op = Operator.fromSymbol(symbol);
            return op != null ? op.iid() : null;
        };
    }

    /**
     * Parse and evaluate in one step — the full pipeline.
     */
    private Object eval(String expression) {
        FrameBody frame = FrameBodyParser.parse(expression, resolver);
        return evaluator.evaluate(frame, scope);
    }

    // ==================================================================================
    // Structure Tests (verify the FrameBody tree shape)
    // ==================================================================================

    @Nested
    class StructureTests {

        @Test
        void simple_addition_produces_correct_frame() {
            FrameBody frame = FrameBodyParser.parse("3 + 4", resolver);
            assertThat(frame.predicate()).isEqualTo(Operator.Add.IID);
            assertThat(frame.frameBindings()).hasSize(2);
            assertThat(frame.frameBindings().get(0).role()).isEqualTo(ThematicRole.Theme.IID);
            assertThat(frame.frameBindings().get(1).role()).isEqualTo(ThematicRole.Goal.IID);
        }

        @Test
        void negation_produces_unary_frame() {
            FrameBody frame = FrameBodyParser.parse("-5", resolver);
            assertThat(frame.predicate()).isEqualTo(Operator.Negate.IID);
            assertThat(frame.frameBindings()).hasSize(1);
            assertThat(frame.frameBindings().get(0).role()).isEqualTo(ThematicRole.Theme.IID);
        }

        @Test
        void not_produces_unary_frame() {
            FrameBody frame = FrameBodyParser.parse("!true", resolver);
            assertThat(frame.predicate()).isEqualTo(Operator.Not.IID);
            assertThat(frame.frameBindings()).hasSize(1);
        }

        @Test
        void function_call_produces_function_frame() {
            FrameBody frame = FrameBodyParser.parse("sqrt(16)", resolver);
            assertThat(frame.predicate()).isEqualTo(Function.Sqrt.IID);
            assertThat(frame.frameBindings()).hasSize(1);
            assertThat(frame.frameBindings().get(0).role()).isEqualTo(ThematicRole.Theme.IID);
        }

        @Test
        void variable_reference_produces_resolve_frame() {
            FrameBody frame = FrameBodyParser.parse("x", resolver);
            assertThat(frame.predicate()).isEqualTo(CoreVocabulary.Resolve.IID);
            assertThat(frame.frameBindings()).hasSize(1);
            // The THEME binding should be a text literal "x"
            Binding theme = frame.frameBindings().get(0);
            assertThat(theme.role()).isEqualTo(ThematicRole.Theme.IID);
        }

        @Test
        void nested_expression_produces_nested_frames() {
            FrameBody frame = FrameBodyParser.parse("(1 + 2) * 3", resolver);
            assertThat(frame.predicate()).isEqualTo(Operator.Multiply.IID);
            // THEME should be a FrameTarget containing ADD
            Binding theme = frame.frameBindings().get(0);
            assertThat(theme.target()).isInstanceOf(BindingTarget.FrameTarget.class);
            FrameBody inner = ((BindingTarget.FrameTarget) theme.target()).body();
            assertThat(inner.predicate()).isEqualTo(Operator.Add.IID);
        }

        @Test
        void conditional_produces_conditional_frame() {
            FrameBody frame = FrameBodyParser.parse("if true then 1 else 0", resolver);
            assertThat(frame.predicate()).isEqualTo(CoreVocabulary.Conditional.IID);
            assertThat(frame.frameBindings()).hasSize(3);
        }

        @Test
        void let_produces_let_frame() {
            FrameBody frame = FrameBodyParser.parse("let x = 5 in x + 1", resolver);
            assertThat(frame.predicate()).isEqualTo(CoreVocabulary.Let.IID);
            assertThat(frame.frameBindings()).hasSize(3);
            // GOAL should be the variable name "x"
            Binding goalBinding = frame.getBinding(ThematicRole.Goal.IID);
            assertThat(goalBinding).isNotNull();
            assertThat(goalBinding.target()).isInstanceOf(Literal.class);
        }

        @Test
        void property_access_produces_access_frame() {
            FrameBody frame = FrameBodyParser.parse("obj.prop", resolver);
            assertThat(frame.predicate()).isEqualTo(CoreVocabulary.Access.IID);
            assertThat(frame.frameBindings()).hasSize(2);
        }
    }

    // ==================================================================================
    // End-to-End Evaluation (parse → evaluate)
    // ==================================================================================

    @Nested
    class Arithmetic {

        @Test
        void addition() {
            assertThat(eval("3 + 4")).isEqualTo(7.0);
        }

        @Test
        void subtraction() {
            assertThat(eval("10 - 3")).isEqualTo(7.0);
        }

        @Test
        void multiplication() {
            assertThat(eval("6 * 7")).isEqualTo(42.0);
        }

        @Test
        void division() {
            assertThat(eval("20 / 4")).isEqualTo(5.0);
        }

        @Test
        void modulo() {
            assertThat(eval("17 % 5")).isEqualTo(2.0);
        }

        @Test
        void power() {
            assertThat(eval("2 ^ 10")).isEqualTo(1024.0);
        }

        @Test
        void negation() {
            assertThat(eval("-5")).isEqualTo(-5.0);
        }

        @Test
        void parenthesized() {
            assertThat(eval("(2 + 3) * 4")).isEqualTo(20.0);
        }

        @Test
        void precedence_mul_before_add() {
            assertThat(eval("2 + 3 * 4")).isEqualTo(14.0);
        }

        @Test
        void left_associativity() {
            // 10 - 3 - 2 should be (10-3)-2 = 5, not 10-(3-2) = 9
            assertThat(eval("10 - 3 - 2")).isEqualTo(5.0);
        }

        @Test
        void right_associativity_power() {
            // 2^3^2 should be 2^(3^2) = 2^9 = 512
            assertThat(eval("2 ^ 3 ^ 2")).isEqualTo(512.0);
        }

        @Test
        void complex_nested() {
            assertThat(eval("((2 + 3) * (4 - 1))")).isEqualTo(15.0);
        }
    }

    @Nested
    class Comparison {

        @Test
        void equal() { assertThat(eval("5 == 5")).isEqualTo(true); }

        @Test
        void not_equal() { assertThat(eval("5 != 3")).isEqualTo(true); }

        @Test
        void less_than() { assertThat(eval("3 < 5")).isEqualTo(true); }

        @Test
        void greater_than() { assertThat(eval("5 > 3")).isEqualTo(true); }

        @Test
        void less_or_equal() { assertThat(eval("5 <= 5")).isEqualTo(true); }

        @Test
        void greater_or_equal() { assertThat(eval("3 >= 5")).isEqualTo(false); }
    }

    @Nested
    class Logical {

        @Test
        void and_true() { assertThat(eval("true && true")).isEqualTo(true); }

        @Test
        void and_false() { assertThat(eval("true && false")).isEqualTo(false); }

        @Test
        void or_true() { assertThat(eval("false || true")).isEqualTo(true); }

        @Test
        void or_false() { assertThat(eval("false || false")).isEqualTo(false); }

        @Test
        void not() { assertThat(eval("!true")).isEqualTo(false); }

        @Test
        void combined() { assertThat(eval("!false && true")).isEqualTo(true); }
    }

    @Nested
    class Functions {

        @Test
        void sqrt() { assertThat(eval("sqrt(16)")).isEqualTo(4.0); }

        @Test
        void abs() { assertThat(eval("abs(-7)")).isEqualTo(7.0); }

        @Test
        void nested_function_in_expression() {
            assertThat(eval("sqrt(16) + 1")).isEqualTo(5.0);
        }
    }

    @Nested
    class ControlFlow {

        @Test
        void conditional_true() {
            assertThat(eval("if true then 42 else 0")).isEqualTo(42L);
        }

        @Test
        void conditional_false() {
            assertThat(eval("if false then 42 else 0")).isEqualTo(0L);
        }

        @Test
        void conditional_with_expression() {
            assertThat(eval("if 5 > 3 then 10 + 20 else 0")).isEqualTo(30.0);
        }

        @Test
        void conditional_without_else() {
            // "if true then 42" — no else branch, should return 42
            assertThat(eval("if true then 42")).isEqualTo(42L);
        }

        @Test
        void let_binding() {
            assertThat(eval("let x = 5 in x + 3")).isEqualTo(8.0);
        }

        @Test
        void let_with_expression_value() {
            assertThat(eval("let x = 2 + 3 in x * 4")).isEqualTo(20.0);
        }
    }

    @Nested
    class Variables {

        @Test
        void resolve_from_scope() {
            Scope withVar = scope.withVariable("y", 42);
            FrameBody frame = FrameBodyParser.parse("y", resolver);
            assertThat(evaluator.evaluate(frame, withVar)).isEqualTo(42);
        }

        @Test
        void variable_in_expression() {
            Scope withVar = scope.withVariable("x", 10L);
            FrameBody frame = FrameBodyParser.parse("x + 5", resolver);
            assertThat(evaluator.evaluate(frame, withVar)).isEqualTo(15.0);
        }
    }

    @Nested
    class StringOps {

        @Test
        void string_concat() {
            assertThat(eval("\"foo\" ++ \"bar\"")).isEqualTo("foobar");
        }

        @Test
        void string_addition() {
            assertThat(eval("\"hello\" + \" world\"")).isEqualTo("hello world");
        }
    }

    @Nested
    class ErrorCases {

        @Test
        void empty_expression() {
            assertThatThrownBy(() -> FrameBodyParser.parse("", resolver))
                    .isInstanceOf(FrameBodyParser.ParseException.class);
        }

        @Test
        void unmatched_paren() {
            assertThatThrownBy(() -> FrameBodyParser.parse("(1 + 2", resolver))
                    .isInstanceOf(FrameBodyParser.ParseException.class);
        }

        @Test
        void unexpected_close_paren() {
            assertThatThrownBy(() -> FrameBodyParser.parse(")", resolver))
                    .isInstanceOf(FrameBodyParser.ParseException.class);
        }

        @Test
        void operator_without_operand() {
            assertThatThrownBy(() -> FrameBodyParser.parse("+", resolver))
                    .isInstanceOf(FrameBodyParser.ParseException.class);
        }
    }
}
