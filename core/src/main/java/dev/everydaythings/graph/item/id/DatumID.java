package dev.everydaythings.graph.item.id;

import dev.everydaythings.graph.encoding.Digest;
import io.ipfs.multibase.Multibase;
import io.ipfs.multihash.Multihash;

/**
 * The semantic identity of a Datum — its structural Merkle hash.
 *
 * <p>Distinct from {@link ContentID}, which is the hash of a Datum's canonical
 * bytes:
 *
 * <ul>
 *   <li><b>ContentID</b> = hash of the canonical CBOR bytes. Universal across
 *       all stored blobs (Datums and opaque content alike). The storage key for
 *       OBJECTS. Different per wire-form: redacted bytes hash to a different
 *       ContentID than full bytes.</li>
 *   <li><b>DatumID</b> = recursive structural Merkle hash over the Datum's
 *       components. Specific to Datums (bodies and records). Stable across
 *       redactions: when a subtree is replaced by a Tag-11 marker carrying its
 *       hash, the Merkle walk short-circuits at the marker and produces the
 *       same root.</li>
 * </ul>
 *
 * <p>References to bodies in the semantic graph use DatumID — signatures sign
 * over it, and downstream frames reference bodies by it. Storage internally
 * keys bytes by ContentID. The two-tier addressing is bridged by the
 * {@code DATUM_INDEX} column in the IndexStore.
 *
 * <p>Non-Datum content (images, opaque blobs) has only a ContentID, never a
 * DatumID — DatumID is meaningful only for Datums (predicate+bindings
 * structures).
 */
public final class DatumID extends HashID {
    public static final String DATUM_PREFIX = "#";
    public static final String PREFIX = DATUM_PREFIX;

    public DatumID(byte[] rawDigest, Multihash.Type type) {
        super(rawDigest, type);
    }

    public DatumID(byte[] serializedMultihash) {
        super(serializedMultihash);
    }

    /**
     * Create a DatumID by hashing arbitrary bytes — primarily for tests and
     * placeholder values. Production code builds DatumIDs via the structural
     * Merkle walk in {@code Datum.datumId()}.
     */
    public static DatumID of(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes cannot be null");
        }
        byte[] digest = Digest.Sha256.digest(bytes);
        return new DatumID(digest, Digest.Sha256.MULTIHASH_TYPE);
    }

    /** Parse a DatumID from text, guessing the format. */
    public static DatumID bestGuess(String token) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("empty datum token");
        }
        String t = token.startsWith(PREFIX) ? token.substring(PREFIX.length()) : token;
        return new DatumID(Multihash.deserialize(Multibase.decode(t)).toBytes());
    }

    @Override
    public String prefix() {
        return DATUM_PREFIX;
    }

    @Override
    public String emoji() {
        return "🔷";  // Diamond — structural semantic identity
    }
}
