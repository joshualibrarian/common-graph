package dev.everydaythings.graph.expression;

import dev.everydaythings.graph.item.component.expression.BinaryExpression;
import dev.everydaythings.graph.item.component.expression.EvaluationContext;
import dev.everydaythings.graph.item.component.expression.Expression;
import dev.everydaythings.graph.item.component.expression.FunctionExpression;
import dev.everydaythings.graph.item.component.expression.LiteralExpression;
import dev.everydaythings.graph.item.component.expression.ReferenceExpression;
import dev.everydaythings.graph.item.component.expression.UnaryExpression;
import dev.everydaythings.graph.value.Operator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ExpressionParser} — Pratt parser for expression tokens.
 */
class ExpressionParserTest {

    // ==================================================================================
    // HELPERS
    // ==================================================================================

    private static ExpressionToken.LiteralToken lit(long n) {
        return ExpressionToken.LiteralToken.ofNumber(n);
    }

    private static ExpressionToken.LiteralToken lit(double n) {
        return ExpressionToken.LiteralToken.ofNumber(n);
    }

    private static ExpressionToken.LiteralToken litStr(String s) {
        return ExpressionToken.LiteralToken.ofString(s);
    }

    private static ExpressionToken.OpToken op(Operator o) {
        return new ExpressionToken.OpToken(o.iid());
    }

    private static ExpressionToken.NameToken name(String n) {
        return new ExpressionToken.NameToken(n);
    }

    private static ExpressionToken.OpenParen open() {
        return new ExpressionToken.OpenParen();
    }

    private static ExpressionToken.CloseParen close() {
        return new ExpressionToken.CloseParen();
    }

    private static EvaluationContext ctx() {
        return new EvaluationContext(null, null);
    }

    private static EvaluationContext ctx(String var, Object val) {
        return new EvaluationContext(null, null).withBinding(var, val);
    }

    // ==================================================================================
    // LITERAL PARSING
    // ==================================================================================

    @Test
    void parseSingleNumber() {
        Expression expr = ExpressionParser.parse(List.of(lit(42)));
        assertThat(expr).isInstanceOf(LiteralExpression.class);
        assertThat(expr.evaluate(ctx())).isEqualTo(42L);
    }

    @Test
    void parseSingleString() {
        Expression expr = ExpressionParser.parse(List.of(litStr("hello")));
        assertThat(expr).isInstanceOf(LiteralExpression.class);
        assertThat(expr.evaluate(ctx())).isEqualTo("hello");
    }

    // ==================================================================================
    // ARITHMETIC
    // ==================================================================================

    @Test
    void parseAddition() {
        // 2 + 3 = 5
        Expression expr = ExpressionParser.parse(List.of(
                lit(2), op(Operator.ADD), lit(3)));
        assertThat(expr).isInstanceOf(BinaryExpression.class);
        assertThat(((Number) expr.evaluate(ctx())).doubleValue()).isEqualTo(5.0);
    }

    @Test
    void parseSubtraction() {
        // 10 - 4 = 6
        Expression expr = ExpressionParser.parse(List.of(
                lit(10), op(Operator.SUBTRACT), lit(4)));
        assertThat(((Number) expr.evaluate(ctx())).doubleValue()).isEqualTo(6.0);
    }

    @Test
    void parseMultiplication() {
        // 3 * 7 = 21
        Expression expr = ExpressionParser.parse(List.of(
                lit(3), op(Operator.MULTIPLY), lit(7)));
        assertThat(((Number) expr.evaluate(ctx())).doubleValue()).isEqualTo(21.0);
    }

    @Test
    void parseDivision() {
        // 15 / 3 = 5
        Expression expr = ExpressionParser.parse(List.of(
                lit(15), op(Operator.DIVIDE), lit(3)));
        assertThat(((Number) expr.evaluate(ctx())).doubleValue()).isEqualTo(5.0);
    }

    @Test
    void parsePower() {
        // 2 ^ 10 = 1024
        Expression expr = ExpressionParser.parse(List.of(
                lit(2), op(Operator.POWER), lit(10)));
        assertThat(((Number) expr.evaluate(ctx())).doubleValue()).isEqualTo(1024.0);
    }

    // ==================================================================================
    // OPERATOR PRECEDENCE
    // ==================================================================================

    @Test
    void precedence_multiplyBeforeAdd() {
        // 2 + 3 * 4 = 14 (not 20)
        Expression expr = ExpressionParser.parse(List.of(
                lit(2), op(Operator.ADD), lit(3), op(Operator.MULTIPLY), lit(4)));
        assertThat(((Number) expr.evaluate(ctx())).doubleValue()).isEqualTo(14.0);
    }

