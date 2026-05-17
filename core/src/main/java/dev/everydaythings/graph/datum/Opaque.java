package dev.everydaythings.graph.datum;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.canonical.Factory;
import dev.everydaythings.graph.encoding.CgCbor;
import dev.everydaythings.graph.id.HashID;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Opaque — a merkle-preserving stand-in for a subtree of a body.
 *
 * <p>Three flavors, all carrying the same structural identity ({@link
 * #wrappedHash() wrappedHash}) plus an optional list of {@link #recordRefs()
 * recordRefs} pointing at records that explain the opacity:
 *
 * <ul>
 *   <li>{@link Redacted} — the subtree is hidden.  Carries only the hash.</li>
 *   <li>{@link Compressed} — the subtree is present but deflated.  Carries
 *       the hash plus the compressed payload bytes.</li>
 *   <li>{@link Encrypted} — the subtree is present but encrypted.  Carries
 *       the hash plus the ciphertext bytes.  Record-refs point at records
 *       whose HEAD is the {@code ~cid} of the ciphertext; those records
 *       carry the algorithm, recipient declarations, and wrapped keys.</li>
 * </ul>
 *
 * <h2>Where Opaque can sit</h2>
 *
 * <p>An Opaque can replace any node in a body's canonical tree.  Three
 * positions are wired today:
 *
 * <ul>
 *   <li><b>Binding target</b> — the value a binding points at.  Today's
 *       only case before this refactor.</li>
 *   <li><b>A single binding in a body's bindings list</b> — hides both the
 *       role and the target of one binding.  Closes the role-leak gap that
 *       target-only redaction had.</li>
 *   <li><b>A single qualifier inside a {@code CompoundKey}</b> — hides one
 *       qualifier sememe while keeping the rest of the role visible.</li>
 * </ul>
 *
 * <p>TODO: Opaque could also sit at a body's head, replace a whole role
 * (the entire {@code CompoundKey}), or replace the bindings array
 * wholesale.  None of those positions are wired today — they're uncommon
 * cases that can be added when concrete use-cases emerge.
 *
 * <h2>Hash semantics</h2>
 *
 * <p>An Opaque's contribution to its parent's structural Merkle hash is
 * exactly {@code Node.Hashed(wrappedHash)} — the hash of the subtree it
 * stands in for.  Two Opaques with the same {@code wrappedHash} but
 * different {@code recordRefs} produce the same parent-hash contribution.
 *
 * <p>This is deliberate.  Record-refs are <b>not</b> part of the structural
 * identity; they're mutable hints (a new recipient's decryption-record can
 * be added without breaking signatures on the parent body).  Trust comes
 * from each record's own signature and its declared subject, not from the
 * refs list.
 */
public sealed interface Opaque permits Opaque.Redacted, Opaque.Compressed, Opaque.Encrypted {

    /**
     * The structural Merkle hash (raw multihash bytes) of the original
     * subtree this Opaque stands in for.  CanonWalker uses this directly:
     * an Opaque short-circuits to {@code Node.Hashed(wrappedHash)} so the
     * parent's hash is invariant across opacity.
     */
    byte[] wrappedHash();

    /**
     * Records that explain this opacity — most commonly {@code #datumID}
     * refs to records whose bindings declare who/why (for Redacted), who
     * compressed and the original signer (for Compressed), or the
     * algorithm/recipients/wrapped-keys (for Encrypted).
     *
     * <p>Empty by default.  The list is a hint for fast lookup; receivers
     * should still consult the librarian's index for records targeting this
     * Opaque, in case the sender hadn't published every relevant record at
     * encoding time.
     */
    List<HashID> recordRefs();

    // ==================================================================================
    // CBOR dispatch — package factory used by BindingTarget.fromCborTree and others.
    // ==================================================================================

    /**
     * Decode an Opaque from its CBOR form.  Dispatches by tag to the
     * appropriate variant's {@code fromCborTree}.
     */
    @Factory
    static Opaque fromCborTree(CBORObject node) {
        if (node == null || node.isNull()) {
            throw new IllegalArgumentException("Cannot decode Opaque from null CBOR node");
        }
        if (!node.isTagged()) {
            throw new IllegalArgumentException("Opaque requires a tagged CBOR value");
        }
        int tag = node.getMostOuterTag().ToInt32Checked();
        return switch (tag) {
            case CgCbor.TAG_REDACTED   -> Redacted.fromCborTree(node);
            case CgCbor.TAG_COMPRESSED -> Compressed.fromCborTree(node);
            case CgCbor.TAG_ENCRYPTED  -> Encrypted.fromCborTree(node);
            default -> throw new IllegalArgumentException(
                    "Not an Opaque CBOR tag: " + tag);
        };
    }

    /** Whether the given tag is one of the Opaque variants' tags. */
    static boolean isOpaqueTag(int tag) {
        return tag == CgCbor.TAG_REDACTED
                || tag == CgCbor.TAG_COMPRESSED
                || tag == CgCbor.TAG_ENCRYPTED;
    }

