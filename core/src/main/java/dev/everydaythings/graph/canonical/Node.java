package dev.everydaythings.graph.canonical;

import dev.everydaythings.graph.id.HashID;

import java.util.List;
import java.util.Objects;

/**
 * Encoding-agnostic structural view of a value.
 *
 * <p>An {@code Encoding} exposes any value it can encode as a {@code Node} tree,
 * letting consumers walk, hash, pretty-print, query, or otherwise inspect the
 * value's structure without knowing what encoding produced it. The tree has
 * three structural kinds:
 *
 * <ul>
 *   <li>{@link Leaf} — a single typed value: a primitive (String, Long, Boolean,
 *       byte[], Instant, Decimal, Rational, ...) or an encoding-specific class
 *       (HashID, Datum, ...). Carries both the typed Object and pure-semantic
 *       bytes for hashing.</li>
 *   <li>{@link Array} — ordered children, indexed by position.</li>
 *   <li>{@link Map} — keyed children, where each child is an {@link Entry} of
 *       (key Node, value Node). The codec is responsible for emitting entries
 *       in canonical key order.</li>
 * </ul>
 *
 * <p>Pattern-matching is the intended consumption style:
 *
 * <pre>{@code
 * return switch (node) {
 *     case Leaf l    -> hashLeaf(l);
 *     case Array a   -> hashArray(a);
 *     case Map m     -> hashMap(m);
 * };
 * }</pre>
 *
 * <p>The tree captures structural identity. Two encoded forms of the same
 * semantic value (different CBOR layouts, different encoding versions) produce
 * the same {@code Node} tree. Encoding-level framing (tag wrappers, length
 * prefixes, byte-string headers) is the codec's concern and never appears in
 * the tree.
 */
public sealed interface Node {

    /**
     * Terminal node carrying a pre-computed hash. Returned by the walker for
     * Merkle elision markers (redactions) and any other case where the hash
     * of an opaque subtree is known without its content. The Merkle walker
     * returns this node's hash bytes verbatim, never re-hashing them — that
     * is precisely what preserves the parent's Merkle root across redactions.
     *
     * <p>{@code hash} bytes must be the exact bytes the original subtree
     * would have produced under the same Merkle-walk protocol; the redactor
     * is responsible for computing them at redaction time.
     */
    record Hashed(byte[] hash) implements Node {
        public Hashed {
            Objects.requireNonNull(hash, "hash");
        }
    }

    /**
     * A single typed value. Carries both:
     *
     * <ul>
     *   <li>{@link #value()} — the typed Object, for consumers that want to do
     *       something semantic with it. Encodings should produce the standard
     *       primitives ({@link String}, {@link Long}, {@link Boolean},
     *       {@code byte[]}, {@link java.time.Instant},
     *       {@link dev.everydaythings.graph.value.Decimal},
     *       {@link dev.everydaythings.graph.value.Rational}) where applicable,
     *       and may additionally produce encoding-specific value classes
     *       (e.g., {@link HashID},
     *       {@link dev.everydaythings.graph.datum.Datum}).</li>
     *   <li>{@link #rawBytes()} — the pure-semantic byte form, for hashing. May
     *       include the codec's own type discriminator bytes when needed to
     *       distinguish kinds of leaves whose underlying bytes might otherwise
     *       collide.</li>
     * </ul>
     *
     * <p>{@code value} may be {@code null} (e.g., for a CBOR-null leaf);
     * {@code rawBytes} is always non-null but may be empty.
     *
     * <p>The {@code byte[]} stored in a {@code Leaf} should be treated as
     * immutable; callers must not mutate it.
     */
    record Leaf(Object value, byte[] rawBytes) implements Node {
        public Leaf {
            Objects.requireNonNull(rawBytes, "rawBytes");
        }
    }

    /**
     * Ordered, positionally-indexed children. The codec emits elements in a
     * canonical order; consumers iterate that order and don't reorder.
     */
    record Array(List<Node> elements) implements Node {
        public Array {
            elements = List.copyOf(Objects.requireNonNull(elements, "elements"));
        }
    }

    /**
     * Keyed children — entries of (key, value). The codec emits entries in
     * canonical key order (for CG-CBOR, that's lexicographic over each key's
     * canonical encoding); consumers iterate that order.
     *
     * <p>Map keys are themselves Nodes — leaves or composites — and carry their
     * own semantic content. There is no separate "key type" channel; the key
     * Node tells the whole story.
     */
    record Map(List<Entry> entries) implements Node {
        public Map {
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        }
    }

    /**
     * A key/value pair inside a {@link Map}. Both sides are Nodes; either may
     * be a leaf or a composite. Entry is not itself a Node — it only exists
     * inside Maps and is not substitutable for a Node in other contexts.
     */
    record Entry(Node key, Node value) {
        public Entry {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
        }
    }

    // ==================================================================================
    // Static factories
    // ==================================================================================

    /** Construct a leaf carrying a typed value and its pure-semantic bytes. */
    static Leaf leaf(Object value, byte[] rawBytes) {
        return new Leaf(value, rawBytes);
    }

    /** Construct an array node from an ordered list of children. */
    static Array array(List<Node> elements) {
        return new Array(elements);
    }

    /** Construct an array node from a varargs sequence of children. */
    static Array array(Node... elements) {
        return new Array(List.of(elements));
    }

    /** Construct a map node from a list of entries. */
    static Map map(List<Entry> entries) {
        return new Map(entries);
    }

    /** Construct an entry from a key and value. */
    static Entry entry(Node key, Node value) {
        return new Entry(key, value);
    }
}
