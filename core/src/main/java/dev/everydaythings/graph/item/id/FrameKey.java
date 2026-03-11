package dev.everydaythings.graph.item.id;

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.component.Factory;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Compound semantic address for a frame on an item.
 *
 * <p>A FrameKey is an immutable sequence of tokens — each either a
 * {@link Sememe} (vocabulary-backed ItemID) or a {@link Literal} (opaque string).
 * Together they form a compound address: {@code (GLOSS, ENG)} is a two-segment
 * key identifying the English gloss of a sememe.
 *
 * <p>Keys can be:
 * <ul>
 *   <li>{@code (TITLE)} — single sememe</li>
 *   <li>{@code (GLOSS, ENG)} — compound sememe (gloss for English)</li>
 *   <li>{@code (CHAT, "tavern")} — mixed (sememe + literal qualifier)</li>
 *   <li>{@code ("x")} — single literal (developer scratch variable)</li>
 * </ul>
 *
 * <p>The first token is the <em>head</em> — the primary predicate. Additional
 * tokens are qualifiers that distinguish multiple instances of the same predicate.
 *
 * <p>A single-literal FrameKey is the degenerate case that corresponds to
 * the current {@link HandleID} — a hash of a string. The migration path is:
 * {@code HandleID.of("vault")} → {@code FrameKey.literal("vault")}.
 *
 * <p>CBOR format: array of tokens. Sememe tokens encode as byte strings
 * (ItemID multihash), literal tokens as text strings. The CBOR type
 * discriminates — no tags or prefixes needed.
 */
public final class FrameKey implements Canonical, Comparable<FrameKey> {

    private final List<FrameToken> tokens;
    private transient HandleID cachedHandleID;

