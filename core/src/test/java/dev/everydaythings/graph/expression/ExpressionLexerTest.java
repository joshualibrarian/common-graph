package dev.everydaythings.graph.expression;

import dev.everydaythings.graph.item.component.expression.BinaryExpression;
import dev.everydaythings.graph.item.component.expression.EvaluationContext;
import dev.everydaythings.graph.item.component.expression.Expression;
import dev.everydaythings.graph.item.component.expression.FunctionExpression;
import dev.everydaythings.graph.item.component.expression.LiteralExpression;
import dev.everydaythings.graph.item.component.expression.PropertyAccessExpression;
import dev.everydaythings.graph.item.component.expression.ReferenceExpression;
import dev.everydaythings.graph.value.Operator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for ExpressionLexer and the string-to-Expression pipeline.
 */
class ExpressionLexerTest {

    // ==================================================================================
    // Lexer — Token Production
    // ==================================================================================

    @Nested
    class Tokenization {

        @Test
        void emptyStringProducesNoTokens() {
            assertThat(ExpressionLexer.tokenize("")).isEmpty();
            assertThat(ExpressionLexer.tokenize("   ")).isEmpty();
            assertThat(ExpressionLexer.tokenize(null)).isEmpty();
        }

        @Test
        void identifiers() {
            var tokens = ExpressionLexer.tokenize("session activity x");
            assertThat(tokens).hasSize(3);
            assertThat(tokens.get(0)).isInstanceOf(ExpressionToken.NameToken.class);
            assertThat(((ExpressionToken.NameToken) tokens.get(0)).name()).isEqualTo("session");
            assertThat(((ExpressionToken.NameToken) tokens.get(1)).name()).isEqualTo("activity");
            assertThat(((ExpressionToken.NameToken) tokens.get(2)).name()).isEqualTo("x");
        }

        @Test
        void integerLiteral() {
            var tokens = ExpressionLexer.tokenize("42");
            assertThat(tokens).hasSize(1);
            assertThat(tokens.getFirst()).isInstanceOf(ExpressionToken.LiteralToken.class);
            assertThat(((ExpressionToken.LiteralToken) tokens.getFirst()).value()).isEqualTo(42L);
        }

        @Test
        void doubleLiteral() {
            var tokens = ExpressionLexer.tokenize("3.14");
            assertThat(tokens).hasSize(1);
            assertThat(((ExpressionToken.LiteralToken) tokens.getFirst()).value()).isEqualTo(3.14);
        }

        @Test
        void stringLiterals() {
            var tokens = ExpressionLexer.tokenize("\"hello\" 'world'");
            assertThat(tokens).hasSize(2);
            assertThat(((ExpressionToken.LiteralToken) tokens.get(0)).value()).isEqualTo("hello");
            assertThat(((ExpressionToken.LiteralToken) tokens.get(1)).value()).isEqualTo("world");
        }

        @Test
        void booleanLiterals() {
            var tokens = ExpressionLexer.tokenize("true false");
            assertThat(tokens).hasSize(2);
            assertThat(((ExpressionToken.LiteralToken) tokens.get(0)).value()).isEqualTo(true);
            assertThat(((ExpressionToken.LiteralToken) tokens.get(1)).value()).isEqualTo(false);
        }

        @Test
        void operators() {
            var tokens = ExpressionLexer.tokenize("+ - * / == != && ||");
            assertThat(tokens).hasSize(8);
            for (var token : tokens) {
                assertThat(token).isInstanceOf(ExpressionToken.OpToken.class);
            }
        }

        @Test
        void parens() {
            var tokens = ExpressionLexer.tokenize("(a + b)");
            assertThat(tokens).hasSize(5);
            assertThat(tokens.get(0)).isInstanceOf(ExpressionToken.OpenParen.class);
            assertThat(tokens.get(4)).isInstanceOf(ExpressionToken.CloseParen.class);
        }

        @Test
        void dotTokens() {
            var tokens = ExpressionLexer.tokenize("a.b.c");
            assertThat(tokens).hasSize(5);
            assertThat(tokens.get(0)).isInstanceOf(ExpressionToken.NameToken.class);
            assertThat(tokens.get(1)).isInstanceOf(ExpressionToken.DotToken.class);
            assertThat(tokens.get(2)).isInstanceOf(ExpressionToken.NameToken.class);
            assertThat(tokens.get(3)).isInstanceOf(ExpressionToken.DotToken.class);
            assertThat(tokens.get(4)).isInstanceOf(ExpressionToken.NameToken.class);
        }

