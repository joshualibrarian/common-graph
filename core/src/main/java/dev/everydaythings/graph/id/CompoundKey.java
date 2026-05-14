package dev.everydaythings.graph.id;

import dev.everydaythings.graph.canonical.Decode;
import dev.everydaythings.graph.canonical.Encode;
import dev.everydaythings.graph.canonical.HashTree;
import dev.everydaythings.graph.canonical.Layout;
import dev.everydaythings.graph.canonical.Order;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Compound semantic address: a head sememe plus zero or more qualifiers.
 *
 * <p>CompoundKey is the unified key type used both for binding identity within a frame
 * (role + qualifiers) and for frame identity within an item (predicate + qualifiers).
 *
 * <p>The <b>head</b> is always a sememe ({@link ItemRef}). The <b>qualifiers</b> are a
 * (possibly empty) list of {@link Qualifier}s, each of which is either a {@link Sememe}
 * (vocabulary-backed ItemRef) or a {@link Text} (opaque string).
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
 * ItemRef multihash). Subsequent elements are byte strings (sememe qualifiers) or text
 * strings (literal qualifiers). The CBOR type discriminates — no tags or prefixes
 * needed within the key itself.
 */
@Layout(Layout.Kind.ARRAY)
public final class CompoundKey implements Comparable<CompoundKey> {

    @Order(0) private final ItemRef head;
    @Order(1) private final List<Qualifier> qualifiers;

    private CompoundKey(ItemRef head, List<Qualifier> qualifiers) {
        this.head = Objects.requireNonNull(head, "CompoundKey head must not be null");
        this.qualifiers = canonicalSortQualifiers(qualifiers);
    }

    /**
     * Qualifiers are a multiset — sorted canonically (by structural hash,
     * bitwise) so two keys with the same qualifiers in different orders are
     * equal. Mirrors how {@code Binding} canonicalizes its qualifier list.
     */
    private static List<Qualifier> canonicalSortQualifiers(List<Qualifier> qualifiers) {
        if (qualifiers == null || qualifiers.isEmpty()) return List.of();
        if (qualifiers.size() == 1) return List.copyOf(qualifiers);
        List<Qualifier> sorted = new ArrayList<>(qualifiers);
        sorted.sort(HashTree.CANONICAL);
        return List.copyOf(sorted);
    }

    // ==================================================================================
    // Tokens
    // ==================================================================================

    /**
     * The qualifier-position sum type: either a semantic reference (a sememe
     * ItemRef wrapper) or a text string. The head of a CompoundKey is always
     * a sememe — only qualifiers admit text.
     *
     * <p>Marker interface only — no wire-form methods. Encoding lives in the
     * codec ({@code CgCbor.encodeQualifier}); display is via pattern-match
     * at use sites or via {@link #displayText()}.
     */
    public sealed interface Qualifier permits Sememe, Text {
        /** Display text for this token. */
        String displayText();
    }

    /**
     * A semantic-reference qualifier — vocabulary-backed, language-resolvable,
     * merge-friendly. Thin wrapper over an ItemRef.
     */
    public record Sememe(ItemRef id) implements Qualifier {
        public Sememe {
            Objects.requireNonNull(id, "sememe id");
        }

        /** Transparent encoding: the codec recurses into the wrapped ItemRef. */
        @Encode
        public ItemRef encode() {
            return id;
        }