    @Test
    void precedence_powerBeforeMultiply() {
        // 2 * 3 ^ 2 = 18 (not 36)
        Expression expr = ExpressionParser.parse(List.of(
                lit(2), op(Operator.MULTIPLY), lit(3), op(Operator.POWER), lit(2)));
        assertThat(((Number) expr.evaluate(ctx())).doubleValue()).isEqualTo(18.0);
    }

    @Test
    void precedence_parenthesesOverride() {
        // (2 + 3) * 4 = 20
        Expression expr = ExpressionParser.parse(List.of(
                open(), lit(2), op(Operator.ADD), lit(3), close(),
                op(Operator.MULTIPLY), lit(4)));
        assertThat(((Number) expr.evaluate(ctx())).doubleValue()).isEqualTo(20.0);
    }

    @Test
    void associativity_leftForSubtract() {
        // 10 - 3 - 2 = 5 (left-associative: (10-3)-2, not 10-(3-2))
        Expression expr = ExpressionParser.parse(List.of(
                lit(10), op(Operator.SUBTRACT), lit(3), op(Operator.SUBTRACT), lit(2)));
        assertThat(((Number) expr.evaluate(ctx())).doubleValue()).isEqualTo(5.0);
    }

    @Test
    void associativity_rightForPower() {
        // 2 ^ 3 ^ 2 = 512 (right-associative: 2^(3^2) = 2^9, not (2^3)^2 = 64)
        Expression expr = ExpressionParser.parse(List.of(
                lit(2), op(Operator.POWER), lit(3), op(Operator.POWER), lit(2)));
        assertThat(((Number) expr.evaluate(ctx())).doubleValue()).isEqualTo(512.0);
    }

    // ==================================================================================
    // COMPARISON
    // ==================================================================================

    @Test
    void parseEqual() {
        // 5 == 5 = true
        Expression expr = ExpressionParser.parse(List.of(
                lit(5), op(Operator.EQUAL), lit(5)));
        assertThat(expr.evaluate(ctx())).isEqualTo(true);
    }

    @Test
    void parseLessThan() {
        // 3 < 5 = true
        Expression expr = ExpressionParser.parse(List.of(
                lit(3), op(Operator.LESS_THAN), lit(5)));
        assertThat(expr.evaluate(ctx())).isEqualTo(true);
    }

    // ==================================================================================
    // LOGICAL
    // ==================================================================================

    @Test
    void parseAnd() {
        Expression expr = ExpressionParser.parse(List.of(
                ExpressionToken.LiteralToken.ofBoolean(true),
                op(Operator.AND),
                ExpressionToken.LiteralToken.ofBoolean(false)));
        assertThat(expr.evaluate(ctx())).isEqualTo(false);
    }

    @Test
    void parseOr() {
        Expression expr = ExpressionParser.parse(List.of(
                ExpressionToken.LiteralToken.ofBoolean(false),
                op(Operator.OR),
                ExpressionToken.LiteralToken.ofBoolean(true)));
        assertThat(expr.evaluate(ctx())).isEqualTo(true);
    }

    // ==================================================================================
    // PREFIX OPERATORS
    // ==================================================================================

    @Test
    void parseNegate() {
        // -42 = -42
        Expression expr = ExpressionParser.parse(List.of(
                op(Operator.NEGATE), lit(42)));
        assertThat(expr).isInstanceOf(UnaryExpression.class);
        assertThat(((Number) expr.evaluate(ctx())).doubleValue()).isEqualTo(-42.0);
    }

    @Test
    void parseNot() {
        // !true = false
        Expression expr = ExpressionParser.parse(List.of(
                op(Operator.NOT), ExpressionToken.LiteralToken.ofBoolean(true)));
        assertThat(expr.evaluate(ctx())).isEqualTo(false);
    }

    @Test
    void negateInExpression() {
        // 5 + -3 = 2
        Expression expr = ExpressionParser.parse(List.of(
                lit(5), op(Operator.ADD), op(Operator.NEGATE), lit(3)));
        assertThat(((Number) expr.evaluate(ctx())).doubleValue()).isEqualTo(2.0);
    }

    // ==================================================================================
    // VARIABLES AND REFERENCES
    // ==================================================================================

    @Test
    void parseVariable() {
        // x where x=10
        Expression expr = ExpressionParser.parse(List.of(name("x")));
        assertThat(expr).isInstanceOf(ReferenceExpression.class);
        assertThat(expr.evaluate(ctx("x", 10))).isEqualTo(10);
    }

