package dev.everydaythings.graph.expression;

import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.value.Operator;

/**
 * Token in the expression input - the building blocks for user input.
 *
 * <p>As the user types and selects from lookup results, they build a sequence
 * of tokens. These tokens can then be converted to core Expression objects.
 *
 * <p>Token types:
 * <ul>
 *   <li>{@link RefToken} - A resolved item reference (selected from lookup)</li>
 *   <li>{@link LiteralToken} - A literal value typed directly</li>
 *   <li>{@link OpToken} - An operator (AND, OR) - references Operator Items</li>
 *   <li>{@link OpenParen} - Opening parenthesis</li>
 *   <li>{@link CloseParen} - Closing parenthesis</li>
 * </ul>
 */
public sealed interface ExpressionToken {

    /**
     * Get the display text for this token (shown in the UI).
     */
    String displayText();

    /**
     * A resolved item reference - created when user selects a posting.
     *
     * <p>Keeps the original posting for context (weight, source, facets).
     */
    record RefToken(
            ItemID target,
            String displayText,
            Posting sourcePosting  // nullable - for context
    ) implements ExpressionToken {

        /**
         * Create from a posting (the common case).
         */
        public static RefToken from(Posting posting) {
            // Use token as display text, or derive a short form from target
            String display = posting.token();
            if (display == null || display.isBlank()) {
                display = shortForm(posting.target());
            }
            return new RefToken(posting.target(), display, posting);
        }

        /**
         * Create directly (without a posting).
         */
        public static RefToken of(ItemID target, String displayText) {
            return new RefToken(target, displayText, null);
        }

        private static String shortForm(ItemID id) {
            String text = id.encodeText();
            // If it's a key-style ID (has :), show after the colon
            int colonIdx = text.indexOf(':');
            if (colonIdx > 0 && colonIdx < text.length() - 1) {
                return text.substring(colonIdx + 1);
            }
            // Otherwise truncate hash
            return text.substring(0, Math.min(12, text.length())) + "...";
        }
    }

    /**
     * A literal value - typed directly, not selected from lookup.
     *
     * <p>Examples: 42, "hello", true
     */
    record LiteralToken(
            Object value,
            String displayText
    ) implements ExpressionToken {

        /**
         * Create a string literal.
         */
        public static LiteralToken ofString(String s) {
            return new LiteralToken(s, "\"" + s + "\"");
        }

        /**
         * Create a number literal.
         */
        public static LiteralToken ofNumber(Number n) {
            return new LiteralToken(n, n.toString());
        }

        /**
         * Create a boolean literal.
         */
        public static LiteralToken ofBoolean(boolean b) {
            return new LiteralToken(b, b ? "true" : "false");
        }

        /**
         * Try to parse a literal from text.
         *
         * @return LiteralToken if parseable, null otherwise
         */
        public static LiteralToken tryParse(String text) {
            if (text == null || text.isBlank()) return null;

            String trimmed = text.trim();

            // Boolean
            if (trimmed.equalsIgnoreCase("true")) {
                return ofBoolean(true);
            }
            if (trimmed.equalsIgnoreCase("false")) {
                return ofBoolean(false);
            }

            // Quoted string
            if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                    || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
                String inner = trimmed.substring(1, trimmed.length() - 1);
                return ofString(inner);
            }

            // Number
            try {
                if (trimmed.contains(".")) {
                    // Note: We might want to avoid floats in CG-CBOR
                    // For now, allow them in the expression model
                    return ofNumber(Double.parseDouble(trimmed));
                } else {
                    return ofNumber(Long.parseLong(trimmed));
                }
            } catch (NumberFormatException e) {
                // Not a number
            }

            return null;
        }
    }

    /**
     * An operator token - references an Operator Item.
     *
     * <p>Operators (AND, OR) are now first-class Items with stable IIDs.
     * This enables discoverability and relations.
     *
     * @param operatorId The ItemID of the Operator item
     */
    record OpToken(ItemID operatorId) implements ExpressionToken {

        /**
         * Create an AND operator token.
         */
        public static OpToken and() {
            return new OpToken(Operator.AND.iid());
        }

        /**
         * Create an OR operator token.
         */
        public static OpToken or() {
            return new OpToken(Operator.OR.iid());
        }

        @Override
        public String displayText() {
            // Resolve from operator metadata (seed lookup path).
            Operator op = Operator.lookupKnown(operatorId);
            if (op != null && op.symbol() != null && !op.symbol().isBlank()) {
                return op.symbol();
            }
            return operatorId.toString();
        }

        /**
         * Get the precedence of this operator.
         *
         * <p>Higher precedence binds tighter. AND (1) binds tighter than OR (0).
         *
         * @return The precedence level
         */
        public int precedence() {
            Operator op = Operator.lookupKnown(operatorId);
            if (op != null) return op.precedence();
            return 0;
        }

        /**
         * Try to parse an operator from text.
         *
         * @param text The text to parse (e.g., "AND", "&&", "OR", "||")
         * @return OpToken if recognized, null otherwise
         */
        public static OpToken tryParse(String text) {
            Operator op = Operator.fromSymbol(text);
            return op != null ? new OpToken(op.iid()) : null;
        }
    }

    /**
     * Opening parenthesis.
     */
    record OpenParen() implements ExpressionToken {
        @Override
        public String displayText() {
            return "(";
        }
    }

    /**
     * Closing parenthesis.
     */
    record CloseParen() implements ExpressionToken {
        @Override
        public String displayText() {
            return ")";
        }
    }

    /**
     * An unresolved name — a bare identifier not yet bound to an item.
     *
     * <p>Used in expression parsing for variable references (x, y, z)
     * and function names. Resolved to bindings at evaluation time.
     */
    record NameToken(String name) implements ExpressionToken {
        @Override
        public String displayText() {
            return name;
        }
    }

    /**
     * Dot — property access separator.
     *
     * <p>Used in expression parsing for property access: {@code session.activity}.
     * Handled as the tightest-binding infix operation in the Pratt parser.
     */
    record DotToken() implements ExpressionToken {
        @Override
        public String displayText() {
            return ".";
        }
    }

    /**
     * Comma — argument separator in function calls.
     */
    record CommaToken() implements ExpressionToken {
        @Override
        public String displayText() {
            return ",";
        }
    }
}
