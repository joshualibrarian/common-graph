package dev.everydaythings.graph.parse;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.FrameBody;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.value.Function;
import dev.everydaythings.graph.value.Operator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pratt parser producing FrameBody trees from expression tokens.
 *
 * <p>This is the bridge from text to the unified evaluation path:
 * {@code text → ExpressionLexer → tokens → FrameBodyParser → FrameBody →
 * FrameEvaluator → result}.
 *
 * <p>Reuses {@link ExpressionLexer} unchanged. Every construct maps to a
 * FrameBody with a semantic predicate and thematic role bindings:
 *
 * <table>
 *   <tr><th>Input</th><th>Predicate</th><th>Bindings</th></tr>
 *   <tr><td>{@code 3 + 4}</td><td>ADD</td><td>THEME→3, GOAL→4</td></tr>
 *   <tr><td>{@code !x}</td><td>NOT</td><td>THEME→x</td></tr>
 *   <tr><td>{@code sqrt(16)}</td><td>SQRT</td><td>THEME→16</td></tr>
 *   <tr><td>{@code x > 5}</td><td>GT</td><td>THEME→x, GOAL→5</td></tr>
 *   <tr><td>{@code x && y}</td><td>AND</td><td>THEME→x, GOAL→y</td></tr>
 *   <tr><td>{@code if c then a else b}</td><td>CONDITIONAL</td><td>THEME→c, RESULT→a, GOAL→b</td></tr>
 *   <tr><td>{@code let x=1 in x+1}</td><td>LET</td><td>GOAL→"x", THEME→1, RESULT→(x+1)</td></tr>
 *   <tr><td>{@code a; b; c}</td><td>SEQUENCE</td><td>THEME→a, THEME→b, THEME→c</td></tr>
 * </table>
 *
 * <p>Leaf values become {@link BindingTarget}s: literals → {@link Literal},
 * item refs → {@link BindingTarget.IidTarget}, variable names → RESOLVE frame,
 * nested expressions → {@link BindingTarget.FrameTarget}.
 */
public class FrameBodyParser {

    private final List<ExpressionToken> tokens;
    private int pos;

    private FrameBodyParser(List<ExpressionToken> tokens) {
        this.tokens = tokens;
        this.pos = 0;
    }

    // ==================================================================================
    // Public API
    // ==================================================================================

    /**
     * Parse a raw expression string into a FrameBody tree.
     *
     * @param expression the expression text
     * @param resolver   maps operator symbols to ItemIDs (from TokenDictionary)
     */
    public static FrameBody parse(String expression, ExpressionLexer.SymbolResolver resolver) {
        List<ExpressionToken> tokens = ExpressionLexer.tokenize(expression, resolver);
        return parse(tokens);
    }

