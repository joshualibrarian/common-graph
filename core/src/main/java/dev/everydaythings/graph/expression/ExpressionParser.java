package dev.everydaythings.graph.expression;

import dev.everydaythings.graph.item.component.expression.BinaryExpression;
import dev.everydaythings.graph.item.component.expression.Expression;
import dev.everydaythings.graph.item.component.expression.FunctionExpression;
import dev.everydaythings.graph.item.component.expression.LiteralExpression;
import dev.everydaythings.graph.item.component.expression.PropertyAccessExpression;
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
     * Parse a raw expression string into an Expression AST.
     *
     * <p>This is the universal entry point — used by {@code @Scene.Bind},
     * stored expressions, and anywhere a string needs to become an expression.
     *
     * @param expression The expression string (e.g., "session.activity.resultText")
     * @return The parsed expression
     * @throws ParseException if the string cannot be parsed
     */
    public static Expression parse(String expression) {
        List<ExpressionToken> tokens = ExpressionLexer.tokenize(expression);
        return parse(tokens);
    }

    /**
     * Try to parse a raw expression string. Returns empty on failure.
     */
    public static Optional<Expression> tryParse(String expression) {
        try {
            return Optional.of(parse(expression));
        } catch (Exception e) {
            return Optional.empty();
        }
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
            if (token instanceof ExpressionToken.DotToken) return true;
        }
        // Bare number is an expression
        if (tokens.size() == 1 && tokens.getFirst() instanceof ExpressionToken.LiteralToken lit) {
            return lit.value() instanceof Number;
        }
        // Number followed by ref/name — quantity via juxtaposition (5m, 2ft)
        if (tokens.size() >= 2 && tokens.getFirst() instanceof ExpressionToken.LiteralToken lit) {
            if (lit.value() instanceof Number) {
                ExpressionToken second = tokens.get(1);
                if (second instanceof ExpressionToken.RefToken
                        || second instanceof ExpressionToken.NameToken) {
                    return true;
                }
            }
        }
        return false;
    }

    // ==================================================================================
    // PRATT PARSER CORE
    // ==================================================================================

    // Juxtaposition (implicit multiplication) precedence.
    // Higher than explicit * (20) so 5m binds before *, but lower than ^ (30)
    // so 5m^2 → 5*(m^2). Left-associative.
    private static final int JUXTAPOSITION_PREC = 21;

    /**
     * Parse an expression with minimum binding power (precedence).
     *
     * <p>This is the heart of the Pratt parser. It parses a prefix expression,
     * then greedily consumes infix operators that bind tighter than minPrec.
     *
     * <p>Juxtaposition (implicit multiplication) is supported: when a prefix
     * expression is immediately followed by another prefix-starting token with
     * no operator between, it's treated as multiplication. This enables
     * natural quantity notation: {@code 5m}, {@code 3.14*r^2}, {@code 2ft}.
     */
    private Expression parseExpression(int minPrec) {
        Expression left = parsePrefix();

        while (pos < tokens.size()) {
            ExpressionToken token = tokens.get(pos);

            // DOT — property access, always tightest binding.
            // Handled before operator precedence check because it
            // binds tighter than any operator.
            if (token instanceof ExpressionToken.DotToken) {
                pos++; // consume dot
                if (pos >= tokens.size()) {
                    throw new ParseException("Expected property name after '.'");
                }
                ExpressionToken propToken = tokens.get(pos);
                if (!(propToken instanceof ExpressionToken.NameToken name)) {
                    throw new ParseException("Expected property name after '.', got: " + propToken.displayText());
                }
                pos++; // consume property name

                // Check for method call: obj.method(args...)
                if (pos < tokens.size() && tokens.get(pos) instanceof ExpressionToken.OpenParen) {
                    // Method call — parse as function call with object as first arg
                    left = parseMethodCall(left, name.name());
                } else {
                    left = new PropertyAccessExpression(left, name.name());
                }
                continue;
            }

            // Check for infix operator
            Operator infixOp = getInfixOperator(token);
            if (infixOp != null) {
                int prec = infixOp.precedence();
                if (prec < minPrec) break;

                pos++; // consume the operator

                // Right-associative operators use same precedence for right side;
                // left-associative use precedence + 1
                int nextMinPrec = (infixOp.associativity() == Operator.Associativity.RIGHT)
                        ? prec : prec + 1;

                Expression right = parseExpression(nextMinPrec);
                left = new BinaryExpression(left, infixOp.iid(), right);
                continue;
            }

            // Juxtaposition — implicit multiplication.
            // If the next token can start a prefix expression and it's not an
            // operator, treat as implicit multiply: 5m → 5 * m, 2(x+1) → 2*(x+1)
            if (JUXTAPOSITION_PREC >= minPrec && canStartPrefix(token)) {
                Expression right = parseExpression(JUXTAPOSITION_PREC + 1);
                left = new BinaryExpression(left, Operator.MULTIPLY.iid(), right);
                continue;
            }

            break;
        }

        return left;
    }

    /**
     * Check if a token can start a prefix expression (for juxtaposition detection).
     */
    private boolean canStartPrefix(ExpressionToken token) {
        return token instanceof ExpressionToken.LiteralToken
                || token instanceof ExpressionToken.NameToken
                || token instanceof ExpressionToken.RefToken
                || token instanceof ExpressionToken.CandidateToken
                || token instanceof ExpressionToken.OpenParen;
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

            case ExpressionToken.CandidateToken candidate -> {
                // Ambiguous — use highest-weight candidate as best guess for expression parsing
                var best = candidate.candidates().getFirst();
                if (pos < tokens.size() && tokens.get(pos) instanceof ExpressionToken.OpenParen) {
                    yield parseFunctionCall(candidate.displayText());
                }
                yield LiteralExpression.item(best.target());
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

            case ExpressionToken.DotToken ignored ->
                    throw new ParseException("Unexpected '.' at start of expression");

            case ExpressionToken.CommaToken ignored ->
                    throw new ParseException("Unexpected ',' outside of function call");
        };
    }

    /**
     * Parse a function call: name(arg1, arg2, ...)
     * Assumes name has been consumed and next token is OpenParen.
     */
    private Expression parseFunctionCall(String functionName) {
        pos++; // consume OpenParen
        List<Expression> args = parseArgumentList();
        expect(ExpressionToken.CloseParen.class, ")");
        return FunctionExpression.call(functionName, args);
    }

    /**
     * Parse a method call: obj.method(arg1, arg2, ...)
     * Assumes dot and method name have been consumed and next token is OpenParen.
     * The object expression becomes an implicit first argument.
     */
    private Expression parseMethodCall(Expression object, String methodName) {
        pos++; // consume OpenParen
        List<Expression> args = new ArrayList<>();
        args.add(object); // object is implicit first argument
        args.addAll(parseArgumentList());
        expect(ExpressionToken.CloseParen.class, ")");
        return FunctionExpression.call(methodName, args);
    }

    /**
     * Parse a comma-separated list of argument expressions.
     * Does NOT consume the closing paren.
     */
    private List<Expression> parseArgumentList() {
        List<Expression> args = new ArrayList<>();

        // Empty argument list?
        if (pos < tokens.size() && tokens.get(pos) instanceof ExpressionToken.CloseParen) {
            return args;
        }

        // First argument
        args.add(parseExpression(Integer.MIN_VALUE));

        // Remaining arguments separated by commas
        while (pos < tokens.size() && tokens.get(pos) instanceof ExpressionToken.CommaToken) {
            pos++; // consume comma
            args.add(parseExpression(Integer.MIN_VALUE));
        }

        return args;
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
