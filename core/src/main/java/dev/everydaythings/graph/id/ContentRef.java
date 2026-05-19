package dev.everydaythings.graph.id;

import dev.everydaythings.graph.canonical.Decode;
import dev.everydaythings.graph.identity.algorithm.Hash;
import io.ipfs.multibase.Multibase;
import io.ipfs.multihash.Multihash;

import java.util.Objects;

/**
 * HashID to raw content, addressed by content hash.
 *
 * <p>Text form: {@code ~<CID>}.
 * <p>Binary form (inside CBOR Tag 6): {@code 0x7E <multihash-CID>}.
 *
 * <p>Used for opaque content blobs — bytes addressed solely by their hash, with no
 * structural interpretation. For structured frame bodies, use {@link DatumRef}.
 */
public final class ContentRef extends HashID {

    public ContentRef(Multihash multihash) {
        super(multihash);
    }

    public ContentRef(byte[] serializedMultihash) {
        super(serializedMultihash);
    }

    public ContentRef(byte[] rawDigest, Multihash.Type type) {
        super(rawDigest, type);
    }

    @Override
    public byte prefixByte() {
        return PREFIX_CONTENT;
    }

    @Override
    public Variant variant() {
        return Variant.CONTENT;
    }

    /** Self-reference (kept for call-site compatibility with the legacy {@code cid()} accessor). */
    public ContentRef cid() {
        return this;
    }

    // ==================================================================================
    // Factories
    // ==================================================================================

    /** Pass-through factory (kept for migration-time call-site compatibility). */
    public static ContentRef of(ContentRef cid) {
        return cid;
    }

    /**
     * Create a ContentRef by hashing raw content bytes.
     * Uses {@link Hash.Sha256} — the protocol-pinned default for content addressing.
     */
    public static ContentRef of(byte[] content) {
        Objects.requireNonNull(content, "content");
        byte[] digest = Hash.Sha256.digestOf(content);
        return new ContentRef(digest, Hash.Sha256.MULTIHASH_TYPE);
    }

    /** Parse a content ref from text, guessing the format. */
    public static ContentRef bestGuess(String token) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("empty content token");
        }
        String t = token.startsWith("~") ? token.substring(1) : token;
        return new ContentRef(Multihash.deserialize(Multibase.decode(t)).toBytes());
    }

    // ==================================================================================
    // @Decode entry points
    // ==================================================================================

    @Decode
    public static ContentRef fromBinary(byte[] refBytes) {
        return fromRefBytesPayload(refBytes);
    }

    @Decode
    public static ContentRef fromText(String text) {
        return parseText(text);
    }

    // ==================================================================================
    // Wire decode helpers (package-private)
    // ==================================================================================

    static ContentRef fromRefBytesPayload(byte[] bytes) {
        if (bytes.length < 1 || bytes[0] != PREFIX_CONTENT) {
            throw new IllegalArgumentException("ContentRef payload must start with '~' (0x7E)");
        }
        HashID.Slice cidSlice = readMultihash(bytes, 1);
        if (cidSlice.next() != bytes.length) {
            throw new IllegalArgumentException("ContentRef has unexpected trailing bytes");
        }
        return new ContentRef(Multihash.deserialize(cidSlice.bytes()));
    }

    static ContentRef parseText(String text) {
        if (text == null || text.isEmpty() || text.charAt(0) != '~') {
            throw new IllegalArgumentException("ContentRef text must start with '~', got: " + text);
        }
        String body = text.substring(1);
        if (body.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("ContentRef has no sub-parts, '\\' is not allowed");
        }
        return new ContentRef(Multibase.decode(body));
    }

    @Override
    public String emoji() {
        return "📄";
    }
}