    // ==================================================================================
    // Redacted — hash only.
    // ==================================================================================

    /**
     * Redacted — the subtree's content is hidden; only its structural hash
     * survives.  Optionally accompanied by record-refs explaining the
     * redaction (who/why/under what policy).
     *
     * <p>Wire form: {@code Tag(12)[Bytes(wrappedHash), Array(recordRefs)]} — 2
     * elements (the tag disambiguates from Compressed and Encrypted, which
     * are 3-element).
     */
    final class Redacted implements Opaque {
        private final byte[] wrappedHash;
        private final List<HashID> recordRefs;

        public Redacted(byte[] wrappedHash, List<HashID> recordRefs) {
            this.wrappedHash = Objects.requireNonNull(wrappedHash, "wrappedHash").clone();
            this.recordRefs = recordRefs == null
                    ? List.of()
                    : Collections.unmodifiableList(new ArrayList<>(recordRefs));
        }

        public Redacted(byte[] wrappedHash) {
            this(wrappedHash, List.of());
        }

        @Override public byte[] wrappedHash() { return wrappedHash.clone(); }
        @Override public List<HashID> recordRefs() { return recordRefs; }

        @Factory
        public static Redacted fromCborTree(CBORObject node) {
            Objects.requireNonNull(node, "node");
            if (!node.isTagged() || !node.HasMostOuterTag(CgCbor.TAG_REDACTED)) {
                throw new IllegalArgumentException("Redacted requires Tag(" + CgCbor.TAG_REDACTED + ")");
            }
            CBORObject inner = node.UntagOne();
            return new Redacted(readHash(inner, "Redacted"), readRecordRefs(inner, "Redacted", 1));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Redacted other)) return false;
            return Arrays.equals(wrappedHash, other.wrappedHash)
                    && recordRefs.equals(other.recordRefs);
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(wrappedHash) + recordRefs.hashCode();
        }

        @Override
        public String toString() {
            return "Redacted{hash=" + wrappedHash.length + "B, refs=" + recordRefs.size() + "}";
        }
    }

    // ==================================================================================
    // Compressed — hash + deflated payload.
    // ==================================================================================

    /**
     * Compressed — the subtree's content survives as deflated bytes; the
     * structural hash matches what the uncompressed subtree would produce.
     * Optionally accompanied by record-refs explaining the compression (who
     * compressed, and optionally the original signer's signature so the
     * decompressed form authenticates).
     *
     * <p>Wire form: {@code Tag(14)[Bytes(wrappedHash), Bytes(deflated),
     * Array(recordRefs)]} — 3 elements.
     */
    final class Compressed implements Opaque {
        private final byte[] wrappedHash;
        private final byte[] compressedPayload;
        private final List<HashID> recordRefs;

        public Compressed(byte[] wrappedHash, byte[] compressedPayload, List<HashID> recordRefs) {
            this.wrappedHash = Objects.requireNonNull(wrappedHash, "wrappedHash").clone();
            this.compressedPayload =
                    Objects.requireNonNull(compressedPayload, "compressedPayload").clone();
            this.recordRefs = recordRefs == null
                    ? List.of()
                    : Collections.unmodifiableList(new ArrayList<>(recordRefs));
        }

        public Compressed(byte[] wrappedHash, byte[] compressedPayload) {
            this(wrappedHash, compressedPayload, List.of());
        }

        @Override public byte[] wrappedHash() { return wrappedHash.clone(); }
        public byte[] compressedPayload() { return compressedPayload.clone(); }
        @Override public List<HashID> recordRefs() { return recordRefs; }

        @Factory
        public static Compressed fromCborTree(CBORObject node) {
            Objects.requireNonNull(node, "node");
            if (!node.isTagged() || !node.HasMostOuterTag(CgCbor.TAG_COMPRESSED)) {
                throw new IllegalArgumentException("Compressed requires Tag(" + CgCbor.TAG_COMPRESSED + ")");
            }
            CBORObject inner = node.UntagOne();
            byte[] hash = readHash(inner, "Compressed");
            byte[] payload = readBytes(inner, 1, "Compressed.payload");
            return new Compressed(hash, payload, readRecordRefs(inner, "Compressed", 2));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Compressed other)) return false;
            return Arrays.equals(wrappedHash, other.wrappedHash)
                    && Arrays.equals(compressedPayload, other.compressedPayload)
                    && recordRefs.equals(other.recordRefs);
        }

        @Override
        public int hashCode() {
            int h = Arrays.hashCode(wrappedHash);
            h = 31 * h + Arrays.hashCode(compressedPayload);
            h = 31 * h + recordRefs.hashCode();
            return h;
        }

        @Override
        public String toString() {
            return "Compressed{hash=" + wrappedHash.length + "B, payload=" + compressedPayload.length
                    + "B, refs=" + recordRefs.size() + "}";
        }
    }

    // ==================================================================================
    // Encrypted — hash + ciphertext.
    // ==================================================================================

    /**
     * Encrypted — the subtree's content survives as ciphertext; the
     * structural hash matches what the plaintext subtree would produce.
     * Record-refs point at records whose HEAD identifies the ciphertext
     * (typically {@code ~cid(ciphertext)}); those records carry the
     * algorithm, recipient declarations, and wrapped content-keys.
     *
     * <p>Wire form: {@code Tag(13)[Bytes(wrappedHash), Bytes(ciphertext),
     * Array(recordRefs)]} — 3 elements.
     */
    final class Encrypted implements Opaque {
        private final byte[] wrappedHash;
        private final byte[] ciphertext;
        private final List<HashID> recordRefs;

        public Encrypted(byte[] wrappedHash, byte[] ciphertext, List<HashID> recordRefs) {
            this.wrappedHash = Objects.requireNonNull(wrappedHash, "wrappedHash").clone();
            this.ciphertext = Objects.requireNonNull(ciphertext, "ciphertext").clone();
            this.recordRefs = recordRefs == null
                    ? List.of()
                    : Collections.unmodifiableList(new ArrayList<>(recordRefs));
        }

        public Encrypted(byte[] wrappedHash, byte[] ciphertext) {
            this(wrappedHash, ciphertext, List.of());
        }

        @Override public byte[] wrappedHash() { return wrappedHash.clone(); }
        public byte[] ciphertext() { return ciphertext.clone(); }
        @Override public List<HashID> recordRefs() { return recordRefs; }

        @Factory
        public static Encrypted fromCborTree(CBORObject node) {
            Objects.requireNonNull(node, "node");
            if (!node.isTagged() || !node.HasMostOuterTag(CgCbor.TAG_ENCRYPTED)) {
                throw new IllegalArgumentException("Encrypted requires Tag(" + CgCbor.TAG_ENCRYPTED + ")");
            }
            CBORObject inner = node.UntagOne();
            byte[] hash = readHash(inner, "Encrypted");
            byte[] cipher = readBytes(inner, 1, "Encrypted.ciphertext");
            return new Encrypted(hash, cipher, readRecordRefs(inner, "Encrypted", 2));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Encrypted other)) return false;
            return Arrays.equals(wrappedHash, other.wrappedHash)
                    && Arrays.equals(ciphertext, other.ciphertext)
                    && recordRefs.equals(other.recordRefs);
        }

        @Override
        public int hashCode() {
            int h = Arrays.hashCode(wrappedHash);
            h = 31 * h + Arrays.hashCode(ciphertext);
            h = 31 * h + recordRefs.hashCode();
            return h;
        }

        @Override
        public String toString() {
            return "Encrypted{hash=" + wrappedHash.length + "B, cipher=" + ciphertext.length
                    + "B, refs=" + recordRefs.size() + "}";
        }
    }

    // ==================================================================================
    // Shared CBOR-read helpers.
    // ==================================================================================

    private static byte[] readHash(CBORObject inner, String variant) {
        if (inner.getType() != com.upokecenter.cbor.CBORType.Array || inner.size() < 1) {
            throw new IllegalArgumentException(
                    variant + " inner must be a CBOR array with at least 1 element");
        }
        return readBytes(inner, 0, variant + ".wrappedHash");
    }

    private static byte[] readBytes(CBORObject inner, int index, String label) {
        if (inner.size() <= index) {
            throw new IllegalArgumentException(label + ": missing element at index " + index);
        }
        CBORObject element = inner.get(index);
        if (element.getType() != com.upokecenter.cbor.CBORType.ByteString) {
            throw new IllegalArgumentException(
                    label + ": expected ByteString, got " + element.getType());
        }
        return element.GetByteString();
    }

    /**
     * Read the optional record-refs array from position {@code index} of an
     * Opaque variant's inner CBOR array.  Empty list when the position is
     * absent or contains an empty array; throws on malformed entries.
     */
    private static List<HashID> readRecordRefs(CBORObject inner, String variant, int index) {
        if (inner.size() <= index) return List.of();
        CBORObject array = inner.get(index);
        if (array.isNull()) return List.of();
        if (array.getType() != com.upokecenter.cbor.CBORType.Array) {
            throw new IllegalArgumentException(
                    variant + ".recordRefs: expected Array, got " + array.getType());
        }
        if (array.size() == 0) return List.of();
        List<HashID> refs = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            CBORObject element = array.get(i);
            if (!element.isTagged() || !element.HasMostOuterTag(CgCbor.TAG_REF)) {
                throw new IllegalArgumentException(
                        variant + ".recordRefs[" + i + "]: expected Tag("
                                + CgCbor.TAG_REF + ") ref, got " + element);
            }
            refs.add(HashID.fromCborTree(element));
        }
        return Collections.unmodifiableList(refs);
    }
}
