package dev.everydaythings.graph.expression;

import dev.everydaythings.graph.item.component.expression.BinaryExpression;
import dev.everydaythings.graph.item.component.expression.Expression;
import dev.everydaythings.graph.item.component.expression.FunctionExpression;
import dev.everydaythings.graph.item.component.expression.LiteralExpression;
import dev.everydaythings.graph.item.component.expression.ReferenceExpression;
import dev.everydaythings.graph.item.component.expression.UnaryExpression;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.value.Operator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pratt parser (top-down operator precedence) for expression tokens.
 *
 * <p>Converts a flat list of {@link ExpressionToken}s into a structured
 * {@link Expression} AST, using operator precedence from {@link Operator} items.
 *
 * <p>This is the bridge between user input and the expression evaluation system.
 * The parser handles:
 * <ul>
 *   <li>Literals: {@code 42}, {@code "hello"}, {@code true}</li>
 *   <li>Variables: {@code x}, {@code y} (as binding references)</li>
 *   <li>Item references: resolved items from lookup</li>
 *   <li>Infix operators: {@code 2 + 3}, {@code x * y}, {@code a == b}</li>
 *   <li>Prefix operators: {@code -x}, {@code !condition}</li>
 *   <li>Parentheses: {@code (a + b) * c}</li>
 *   <li>Function calls: {@code sqrt(x)}, {@code max(a, b)}</li>
 *   <li>Assignment: {@code f(x) = x^2 + 1}</li>
 *   <li>Pipe: {@code data |> filter |> plot}</li>
 * </ul>
 *
 * <p>The parser uses operator precedence and associativity metadata from
 * {@link Operator} items — making precedence data-driven and extensible.
 */
public class ExpressionParser {

    private final List<ExpressionToken> tokens;
    private int pos;

    private ExpressionParser(List<ExpressionToken> tokens) {
        this.tokens = tokens;
        this.pos = 0;
    }

    /**
     * Parse a list of expression tokens into an Expression AST.
     *
     * @param tokens The tokens to parse
     * @return The parsed expression
     * @throws ParseException if the tokens cannot be parsed
     */
    public static Expression parse(List<ExpressionToken> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            throw new ParseException("Empty expression");
        }
        ExpressionParser parser = new ExpressionParser(tokens);
        Expression result = parser.parseExpression(Integer.MIN_VALUE);