    private FrameKey(List<FrameToken> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            throw new IllegalArgumentException("FrameKey must have at least one token");
        }
        this.tokens = List.copyOf(tokens);
    }

    // ==================================================================================
    // Tokens
    // ==================================================================================

    /**
     * A token in a FrameKey — either a semantic reference or a literal string.
     */
    public sealed interface FrameToken permits Sememe, Literal {

        /** Encode this token to CBOR. */
        CBORObject toCbor();

        /** Display text for this token. */
        String displayText();
    }

    /**
     * A semantic token — vocabulary-backed, language-resolvable, merge-friendly.
     */
    public record Sememe(ItemID id) implements FrameToken {
        public Sememe {
            Objects.requireNonNull(id, "sememe id");
        }

        @Override
        public CBORObject toCbor() {
            return id.toCborTree(Scope.BODY);
        }

        @Override
        public String displayText() {
            String text = id.encodeText();
            int colon = text.lastIndexOf(':');
            if (colon >= 0 && colon < text.length() - 1) {
                String suffix = text.substring(colon + 1);
                int slash = suffix.lastIndexOf('/');
                return slash >= 0 ? suffix.substring(slash + 1).toUpperCase() : suffix.toUpperCase();
            }
            return text;
        }
    }

    /**
     * A literal token — opaque, fast, not vocabulary-resolvable.
     *
     * <p>Literal keys persist across versions of the same item (same key = same frame)
     * but don't participate in semantic discovery or cross-language resolution.
     */
    public record Literal(String value) implements FrameToken {
        public Literal {
            Objects.requireNonNull(value, "literal value");
            if (value.isBlank()) {
                throw new IllegalArgumentException("literal token cannot be blank");
            }
        }

        @Override
        public CBORObject toCbor() {
            return CBORObject.FromString(value);
        }

        @Override
        public String displayText() {
            return value;
        }
    }

    // ==================================================================================
    // Factories
    // ==================================================================================

    /**
     * Create a FrameKey from a single sememe.
     *
     * <p>Example: {@code FrameKey.of(TITLE)} → {@code (TITLE)}
     */
    public static FrameKey of(ItemID sememe) {
        return new FrameKey(List.of(new Sememe(sememe)));
    }

    /**
     * Create a compound FrameKey from multiple sememes.
     *
     * <p>Example: {@code FrameKey.of(GLOSS, ENG)} → {@code (GLOSS, ENG)}
     */
    public static FrameKey of(ItemID... sememes) {
        if (sememes.length == 0) {
            throw new IllegalArgumentException("FrameKey requires at least one token");
        }
        return new FrameKey(Arrays.stream(sememes)
                .map(Sememe::new)
                .map(s -> (FrameToken) s)
                .toList());
    }

    /**
     * Create a single-literal FrameKey.
     *
     * <p>This is the degenerate case corresponding to the current HandleID.
     * {@code FrameKey.literal("vault")} behaves like {@code HandleID.of("vault")}.
     */
    public static FrameKey literal(String value) {
        return new FrameKey(List.of(new Literal(value)));
    }

    /**
     * Create a mixed FrameKey — sememe head with literal qualifier.
     *
     * <p>Example: {@code FrameKey.mixed(CHAT, "tavern")} → {@code (CHAT, "tavern")}
     */
    public static FrameKey mixed(ItemID head, String qualifier) {
        return new FrameKey(List.of(new Sememe(head), new Literal(qualifier)));
    }

    /**
     * Create a FrameKey from an arbitrary list of tokens.
     */
    public static FrameKey ofTokens(List<FrameToken> tokens) {
        return new FrameKey(tokens);
    }

    /**
     * Create a FrameKey from a HandleID string (backward compat).
     *
     * <p>Produces a single-literal key whose value is the handle string.
     */
    public static FrameKey fromHandle(String handle) {
        return literal(handle);
    }

    // ==================================================================================
    // Accessors
    // ==================================================================================

    /** All tokens in this key. */
    public List<FrameToken> tokens() {
        return tokens;
    }

    /** Number of tokens in this key. */
    public int size() {
        return tokens.size();
    }

    /** The first (head) token — the primary predicate. */
    public FrameToken head() {
        return tokens.getFirst();
    }

    /** The qualifier tokens (everything after the head), or empty. */
    public List<FrameToken> qualifiers() {
        return tokens.size() > 1 ? tokens.subList(1, tokens.size()) : List.of();
    }

    /**
     * True if this key is a single literal token (the HandleID degenerate case).
     */
    public boolean isLiteral() {
        return tokens.size() == 1 && tokens.getFirst() instanceof Literal;
    }

    /**
     * True if every token is a sememe (fully semantic key).
     */
    public boolean isSemantic() {
        return tokens.stream().allMatch(t -> t instanceof Sememe);
    }

    /**
     * Get the literal value if this is a single-literal key, or null.
     */
    public String literalValue() {
        if (isLiteral()) {
            return ((Literal) tokens.getFirst()).value();
        }
        return null;
    }

    /**
     * Get the head sememe ID if the head is a sememe, or null.
     */
    public ItemID headSememe() {
        if (tokens.getFirst() instanceof Sememe s) {
            return s.id();
        }
        return null;
    }

    // ==================================================================================
    // HandleID Compatibility
    // ==================================================================================

    /**
     * Convert to HandleID for backward compatibility.
     *
     * <p>For single-literal keys, hashes the literal value (same as HandleID.of()).
     * For semantic/compound keys, hashes the display string.
     */
    public HandleID toHandleID() {
        if (cachedHandleID == null) {
            cachedHandleID = HandleID.of(toHandleString());
        }
        return cachedHandleID;
    }

    /**
     * The string used for HandleID hashing — the canonical handle name.
     *
     * <p>For literal keys, this is the literal value itself.
     * For semantic keys, this is a deterministic string from the token sequence.
     */
    public String toHandleString() {
        if (isLiteral()) {
            return ((Literal) tokens.getFirst()).value();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) sb.append('/');
            FrameToken token = tokens.get(i);
            if (token instanceof Sememe s) {
                sb.append(s.id().encodeText());
            } else if (token instanceof Literal l) {
                sb.append(l.value());
            }
        }
        return sb.toString();
    }

    // ==================================================================================
    // Display
    // ==================================================================================

    /**
     * Human-readable display: {@code (TITLE)}, {@code (GLOSS, ENG)}, {@code ("x")}.
     */
    public String displayText() {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) sb.append(", ");
            FrameToken token = tokens.get(i);
            if (token instanceof Literal l) {
                sb.append('"').append(l.value()).append('"');
            } else {
                sb.append(token.displayText());
            }
        }
        sb.append(')');
        return sb.toString();
    }

    @Override
    public String toString() {
        return displayText();
    }

    // ==================================================================================
    // Canonical (CBOR) Encoding
    // ==================================================================================

    /**
     * Encode as CBOR array of tokens.
     *
     * <p>Sememe tokens encode as byte strings (ItemID multihash bytes).
     * Literal tokens encode as text strings. The CBOR type discriminates.
     */
    @Override
    public CBORObject toCborTree(Scope scope) {
        CBORObject array = CBORObject.NewArray();
        for (FrameToken token : tokens) {
            array.Add(token.toCbor());
        }
        return array;
    }

    /**
     * Decode a FrameKey from CBOR.
     *
     * <p>Expects a CBOR array where each element is either a byte string
     * (sememe ItemID) or a text string (literal).
     */
    @Factory
    public static FrameKey fromCborTree(CBORObject node) {
        if (node == null || node.getType() != CBORType.Array || node.size() == 0) {
            throw new IllegalArgumentException("FrameKey requires a non-empty CBOR array");
        }
        List<FrameToken> tokens = new java.util.ArrayList<>(node.size());
        for (CBORObject element : node.getValues()) {
            if (element.getType() == CBORType.ByteString) {
                tokens.add(new Sememe(new ItemID(element.GetByteString())));
            } else if (element.getType() == CBORType.TextString) {
                tokens.add(new Literal(element.AsString()));
            } else {
                throw new IllegalArgumentException(
                        "FrameKey token must be byte string (sememe) or text string (literal), got: "
                                + element.getType());
            }
        }
        return new FrameKey(tokens);
    }

    // ==================================================================================
    // Equality and Comparison
    // ==================================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FrameKey other)) return false;
        return tokens.equals(other.tokens);
    }

    @Override
    public int hashCode() {
        return tokens.hashCode();
    }

    /**
     * Lexicographic comparison by token sequence.
     *
     * <p>Sememes sort before literals. Within sememes, by ItemID bytes.
     * Within literals, by string value.
     */
    @Override
    public int compareTo(FrameKey other) {
        int len = Math.min(tokens.size(), other.tokens.size());
        for (int i = 0; i < len; i++) {
            int cmp = compareTokens(tokens.get(i), other.tokens.get(i));
            if (cmp != 0) return cmp;
        }
        return Integer.compare(tokens.size(), other.tokens.size());
    }

    private static int compareTokens(FrameToken a, FrameToken b) {
        if (a instanceof Sememe sa && b instanceof Sememe sb) {
            return sa.id().encodeText().compareTo(sb.id().encodeText());
        }
        if (a instanceof Literal la && b instanceof Literal lb) {
            return la.value().compareTo(lb.value());
        }
        // Sememes sort before literals
        return (a instanceof Sememe) ? -1 : 1;
    }
}
