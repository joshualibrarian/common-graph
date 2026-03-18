package dev.everydaythings.graph.item.id;

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.Factory;

import java.util.ArrayList;
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
 *   <li>{@code (EXPRESSION, "x")} — sememe head + literal qualifier</li>
 *   <li>{@code (DISPLAY_LAYOUT, hostId, "Retina Display-0")} — multi-qualifier</li>
 * </ul>
 *
 * <p>The first token (head) is always a <em>sememe</em> — the semantic predicate.
 * Additional tokens are qualifiers that distinguish multiple instances.
 * Qualifiers can be sememes (ItemID) or literals (String).
 *
 * <p>CBOR format: array of tokens. Sememe tokens encode as byte strings
 * (ItemID multihash), literal tokens as text strings. The CBOR type
 * discriminates — no tags or prefixes needed.
 */
public final class FrameKey implements Canonical, Comparable<FrameKey> {

    private final List<FrameToken> tokens;

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

    private static final org.apache.logging.log4j.Logger logger =
            org.apache.logging.log4j.LogManager.getLogger(FrameKey.class);

    /**
     * Create a FrameKey with a semantic head and optional qualifiers.
     *
     * <p>The head is always a sememe (ItemID). Qualifiers can be:
     * <ul>
     *   <li>{@link ItemID} — becomes a Sememe token</li>
     *   <li>{@link String} — becomes a Literal token</li>
     * </ul>
     *
     * <p>Examples:
     * <pre>{@code
     * FrameKey.of(TITLE)                    // (TITLE)
     * FrameKey.of(GLOSS, ENG)              // (GLOSS, ENG)
     * FrameKey.of(EXPRESSION, "x")          // (EXPRESSION, "x")
     * FrameKey.of(DISPLAY, hostId, "Retina Display-0")
     * }</pre>
     *
     * @param head       the semantic predicate (must be an ItemID)
     * @param qualifiers optional qualifiers (ItemID or String)
     * @return the FrameKey
     */
    public static FrameKey of(ItemID head, Object... qualifiers) {
        Objects.requireNonNull(head, "FrameKey head must not be null");
        if (qualifiers.length == 0) {
            return new FrameKey(List.of(new Sememe(head)));
        }
        List<FrameToken> tokens = new java.util.ArrayList<>(1 + qualifiers.length);
        tokens.add(new Sememe(head));
        for (Object q : qualifiers) {
            if (q instanceof ItemID id) {
                tokens.add(new Sememe(id));
            } else if (q instanceof String s) {
                tokens.add(new Literal(s));
            } else {
                throw new IllegalArgumentException(
                        "FrameKey qualifier must be ItemID or String, got: "
                                + (q == null ? "null" : q.getClass().getSimpleName()));
            }
        }
        return new FrameKey(tokens);
    }

    /**
     * Parse a canonical string (produced by {@link #toCanonicalString()}) back to a FrameKey.
     *
     * <p>Canonical strings are slash-separated tokens. Each token that looks like
     * an encoded ItemID is parsed as a Sememe; otherwise it's treated as a Literal qualifier.
     * The head token must be a valid ItemID.
     */
    public static FrameKey fromCanonicalString(String canonical) {
        if (canonical == null || canonical.isBlank()) {
            throw new IllegalArgumentException("Cannot parse empty canonical string");
        }
        String[] parts = canonical.split("/");
        List<FrameToken> tokens = new ArrayList<>();
        for (String part : parts) {
            try {
                tokens.add(new Sememe(ItemID.parse(part)));
            } catch (Exception e) {
                tokens.add(new Literal(part));
            }
        }
        return new FrameKey(tokens);
    }

    /**
     * Create a FrameKey from an arbitrary list of tokens.
     */
    public static FrameKey ofTokens(List<FrameToken> tokens) {
        return new FrameKey(tokens);
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
     * True if every token is a sememe (fully semantic key).
     */
    public boolean isSemantic() {
        return tokens.stream().allMatch(t -> t instanceof Sememe);
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
    // String Representation
    // ==================================================================================

    /**
     * A canonical, deterministic string form of this FrameKey.
     *
     * <p>For literal keys, this is the literal value itself (e.g., "vault").
     * For semantic keys, a slash-separated string of token representations.
     * Used for filesystem paths, display, and debugging.
     */
    public String toCanonicalString() {
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
