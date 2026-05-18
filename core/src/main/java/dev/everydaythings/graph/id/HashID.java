package dev.everydaythings.graph.id;

import dev.everydaythings.graph.canonical.Encode;
import dev.everydaythings.graph.datum.DatumNode;
import dev.everydaythings.graph.encoding.TextBase;
import io.ipfs.multihash.Multihash;

import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

/**
 * Universal hash-based reference in Common Graph — the sealed family of
 * prefix-tagged multihash references.
 *
 * <p>Five variants, distinguished by leading prefix byte.  Three of them
 * ({@link ItemRef}, {@link TypeRef}, {@link SchemaRef}) all reference IIDs in
 * the item-id space but carry different operational meanings; the other two
 * ({@link ContentRef}, {@link DatumRef}) reference distinct hash spaces.
 *
 * <ul>
 *   <li>{@link ItemRef}    ({@code @<IID>[\<VID>]}) — literal reference to an item, optionally pinned to a version</li>
 *   <li>{@link TypeRef}    ({@code ?<IID>})        — query/match reference: match bodies in this IID's archetype hierarchy</li>
 *   <li>{@link SchemaRef}  ({@code !<IID>})        — schema/expects reference: instances of this IID look like the carrying body's shape</li>
 *   <li>{@link ContentRef} ({@code ~<CID>})        — references raw content bytes by content hash</li>
 *   <li>{@link DatumRef}   ({@code #<DatumRef>})   — references a specific Datum body by its semantic Merkle identity</li>
 * </ul>
 *
 * <p>The {@code ?} and {@code !} variants are mutually exclusive with each
 * other and with {@code @} on any given reference — one operational mode per
 * reference.  They're meta-modifiers on the same IID space, not separate
 * hashes.
 *
 * <h3>Binary form (the bytes inside CBOR Tag 6)</h3>
 * <pre>
 * &lt;prefix-byte&gt; &lt;multihash&gt; [ 0x5C &lt;multihash&gt; ]*
 *
 * prefix-byte = 0x40 (@) | 0x3F (?) | 0x21 (!) | 0x7E (~) | 0x23 (#)
 * </pre>
 *
 * <h3>Text form</h3>
 * <pre>
 * @&lt;multibase(IID)&gt;[\&lt;multibase(VID)&gt;]   — item
 * ?&lt;multibase(IID)&gt;                       — type / query
 * !&lt;multibase(IID)&gt;                       — schema / expects
 * ~&lt;multibase(CID)&gt;                       — content
 * #&lt;multibase(DatumRef)&gt;                  — datum
 * </pre>
 *
 * <h3>Self-describing bytes</h3>
 *
 * <p>The {@link #toRefBytes()} method returns the full self-describing wire
 * form — prefix byte + multihash + any variant-specific sub-parts. This is
 * what {@code @Encode} on this class produces: the codec wraps the result
 * in CBOR Tag 6 to emit it as a value.
 *
 * <p>The {@link #multihash()} accessor returns just the raw multihash bytes
 * (no prefix). Used internally when building nested ref-bytes (e.g., an
 * {@link ItemRef}'s pinned-version sub-part) and at index/hash callsites
 * that want the bare digest.
 */
public abstract sealed class HashID implements DatumNode permits ItemRef, TypeRef, SchemaRef, ContentRef, DatumRef {

    public static final int KEY_LENGTH = 32;

    /** Prefix byte for {@link ItemRef}: {@code '@'} (0x40). */
    public static final byte PREFIX_ITEM    = 0x40;
    /** Prefix byte for {@link TypeRef}: {@code '?'} (0x3F). */
    public static final byte PREFIX_TYPE    = 0x3F;
    /** Prefix byte for {@link SchemaRef}: {@code '!'} (0x21). */
    public static final byte PREFIX_SCHEMA  = 0x21;
    /** Prefix byte for {@link ContentRef}: {@code '~'} (0x7E). */
    public static final byte PREFIX_CONTENT = 0x7E;
    /** Prefix byte for {@link DatumRef}: {@code '#'} (0x23). */
    public static final byte PREFIX_DATUM   = 0x23;
    /** Universal sub-part separator byte: {@code '\\'} (0x5C). */
    public static final byte SEPARATOR      = 0x5C;

    protected final Multihash multihash;

    protected HashID(Multihash multihash) {
        this.multihash = Objects.requireNonNull(multihash);
    }

    protected HashID(byte[] serializedMultihash) {
        this.multihash = Multihash.deserialize(Objects.requireNonNull(serializedMultihash));
    }

    protected HashID(byte[] rawDigest, Multihash.Type type) {
        this.multihash = new Multihash(type, rawDigest);
    }

    /** Variant prefix byte: {@code @}, {@code ~}, or {@code #}. */
    public abstract byte prefixByte();

    /** Variant marker. */
    public abstract Variant variant();

    /** Multihash algorithm. */
    public Multihash.Type hashType() {
        return multihash.getType();
    }

    /**
     * Raw multihash bytes — no prefix, no sub-parts. Used for index keys,
     * hash inputs, and assembling sub-parts inside other refs.
     */
    public byte[] multihash() {
        return multihash.toBytes();
    }

    /**
     * Alias for {@link #multihash()} — kept for call-site compatibility
     * with the legacy {@code ItemRef/ContentRef/DatumRef.encodeBinary()} API.
     * Returns raw multihash bytes (no prefix). New code should prefer
     * {@link #multihash()}.
     */
    public byte[] encodeBinary() {
        return multihash.toBytes();
    }

