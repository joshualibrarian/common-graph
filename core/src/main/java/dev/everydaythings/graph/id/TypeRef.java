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
 * Query reference — references an IID in the item-id space with query semantics:
 * "match any body whose head is in this IID's archetype/predicate hierarchy."
 *
 * <p>Text form: {@code ?<IID>}.  Binary form (inside CBOR Tag 6):
 * {@code 0x3F <multihash-IID>}.
 *
 * <p>A TypeRef has the same wire shape as a bare {@link ItemRef} — just a
 * different prefix byte.  No version pinning; queries don't pin to a specific
 * version of an archetype.
 *
 * <p>TypeRef is mutually exclusive with {@link SchemaRef} and the literal
 * {@link ItemRef} on any given reference position.  Picking one determines the
 * operational mode: literal (match exactly), query (match the hierarchy), or
 * schema (declare instance shape).
 */
public final class TypeRef extends HashID {

    public TypeRef(Multihash multihash) {
        super(multihash);
    }

    public TypeRef(byte[] serializedMultihash) {
        super(serializedMultihash);
    }

    public TypeRef(byte[] rawDigest, Multihash.Type type) {
        super(rawDigest, type);
    }

    @Override
    public byte prefixByte() {
        return PREFIX_TYPE;
    }

    @Override
    public Variant variant() {
        return Variant.TYPE;
    }

    /** The underlying IID as an unpinned {@link ItemRef}. */
    public ItemRef iid() {
        return new ItemRef(multihash);
    }

    // ==================================================================================
    // Factories
    // ==================================================================================

    /** Wrap an existing ItemRef as a query reference to the same IID. */
    public static TypeRef of(ItemRef iid) {
        Objects.requireNonNull(iid, "iid");
        return new TypeRef(iid.multihash);
    }

    /**
     * Cache of canonical-key → TypeRef.  Lazily initialized via holder class to
     * avoid static-init ordering hazards.
     */
    private static final class FromStringCache {
        static final ConcurrentHashMap<String, TypeRef> CACHE = new ConcurrentHashMap<>();
    }

    /**
     * Create a deterministic TypeRef from a canonical-key string.  The IID is
     * derived the same way {@link ItemRef#fromString} derives it (SHA-256 of
     * the UTF-8 bytes), so {@code TypeRef.fromString("K")} and
     * {@code ItemRef.fromString("K")} reference the same IID — only the
     * operational mode (query vs literal) differs.
     */
    public static TypeRef fromString(String s) {
        Objects.requireNonNull(s, "s");
        return FromStringCache.CACHE.computeIfAbsent(s, TypeRef::computeFromString);
    }

    /** Short alias for {@link #fromString(String)} — mirrors {@link ItemRef#iid(String)}. */
    public static TypeRef iid(String key) {
        return fromString(key);
    }

    private static TypeRef computeFromString(String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        return new TypeRef(sha256(bytes), Multihash.Type.sha2_256);
    }

    // ==================================================================================
    // @Decode entry points
    // ==================================================================================

    @Decode
    public static TypeRef fromBinary(byte[] refBytes) {
        return fromRefBytesPayload(refBytes);
    }

    @Decode
    public static TypeRef fromText(String text) {
        return parseText(text);
    }

    // ==================================================================================
    // Wire decode helpers (package-private)
    // ==================================================================================

    static TypeRef fromRefBytesPayload(byte[] bytes) {
        if (bytes.length < 1 || bytes[0] != PREFIX_TYPE) {
            throw new IllegalArgumentException("TypeRef payload must start with '?' (0x3F)");
        }
        HashID.Slice iidSlice = readMultihash(bytes, 1);
        if (iidSlice.next() != bytes.length) {
            throw new IllegalArgumentException("TypeRef has unexpected trailing bytes");
        }
        return new TypeRef(Multihash.deserialize(iidSlice.bytes()));
    }

    static TypeRef parseText(String text) {
        if (text == null || text.isEmpty() || text.charAt(0) != '?') {
            throw new IllegalArgumentException("TypeRef text must start with '?', got: " + text);
        }
        String body = text.substring(1);
        if (body.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("TypeRef has no sub-parts, '\\' is not allowed");
        }
        return new TypeRef(Multibase.decode(body));
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
        return "❓";
    }
}