        @Test
        void commaTokens() {
            var tokens = ExpressionLexer.tokenize("max(a, b, c)");
            assertThat(tokens).hasSize(8);
            assertThat(tokens.get(3)).isInstanceOf(ExpressionToken.CommaToken.class);
            assertThat(tokens.get(5)).isInstanceOf(ExpressionToken.CommaToken.class);
        }

        @Test
        void dotVsDecimalNumber() {
            // ".5" should be a number, not dot + number
            var tokens = ExpressionLexer.tokenize(".5");
            assertThat(tokens).hasSize(1);
            assertThat(((ExpressionToken.LiteralToken) tokens.getFirst()).value()).isEqualTo(0.5);
        }

        @Test
        void multiCharOperators() {
            var tokens = ExpressionLexer.tokenize("x == y != z <= w >= v");
            // x, ==, y, !=, z, <=, w, >=, v
            assertThat(tokens).hasSize(9);
            assertThat(tokens.get(1)).isInstanceOf(ExpressionToken.OpToken.class);
            assertThat(tokens.get(3)).isInstanceOf(ExpressionToken.OpToken.class);
        }

        @Test
        void complexExpression() {
            var tokens = ExpressionLexer.tokenize("last(session.activity).resultText");
            // last, (, session, ., activity, ), ., resultText
            assertThat(tokens).hasSize(8);
            assertThat(((ExpressionToken.NameToken) tokens.get(0)).name()).isEqualTo("last");
            assertThat(tokens.get(1)).isInstanceOf(ExpressionToken.OpenParen.class);
            assertThat(((ExpressionToken.NameToken) tokens.get(2)).name()).isEqualTo("session");
            assertThat(tokens.get(3)).isInstanceOf(ExpressionToken.DotToken.class);
            assertThat(((ExpressionToken.NameToken) tokens.get(4)).name()).isEqualTo("activity");
            assertThat(tokens.get(5)).isInstanceOf(ExpressionToken.CloseParen.class);
            assertThat(tokens.get(6)).isInstanceOf(ExpressionToken.DotToken.class);
            assertThat(((ExpressionToken.NameToken) tokens.get(7)).name()).isEqualTo("resultText");
        }
    }

    // ==================================================================================
    // String-to-Expression Parsing (end-to-end)
    // ==================================================================================

    @Nested
    class StringParsing {

        @Test
        void simpleArithmetic() {
            Expression expr = ExpressionParser.parse("2 + 3");
            EvaluationContext ctx = new EvaluationContext(null, null);
            assertThat(expr.evaluate(ctx)).isEqualTo(5.0);
        }

        @Test
        void complexArithmetic() {
            Expression expr = ExpressionParser.parse("2 + 3 * 4");
            EvaluationContext ctx = new EvaluationContext(null, null);
            // 3 * 4 = 12, 2 + 12 = 14
            assertThat(expr.evaluate(ctx)).isEqualTo(14.0);
        }

        @Test
        void parentheses() {
            Expression expr = ExpressionParser.parse("(2 + 3) * 4");
            EvaluationContext ctx = new EvaluationContext(null, null);
            assertThat(expr.evaluate(ctx)).isEqualTo(20.0);
        }

        @Test
        void comparison() {
            Expression expr = ExpressionParser.parse("5 > 3");
            EvaluationContext ctx = new EvaluationContext(null, null);
            assertThat(expr.evaluate(ctx)).isEqualTo(true);
        }

        @Test
        void logicalOperators() {
            Expression expr = ExpressionParser.parse("true && false");
            EvaluationContext ctx = new EvaluationContext(null, null);
            assertThat(expr.evaluate(ctx)).isEqualTo(false);
        }

        @Test
        void negation() {
            Expression expr = ExpressionParser.parse("-5");
            EvaluationContext ctx = new EvaluationContext(null, null);
            assertThat(expr.evaluate(ctx)).isEqualTo(-5.0);
        }

        @Test
        void notOperator() {
            Expression expr = ExpressionParser.parse("!true");
            EvaluationContext ctx = new EvaluationContext(null, null);
            assertThat(expr.evaluate(ctx)).isEqualTo(false);
        }

