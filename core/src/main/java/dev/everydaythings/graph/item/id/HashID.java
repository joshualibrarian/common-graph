package dev.everydaythings.graph.item.id;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Encoding;
import dev.everydaythings.graph.Hash;
import dev.everydaythings.graph.item.Factory;
import io.ipfs.multibase.Multibase;
import io.ipfs.multihash.Multihash;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.security.SecureRandom;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.EMPTY;

@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public abstract class HashID implements Canonical {

    public static final int KEY_LENGTH = 32;

        /**
     * 32 random bytes (256-bit)
     */
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
    /**
     * A self-delimiting multihash slice
     * (the multihash bytes themselves, and the index of the next unread byte).
     */
    public record Slice(byte[] bytes, int next) {}

    /**
     * Reads one self-delimiting multihash starting at {@code off}.
     * Current framing: 1-byte alg, 1-byte len, then {@code len} digest bytes.
     * Adjust framing if your wire changes.
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

    @EqualsAndHashCode.Include
    protected Multihash multihash;

    protected HashID(Multihash multihash) {
        this.multihash = requireNonNull(multihash);
    }

    protected HashID(byte[] serializedMultihash) {
        this.multihash = Multihash.deserialize(requireNonNull(serializedMultihash));
    }

    protected HashID(byte[] rawDigest, Hash type) {
        this.multihash = new Multihash(type.multihashType, rawDigest);
    }

    protected HashID(String text) {
        this.multihash = Multihash.deserialize(Multibase.decode(text));
    }

    protected HashID() {
        this(randomID(KEY_LENGTH), Hash.ID);
    }

    public Hash hashType() {
        return Hash.of(multihash.getType());
    }

    /**
     * Encode as CBOR byte string containing the raw multihash bytes.
     */
    @Override
    public CBORObject toCborTree(Scope scope) {
        return CBORObject.FromByteArray(multihash.toBytes());
    }

    @Override
    public byte[] encodeBinary(Scope scope) {
        return toCborTree(scope).EncodeToBytes();
    }

    /**
     * Raw multihash bytes (for use when building CG-REF tags, etc.).
     */
    public byte[] encodeBinary() {
        return multihash.toBytes();
    }

    /**
     * Decode a HashID from binary form.
     * Since all HashID subclasses serialize identically (raw multihash bytes),
     * we return an ItemID as the generic concrete implementation.
     * Callers needing specific subtypes should use those classes' constructors directly.
     */
    @Factory
    public static HashID decodeBinary(byte[] bytes) {
        return new ItemID(bytes);
    }

    @Override
    public String encodeText(Scope scope) {
        return encodeText();
    }

    /** Lean text: {@code prefix() + base32/base64url(multihash-bytes)}. */
    public String encodeText() {
        return prefix() + Encoding.DEFAULT.encode(multihash.toBytes());
    }

    /** Subclasses override to prepend e.g. "iid:". */
    public String prefix() {
        return EMPTY;
    }

    @Override
    public String toString() {
        return encodeText();
    }

    // ==================================================================================
    // Display Methods
    // ==================================================================================

    /**
     * IDs are self-referential values, not graph nodes with paths.
     * They don't have a parent Item that contains them.
     */
    public Ref ref() {
        return null;
    }

    /**
     * IDs are leaf values - they don't expand to children.
     */
    public boolean isExpandable() {
        return false;
    }

    // ==================================================================================
    // Self-Rendering - Each ID type knows how to display itself
    // ==================================================================================

    /**
     * Display width specification for columns containing this type.
     *
     * <p>IDs are flexible: they can be as small as 1 character (emoji) or
     * as large as 45 characters (full hash). They render progressively:
     * <ul>
     *   <li>1 ch: just emoji</li>
     *   <li>4 ch: emoji + "…"</li>
     *   <li>10 ch: emoji + abbreviated hash</li>
     *   <li>45 ch: full encoded text</li>
     * </ul>
     */
    public static final dev.everydaythings.graph.value.DisplayWidth DISPLAY_WIDTH =
            dev.everydaythings.graph.value.DisplayWidth.of(1, 10, 45, dev.everydaythings.graph.value.Unit.CharacterWidth.SEED);

    /**
     * Emoji for compact single-character display.
     * Subclasses override with their specific emoji.
     */
    public String emoji() {
        return "#";
    }

    /**
     * Display token - the encoded hash text.
     */
    public String displayToken() {
        return encodeText();
    }

    /**
     * Render at a specific width (in characters).
     *
     * <p>Progressive rendering:
     * <ul>
     *   <li>1-2 ch: emoji only</li>
     *   <li>3-5 ch: emoji + "…"</li>
     *   <li>6-12 ch: emoji + short hash + "…"</li>
     *   <li>13+ ch: emoji + longer hash + "…" (or full if fits)</li>
     * </ul>
     *
     * @param widthChars Available width in characters
     * @return Display string that fits within the width
     */
    public String displayAtWidth(int widthChars) {
        if (widthChars <= 2) {
            return emoji();
        }

        String full = encodeText();
        String em = emoji();

        // Account for emoji (assume 2 chars wide) + space
        int available = widthChars - 3;

        if (available >= full.length()) {
            // Full text fits
            return em + " " + full;
        }

        if (available <= 1) {
            // Just emoji + ellipsis
            return em + "…";
        }

        // Truncate with ellipsis
        return em + " " + full.substring(0, available - 1) + "…";
    }

    /**
     * Compact display - just the emoji.
     * Used in tight table cells (1-2 characters).
     */
    public String compactDisplay() {
        return emoji();
    }

    /**
     * Short display - emoji + abbreviated hash.
     * Used when some context is available (8-12 characters).
     */
    public String shortDisplay() {
        return displayAtWidth(12);
    }

    /**
     * Medium display - emoji + more of the hash with trailing ellipsis.
     * Used when moderate space is available (15-20 characters).
     */
    public String mediumDisplay() {
        return displayAtWidth(20);
    }

    /**
     * Full display - complete encoded text.
     * Used in tooltips and expanded views.
     */
    public String fullDisplay() {
        return encodeText();
    }

    @Getter
    @AllArgsConstructor
    public enum IdType {
        MANIFEST ('@', ContentID.class),
        CONTENT ('\\', ContentID.class),
        CHUNK ('!', HashID.class),
        BUNDLE ('^', HashID.class);

//        String textPrefix;
        final char binaryPrefix;
        final Class<? extends HashID> idClass;

        public byte tag() {
            return (byte) binaryPrefix;
        }
    }
}
