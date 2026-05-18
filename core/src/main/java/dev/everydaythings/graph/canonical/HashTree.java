package dev.everydaythings.graph.canonical;

import dev.everydaythings.graph.encoding.Digest;
import io.ipfs.multihash.Multihash;
import lombok.experimental.UtilityClass;

import java.util.Arrays;
import java.util.Comparator;

/**
 * The structural-identity protocol — recursive Merkle hashing over a
 * {@link Node} tree, with a canonical comparator for ordering multisets.
 *
 * <p>HashTree is the single authority for "what's the structural identity of
 * a value." Given any {@link Node} tree — produced by the {@link CanonWalker} —
 * and a hash algorithm, it computes a deterministic digest. The result is
 * <b>encoding-invariant</b>: the same semantic value produces the same digest
 * under any encoder (CG-CBOR, a hypothetical CG-JSON, etc.) because all
 * encoders walk values into the same Node tree.
 *
 * <p>The hashing rules:
 *
 * <pre>
 * leaf(rawBytes)          = digest(algo, [LEAF_KIND]   || rawBytes)
 * array([n0, n1, ...])    = digest(algo, [ARRAY_KIND]  || hash(n0) || hash(n1) || ...)
 * map([(k0,v0), ...])     = digest(algo, [MAP_KIND]    || entryHash(k0,v0) || ...)
 * entryHash(k, v)         = digest(algo, [ENTRY_KIND]  || hash(k) || hash(v))
 * hashed(precomputed)     = precomputed  (verbatim — preserves redaction)
 * </pre>
 *
 * <p>The 1-byte kind prefix preserves the structural distinction between an
 * array {@code [k, v]} and a map {@code {k: v}}: they hash differently even
 * when they contain the same elements.
 *
 * <p>The returned bytes are the algorithm's raw digest output (e.g., 32 bytes
 * for SHA-256). External callers typically wrap the result in multihash
 * framing to produce an algorithm-self-describing identifier.
 */
@UtilityClass
public final class HashTree {

    /**
     * Default Merkle-walk algorithm. SHA-256 is the network protocol commitment
     * for now; the multihash framing on the resulting DatumRef self-describes
     * the algorithm so future migrations remain unambiguous.
     */
    public static final Multihash.Type DEFAULT_DIGEST = Multihash.Type.sha2_256;

    /**
     * Kind discriminator prefixes embedded in the hashed material at every
     * composition level. Stable forever; changing one rotates every DatumRef
     * that contains that kind of node.
     */
    public static final byte KIND_LEAF  = 0x00;
    public static final byte KIND_ARRAY = 0x01;
    public static final byte KIND_MAP   = 0x02;
    public static final byte KIND_ENTRY = 0x03;

    /**
     * Canonical comparator — bitwise comparison of two values' structural
     * hashes under the default algorithm. Stable, deterministic, encoder-
     * independent. Used to sort multisets (binding qualifiers, bindings on a
     * Datum, etc.) into canonical order.
     *
     * <p>Sorting is on the 32-byte (for SHA-256) digest output, NOT the
     * variable-length semantic bytes — fixed-length keys give clean bitwise
     * ordering with no prefix-domination weirdness.
     */
    public static final Comparator<Object> CANONICAL =
            (a, b) -> Arrays.compareUnsigned(
                    hashOf(a, DEFAULT_DIGEST),
                    hashOf(b, DEFAULT_DIGEST));

    // ==================================================================================
    // Public API
    // ==================================================================================

    /**
     * Compute the structural Merkle digest of a Node tree under the given
     * hash algorithm. Returns the raw digest bytes; no multihash framing.
     */
    public static byte[] hash(Node node, Multihash.Type algo) {
        return switch (node) {
            case Node.Leaf l    -> Digest.compute(algo, prefix(KIND_LEAF, l.rawBytes()));
            case Node.Array a   -> Digest.compute(algo, prefix(KIND_ARRAY, concatHashes(a.elements(), algo)));
            case Node.Map m     -> Digest.compute(algo, prefix(KIND_MAP, concatEntryHashes(m.entries(), algo)));
            case Node.Hashed h  -> h.hash().clone();
        };
    }

    /**
     * Compute the structural hash of any value: walks it via {@link CanonWalker}
     * and hashes the resulting Node tree. The convenience entry point most
     * callers want.
     */
    public static byte[] hashOf(Object value, Multihash.Type algo) {
        return hash(CanonWalker.walk(value), algo);
    }

    /**
     * The bytes a signer signs over (or a verifier verifies against) for a
     * Datum. Always the body-part Merkle root — head + bindings, with NO
     * signature element regardless of whether the Datum is a {@code Body} or
     * a {@code Record}. A signature attests body content; including the
     * signature in what's signed would be circular.
     */
    public static byte[] signingPayload(dev.everydaythings.graph.datum.Datum datum) {
        return hash(CanonWalker.walkBodyPart(datum), DEFAULT_DIGEST);
    }

    // ==================================================================================
    // Internal — composition
    // ==================================================================================

    private static byte[] concatHashes(Iterable<Node> nodes, Multihash.Type algo) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (Node child : nodes) out.writeBytes(hash(child, algo));
        return out.toByteArray();
    }

    private static byte[] concatEntryHashes(Iterable<Node.Entry> entries, Multihash.Type algo) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (Node.Entry e : entries) out.writeBytes(entryHash(e, algo));
        return out.toByteArray();
    }

    private static byte[] entryHash(Node.Entry e, Multihash.Type algo) {
        java.io.ByteArrayOutputStream payload = new java.io.ByteArrayOutputStream();
        payload.writeBytes(hash(e.key(), algo));
        payload.writeBytes(hash(e.value(), algo));
        return Digest.compute(algo, prefix(KIND_ENTRY, payload.toByteArray()));
    }

    private static byte[] prefix(byte kind, byte[] payload) {
        byte[] out = new byte[payload.length + 1];
        out[0] = kind;
        System.arraycopy(payload, 0, out, 1, payload.length);
        return out;
    }
}