        @Test
        void variableReference() {
            Expression expr = ExpressionParser.parse("x + 1");
            EvaluationContext ctx = new EvaluationContext(null, null)
                    .withBinding("x", 10);
            assertThat(expr.evaluate(ctx)).isEqualTo(11.0);
        }

        @Test
        void functionCall() {
            Expression expr = ExpressionParser.parse("sqrt(16)");
            EvaluationContext ctx = new EvaluationContext(null, null);
            assertThat(expr.evaluate(ctx)).isEqualTo(4.0);
        }

        @Test
        void multiArgFunctionCall() {
            Expression expr = ExpressionParser.parse("pow(2, 10)");
            EvaluationContext ctx = new EvaluationContext(null, null);
            assertThat(expr.evaluate(ctx)).isEqualTo(1024.0);
        }

        @Test
        void stringLiteral() {
            Expression expr = ExpressionParser.parse("\"hello\"");
            EvaluationContext ctx = new EvaluationContext(null, null);
            assertThat(expr.evaluate(ctx)).isEqualTo("hello");
        }

        @Test
        void power() {
            Expression expr = ExpressionParser.parse("2 ^ 10");
            EvaluationContext ctx = new EvaluationContext(null, null);
            assertThat(expr.evaluate(ctx)).isEqualTo(1024.0);
        }

        @Test
        void tryParseString() {
            assertThat(ExpressionParser.tryParse("2 + 3")).isPresent();
            assertThat(ExpressionParser.tryParse("???")).isEmpty();
        }
    }

    // ==================================================================================
    // Property Access (DOT)
    // ==================================================================================

    @Nested
    class PropertyAccess {

        @Test
        void simplePropertyAccess() {
            Expression expr = ExpressionParser.parse("a.b");
            assertThat(expr).isInstanceOf(PropertyAccessExpression.class);
            PropertyAccessExpression pa = (PropertyAccessExpression) expr;
            assertThat(pa.property()).isEqualTo("b");
        }

        @Test
        void chainedPropertyAccess() {
            Expression expr = ExpressionParser.parse("a.b.c");
            // Should parse as (a.b).c — left-associative
            assertThat(expr).isInstanceOf(PropertyAccessExpression.class);
            PropertyAccessExpression outer = (PropertyAccessExpression) expr;
            assertThat(outer.property()).isEqualTo("c");
            assertThat(outer.object()).isInstanceOf(PropertyAccessExpression.class);

            PropertyAccessExpression inner = (PropertyAccessExpression) outer.object();
            assertThat(inner.property()).isEqualTo("b");
        }

        @Test
        void propertyAccessEvaluatesViaReflection() {
            // Use a Map as the object
            Map<String, Object> data = new HashMap<>();
            data.put("name", "Alice");

            Expression expr = ExpressionParser.parse("obj.name");
            EvaluationContext ctx = new EvaluationContext(null, null)
                    .withBinding("obj", data);
            assertThat(expr.evaluate(ctx)).isEqualTo("Alice");
        }

        @Test
        void nestedMapAccess() {
            Map<String, Object> inner = Map.of("city", "Portland");
            Map<String, Object> outer = new HashMap<>();
            outer.put("address", inner);

            Expression expr = ExpressionParser.parse("person.address.city");
            EvaluationContext ctx = new EvaluationContext(null, null)
                    .withBinding("person", outer);
            assertThat(expr.evaluate(ctx)).isEqualTo("Portland");
        }

        @Test
        void dotBindsTighterThanOperators() {
            // "a.x + b.y" should parse as "(a.x) + (b.y)"
            Map<String, Object> a = Map.of("x", 3);
            Map<String, Object> b = Map.of("y", 7);

            Expression expr = ExpressionParser.parse("a.x + b.y");
            EvaluationContext ctx = new EvaluationContext(null, null)
                    .withBinding("a", a)
                    .withBinding("b", b);
            assertThat(expr.evaluate(ctx)).isEqualTo(10.0);
        }

        @Test
        void propertyAccessAfterFunctionCall() {
            // "upper(name).length" — but since upper returns a String,
            // and String has a length() method, this should work via reflection
            // Actually, this parses as: upper("hello"), then .length property
            // But we need the function to return something with a "length" property.
            // Let's test the parse structure instead.
            Expression expr = ExpressionParser.parse("fn(x).prop");
            assertThat(expr).isInstanceOf(PropertyAccessExpression.class);
            PropertyAccessExpression pa = (PropertyAccessExpression) expr;
            assertThat(pa.property()).isEqualTo("prop");
            assertThat(pa.object()).isInstanceOf(FunctionExpression.class);
        }

