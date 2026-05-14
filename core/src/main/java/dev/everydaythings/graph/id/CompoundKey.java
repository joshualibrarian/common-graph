package dev.everydaythings.graph.id;

import dev.everydaythings.graph.canonical.Scope;

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;
import dev.everydaythings.graph.canonical.Canonical;
import dev.everydaythings.graph.canonical.Factory;
import dev.everydaythings.graph.canonical.HashTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Compound semantic address: a head sememe plus zero or more qualifiers.
 *
 * <p>CompoundKey is the unified key type used both for binding identity within a frame
 * (role + qualifiers) and for frame identity within an item (predicate + qualifiers).
 *
 * <p>The <b>head</b> is always a sememe ({@link ItemID}). The <b>qualifiers</b> are a
 * (possibly empty) list of {@link FrameToken}s, each of which is either a {@link Sememe}
 * (vocabulary-backed ItemID) or a {@link Literal} (opaque string).
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code (TITLE)} — head only, no qualifiers</li>
 *   <li>{@code (GLOSS, ENG)} — head with a sememe qualifier</li>
 *   <li>{@code (EXPRESSION, "x")} — head with a literal qualifier</li>
 *   <li>{@code (DISPLAY_LAYOUT, hostId, "Retina Display-0")} — multi-qualifier mixing sememes and literals</li>
 * </ul>
 *
 * <p>The structural separation of head from qualifiers reflects the architectural fact
 * that the head is constitutive of the key's identity and must always be a sememe — it
 * cannot be a literal. Qualifiers are narrowing modifiers that may be either sememes or
 * literals depending on use.
 *
 * <p>CBOR format: array of tokens. The first element is a byte string (head sememe's
 * ItemID multihash). Subsequent elements are byte strings (sememe qualifiers) or text
 * strings (literal qualifiers). The CBOR type discriminates — no tags or prefixes
 * needed within the key itself.
 */
public final class CompoundKey implements Canonical, Comparable<CompoundKey> {

    private final ItemID head;
    private final List<FrameToken> qualifiers;

    private CompoundKey(ItemID head, List<FrameToken> qualifiers) {
        this.head = Objects.requireNonNull(head, "CompoundKey head must not be null");
        this.qualifiers = canonicalSortQualifiers(qualifiers);
    }

    /**
     * Qualifiers are a multiset — sorted canonically (by structural hash,
     * bitwise) so two keys with the same qualifiers in different orders are
     * equal. Mirrors how {@code Binding} canonicalizes its qualifier list.
     */
    private static List<FrameToken> canonicalSortQualifiers(List<FrameToken> qualifiers) {
        if (qualifiers == null || qualifiers.isEmpty()) return List.of();
        if (qualifiers.size() == 1) return List.copyOf(qualifiers);
        List<FrameToken> sorted = new ArrayList<>(qualifiers);
        sorted.sort(HashTree.CANONICAL);
        return List.copyOf(sorted);
    }

    // ==================================================================================
    // Tokens
    // ==================================================================================

    /**
     * A token that may appear as a qualifier — either a semantic reference or a literal string.
     *
     * <p>Note: the head of a CompoundKey is structurally always a sememe ({@link ItemID}),
     * not a FrameToken. FrameToken is the qualifier-position type, where either kind is valid.
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
     * <p>Literal qualifiers persist across versions of the same item (same key = same frame)
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
            org.apache.logging.log4j.LogManager.getLogger(CompoundKey.class);

    /**
     * Create a CompoundKey with a sememe head and optional qualifiers.
     *
     * <p>Qualifiers can be:
     * <ul>
     *   <li>{@link ItemID} — becomes a {@link Sememe} qualifier</li>
     *   <li>{@link String} — becomes a {@link Literal} qualifier</li>
     *   <li>{@link FrameToken} — passed through directly</li>
     * </ul>
     *
     * <p>Examples:
     * <pre>{@code
     * CompoundKey.of(TITLE)                    // (TITLE)
     * CompoundKey.of(GLOSS, ENG)               // (GLOSS, ENG)
     * CompoundKey.of(EXPRESSION, "x")          // (EXPRESSION, "x")
     * CompoundKey.of(DISPLAY, hostId, "Retina Display-0")
     * }</pre>
     *
     * @param head       the sememe head
     * @param qualifiers optional qualifiers (ItemID, String, or FrameToken)
     * @return the CompoundKey
     */
    public static CompoundKey of(ItemID head, Object... qualifiers) {
        Objects.requireNonNull(head, "CompoundKey head must not be null");
        if (qualifiers.length == 0) {
            return new CompoundKey(head, List.of());
        }
        List<FrameToken> tokens = new ArrayList<>(qualifiers.length);
        for (Object q : qualifiers) {
            tokens.add(toToken(q));
        }
        return new CompoundKey(head, tokens);
    }

