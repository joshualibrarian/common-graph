package dev.everydaythings.graph.ref;

import dev.everydaythings.graph.canonical.Decode;
import dev.everydaythings.graph.cryptography.algorithm.Hash;
import io.ipfs.multibase.Multibase;
import io.ipfs.multihash.Multihash;

import java.util.Objects;

/**
 * HashID to a specific Datum body by its semantic Merkle identity.
 *
 * <p>Text form: {@code #<DatumRef>}.
 * <p>Binary form (inside CBOR Tag 6): {@code 0x23 <multihash-DatumRef>}.
 *
 * <p>DatumRef = multihash of the structural Merkle walk over a Datum's head +
 * bindings (excluding signature). It's invariant under re-encoding and
 * redaction — a DatumRef keeps pointing at the same semantic body no matter
 * how its bytes are realized.
 */
public final class DatumRef extends HashID {

    public DatumRef(Multihash multihash) {
        super(multihash);
    }

    public DatumRef(byte[] serializedMultihash) {
        super(serializedMultihash);
    }

    public DatumRef(byte[] rawDigest, Multihash.Type type) {
        super(rawDigest, type);
    }

    @Override
    public byte prefixByte() {
        return PREFIX_DATUM;
    }

    @Override
    public Variant variant() {
        return Variant.DATUM;
    }

    /** Self-reference (kept for call-site compatibility with the legacy {@code bodyId()} accessor). */
    public DatumRef bodyId() {
        return this;
    }

    // ==================================================================================
    // Factories
    // ==================================================================================

    /** Pass-through factory (call-site compatibility during migration). */
    public static DatumRef of(DatumRef did) {
        return did;
    }

    /**
     * Create a DatumRef by hashing arbitrary bytes — primarily for tests and
     * placeholder values. Production datums get their DatumRef via the
     * structural Merkle walk in {@code Datum.datumId()}.
     */
    public static DatumRef of(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        byte[] digest = Hash.Sha256.digestOf(bytes);
        return new DatumRef(digest, Hash.Sha256.MULTIHASH_TYPE);
    }

    /** Parse a DatumRef from text, guessing the format. */
    public static DatumRef bestGuess(String token) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("empty datum token");
        }
        String t = token.startsWith("#") ? token.substring(1) : token;
        return new DatumRef(Multihash.deserialize(Multibase.decode(t)).toBytes());
    }

    // ==================================================================================
    // @Decode entry points
    // ==================================================================================

    @Decode
    public static DatumRef fromBinary(byte[] refBytes) {
        return fromRefBytesPayload(refBytes);
    }

    @Decode
    public static DatumRef fromText(String text) {
        return parseText(text);
    }

    // ==================================================================================
    // Wire decode helpers (package-private)
    // ==================================================================================

    static DatumRef fromRefBytesPayload(byte[] bytes) {
        if (bytes.length < 1 || bytes[0] != PREFIX_DATUM) {
            throw new IllegalArgumentException("DatumRef payload must start with '#' (0x23)");
        }
        HashID.Slice slice = readMultihash(bytes, 1);
        if (slice.next() != bytes.length) {
            throw new IllegalArgumentException(
                    "DatumRef payload must contain exactly one DatumRef multihash");
        }
        return new DatumRef(Multihash.deserialize(slice.bytes()));
    }

    static DatumRef parseText(String text) {
        if (text == null || text.isEmpty() || text.charAt(0) != '#') {
            throw new IllegalArgumentException("DatumRef text must start with '#', got: " + text);
        }
        String body = text.substring(1);
        return new DatumRef(Multibase.decode(body));
    }

    @Override
    public String emoji() {
        return "🔷";
    }
}