        @Test
        void propertyAccessAfterParens() {
            // "(a + b).toString" — property access on a grouped expression
            Expression expr = ExpressionParser.parse("(x).name");
            assertThat(expr).isInstanceOf(PropertyAccessExpression.class);
        }

        @Test
        void expressionString() {
            Expression expr = ExpressionParser.parse("session.activity.resultText");
            assertThat(expr.toExpressionString()).isEqualTo("session.activity.resultText");
        }

        @Test
        void nullSafePropertyAccess() {
            // If the object is null, property access returns null
            Expression expr = ExpressionParser.parse("x.y");
            EvaluationContext ctx = new EvaluationContext(null, null);
            // x is not bound, so resolves to null
            assertThat(expr.evaluate(ctx)).isNull();
        }

        @Test
        void fluentGetterAccess() {
            // Test that Lombok-style fluent getters work
            // Use an Operator as the test object — it has fluent accessors
            Expression expr = ExpressionParser.parse("op.symbol");
            EvaluationContext ctx = new EvaluationContext(null, null)
                    .withBinding("op", Operator.ADD);
            assertThat(expr.evaluate(ctx)).isEqualTo("+");
        }

        @Test
        void chainedFluentGetterAccess() {
            Expression expr = ExpressionParser.parse("op.name");
            EvaluationContext ctx = new EvaluationContext(null, null)
                    .withBinding("op", Operator.ADD);
            assertThat(expr.evaluate(ctx)).isEqualTo("add");
        }
    }

    // ==================================================================================
    // Method Calls (obj.method(args))
    // ==================================================================================

    @Nested
    class MethodCalls {

        @Test
        void methodCallStructure() {
            // obj.method(arg) becomes function call "method" with obj as first arg
            Expression expr = ExpressionParser.parse("list.length()");
            assertThat(expr).isInstanceOf(FunctionExpression.class);
            FunctionExpression fn = (FunctionExpression) expr;
            assertThat(fn.function()).isEqualTo("length");
            // obj is first implicit arg
            assertThat(fn.arguments()).hasSize(1);
        }

        @Test
        void methodCallWithArgs() {
            // "text.substring(1, 3)" → function("substring", text, 1, 3)
            Expression expr = ExpressionParser.parse("text.substr(1, 3)");
            assertThat(expr).isInstanceOf(FunctionExpression.class);
            FunctionExpression fn = (FunctionExpression) expr;
            assertThat(fn.function()).isEqualTo("substr");
            // text + 2 args = 3
            assertThat(fn.arguments()).hasSize(3);
        }
    }

    // ==================================================================================
    // Combined Features
    // ==================================================================================

    @Nested
    class Combined {

        @Test
        void bindExpressionStyle() {
            // The kind of expression a @Scene.Bind would contain
            Expression expr = ExpressionParser.parse("session.lastActivity.resultText");
            assertThat(expr.toExpressionString()).isEqualTo("session.lastActivity.resultText");
        }

        @Test
        void comparisonWithPropertyAccess() {
            Map<String, Object> item = Map.of("count", 5);

            Expression expr = ExpressionParser.parse("item.count > 3");
            EvaluationContext ctx = new EvaluationContext(null, null)
                    .withBinding("item", item);
            assertThat(expr.evaluate(ctx)).isEqualTo(true);
        }

        @Test
        void functionWithPropertyAccessArg() {
            Map<String, Object> data = Map.of("value", 16);

            Expression expr = ExpressionParser.parse("sqrt(data.value)");
            EvaluationContext ctx = new EvaluationContext(null, null)
                    .withBinding("data", data);
            assertThat(expr.evaluate(ctx)).isEqualTo(4.0);
        }

        @Test
        void arithmeticWithPropertiesAndFunctions() {
            Map<String, Object> a = Map.of("x", 3);
            Map<String, Object> b = Map.of("x", 4);

            Expression expr = ExpressionParser.parse("sqrt(a.x ^ 2 + b.x ^ 2)");
            EvaluationContext ctx = new EvaluationContext(null, null)
                    .withBinding("a", a)
                    .withBinding("b", b);
            // sqrt(9 + 16) = sqrt(25) = 5
            assertThat(expr.evaluate(ctx)).isEqualTo(5.0);
        }
    }
}