        if (parser.pos < parser.tokens.size()) {
            ExpressionToken remaining = parser.tokens.get(parser.pos);
            throw new ParseException("Unexpected token after expression: " + remaining.displayText());
        }
        return result;
    }

    /**
     * Try to parse tokens as an expression. Returns empty if the tokens
     * don't form a valid expression (e.g., they're a verb command).
     */
    public static Optional<Expression> tryParse(List<ExpressionToken> tokens) {
        try {
            return Optional.of(parse(tokens));
        } catch (ParseException e) {
            return Optional.empty();
        }
    }

    /**
     * Check whether a token list looks like it contains expression operators.
     * Quick heuristic to avoid parsing verb commands as expressions.
     */
    public static boolean looksLikeExpression(List<ExpressionToken> tokens) {
        for (ExpressionToken token : tokens) {
            if (token instanceof ExpressionToken.OpToken) return true;
            if (token instanceof ExpressionToken.OpenParen) return true;
            if (token instanceof ExpressionToken.CloseParen) return true;
        }
        // Also check for numeric literals (bare numbers are expressions)
        if (tokens.size() == 1 && tokens.getFirst() instanceof ExpressionToken.LiteralToken lit) {
            return lit.value() instanceof Number;
        }
        return false;
    }

    // ==================================================================================
    // PRATT PARSER CORE
    // ==================================================================================

    /**
     * Parse an expression with minimum binding power (precedence).
     *
     * <p>This is the heart of the Pratt parser. It parses a prefix expression,
     * then greedily consumes infix operators that bind tighter than minPrec.
     */
    private Expression parseExpression(int minPrec) {
        Expression left = parsePrefix();

        while (pos < tokens.size()) {
            ExpressionToken token = tokens.get(pos);

            // Check for infix operator
            Operator infixOp = getInfixOperator(token);
            if (infixOp == null) break;

            int prec = infixOp.precedence();
            if (prec < minPrec) break;

            pos++; // consume the operator

            // Right-associative operators use same precedence for right side;
            // left-associative use precedence + 1
            int nextMinPrec = (infixOp.associativity() == Operator.Associativity.RIGHT)
                    ? prec : prec + 1;

            Expression right = parseExpression(nextMinPrec);
            left = new BinaryExpression(left, infixOp.iid(), right);
        }

        return left;
    }

    /**
     * Parse a prefix expression (null denotation in Pratt terminology).
     *
     * <p>Handles:
     * <ul>
     *   <li>Literals: numbers, strings, booleans</li>
     *   <li>Names/variables: unresolved identifiers</li>
     *   <li>Item references: resolved items</li>
     *   <li>Prefix operators: {@code -x}, {@code !x}</li>
     *   <li>Parenthesized expressions: {@code (a + b)}</li>
     *   <li>Function calls: {@code name(args...)}</li>
     * </ul>
     */
    private Expression parsePrefix() {
        if (pos >= tokens.size()) {
            throw new ParseException("Unexpected end of expression");
        }

        ExpressionToken token = tokens.get(pos);
        pos++;

        return switch (token) {
            case ExpressionToken.LiteralToken lit ->
                    LiteralExpression.of(lit.value());

            case ExpressionToken.NameToken name -> {
                // Check for function call: name(args...)
                if (pos < tokens.size() && tokens.get(pos) instanceof ExpressionToken.OpenParen) {
                    yield parseFunctionCall(name.name());
                }
                // Otherwise it's a variable reference (binding lookup)
                yield ReferenceExpression.local(name.name());
            }

            case ExpressionToken.RefToken ref -> {
                // Check for function call: ref(args...)
                if (pos < tokens.size() && tokens.get(pos) instanceof ExpressionToken.OpenParen) {
                    yield parseFunctionCall(ref.displayText());
                }
                // Otherwise it's an item reference
                yield LiteralExpression.item(ref.target());
            }

            case ExpressionToken.OpToken op -> {
                // Must be a prefix operator
                Operator prefixOp = Operator.lookupKnown(op.operatorId());
                if (prefixOp == null || !prefixOp.isPrefix()) {
                    // Check if the symbol has a prefix variant
                    // (e.g., "-" is both SUBTRACT (infix) and NEGATE (prefix))
                    prefixOp = findPrefixForOp(op.operatorId());
                    if (prefixOp == null) {
                        throw new ParseException("Expected a value, got operator: " + op.displayText());
                    }
                }
                Expression operand = parseExpression(prefixOp.precedence());
                yield createUnaryExpression(prefixOp, operand);
            }

            case ExpressionToken.OpenParen ignored -> {
                Expression inner = parseExpression(Integer.MIN_VALUE);
                expect(ExpressionToken.CloseParen.class, ")");
                yield inner;
            }

            case ExpressionToken.CloseParen ignored ->
                    throw new ParseException("Unexpected closing parenthesis");
        };
    }

    /**
     * Parse a function call: name(arg1, arg2, ...)
     * Assumes name has been consumed and next token is OpenParen.
     */
    private Expression parseFunctionCall(String functionName) {
        pos++; // consume OpenParen

        List<Expression> args = new ArrayList<>();

        // Empty argument list?
        if (pos < tokens.size() && tokens.get(pos) instanceof ExpressionToken.CloseParen) {
            pos++; // consume CloseParen
            return FunctionExpression.call(functionName, args);
        }

        // Parse comma-separated arguments
        // (commas aren't tokens yet, so arguments are separated by the natural
        // parsing boundaries — for now, each argument is a single expression)
        args.add(parseExpression(Integer.MIN_VALUE));

        // TODO: when comma token is added, parse more arguments here
        // while (pos < tokens.size() && isComma(tokens.get(pos))) {
        //     pos++; // consume comma
        //     args.add(parseExpression(Integer.MIN_VALUE));
        // }

        expect(ExpressionToken.CloseParen.class, ")");
        return FunctionExpression.call(functionName, args);
    }

    // ==================================================================================
    // HELPERS
    // ==================================================================================

    /**
     * Get the infix operator for a token, or null if it's not an infix operator.
     */
    private Operator getInfixOperator(ExpressionToken token) {
        if (!(token instanceof ExpressionToken.OpToken op)) return null;

        Operator operator = Operator.lookupKnown(op.operatorId());
        if (operator == null) return null;

        // For operators that can be both prefix and infix (like "-"),
        // in infix position we want the infix variant.
        if (operator.isPrefix()) {
            // Look for an infix variant with the same symbol
            Operator infixVariant = Operator.infixFromSymbol(operator.symbol());
            return (infixVariant != null) ? infixVariant : null;
        }

        return operator;
    }

    /**
     * Find the prefix variant for an operator that was resolved as infix.
     * Handles the "-" ambiguity: SUBTRACT (infix) vs NEGATE (prefix).
     */
    private Operator findPrefixForOp(ItemID operatorId) {
        Operator op = Operator.lookupKnown(operatorId);
        if (op == null) return null;
        return Operator.prefixFromSymbol(op.symbol());
    }

    /**
     * Create a UnaryExpression from an Operator.
     */
    private Expression createUnaryExpression(Operator op, Expression operand) {
        // Map to the existing UnaryExpression enum for now
        if (op.iid().equals(Operator.NEGATE.iid())) {
            return UnaryExpression.negate(operand);
        }
        if (op.iid().equals(Operator.NOT.iid())) {
            return UnaryExpression.not(operand);
        }
        // For other prefix operators, wrap as binary with a null/identity left
        throw new ParseException("Unsupported prefix operator: " + op.symbol());
    }

    private void expect(Class<? extends ExpressionToken> type, String display) {
        if (pos >= tokens.size()) {
            throw new ParseException("Expected '" + display + "' but reached end of expression");
        }
        ExpressionToken token = tokens.get(pos);
        if (!type.isInstance(token)) {
            throw new ParseException("Expected '" + display + "' but got: " + token.displayText());
        }
        pos++;
    }

    // ==================================================================================
    // EXCEPTION
    // ==================================================================================

    /**
     * Thrown when expression parsing fails.
     */
    public static class ParseException extends RuntimeException {
        public ParseException(String message) {
            super(message);
        }
    }
}