    /**
     * Try to parse a raw expression string. Returns empty on failure.
     */
    public static Optional<FrameBody> tryParse(String expression, ExpressionLexer.SymbolResolver resolver) {
        try {
            return Optional.of(parse(expression, resolver));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Try to parse a list of expression tokens. Returns empty on failure.
     */
    public static Optional<FrameBody> tryParse(List<ExpressionToken> tokens) {
        try {
            return Optional.of(parse(tokens));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Parse a list of expression tokens into a FrameBody tree.
     */
    public static FrameBody parse(List<ExpressionToken> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            throw new ParseException("Empty expression");
        }
        FrameBodyParser parser = new FrameBodyParser(tokens);
        BindingTarget result = parser.parseExpression(Integer.MIN_VALUE);

        if (parser.pos < parser.tokens.size()) {
            ExpressionToken remaining = parser.tokens.get(parser.pos);
            throw new ParseException("Unexpected token after expression: " + remaining.displayText());
        }

        // Unwrap: if top-level is a FrameTarget, return its body directly
        if (result instanceof BindingTarget.FrameTarget ft) {
            return ft.body();
        }
        // Bare literal or reference — wrap in an identity frame
        return wrapAsFrame(result);
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
        // Single name — could be a component reference (variable lookup)
        if (tokens.size() == 1 && tokens.getFirst() instanceof ExpressionToken.NameToken) {
            return true;
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
    // Pratt Parser Core
    // ==================================================================================

    // Juxtaposition precedence — same as ExpressionParser
    private static final int JUXTAPOSITION_PREC = 21;

    /**
     * Parse an expression with minimum binding power.
     *
     * <p>Returns a BindingTarget — either a Literal for leaf values,
     * or a FrameTarget wrapping a FrameBody for compound expressions.
     */
    private BindingTarget parseExpression(int minPrec) {
        BindingTarget left = parsePrefix();

        while (pos < tokens.size()) {
            ExpressionToken token = tokens.get(pos);

            // Stop at keywords — they terminate sub-expressions
            if (token instanceof ExpressionToken.NameToken name && KEYWORDS.contains(name.name())) {
                break;
            }

            // DOT — property access, tightest binding
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

                // Method call: obj.method(args...)
                if (pos < tokens.size() && tokens.get(pos) instanceof ExpressionToken.OpenParen) {
                    left = parseMethodCall(left, name.name());
                } else {
                    // Property access → ACCESS { THEME→obj, GOAL→"prop" }
                    left = frameTarget(CoreVocabulary.Access.IID, List.of(
                            new Binding(ThematicRole.Theme.IID, left),
                            new Binding(ThematicRole.Goal.IID, Literal.ofText(name.name()))));
                }
                continue;
            }

            // Check for infix operator
            Operator infixOp = getInfixOperator(token);
            if (infixOp != null) {
                int prec = infixOp.precedence();
                if (prec < minPrec) break;

                pos++; // consume operator

                int nextMinPrec = (infixOp.associativity() == Operator.Associativity.RIGHT)
                        ? prec : prec + 1;

                BindingTarget right = parseExpression(nextMinPrec);

                // Binary op → FrameBody { predicate=op, THEME→left, GOAL→right }
                left = frameTarget(infixOp.iid(), List.of(
                        new Binding(ThematicRole.Theme.IID, left),
                        new Binding(ThematicRole.Goal.IID, right)));
                continue;
            }

            // Juxtaposition — implicit multiplication
            if (JUXTAPOSITION_PREC >= minPrec && canStartPrefix(token)) {
                BindingTarget right = parseExpression(JUXTAPOSITION_PREC + 1);
                left = frameTarget(Operator.Multiply.IID, List.of(
                        new Binding(ThematicRole.Theme.IID, left),
                        new Binding(ThematicRole.Goal.IID, right)));
                continue;
            }

            break;
        }

        return left;
    }

    /**
     * Parse a prefix expression (null denotation).
     */
    private BindingTarget parsePrefix() {
        if (pos >= tokens.size()) {
            throw new ParseException("Unexpected end of expression");
        }

        ExpressionToken token = tokens.get(pos);
        pos++;

        return switch (token) {
            case ExpressionToken.LiteralToken lit -> toLiteral(lit.value());

            case ExpressionToken.NameToken name -> {
                String word = name.name();

                // Keywords: if...then...else
                if ("if".equals(word)) {
                    yield parseConditional();
                }

                // Keywords: let...in
                if ("let".equals(word)) {
                    yield parseLet();
                }

                // Function call: name(args...)
                if (pos < tokens.size() && tokens.get(pos) instanceof ExpressionToken.OpenParen) {
                    yield parseFunctionCall(word);
                }

                // Variable reference → RESOLVE { THEME→"name" }
                yield frameTarget(CoreVocabulary.Resolve.IID, List.of(
                        new Binding(ThematicRole.Theme.IID, Literal.ofText(word))));
            }

            case ExpressionToken.RefToken ref -> {
                // Function call: ref(args...)
                if (pos < tokens.size() && tokens.get(pos) instanceof ExpressionToken.OpenParen) {
                    yield parseFunctionCallByRef(ref);
                }
                // Item reference
                yield BindingTarget.iid(ref.target());
            }

            case ExpressionToken.CandidateToken candidate -> {
                var best = candidate.candidates().getFirst();
                if (pos < tokens.size() && tokens.get(pos) instanceof ExpressionToken.OpenParen) {
                    yield parseFunctionCallByRef(ExpressionToken.RefToken.of(best.target(), candidate.displayText()));
                }
                yield BindingTarget.iid(best.target());
            }

            case ExpressionToken.OpToken op -> {
                // Prefix operator
                Operator prefixOp = Operator.lookupKnown(op.operatorId());
                if (prefixOp == null || !prefixOp.isPrefix()) {
                    prefixOp = findPrefixForOp(op.operatorId());
                    if (prefixOp == null) {
                        throw new ParseException("Expected a value, got operator: " + op.displayText());
                    }
                }
                BindingTarget operand = parseExpression(prefixOp.precedence());
                // Unary op → FrameBody { predicate=op, THEME→operand }
                yield frameTarget(prefixOp.iid(), List.of(
                        new Binding(ThematicRole.Theme.IID, operand)));
            }

            case ExpressionToken.OpenParen ignored -> {
                BindingTarget inner = parseExpression(Integer.MIN_VALUE);
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

    // ==================================================================================
    // Control Flow Parsing
    // ==================================================================================

    /**
     * Parse: if COND then THEN else ELSE
     * (assumes "if" already consumed)
     */
    private BindingTarget parseConditional() {
        BindingTarget condition = parseExpression(Integer.MIN_VALUE);
        expectName("then");
        BindingTarget thenBranch = parseExpression(Integer.MIN_VALUE);

        List<Binding> bindings = new ArrayList<>();
        bindings.add(new Binding(ThematicRole.Theme.IID, condition));
        bindings.add(new Binding(ThematicRole.Result.IID, thenBranch));

        // Optional else clause
        if (pos < tokens.size() && isName("else")) {
            pos++; // consume "else"
            BindingTarget elseBranch = parseExpression(Integer.MIN_VALUE);
            bindings.add(new Binding(ThematicRole.Goal.IID, elseBranch));
        }

        return frameTarget(CoreVocabulary.Conditional.IID, bindings);
    }

    /**
     * Parse: let NAME = VALUE in BODY
     * (assumes "let" already consumed)
     */
    private BindingTarget parseLet() {
        if (pos >= tokens.size() || !(tokens.get(pos) instanceof ExpressionToken.NameToken name)) {
            throw new ParseException("Expected variable name after 'let'");
        }
        pos++; // consume name

        // Expect "=" (the assign operator)
        if (pos >= tokens.size()) {
            throw new ParseException("Expected '=' after variable name in let");
        }
        ExpressionToken eqToken = tokens.get(pos);
        if (eqToken instanceof ExpressionToken.OpToken op && Operator.Assign.IID.equals(op.operatorId())) {
            pos++; // consume "="
        } else {
            throw new ParseException("Expected '=' after variable name in let, got: " + eqToken.displayText());
        }

        BindingTarget value = parseExpression(Integer.MIN_VALUE);
        expectName("in");
        BindingTarget body = parseExpression(Integer.MIN_VALUE);

        return frameTarget(CoreVocabulary.Let.IID, List.of(
                new Binding(ThematicRole.Goal.IID, Literal.ofText(name.name())),
                new Binding(ThematicRole.Theme.IID, value),
                new Binding(ThematicRole.Result.IID, body)));
    }

    // ==================================================================================
    // Function Call Parsing
    // ==================================================================================

    /**
     * Parse: name(arg1, arg2, ...)
     * Assumes name consumed, next token is OpenParen.
     */
    private BindingTarget parseFunctionCall(String functionName) {
        pos++; // consume OpenParen
        List<BindingTarget> args = parseArgumentList();
        expect(ExpressionToken.CloseParen.class, ")");

        // Try to resolve function name to a seed Function IID
        Function fn = Function.lookupByName(functionName);
        if (fn != null) {
            return buildFunctionFrame(fn.iid(), args);
        }

        // Try as operator (some operators can be called as functions)
        Operator op = Operator.fromSymbol(functionName);
        if (op != null) {
            return buildFunctionFrame(op.iid(), args);
        }

        // Unresolved function — use RESOLVE to look it up, then this becomes
        // a verb dispatch at evaluation time. Use the name as a text literal
        // predicate placeholder. For now, wrap as a frame with the function
        // name as a deterministic IID.
        ItemID funcId = ItemID.fromString("cg.fn:" + functionName);
        return buildFunctionFrame(funcId, args);
    }

    /**
     * Parse function call by resolved ref: ref(arg1, arg2, ...)
     */
    private BindingTarget parseFunctionCallByRef(ExpressionToken.RefToken ref) {
        pos++; // consume OpenParen
        List<BindingTarget> args = parseArgumentList();
        expect(ExpressionToken.CloseParen.class, ")");
        return buildFunctionFrame(ref.target(), args);
    }

    /**
     * Parse method call: obj.method(arg1, arg2, ...)
     * Assumes dot and method name consumed, next token is OpenParen.
     */
    private BindingTarget parseMethodCall(BindingTarget object, String methodName) {
        pos++; // consume OpenParen
        List<BindingTarget> args = new ArrayList<>();
        args.add(object); // object is implicit first argument
        args.addAll(parseArgumentList());
        expect(ExpressionToken.CloseParen.class, ")");

        Function fn = Function.lookupByName(methodName);
        ItemID funcId = fn != null ? fn.iid() : ItemID.fromString("cg.fn:" + methodName);
        return buildFunctionFrame(funcId, args);
    }

    /**
     * Build a function frame from a predicate and argument list.
     *
     * <p>First arg → THEME, second arg → GOAL, additional args → additional THEME bindings.
     * This matches the FrameEvaluator's function resolution which collects all THEME
     * bindings and then checks GOAL.
     */
    private BindingTarget buildFunctionFrame(ItemID predicate, List<BindingTarget> args) {
        List<Binding> bindings = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            // All args go as THEME bindings (multi-valued THEME)
            bindings.add(new Binding(ThematicRole.Theme.IID, args.get(i)));
        }
        return frameTarget(predicate, bindings);
    }

    /**
     * Parse comma-separated argument expressions (does NOT consume closing paren).
     */
    private List<BindingTarget> parseArgumentList() {
        List<BindingTarget> args = new ArrayList<>();

        if (pos < tokens.size() && tokens.get(pos) instanceof ExpressionToken.CloseParen) {
            return args;
        }

        args.add(parseExpression(Integer.MIN_VALUE));

        while (pos < tokens.size() && tokens.get(pos) instanceof ExpressionToken.CommaToken) {
            pos++; // consume comma
            args.add(parseExpression(Integer.MIN_VALUE));
        }

        return args;
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    /**
     * Wrap a BindingTarget list in a FrameTarget.
     */
    private static BindingTarget frameTarget(ItemID predicate, List<Binding> bindings) {
        return new BindingTarget.FrameTarget(new FrameBody(predicate, bindings));
    }

    /**
     * Convert a Java value to the appropriate Literal BindingTarget.
     */
    private static BindingTarget toLiteral(Object value) {
        if (value instanceof String s) return Literal.ofText(s);
        if (value instanceof Long l) return Literal.ofInteger(l);
        if (value instanceof Integer i) return Literal.ofInteger(i);
        if (value instanceof Double d) {
            // CG-CBOR forbids IEEE 754, but for expression parsing we
            // round-trip through string to keep precision
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return Literal.ofInteger((long) d.doubleValue());
            }
            // Store as text and let evaluator handle numeric coercion
            return Literal.ofText(String.valueOf(d));
        }
        if (value instanceof Boolean b) return Literal.ofBoolean(b);
        if (value instanceof Number n) return Literal.ofInteger(n.longValue());
        return Literal.ofText(String.valueOf(value));
    }

    /**
     * Wrap a bare BindingTarget as a trivial FrameBody (identity).
     * Used when the top-level parse result is a leaf value.
     */
    private static FrameBody wrapAsFrame(BindingTarget target) {
        // For a bare literal or reference, wrap in RESOLVE if it's text,
        // or return a minimal frame
        if (target instanceof Literal) {
            // Bare literal at top level — could be a variable name or a value
            return new FrameBody(CoreVocabulary.Resolve.IID, List.of(
                    new Binding(ThematicRole.Theme.IID, target)));
        }
        if (target instanceof BindingTarget.IidTarget iid) {
            // Bare item reference
            return new FrameBody(CoreVocabulary.Resolve.IID, List.of(
                    new Binding(ThematicRole.Theme.IID, target)));
        }
        // Shouldn't happen — FrameTarget is unwrapped before this
        return new FrameBody(CoreVocabulary.Resolve.IID, List.of(
                new Binding(ThematicRole.Theme.IID, target)));
    }

    private Operator getInfixOperator(ExpressionToken token) {
        if (!(token instanceof ExpressionToken.OpToken op)) return null;
        Operator operator = Operator.lookupKnown(op.operatorId());
        if (operator == null) return null;
        if (operator.isPrefix()) {
            Operator infixVariant = Operator.infixFromSymbol(operator.symbol());
            return infixVariant;
        }
        return operator;
    }

    private Operator findPrefixForOp(ItemID operatorId) {
        Operator op = Operator.lookupKnown(operatorId);
        if (op == null) return null;
        return Operator.prefixFromSymbol(op.symbol());
    }

    /** Keywords that terminate sub-expressions (not valid starts for juxtaposition). */
    private static final java.util.Set<String> KEYWORDS = java.util.Set.of(
            "then", "else", "in");

    private boolean canStartPrefix(ExpressionToken token) {
        if (token instanceof ExpressionToken.NameToken name && KEYWORDS.contains(name.name())) {
            return false;
        }
        return token instanceof ExpressionToken.LiteralToken
                || token instanceof ExpressionToken.NameToken
                || token instanceof ExpressionToken.RefToken
                || token instanceof ExpressionToken.CandidateToken
                || token instanceof ExpressionToken.OpenParen;
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

    private void expectName(String expected) {
        if (pos >= tokens.size()) {
            throw new ParseException("Expected '" + expected + "' but reached end of expression");
        }
        ExpressionToken token = tokens.get(pos);
        if (token instanceof ExpressionToken.NameToken name && expected.equals(name.name())) {
            pos++;
        } else {
            throw new ParseException("Expected '" + expected + "' but got: " + token.displayText());
        }
    }

    private boolean isName(String expected) {
        if (pos >= tokens.size()) return false;
        return tokens.get(pos) instanceof ExpressionToken.NameToken name
                && expected.equals(name.name());
    }

    /**
     * Thrown when frame body parsing fails.
     */
    public static class ParseException extends RuntimeException {
        public ParseException(String message) {
            super(message);
        }
    }
}
