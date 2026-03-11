package dev.everydaythings.graph.item.id;

import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Encoding;
import dev.everydaythings.graph.item.component.Factory;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Unified reference to anything in Common Graph.
 *
 * <p>A Ref addresses an item, optionally at a specific version, optionally
 * drilling into a specific frame, optionally selecting a range within content.
 *
 * <h3>Structure</h3>
 * <pre>
 * Ref = target [ @version ] [ \frameKey ]* [ [selector] ]
 * </pre>
 *
 * <h3>Binary encoding (Tag 6 payload)</h3>
 * <pre>
 * &lt;multihash&gt; [ 0x40 &lt;multihash&gt; ] [ 0x5C &lt;key&gt; ]* [ 0x5B &lt;selector-bytes&gt; ]
 *
 * key = &lt;multihash&gt;                     — sememe (ItemID multihash)
 *     | 0x02 &lt;varint-len&gt; &lt;utf8-bytes&gt;  — literal string
 * </pre>
 *
 * <p>Multihash segments are self-delimiting (alg byte + length byte + digest),
 * so no length prefix is needed for hash-based keys. The marker bytes appear
 * only at structural positions (after a complete multihash), never inline.
 *
 * <h3>Text encoding</h3>
 * <pre>
 * target@version\KEY\KEY\"literal"[selector]
 * </pre>
 *
 * <h3>Marker bytes</h3>
 * <table>
 *   <tr><td>{@code 0x40}</td><td>{@code @}</td><td>version (manifest CID) follows</td></tr>
 *   <tr><td>{@code 0x5C}</td><td>{@code \}</td><td>frame key segment follows</td></tr>
 *   <tr><td>{@code 0x02}</td><td>STX</td><td>literal string key follows (text uses {@code "})</td></tr>
 *   <tr><td>{@code 0x5B}</td><td>{@code [}</td><td>selector follows</td></tr>
 * </table>
 *
 * <h3>Backward compatibility</h3>
 * <p>A bare multihash inside Tag 6 (no markers) is the degenerate case — a simple
 * item reference. This is backward compatible with using Tag 6 for bare ItemIDs.
 */
public final class Ref implements Canonical {

    // ==================================================================================
    // Binary marker bytes
    // ==================================================================================

    /** Version marker: {@code @} (0x40). Next segment is a CID multihash. */
    public static final byte MARKER_VERSION  = 0x40;

    /** Frame key marker: {@code \} (0x5C). Next segment is a key (sememe or literal). */
    public static final byte MARKER_FRAME    = 0x5C;

    /** Literal string marker: STX (0x02). Next is varint length + UTF-8 bytes. */
    public static final byte MARKER_LITERAL  = 0x02;

    /** Selector marker: {@code [} (0x5B). Remaining bytes are selector data. */
    public static final byte MARKER_SELECTOR = 0x5B;

    // ==================================================================================
    // Text marker characters
    // ==================================================================================

    static final char TEXT_VERSION   = '@';
    static final char TEXT_FRAME     = '\\';
    static final char TEXT_QUOTE     = '"';
    static final char TEXT_SEL_OPEN  = '[';
    static final char TEXT_SEL_CLOSE = ']';

    // ==================================================================================
    // Fields
    // ==================================================================================

    private final ItemID target;
    private final ContentID version;
    private final FrameKey frameKey;
    private final Selector selector;

    /**
     * The raw text that was entered to produce this Ref.
     *
     * <p>Purely for debugging and auditing — records what the user actually
     * typed before resolution (e.g., "chess-club" or "/games/chess").
     * Not included in binary/CBOR encoding, equality, or hashCode.
     * Can be null (most programmatic Refs won't have one).
     */
    private final String asEntered;

    private Ref(ItemID target, ContentID version, FrameKey frameKey, Selector selector, String asEntered) {
        this.target = Objects.requireNonNull(target, "target");
        this.version = version;
        this.frameKey = frameKey;
        this.selector = selector;
        this.asEntered = asEntered;
    }

    // ==================================================================================
    // Factories
    // ==================================================================================

    /** Ref to an item (no version, frame, or selector). */
    public static Ref of(ItemID target) {
        return new Ref(target, null, null, null, null);
    }

    /** Ref to an item at a specific version. */
    public static Ref of(ItemID target, ContentID version) {
        return new Ref(target, version, null, null, null);
    }

    /** Ref to a specific frame on an item. */
    public static Ref of(ItemID target, FrameKey frameKey) {
        return new Ref(target, null, frameKey, null, null);
    }

    /** Ref to a specific frame on a specific version of an item. */
    public static Ref of(ItemID target, ContentID version, FrameKey frameKey) {
        return new Ref(target, version, frameKey, null, null);
    }

    /** Full ref with all fields. */
    public static Ref of(ItemID target, ContentID version, FrameKey frameKey, Selector selector) {
        return new Ref(target, version, frameKey, selector, null);
    }

    /**
     * Attach "as entered" text to this Ref for debugging/auditing.
     * Returns a new Ref with the same formal address plus the original input text.
     */
    public Ref asEntered(String originalInput) {
        return new Ref(target, version, frameKey, selector, originalInput);
    }

    // ==================================================================================
    // Accessors
    // ==================================================================================

    /** The target item. Always present. */
    public ItemID target() { return target; }

    /** The version (manifest CID), or null for latest. */
    public ContentID version() { return version; }

    /** The frame key path, or null for the whole item. */
    public FrameKey frameKey() { return frameKey; }

    /** The selector, or null for all content. */
    public Selector selector() { return selector; }

    /** True if this is a bare item reference (no version, frame, or selector). */
    public boolean isSimple() {
        return version == null && frameKey == null && selector == null;
    }

    /** True if a specific version is specified. */
    public boolean isVersioned() { return version != null; }

    /** True if a frame path is specified. */
    public boolean hasFrame() { return frameKey != null; }

    /** True if a selector is specified. */
    public boolean hasSelector() { return selector != null; }

    /** The raw text that produced this Ref, or null. For debugging/auditing only. */
    public String asEntered() { return asEntered; }

    // ==================================================================================
    // Binary Encoding
    // ==================================================================================

    /**
     * Encode to the internal binary reference format.
     * This is the raw payload inside Tag 6 — not CBOR-wrapped.
     */
    public byte[] toRefBytes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream(64);

        // Target IID (self-delimiting multihash)
        writeBytes(out, target.encodeBinary());

        // Optional version
        if (version != null) {
            out.write(MARKER_VERSION);
            writeBytes(out, version.encodeBinary());
        }

        // Optional frame key segments
        if (frameKey != null) {
            for (FrameKey.FrameToken token : frameKey.tokens()) {
                out.write(MARKER_FRAME);
                if (token instanceof FrameKey.Sememe s) {
                    writeBytes(out, s.id().encodeBinary());
                } else if (token instanceof FrameKey.Literal l) {
                    out.write(MARKER_LITERAL);
                    byte[] strBytes = l.value().getBytes(StandardCharsets.UTF_8);
                    writeVarint(out, strBytes.length);
                    writeBytes(out, strBytes);
                }
            }
        }

        // Optional selector
        if (selector != null && !selector.isEmpty()) {
            out.write(MARKER_SELECTOR);
            writeBytes(out, selector.binary());
        }

        return out.toByteArray();
    }

    /**
     * Decode from the internal binary reference format.
     */
    public static Ref fromRefBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("empty ref bytes");
        }

        int pos = 0;

        // Read target IID (self-delimiting multihash)
        HashID.Slice iidSlice = HashID.splitLeadingMultihashFromByteArray(bytes, pos);
        ItemID target = new ItemID(iidSlice.bytes());
        pos = iidSlice.next();

        ContentID version = null;
        List<FrameKey.FrameToken> frameTokens = null;
        Selector selector = null;

        while (pos < bytes.length) {
            byte marker = bytes[pos];

            if (marker == MARKER_VERSION) {
                pos++;
                HashID.Slice cidSlice = HashID.splitLeadingMultihashFromByteArray(bytes, pos);
                version = new ContentID(cidSlice.bytes());
                pos = cidSlice.next();

            } else if (marker == MARKER_FRAME) {
                pos++;
                if (frameTokens == null) frameTokens = new ArrayList<>();

                if (pos < bytes.length && bytes[pos] == MARKER_LITERAL) {
                    // Literal string key: STX + varint length + UTF-8 bytes
                    pos++;
                    int[] varint = readVarint(bytes, pos);
                    int strLen = varint[0];
                    pos = varint[1];
                    String value = new String(bytes, pos, strLen, StandardCharsets.UTF_8);
                    pos += strLen;
                    frameTokens.add(new FrameKey.Literal(value));
                } else {
                    // Sememe key: multihash
                    HashID.Slice semSlice = HashID.splitLeadingMultihashFromByteArray(bytes, pos);
                    frameTokens.add(new FrameKey.Sememe(new ItemID(semSlice.bytes())));
                    pos = semSlice.next();
                }

            } else if (marker == MARKER_SELECTOR) {
                pos++;
                // Remaining bytes are selector data
                byte[] selBytes = new byte[bytes.length - pos];
                System.arraycopy(bytes, pos, selBytes, 0, selBytes.length);
                selector = Selector.fromBinary(selBytes);
                pos = bytes.length;

            } else {
                throw new IllegalArgumentException(
                        "unexpected marker byte 0x" + String.format("%02x", marker & 0xFF)
                                + " at position " + pos);
            }
        }

        FrameKey frameKey = frameTokens != null ? FrameKey.ofTokens(frameTokens) : null;
        return new Ref(target, version, frameKey, selector, null);
    }

    // ==================================================================================
    // CBOR Encoding (Tag 6)
    // ==================================================================================

    /**
     * Encode as CBOR Tag 6 wrapping the binary ref bytes.
     */
    @Override
    public CBORObject toCborTree(Scope scope) {
        return CBORObject.FromCBORObjectAndTag(
                CBORObject.FromByteArray(toRefBytes()),
                CgTag.REF
        );
    }

    /**
     * Decode from a CBOR Tag 6 node.
     */
    @Factory
    public static Ref fromCborTree(CBORObject node) {
        if (node == null) throw new IllegalArgumentException("null CBOR node");

        CBORObject payload = node;
        if (node.isTagged() && node.getMostInnerTag().ToInt32Checked() == CgTag.REF) {
            payload = node.UntagOne();
        }

        if (payload.getType() != CBORType.ByteString) {
            throw new IllegalArgumentException(
                    "Ref payload must be a byte string, got: " + payload.getType());
        }

        return fromRefBytes(payload.GetByteString());
    }

    // ==================================================================================
    // Text Encoding
    // ==================================================================================

    /**
     * Encode to human-readable text form.
     *
     * <p>Uses multibase-encoded multihash for IDs. Frame key sememes are
     * encoded as multihash text; literals are quoted with {@code "}.
     *
     * <p>Example: {@code bciaa...@bcibb...\bcicc...\"tavern"[0..100]}
     */
    public String encodeText() {
        StringBuilder sb = new StringBuilder();

        // Target — multibase-encoded multihash, no prefix
        sb.append(encodeMultihash(target));

        // Version
        if (version != null) {
            sb.append(TEXT_VERSION);
            sb.append(encodeMultihash(version));
        }

        // Frame key segments
        if (frameKey != null) {
            for (FrameKey.FrameToken token : frameKey.tokens()) {
                sb.append(TEXT_FRAME);
                if (token instanceof FrameKey.Sememe s) {
                    sb.append(encodeMultihash(s.id()));
                } else if (token instanceof FrameKey.Literal l) {
                    sb.append(TEXT_QUOTE).append(l.value()).append(TEXT_QUOTE);
                }
            }
        }

        // Selector
        if (selector != null && !selector.isEmpty()) {
            sb.append(TEXT_SEL_OPEN).append(selector.text()).append(TEXT_SEL_CLOSE);
        }

        return sb.toString();
    }

    /**
     * Parse a Ref from its text encoding.
     *
     * <p>The text form uses structural markers that never appear in
     * multibase-encoded hashes: {@code @}, {@code \}, {@code "}, {@code [}, {@code ]}.
     */
    public static Ref parse(String text) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("empty ref text");
        }

        int pos = 0;
        int len = text.length();

        // Read target — everything until first structural marker or end
        int targetEnd = nextStructuralMarker(text, pos);
        if (targetEnd == pos) {
            throw new IllegalArgumentException("ref must start with a target IID");
        }
        ItemID target = decodeMultihash(text.substring(pos, targetEnd));
        pos = targetEnd;

        ContentID version = null;
        List<FrameKey.FrameToken> frameTokens = null;
        Selector selector = null;

        while (pos < len) {
            char ch = text.charAt(pos);

            if (ch == TEXT_VERSION) {
                pos++;
                int end = nextStructuralMarker(text, pos);
                if (end == pos) throw new IllegalArgumentException("empty version at position " + pos);
                version = new ContentID(decodeMultihashBytes(text.substring(pos, end)));
                pos = end;

            } else if (ch == TEXT_FRAME) {
                pos++;
                if (frameTokens == null) frameTokens = new ArrayList<>();

                if (pos < len && text.charAt(pos) == TEXT_QUOTE) {
                    // Literal: "value"
                    pos++; // skip opening quote
                    int closeQuote = text.indexOf(TEXT_QUOTE, pos);
                    if (closeQuote < 0) throw new IllegalArgumentException("unclosed quote at position " + pos);
                    frameTokens.add(new FrameKey.Literal(text.substring(pos, closeQuote)));
                    pos = closeQuote + 1;
                } else {
                    // Sememe: multibase-encoded multihash
                    int end = nextStructuralMarker(text, pos);
                    if (end == pos) throw new IllegalArgumentException("empty frame key at position " + pos);
                    frameTokens.add(new FrameKey.Sememe(decodeMultihash(text.substring(pos, end))));
                    pos = end;
                }

            } else if (ch == TEXT_SEL_OPEN) {
                pos++;
                int closeBracket = text.indexOf(TEXT_SEL_CLOSE, pos);
                if (closeBracket < 0) throw new IllegalArgumentException("unclosed selector at position " + pos);
                selector = Selector.fromText(text.substring(pos, closeBracket));
                pos = closeBracket + 1;

            } else {
                throw new IllegalArgumentException(
                        "unexpected character '" + ch + "' at position " + pos);
            }
        }

        FrameKey frameKey = frameTokens != null ? FrameKey.ofTokens(frameTokens) : null;
        return new Ref(target, version, frameKey, selector, null);
    }

    @Override
    public String toString() {
        if (asEntered != null) {
            return encodeText() + " (entered: " + asEntered + ")";
        }
        return encodeText();
    }

    // ==================================================================================
    // Equality
    // ==================================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Ref other)) return false;
        return target.equals(other.target)
                && Objects.equals(version, other.version)
                && Objects.equals(frameKey, other.frameKey)
                && Objects.equals(selector, other.selector);
    }

    @Override
    public int hashCode() {
        return Objects.hash(target, version, frameKey, selector);
    }

    // ==================================================================================
    // Internal helpers
    // ==================================================================================

    /** Multibase-encode a HashID's multihash bytes. */
    private static String encodeMultihash(HashID id) {
        return Encoding.DEFAULT.encode(id.encodeBinary());
    }

    /** Decode a multibase string to an ItemID. */
    private static ItemID decodeMultihash(String multibase) {
        return new ItemID(Encoding.decode(multibase));
    }

    /** Decode a multibase string to raw multihash bytes. */
    private static byte[] decodeMultihashBytes(String multibase) {
        return Encoding.decode(multibase);
    }

    /** Find the next structural marker position, or end of string. */
    private static int nextStructuralMarker(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == TEXT_VERSION || ch == TEXT_FRAME || ch == TEXT_SEL_OPEN) {
                return i;
            }
        }
        return text.length();
    }

    /** Write bytes to a stream (convenience to avoid checked exceptions). */
    private static void writeBytes(ByteArrayOutputStream out, byte[] data) {
        out.write(data, 0, data.length);
    }

    /** Write an unsigned varint (LEB128) to the stream. */
    static void writeVarint(ByteArrayOutputStream out, int value) {
        if (value < 0) throw new IllegalArgumentException("negative varint: " + value);
        do {
            int b = value & 0x7F;
            value >>>= 7;
            if (value != 0) b |= 0x80;
            out.write(b);
        } while (value != 0);
    }

    /** Read an unsigned varint (LEB128) from bytes. Returns {value, newPosition}. */
    static int[] readVarint(byte[] bytes, int pos) {
        int value = 0;
        int shift = 0;
        while (pos < bytes.length) {
            int b = bytes[pos++] & 0xFF;
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
        }
        return new int[]{value, pos};
    }
}
