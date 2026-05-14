package dev.everydaythings.graph.id;

import dev.everydaythings.graph.canonical.Decode;
import dev.everydaythings.graph.encoding.TextBase;
import io.ipfs.multibase.Multibase;
import io.ipfs.multihash.Multihash;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HashID to an item — a stable identity, optionally pinned to a specific version.
 *
 * <p>An ItemRef IS the item's identity. The legacy "ItemRef" concept (a raw
 * multihash without prefix) has been absorbed into this class — the
 * multihash bytes live on the inherited {@link HashID#multihash()} field.
 *
 * <p>Text form: {@code @<IID>} or {@code @<IID>\<VID>} (when pinned).
 * Binary form (inside CBOR Tag 6): {@code 0x40 <multihash-IID> [0x5C <multihash-VID>]}.
 */
public final class ItemRef extends HashID {

    /** Legacy text prefix kept for backward-compat in old-form parse() callers. */
    public static final String IID_PREFIX = "iid:";

    private final Optional<ContentRef> pinnedVersion;

    public ItemRef(Multihash multihash) {
        super(multihash);
        this.pinnedVersion = Optional.empty();
    }

    public ItemRef(byte[] serializedMultihash) {
        super(serializedMultihash);
        this.pinnedVersion = Optional.empty();
    }

    public ItemRef(byte[] rawDigest, Multihash.Type type) {
        super(rawDigest, type);
        this.pinnedVersion = Optional.empty();
    }

    public ItemRef(Multihash multihash, Optional<ContentRef> pinnedVersion) {
        super(multihash);
        this.pinnedVersion = Objects.requireNonNull(pinnedVersion);
    }

    /** Random identity (256-bit). */
    public ItemRef() {
        super(randomID(KEY_LENGTH), Multihash.Type.id);
        this.pinnedVersion = Optional.empty();
    }

    @Override
    public byte prefixByte() {
        return PREFIX_ITEM;
    }

    @Override
    public Variant variant() {
        return Variant.ITEM;
    }

    /**
     * Self-reference (returning the unpinned form). Kept for call-site
     * compatibility with the legacy {@code ItemRef.iid()} accessor that
     * returned the wrapped {@code ItemRef}. With the merge, an ItemRef IS
     * the iid — this just strips any version pin.
     */
    public ItemRef iid() {
        return unpinned();
    }

    /**
     * Optional version pin — backward-compat alias for {@link #pinnedVersion()}.
     */
    public Optional<ContentRef> version() {
        return pinnedVersion;
    }

    /** The pinned version, if this ref is pinned to a specific manifest. */
    public Optional<ContentRef> pinnedVersion() {
        return pinnedVersion;
    }

    /** Whether this reference is pinned to a specific version. */
    public boolean isPinned() {
        return pinnedVersion.isPresent();
    }

    @Override
    public boolean isCompound() {
        return pinnedVersion.isPresent();
    }

    /** Return this ItemRef pinned to a specific version. */
    public ItemRef pinned(ContentRef version) {
        return new ItemRef(multihash, Optional.of(Objects.requireNonNull(version)));
    }

    /** Return this ItemRef stripped of any version pin (unpinned form). */
    public ItemRef unpinned() {
        return pinnedVersion.isEmpty() ? this : new ItemRef(multihash, Optional.empty());
    }

    @Override
    protected void writeSubParts(ByteArrayOutputStream out) {
        if (pinnedVersion.isPresent()) {
            writeSeparator(out);
            out.writeBytes(pinnedVersion.get().multihash());
        }
    }

    @Override
    protected void appendTextSubParts(StringBuilder sb) {
        if (pinnedVersion.isPresent()) {
            sb.append('\\');
            sb.append(TextBase.Base32Lower.encode(pinnedVersion.get().multihash()));
        }
    }

    // ==================================================================================
    // Factories
    // ==================================================================================

    /** Construct an unpinned ItemRef from an underlying ItemRef (identity). */
    public static ItemRef of(ItemRef iid) {
        return iid.isPinned() ? iid.unpinned() : iid;
    }

    /** Construct an ItemRef pinned to a specific version. */
    public static ItemRef of(ItemRef iid, ContentRef version) {
        return iid.unpinned().pinned(version);
    }

    /** Random ItemRef (256-bit random identity). */
    public static ItemRef random() {
        return new ItemRef();
    }

    /**
     * Cache of canonical-key → ItemRef. Lazily initialized via holder class to
     * avoid static-init ordering hazards.
     */
    private static final class FromStringCache {
        static final ConcurrentHashMap<String, ItemRef> CACHE = new ConcurrentHashMap<>();
    }

    /**
     * Create a deterministic ItemRef from a canonical-key string. Memoized:
     * repeated calls with the same string return the same instance.
     */
    public static ItemRef fromString(String s) {
        Objects.requireNonNull(s, "s");
        return FromStringCache.CACHE.computeIfAbsent(s, ItemRef::computeFromString);
    }

    private static ItemRef computeFromString(String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        return new ItemRef(sha256(bytes), Multihash.Type.sha2_256);
    }

    /**
     * Create an ItemRef by hashing multikey-encoded bytes — cryptographically
     * binds an item identity to its initial signing key.
     */
    public static ItemRef fromMultikeyBytes(byte[] multikeyBytes) {
        Objects.requireNonNull(multikeyBytes, "multikeyBytes");
        return new ItemRef(sha256(multikeyBytes), Multihash.Type.sha2_256);
    }

    /** Strict legacy parse: requires {@code iid:} prefix. */
    public static ItemRef parse(String text) {
        Objects.requireNonNull(text, "text");
        if (containsWhitespace(text)) {
            throw new IllegalArgumentException("whitespace not allowed in ItemRef: " + text);
        }
        if (!text.startsWith(IID_PREFIX)) {
            throw new IllegalArgumentException("ItemRef must start with " + IID_PREFIX);
        }
        String token = text.substring(IID_PREFIX.length());
        if (token.isEmpty()) throw new IllegalArgumentException("empty iid token");
        return new ItemRef(Multihash.deserialize(Multibase.decode(token)).toBytes());
    }

    // ==================================================================================
    // @Decode entry points
    // ==================================================================================

    /** Decode from full ref-bytes (with @ prefix). */
    @Decode
    public static ItemRef fromBinary(byte[] refBytes) {
        return fromRefBytesPayload(refBytes);
    }

    /** Decode from text form ({@code @<multibase>[\<multibase>]}). */
    @Decode
    public static ItemRef fromText(String text) {
        return parseText(text);
    }

    // ==================================================================================
    // Wire decode helpers (package-private; called by HashID and HashID resolvers)
    // ==================================================================================

    static ItemRef fromRefBytesPayload(byte[] bytes) {
        if (bytes.length < 1 || bytes[0] != PREFIX_ITEM) {
            throw new IllegalArgumentException("ItemRef payload must start with '@' (0x40)");
        }
        HashID.Slice iidSlice = readMultihash(bytes, 1);
        Multihash mh = Multihash.deserialize(iidSlice.bytes());
        int pos = iidSlice.next();
        if (pos == bytes.length) {
            return new ItemRef(mh, Optional.empty());
        }
        if (bytes[pos] != SEPARATOR) {
            throw new IllegalArgumentException(
                    "ItemRef expected separator (0x5C) after IID, got 0x"
                            + String.format("%02x", bytes[pos] & 0xFF));
        }
        HashID.Slice vidSlice = readMultihash(bytes, pos + 1);
        if (vidSlice.next() != bytes.length) {
            throw new IllegalArgumentException("ItemRef has unexpected trailing bytes after VID");
        }
        ContentRef version = new ContentRef(Multihash.deserialize(vidSlice.bytes()));
        return new ItemRef(mh, Optional.of(version));
    }

    public static ItemRef parseText(String text) {
        if (text == null || text.isEmpty() || text.charAt(0) != '@') {
            throw new IllegalArgumentException("ItemRef text must start with '@', got: " + text);
        }
        String body = text.substring(1);
        int sep = body.indexOf('\\');
        if (sep < 0) {
            return new ItemRef(Multibase.decode(body));
        }
        String iidText = body.substring(0, sep);
        String vidText = body.substring(sep + 1);
        Multihash iidMh = Multihash.deserialize(Multibase.decode(iidText));
        Multihash vidMh = Multihash.deserialize(Multibase.decode(vidText));
        return new ItemRef(iidMh, Optional.of(new ContentRef(vidMh)));
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
        return "📦";
    }
}