    /**
     * Self-describing wire bytes: {@code prefix + multihash + sub-parts}.
     * This is the value's "bare bytes" — what the codec wraps in CBOR Tag 6
     * to emit at value position.
     */
    @Encode
    public byte[] toRefBytes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(prefixByte() & 0xFF);
        out.writeBytes(multihash.toBytes());
        writeSubParts(out);
        return out.toByteArray();
    }

    /** Override to append additional sub-parts after the primary multihash. */
    protected void writeSubParts(ByteArrayOutputStream out) {
        // default: no sub-parts
    }

    /**
     * Self-describing text form: {@code prefix-char + multibase(...) + text-sub-parts}.
     */
    public String encodeText() {
        StringBuilder sb = new StringBuilder();
        sb.append((char) (prefixByte() & 0xFF));
        sb.append(TextBase.Base32Lower.encode(multihash.toBytes()));
        appendTextSubParts(sb);
        return sb.toString();
    }

    /** Override to append additional text sub-parts. */
    protected void appendTextSubParts(StringBuilder sb) {
        // default: no sub-parts
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HashID other)) return false;
        return Arrays.equals(toRefBytes(), other.toRefBytes());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(toRefBytes());
    }

    @Override
    public String toString() {
        return encodeText();
    }

    /**
     * Decode from full ref-bytes (with prefix). Dispatches to the right variant.
     */
    public static HashID fromRefBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("HashID payload cannot be empty");
        }
        byte prefix = bytes[0];
        return switch (prefix) {
            case PREFIX_ITEM    -> ItemRef.fromRefBytesPayload(bytes);
            case PREFIX_TYPE    -> TypeRef.fromRefBytesPayload(bytes);
            case PREFIX_SCHEMA  -> SchemaRef.fromRefBytesPayload(bytes);
            case PREFIX_CONTENT -> ContentRef.fromRefBytesPayload(bytes);
            case PREFIX_DATUM   -> DatumRef.fromRefBytesPayload(bytes);
            default -> throw new IllegalArgumentException(
                    "Unknown HashID prefix byte: 0x" + String.format("%02x", prefix & 0xFF));
        };
    }

    /**
     * Parse from text form. Dispatches on the leading prefix character.
     */
    public static HashID parse(String text) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("HashID text cannot be empty");
        }
        char prefix = text.charAt(0);
        return switch (prefix) {
            case '@' -> ItemRef.parseText(text);
            case '?' -> TypeRef.parseText(text);
            case '!' -> SchemaRef.parseText(text);
            case '~' -> ContentRef.parseText(text);
            case '#' -> DatumRef.parseText(text);
            default -> throw new IllegalArgumentException(
                    "Unknown HashID prefix character: '" + prefix + "'");
        };
    }

    /** A self-delimiting multihash slice — bytes and the next-read index. */
    public record Slice(byte[] bytes, int next) {}

    /**
     * Reads one self-delimiting multihash starting at {@code off}.
     * Framing: 1-byte alg, 1-byte len, then {@code len} digest bytes.
     */
    public static Slice splitLeadingMultihashFromByteArray(byte[] buf, int off) {
        if (buf == null || off < 0 || off + 2 > buf.length) {
            throw new IllegalArgumentException("buffer underflow (header)");
        }
        int alg = Byte.toUnsignedInt(buf[off++]);
        int len = Byte.toUnsignedInt(buf[off++]);
        int end = off + len;
        if (end > buf.length) {
            throw new IllegalArgumentException("buffer underflow (digest)");
        }
        byte[] mh = new byte[2 + len];
        mh[0] = (byte) alg;
        mh[1] = (byte) len;
        System.arraycopy(buf, off, mh, 2, len);
        return new Slice(mh, end);
    }

    /** Alias for {@link #splitLeadingMultihashFromByteArray}. */
    public static Slice readMultihash(byte[] buf, int off) {
        return splitLeadingMultihashFromByteArray(buf, off);
    }

    /** Write the separator byte (0x5C) between multihash sub-parts. */
    public static void writeSeparator(ByteArrayOutputStream out) {
        out.write(SEPARATOR & 0xFF);
    }

    /** Generate a random byte array of the given length. */
    public static byte[] randomID(int bytes) {
        SecureRandom rng = new SecureRandom();
        byte[] b = new byte[bytes];
        rng.nextBytes(b);
        return b;
    }

    public static boolean containsWhitespace(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) return true;
        }
        return false;
    }

    /** Whether this is a compound reference (has sub-parts beyond the primary multihash). */
    public boolean isCompound() {
        return false;
    }

    /** UI emoji — overridden per variant. */
    public String emoji() {
        return "#";
    }

    /** UI display token — the encoded text. */
    public String displayToken() {
        return encodeText();
    }

    /**
     * Progressive display at a target character width.
     */
    public String displayAtWidth(int widthChars) {
        if (widthChars <= 2) return emoji();
        String full = encodeText();
        String em = emoji();
        int available = widthChars - 3;
        if (available >= full.length()) return em + " " + full;
        if (available <= 1) return em + "…";
        return em + " " + full.substring(0, available - 1) + "…";
    }

    public String shortDisplay()    { return displayAtWidth(12); }
    public String mediumDisplay()   { return displayAtWidth(20); }
    public String fullDisplay()     { return encodeText(); }
    public String compactDisplay()  { return emoji(); }

    /** The five variants of a HashID. */
    public enum Variant {
        ITEM, TYPE, SCHEMA, CONTENT, DATUM
    }
}