        @Decode
        public static Sememe fromItemRef(ItemRef id) {
            return new Sememe(id);
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
     * A text-string qualifier — opaque, fast, not vocabulary-resolvable.
     *
     * <p>Text qualifiers persist across versions of the same item (same key
     * = same frame) but don't participate in semantic discovery or
     * cross-language resolution.
     */
    public record Text(String value) implements Qualifier {
        public Text {
            Objects.requireNonNull(value, "text value");
            if (value.isBlank()) {
                throw new IllegalArgumentException("text token cannot be blank");
            }
        }

        /** Transparent encoding: the codec recurses into the wrapped String. */
        @Encode
        public String encode() {
            return value;
        }

        @Decode
        public static Text fromString(String value) {
            return new Text(value);
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
     *   <li>{@link ItemRef} — becomes a {@link Sememe} qualifier</li>
     *   <li>{@link String} — becomes a {@link Text} qualifier</li>
     *   <li>{@link Qualifier} — passed through directly</li>
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
     * @param qualifiers optional qualifiers (ItemRef, String, or Qualifier)
     * @return the CompoundKey
     */
    public static CompoundKey of(ItemRef head, Object... qualifiers) {
        Objects.requireNonNull(head, "CompoundKey head must not be null");
        if (qualifiers.length == 0) {
            return new CompoundKey(head, List.of());
        }
        List<Qualifier> tokens = new ArrayList<>(qualifiers.length);
        for (Object q : qualifiers) {
            tokens.add(toToken(q));
        }
        return new CompoundKey(head, tokens);
    }

    /**
     * Create a CompoundKey from an explicit head and qualifier list.
     */
    public static CompoundKey of(ItemRef head, List<Qualifier> qualifiers) {
        return new CompoundKey(head, qualifiers);
    }

    /**
     * Create a CompoundKey from a flat token list, where the first token is the head.
     *
     * <p>The first token must be a {@link Sememe} (the head must be an ItemRef).
     * Subsequent tokens become qualifiers. Provided for parity with the prior
     * {@code FrameKey.ofTokens(List)} API and for code that consumes a unified
     * token sequence (e.g., binary/text decoders that parse tokens linearly).
     *
     * @throws IllegalArgumentException if {@code tokens} is empty or the first
     *         token is not a Sememe.
     */
    public static CompoundKey ofTokens(List<Qualifier> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            throw new IllegalArgumentException("CompoundKey requires at least one token (the head)");
        }
        Qualifier first = tokens.get(0);
        if (!(first instanceof Sememe headToken)) {
            throw new IllegalArgumentException(
                    "CompoundKey head must be a Sememe (ItemRef), got: "
                            + first.getClass().getSimpleName());
        }
        List<Qualifier> qualifiers = tokens.size() > 1
                ? tokens.subList(1, tokens.size())
                : List.of();
        return new CompoundKey(headToken.id(), qualifiers);
    }

    /**
     * Convert an arbitrary value to a {@link Qualifier}.
     */
    private static Qualifier toToken(Object q) {
        if (q instanceof Qualifier t) {
            return t;
        }
        if (q instanceof ItemRef id) {
            return new Sememe(id);
        }
        if (q instanceof String s) {
            return new Text(s);
        }
        throw new IllegalArgumentException(
                "CompoundKey qualifier must be Qualifier, ItemRef, or String, got: "
                        + (q == null ? "null" : q.getClass().getSimpleName()));
    }

    /**
     * Parse a canonical string (produced by {@link #toCanonicalString()}) back to a CompoundKey.
     *
     * <p>Canonical strings are slash-separated tokens. The first token must parse as an
     * {@link ItemRef} (the head). Subsequent tokens that look like an encoded ItemRef are
     * parsed as {@link Sememe}; otherwise they're treated as {@link Text} qualifiers.
     */
    public static CompoundKey fromCanonicalString(String canonical) {
        if (canonical == null || canonical.isBlank()) {
            throw new IllegalArgumentException("Cannot parse empty canonical string");
        }
        String[] parts = canonical.split("/");
        ItemRef head;
        try {
            head = ItemRef.parseText(parts[0]);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "CompoundKey canonical string head must be a valid ItemRef, got: " + parts[0], e);
        }
        List<Qualifier> qualifiers = new ArrayList<>(parts.length - 1);
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            try {
                qualifiers.add(new Sememe(ItemRef.parseText(part)));
            } catch (Exception e) {
                qualifiers.add(new Text(part));
            }
        }
        return new CompoundKey(head, qualifiers);
    }

    // ==================================================================================
    // Accessors
    // ==================================================================================

    /** The head sememe of this key — always non-null, always an ItemRef. */
    public ItemRef head() {
        return head;
    }

    /**
     * The head sememe of this key.
     *
     * <p>Alias for {@link #head()} preserved from the prior FrameKey API for migration ease.
     */
    public ItemRef headSememe() {
        return head;
    }

    /** The qualifier tokens after the head; possibly empty. */
    public List<Qualifier> qualifiers() {
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
    public List<Qualifier> tokens() {
        if (qualifiers.isEmpty()) {
            return List.of(new Sememe(head));
        }
        List<Qualifier> all = new ArrayList<>(1 + qualifiers.size());
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
     * <p>Slash-separated tokens. The head's encoded ItemRef, followed by qualifier tokens
     * (sememe ItemIDs or literal strings). Used for filesystem paths, display, and debugging.
     */
    public String toCanonicalString() {
        StringBuilder sb = new StringBuilder();
        sb.append(head.encodeText());
        for (Qualifier token : qualifiers) {
            sb.append('/');
            if (token instanceof Sememe s) {
                sb.append(s.id().encodeText());
            } else if (token instanceof Text l) {
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
        for (Qualifier token : qualifiers) {
            sb.append(", ");
            if (token instanceof Text l) {
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
     * ItemRef text. Within literals, by string value.
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

    private static int compareTokens(Qualifier a, Qualifier b) {
        if (a instanceof Sememe sa && b instanceof Sememe sb) {
            return sa.id().encodeText().compareTo(sb.id().encodeText());
        }
        if (a instanceof Text la && b instanceof Text lb) {
            return la.value().compareTo(lb.value());
        }
        // Sememes sort before literals
        return (a instanceof Sememe) ? -1 : 1;
    }
}
