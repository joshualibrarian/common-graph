package dev.everydaythings.graph.expression;

import dev.everydaythings.graph.value.Operator;

import java.util.ArrayList;
import java.util.List;

/**
 * Tokenizes raw expression strings into {@link ExpressionToken} lists.
 *
 * <p>This is the bridge between text (bind paths, user input, stored expressions)
 * and the {@link ExpressionParser}. The lexer handles:
 * <ul>
 *   <li>Identifiers: {@code session}, {@code x}, {@code resultText}</li>
 *   <li>Numbers: {@code 42}, {@code 3.14}</li>
 *   <li>Strings: {@code "hello"}, {@code 'world'}</li>
 *   <li>Booleans: {@code true}, {@code false}</li>
 *   <li>Operators: {@code +}, {@code -}, {@code *}, {@code ==}, {@code &&}, etc.</li>
 *   <li>Parentheses: {@code (}, {@code )}</li>
 *   <li>Dot: {@code .} (property access)</li>
 *   <li>Comma: {@code ,} (function argument separator)</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * List<ExpressionToken> tokens = ExpressionLexer.tokenize("session.activity.resultText");
 * Expression expr = ExpressionParser.parse(tokens);
 * }</pre>
 */
public class ExpressionLexer {

    private final String input;
    private int pos;

    private ExpressionLexer(String input) {
        this.input = input;
        this.pos = 0;
    }

    /**
     * Tokenize an expression string.
     *
     * @param input The expression string to tokenize
     * @return List of expression tokens
     * @throws LexException if the input contains invalid characters
     */
    public static List<ExpressionToken> tokenize(String input) {
        if (input == null || input.isBlank()) {
            return List.of();
        }
        return new ExpressionLexer(input).lex();
    }

    private List<ExpressionToken> lex() {
        List<ExpressionToken> tokens = new ArrayList<>();

        while (pos < input.length()) {
            char c = input.charAt(pos);

            // Skip whitespace
            if (Character.isWhitespace(c)) {
                pos++;
                continue;
            }

            // Parentheses
            if (c == '(') {
                tokens.add(new ExpressionToken.OpenParen());
                pos++;
                continue;
            }
            if (c == ')') {
                tokens.add(new ExpressionToken.CloseParen());
                pos++;
                continue;
            }

            // Comma
            if (c == ',') {
                tokens.add(new ExpressionToken.CommaToken());
                pos++;
                continue;
            }

            // Dot — but only if NOT followed by a digit (to distinguish from decimal numbers)
            if (c == '.' && (pos + 1 >= input.length() || !Character.isDigit(input.charAt(pos + 1)))) {
                tokens.add(new ExpressionToken.DotToken());
                pos++;
                continue;
            }

            // String literals
            if (c == '"' || c == '\'') {
                tokens.add(lexString(c));
                continue;
            }

            // Numbers (including leading dot: .5)
            if (Character.isDigit(c) || (c == '.' && pos + 1 < input.length() && Character.isDigit(input.charAt(pos + 1)))) {
                tokens.add(lexNumber());
                continue;
            }

            // Identifiers and keywords
            if (Character.isLetter(c) || c == '_') {
                tokens.add(lexIdentifier());
                continue;
            }

            // Operators (multi-char first, then single-char)
            ExpressionToken opToken = lexOperator();
            if (opToken != null) {
                tokens.add(opToken);
                continue;
            }

            throw new LexException("Unexpected character: '" + c + "' at position " + pos);
        }

        return tokens;
    }

    // ==================================================================================
    // Token Lexers
    // ==================================================================================

    private ExpressionToken.LiteralToken lexString(char quote) {
        pos++; // consume opening quote
        StringBuilder sb = new StringBuilder();
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == '\\' && pos + 1 < input.length()) {
                pos++; // consume backslash
                char escaped = input.charAt(pos);
                switch (escaped) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case '\\' -> sb.append('\\');
                    default -> {
                        sb.append(quote == escaped ? escaped : '\\');
                        if (quote != escaped) sb.append(escaped);
                    }
                }
                pos++;
            } else if (c == quote) {
                pos++; // consume closing quote
                return ExpressionToken.LiteralToken.ofString(sb.toString());
            } else {
                sb.append(c);
                pos++;
            }
        }
        throw new LexException("Unterminated string literal");
    }

    private ExpressionToken.LiteralToken lexNumber() {
        int start = pos;
        boolean hasDecimal = false;

        if (input.charAt(pos) == '.') {
            hasDecimal = true;
            pos++;
        }

        while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
            pos++;
        }

        // Decimal point (if we haven't seen one yet)
        if (!hasDecimal && pos < input.length() && input.charAt(pos) == '.'
                && pos + 1 < input.length() && Character.isDigit(input.charAt(pos + 1))) {
            hasDecimal = true;
            pos++; // consume dot
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                pos++;
            }
        }

        String text = input.substring(start, pos);
        if (hasDecimal) {
            return ExpressionToken.LiteralToken.ofNumber(Double.parseDouble(text));
        } else {
            return ExpressionToken.LiteralToken.ofNumber(Long.parseLong(text));
        }
    }

    private ExpressionToken lexIdentifier() {
        int start = pos;
        while (pos < input.length() && (Character.isLetterOrDigit(input.charAt(pos)) || input.charAt(pos) == '_')) {
            pos++;
        }
        String word = input.substring(start, pos);

        // Check for boolean literals
        if (word.equals("true")) return ExpressionToken.LiteralToken.ofBoolean(true);
        if (word.equals("false")) return ExpressionToken.LiteralToken.ofBoolean(false);

        // Otherwise it's a name (variable, function, keyword)
        return new ExpressionToken.NameToken(word);
    }

    /**
     * Try to lex an operator symbol. Multi-character operators are tried first.
     */
    private ExpressionToken lexOperator() {
        // Try 2-char operators first
        if (pos + 1 < input.length()) {
            String two = input.substring(pos, pos + 2);
            Operator op = tryOperatorSymbol(two);
            if (op != null) {
                pos += 2;
                return new ExpressionToken.OpToken(op.iid());
            }
        }

        // Single-char operators
        String one = input.substring(pos, pos + 1);
        Operator op = tryOperatorSymbol(one);
        if (op != null) {
            pos += 1;
            return new ExpressionToken.OpToken(op.iid());
        }

        return null;
    }

    /**
     * Try to resolve a symbol as an operator.
     *
     * <p>Checks both infix and prefix operator tables.
     */
    private Operator tryOperatorSymbol(String symbol) {
        Operator op = Operator.infixFromSymbol(symbol);
        if (op != null) return op;
        return Operator.prefixFromSymbol(symbol);
    }

    // ==================================================================================
    // Exception
    // ==================================================================================

    /**
     * Thrown when expression lexing fails.
     */
    public static class LexException extends RuntimeException {
        public LexException(String message) {
            super(message);
        }
    }
}