    @Test
    void parseVariableExpression() {
        // x + y where x=3, y=4
        Expression expr = ExpressionParser.parse(List.of(
                name("x"), op(Operator.ADD), name("y")));
        EvaluationContext evalCtx = ctx("x", 3).withBinding("y", 4);
        assertThat(((Number) expr.evaluate(evalCtx)).doubleValue()).isEqualTo(7.0);
    }

    @Test
    void parsePolynomial() {
        // x^2 + 3*x + 1 where x=5 → 25+15+1 = 41
        Expression expr = ExpressionParser.parse(List.of(
                name("x"), op(Operator.POWER), lit(2),
                op(Operator.ADD),
                lit(3), op(Operator.MULTIPLY), name("x"),
                op(Operator.ADD),
                lit(1)));
        assertThat(((Number) expr.evaluate(ctx("x", 5))).doubleValue()).isEqualTo(41.0);
    }

    // ==================================================================================
    // FUNCTION CALLS
    // ==================================================================================

    @Test
    void parseFunctionCall() {
        // sqrt(16) = 4
        Expression expr = ExpressionParser.parse(List.of(
                name("sqrt"), open(), lit(16), close()));
        assertThat(expr).isInstanceOf(FunctionExpression.class);
        assertThat(((Number) expr.evaluate(ctx())).doubleValue()).isEqualTo(4.0);
    }

    // ==================================================================================
    // COMPLEX EXPRESSIONS
    // ==================================================================================

    @Test
    void complexArithmetic() {
        // (2 + 3) * (10 - 4) / 3 = 10
        Expression expr = ExpressionParser.parse(List.of(
                open(), lit(2), op(Operator.ADD), lit(3), close(),
                op(Operator.MULTIPLY),
                open(), lit(10), op(Operator.SUBTRACT), lit(4), close(),
                op(Operator.DIVIDE),
                lit(3)));
        assertThat(((Number) expr.evaluate(ctx())).doubleValue()).isEqualTo(10.0);
    }

    @Test
    void nestedParentheses() {
        // ((2 + 3)) = 5
        Expression expr = ExpressionParser.parse(List.of(
                open(), open(), lit(2), op(Operator.ADD), lit(3), close(), close()));
        assertThat(((Number) expr.evaluate(ctx())).doubleValue()).isEqualTo(5.0);
    }

    @Test
    void comparisonInExpression() {
        // (3 + 2) == 5 → true
        Expression expr = ExpressionParser.parse(List.of(
                open(), lit(3), op(Operator.ADD), lit(2), close(),
                op(Operator.EQUAL), lit(5)));
        assertThat(expr.evaluate(ctx())).isEqualTo(true);
    }

    // ==================================================================================
    // EXPRESSION STRING
    // ==================================================================================

    @Test
    void toExpressionString() {
        // 2 + 3 * 4
        Expression expr = ExpressionParser.parse(List.of(
                lit(2), op(Operator.ADD), lit(3), op(Operator.MULTIPLY), lit(4)));
        String str = expr.toExpressionString();
        assertThat(str).contains("+").contains("*");
    }

    // ==================================================================================
    // HEURISTIC
    // ==================================================================================

    @Test
    void looksLikeExpression_withOperator() {
        assertThat(ExpressionParser.looksLikeExpression(
                List.of(lit(2), op(Operator.ADD), lit(3)))).isTrue();
    }

    @Test
    void looksLikeExpression_bareNumber() {
        assertThat(ExpressionParser.looksLikeExpression(
                List.of(lit(42)))).isTrue();
    }

    @Test
    void looksLikeExpression_nameOnly() {
        assertThat(ExpressionParser.looksLikeExpression(
                List.of(name("create")))).isFalse();
    }

    // ==================================================================================
    // ERROR CASES
    // ==================================================================================

    @Test
    void emptyTokensThrows() {
        assertThatThrownBy(() -> ExpressionParser.parse(List.of()))
                .isInstanceOf(ExpressionParser.ParseException.class);
    }

    @Test
    void unmatchedParenThrows() {
        assertThatThrownBy(() -> ExpressionParser.parse(List.of(
                open(), lit(2), op(Operator.ADD), lit(3))))
                .isInstanceOf(ExpressionParser.ParseException.class);
    }

    @Test
    void tryParse_returnsEmptyOnFailure() {
        assertThat(ExpressionParser.tryParse(List.of())).isEmpty();
    }

    @Test
    void tryParse_returnsExpressionOnSuccess() {
        assertThat(ExpressionParser.tryParse(List.of(lit(42)))).isPresent();
    }
}
