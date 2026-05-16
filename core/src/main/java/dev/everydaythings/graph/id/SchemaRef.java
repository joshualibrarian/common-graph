package dev.everydaythings.graph.id;

import dev.everydaythings.graph.canonical.Decode;
import io.ipfs.multibase.Multibase;
import io.ipfs.multihash.Multihash;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Schema reference — references an IID in the item-id space with schema/expects
 * semantics: "instances of this IID look like the body that carries me."
 *
 * <p>Text form: {@code !<IID>}.  Binary form (inside CBOR Tag 6):
 * {@code 0x21 <multihash-IID>}.
 *
 * <p>A SchemaRef has the same wire shape as a bare {@link ItemRef} — just a
 * different prefix byte.  No version pinning.
 *
 * <p>Used at the head of a body to declare "this body is the schema for
 * instances of this IID" (whole-body scope), or as a binding's role to declare
 * "instances will carry a binding with this role" (binding scope).  Mutually
 * exclusive with {@link TypeRef} and literal {@link ItemRef} on any given
 * reference position.
 */
public final class SchemaRef extends HashID {

    public SchemaRef(Multihash multihash) {
        super(multihash);
    }

    public SchemaRef(byte[] serializedMultihash) {
        super(serializedMultihash);
    }

    public SchemaRef(byte[] rawDigest, Multihash.Type type) {
        super(rawDigest, type);
    }

    @Override
    public byte prefixByte() {
        return PREFIX_SCHEMA;
    }

    @Override
    public Variant variant() {
        return Variant.SCHEMA;
    }

    /** The underlying IID as an unpinned {@link ItemRef}. */
    public ItemRef iid() {
        return new ItemRef(multihash);
    }

    // ==================================================================================
    // Factories
    // ==================================================================================

    /** Wrap an existing ItemRef as a schema reference to the same IID. */
    public static SchemaRef of(ItemRef iid) {
        Objects.requireNonNull(iid, "iid");
        return new SchemaRef(iid.multihash);
    }

    /**
     * Cache of canonical-key → SchemaRef.  Lazily initialized via holder class
     * to avoid static-init ordering hazards.
     */
    private static final class FromStringCache {
        static final ConcurrentHashMap<String, SchemaRef> CACHE = new ConcurrentHashMap<>();
    }

    /**
     * Create a deterministic SchemaRef from a canonical-key string.  The IID is
     * derived the same way {@link ItemRef#fromString} derives it, so
     * {@code SchemaRef.fromString("K")} and {@code ItemRef.fromString("K")}
     * reference the same IID — only the operational mode differs.
     */
    public static SchemaRef fromString(String s) {
        Objects.requireNonNull(s, "s");
        return FromStringCache.CACHE.computeIfAbsent(s, SchemaRef::computeFromString);
    }

    /** Short alias for {@link #fromString(String)} — mirrors {@link ItemRef#iid(String)}. */
    public static SchemaRef iid(String key) {
        return fromString(key);
    }

    private static SchemaRef computeFromString(String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        return new SchemaRef(sha256(bytes), Multihash.Type.sha2_256);
    }

    // ==================================================================================
    // @Decode entry points
    // ==================================================================================

    @Decode
    public static SchemaRef fromBinary(byte[] refBytes) {
        return fromRefBytesPayload(refBytes);
    }

    @Decode
    public static SchemaRef fromText(String text) {
        return parseText(text);
    }

    // ==================================================================================
    // Wire decode helpers (package-private)
    // ==================================================================================

    static SchemaRef fromRefBytesPayload(byte[] bytes) {
        if (bytes.length < 1 || bytes[0] != PREFIX_SCHEMA) {
            throw new IllegalArgumentException("SchemaRef payload must start with '!' (0x21)");
        }
        HashID.Slice iidSlice = readMultihash(bytes, 1);
        if (iidSlice.next() != bytes.length) {
            throw new IllegalArgumentException("SchemaRef has unexpected trailing bytes");
        }
        return new SchemaRef(Multihash.deserialize(iidSlice.bytes()));
    }

    static SchemaRef parseText(String text) {
        if (text == null || text.isEmpty() || text.charAt(0) != '!') {
            throw new IllegalArgumentException("SchemaRef text must start with '!', got: " + text);
        }
        String body = text.substring(1);
        if (body.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("SchemaRef has no sub-parts, '\\' is not allowed");
        }
        return new SchemaRef(Multibase.decode(body));
    }

    // ==================================================================================
    // SHA-256 utility — protocol-pinned for canonical-key → IID derivation
    // ==================================================================================

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    @Override
    public String emoji() {
        return "❗";
    }
}