    /**
     * Create a CompoundKey from an explicit head and qualifier list.
     */
    public static CompoundKey of(ItemID head, List<FrameToken> qualifiers) {
        return new CompoundKey(head, qualifiers);
    }

    /**
     * Create a CompoundKey from a flat token list, where the first token is the head.
     *
     * <p>The first token must be a {@link Sememe} (the head must be an ItemID).
     * Subsequent tokens become qualifiers. Provided for parity with the prior
     * {@code FrameKey.ofTokens(List)} API and for code that consumes a unified
     * token sequence (e.g., binary/text decoders that parse tokens linearly).
     *
     * @throws IllegalArgumentException if {@code tokens} is empty or the first
     *         token is not a Sememe.
     */
    public static CompoundKey ofTokens(List<FrameToken> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            throw new IllegalArgumentException("CompoundKey requires at least one token (the head)");
        }
        FrameToken first = tokens.get(0);
        if (!(first instanceof Sememe headToken)) {
            throw new IllegalArgumentException(
                    "CompoundKey head must be a Sememe (ItemID), got: "
                            + first.getClass().getSimpleName());
        }
        List<FrameToken> qualifiers = tokens.size() > 1
                ? tokens.subList(1, tokens.size())
                : List.of();
        return new CompoundKey(headToken.id(), qualifiers);
    }

    /**
     * Convert an arbitrary value to a {@link FrameToken}.
     */
    private static FrameToken toToken(Object q) {
        if (q instanceof FrameToken t) {
            return t;
        }
        if (q instanceof ItemID id) {
            return new Sememe(id);
        }
        if (q instanceof String s) {
            return new Literal(s);
        }
        throw new IllegalArgumentException(
                "CompoundKey qualifier must be FrameToken, ItemID, or String, got: "
                        + (q == null ? "null" : q.getClass().getSimpleName()));
    }

    /**
     * Parse a canonical string (produced by {@link #toCanonicalString()}) back to a CompoundKey.
     *
     * <p>Canonical strings are slash-separated tokens. The first token must parse as an
     * {@link ItemID} (the head). Subsequent tokens that look like an encoded ItemID are
     * parsed as {@link Sememe}; otherwise they're treated as {@link Literal} qualifiers.
     */
    public static CompoundKey fromCanonicalString(String canonical) {
        if (canonical == null || canonical.isBlank()) {
            throw new IllegalArgumentException("Cannot parse empty canonical string");
        }
        String[] parts = canonical.split("/");
        ItemID head;
        try {
            head = ItemID.parse(parts[0]);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "CompoundKey canonical string head must be a valid ItemID, got: " + parts[0], e);
        }
        List<FrameToken> qualifiers = new ArrayList<>(parts.length - 1);
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            try {
                qualifiers.add(new Sememe(ItemID.parse(part)));
            } catch (Exception e) {
                qualifiers.add(new Literal(part));
            }
        }
        return new CompoundKey(head, qualifiers);
    }

    // ==================================================================================
    // Accessors
    // ==================================================================================

    /** The head sememe of this key — always non-null, always an ItemID. */
    public ItemID head() {
        return head;
    }

    /**
     * The head sememe of this key.
     *
     * <p>Alias for {@link #head()} preserved from the prior FrameKey API for migration ease.
     */
    public ItemID headSememe() {
        return head;
    }

    /** The qualifier tokens after the head; possibly empty. */
    public List<FrameToken> qualifiers() {
        return qualifiers;
    }

    /** Number of tokens including the head: 1 + qualifiers.size(). */
    public int size() {
        return 1 + qualifiers.size();
    }

    /** True if every qualifier is a sememe (the head is always a sememe). */
    public boolean isSemantic() {
        return qualifiers.stream().allMatch(t -> t instanceof Sememe);
    }

    /**
     * The full token list including the head as a {@link Sememe} token.
     *
     * <p>Provided for parity with the prior FrameKey API and for code that consumes
     * the unified token sequence.
     */
    public List<FrameToken> tokens() {
        if (qualifiers.isEmpty()) {
            return List.of(new Sememe(head));
        }
        List<FrameToken> all = new ArrayList<>(1 + qualifiers.size());
        all.add(new Sememe(head));
        all.addAll(qualifiers);
        return List.copyOf(all);
    }

    // ==================================================================================
    // String Representation
    // ==================================================================================

    /**
     * A canonical, deterministic string form of this CompoundKey.
     *
     * <p>Slash-separated tokens. The head's encoded ItemID, followed by qualifier tokens
     * (sememe ItemIDs or literal strings). Used for filesystem paths, display, and debugging.
     */
    public String toCanonicalString() {
        StringBuilder sb = new StringBuilder();
        sb.append(head.encodeText());
        for (FrameToken token : qualifiers) {
            sb.append('/');
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
     * Human-readable display: {@code (TITLE)}, {@code (GLOSS, ENG)}, {@code (EXPRESSION, "x")}.
     */
    public String displayText() {
        StringBuilder sb = new StringBuilder("(");
        sb.append(new Sememe(head).displayText());
        for (FrameToken token : qualifiers) {
            sb.append(", ");
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
     * Encode as a CBOR array of tokens.
     *
     * <p>The first element is a byte string (head sememe's ItemID multihash bytes).
     * Subsequent elements are byte strings (sememe qualifiers) or text strings (literal
     * qualifiers). The CBOR type discriminates between sememe and literal qualifiers.
     */
    @Override
    public CBORObject toCborTree(Scope scope) {
        CBORObject array = CBORObject.NewArray();
        array.Add(head.toCborTree(Scope.BODY));
        for (FrameToken token : qualifiers) {
            array.Add(token.toCbor());
        }
        return array;
    }

    /**
     * Decode a CompoundKey from CBOR.
     *
     * <p>Expects a CBOR array whose first element is a byte string (the head sememe's
     * ItemID) and whose remaining elements are byte strings (sememe qualifiers) or
     * text strings (literal qualifiers).
     */
    @Factory
    public static CompoundKey fromCborTree(CBORObject node) {
        if (node == null || node.getType() != CBORType.Array || node.size() == 0) {
            throw new IllegalArgumentException("CompoundKey requires a non-empty CBOR array");
        }
        CBORObject headElement = node.get(0);
        if (headElement.getType() != CBORType.ByteString) {
            throw new IllegalArgumentException(
                    "CompoundKey head must be a byte string (sememe ItemID), got: "
                            + headElement.getType());
        }
        ItemID head = new ItemID(headElement.GetByteString());
        List<FrameToken> qualifiers = new ArrayList<>(node.size() - 1);
        for (int i = 1; i < node.size(); i++) {
            CBORObject element = node.get(i);
            if (element.getType() == CBORType.ByteString) {
                qualifiers.add(new Sememe(new ItemID(element.GetByteString())));
            } else if (element.getType() == CBORType.TextString) {
                qualifiers.add(new Literal(element.AsString()));
            } else {
                throw new IllegalArgumentException(
                        "CompoundKey qualifier must be byte string (sememe) or text string (literal), got: "
                                + element.getType());
            }
        }
        return new CompoundKey(head, qualifiers);
    }

    // ==================================================================================
    // Equality and Comparison
    // ==================================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CompoundKey other)) return false;
        return head.equals(other.head) && qualifiers.equals(other.qualifiers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(head, qualifiers);
    }

    /**
     * Lexicographic comparison: by head first, then by qualifier sequence.
     *
     * <p>Sememe qualifiers sort before literal qualifiers. Within sememes, by encoded
     * ItemID text. Within literals, by string value.
     */
    @Override
    public int compareTo(CompoundKey other) {
        int headCmp = head.encodeText().compareTo(other.head.encodeText());
        if (headCmp != 0) return headCmp;
        int len = Math.min(qualifiers.size(), other.qualifiers.size());
        for (int i = 0; i < len; i++) {
            int cmp = compareTokens(qualifiers.get(i), other.qualifiers.get(i));
            if (cmp != 0) return cmp;
        }
        return Integer.compare(qualifiers.size(), other.qualifiers.size());
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
