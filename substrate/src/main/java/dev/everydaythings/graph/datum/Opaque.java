package dev.everydaythings.graph.datum;

import dev.everydaythings.graph.ref.HashID;

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
public sealed interface Opaque extends DatumNode permits Opaque.Redacted, Opaque.Compressed, Opaque.Encrypted {

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
    // Redacted — hash only.
    // ==================================================================================

    /**
     * Redacted — the subtree's content is hidden; only its structural hash
     * survives.  Optionally accompanied by record-refs explaining the
     * redaction (who/why/under what policy).
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
     * <p>See {@link dev.everydaythings.graph.encoding.Compress} for the
     * compress / decompress operations (which take a caller-supplied
     * encoding to turn the body into / out of bytes).
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

}
