package dev.everydaythings.graph.datum;


import dev.everydaythings.graph.id.*;
import java.util.Objects;

/**
 * Value type for frame binding targets.
 *
 * <p>A binding target is the value side of a role binding in a frame.
 * Implementations:
 * <ul>
 *   <li>{@link RefTarget} — unified reference (Tag 6) to any HashID (ItemRef or ContentRef)</li>
 *   <li>{@link IidTarget} — legacy reference to another Item (encodes as bare ByteString)</li>
 *   <li>{@link FrameTarget} — inline nested frame (Tag 23) for expression trees</li>
 *   <li>{@link RedactedTarget} — Merkle redaction marker (Tag 11) wrapping the
 *       hash of an elided subtree. Short-circuits during structural Merkle hashing.</li>
 *   <li>Raw Java values ({@link String}, {@link Long}, {@link Boolean},
 *       {@code byte[]}, {@link java.time.Instant}, etc.) — inline typed value</li>
 * </ul>
 */
public interface BindingTarget extends DatumNode {

    /**
     * Convenience factory for item references in bindings — produces a
     * {@link RefTarget} wrapping an {@link ItemRef}.
     */
    static RefTarget iid(ItemRef iid) { return new RefTarget(ItemRef.of(iid)); }

    /**
     * Convenience factory wrapping a {@link DatumRef} as a {@link DatumRef} —
     * common for ENDORSES bindings and version-id references.
     */
    static RefTarget ref(DatumRef datumId) { return new RefTarget(DatumRef.of(datumId)); }

    /**
     * Convenience factory wrapping a {@link ContentRef} as a {@link ContentRef}.
     */
    static RefTarget ref(ContentRef cid) { return new RefTarget(ContentRef.of(cid)); }

    /** Convenience factory wrapping any {@link HashID} (caller picks the variant). */
    static RefTarget ref(HashID reference) { return new RefTarget(reference); }

    /** Convenience factory for inline nested datums (Tag 12). */
    static FrameTarget frame(Body body) { return new FrameTarget(body); }

    /**
     * HashID-valued binding target — wraps a {@link HashID} (the sealed
     * {@link ItemRef}/{@link ContentRef}/{@link DatumRef} sum type).
     *
     * <p>Encoded as CG-CBOR Tag 6 wrapping the HashID's binary payload.
     *
     * <p>The convenience accessors return the underlying target of the
     * appropriate variant, or {@code null} when the variant doesn't match —
     * callers gate on {@link #isCompound()} or directly inspect
     * {@link #asReference()} when they need disambiguation.
     */
    final class RefTarget implements BindingTarget {
        private final HashID reference;

        public RefTarget(HashID reference) {
            this.reference = Objects.requireNonNull(reference, "reference");
        }

        /** The underlying HashID (use {@code instanceof} to disambiguate the variant). */
        public HashID asReference() { return reference; }

        /**
         * For an {@link ItemRef}, the target {@link ItemRef}; {@code null} otherwise.
         */
        public ItemRef asItemId() {
            return reference instanceof ItemRef ir ? ir.iid() : null;
        }

        /**
         * For a {@link ContentRef}, the target {@link ContentRef}; {@code null} otherwise.
         */
        public ContentRef asCid() {
            return reference instanceof ContentRef cr ? cr.cid() : null;
        }

        /**
         * For a {@link DatumRef}, the body's {@link DatumRef}; {@code null} otherwise.
         */
        public DatumRef asDatumId() {
            return reference instanceof DatumRef fr ? fr.bodyId() : null;
        }

        /**
         * Whether this is anything other than a bare unpinned {@link ItemRef}.
         *
         * <p>True for ItemRefs pinned to a version, ContentRefs, and FrameRefs.
         * False for the common case: a bare item reference with no version/key/portion.
         */
        public boolean isCompound() {
            if (reference instanceof ItemRef ir) return ir.isPinned();
            return true;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RefTarget other)) return false;
            return Objects.equals(reference, other.reference);
        }

        @Override
        public int hashCode() {
            return Objects.hash(reference);
        }

        @Override
        public String toString() {
            return "Ref(" + reference.encodeText() + ")";
        }
    }

    /**
     * Inline nested datum — enables expression trees and compositional frames.
     *
     * <p>Encodes as CG-CBOR Tag 12 (DATUM) wrapping the inner body's CBOR. Same
     * tag as outer-level Body/Record encoding — the wire shape of an inline
     * datum and an outer datum is identical, and decoders dispatch by context.
     */
    final class FrameTarget implements BindingTarget {
        private final Body body;

        public FrameTarget(Body body) {
            this.body = Objects.requireNonNull(body, "body");
        }

        public Body body() { return body; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FrameTarget other)) return false;
            return Objects.equals(body, other.body);
        }

        @Override
        public int hashCode() {
            return Objects.hash(body);
        }

        @Override
        public String toString() {
            return "Frame(" + body + ")";
        }
    }

}
