package dev.everydaythings.graph.item.id;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Encoding;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/**
 * Reference to a specific frame body, content-addressed by its CID, optionally drilling
 * into a binding-key and a portion of that binding's content.
 *
 * <p>Text form:
 * <ul>
 *   <li>{@code #<CID>} — the whole frame body</li>
 *   <li>{@code #<CID>\<key>} — a specific binding within the frame</li>
 *   <li>{@code #<CID>\<key>\<portion>} — a portion of the binding's content</li>
 * </ul>
 *
 * <p>Binary form (inside Tag 6):
 * <pre>
 * 0x23 &lt;multihash-CID&gt; [ 0x5C &lt;key-bytes&gt; [ 0x5C &lt;portion-bytes&gt; ] ]
 * </pre>
 *
 * <p>The key is encoded as the CBOR-array form of a {@link CompoundKey}. The portion
 * spec is currently the encoded text bytes of a {@link Selector} (UTF-8). Both are
 * length-known via the surrounding structure (key by self-delimiting CBOR, portion
 * by reading to end of payload).
 *
 * @param bodyCid the frame body's content hash
 * @param key     optional binding-key drill-down
 * @param portion optional portion-spec drill-down within the binding's content
 */
public record FrameRef(ContentID bodyCid, Optional<CompoundKey> key, Optional<Selector> portion)
        implements Reference {

    public FrameRef {
        Objects.requireNonNull(bodyCid, "bodyCid");
        Objects.requireNonNull(key, "key (use Optional.empty() for whole-frame ref)");
        Objects.requireNonNull(portion, "portion (use Optional.empty() for no portion)");
        if (portion.isPresent() && key.isEmpty()) {
            throw new IllegalArgumentException(
                    "FrameRef cannot have a portion without a key");
        }
    }

    /** Reference the whole frame body. */
    public static FrameRef of(ContentID bodyCid) {
        return new FrameRef(bodyCid, Optional.empty(), Optional.empty());
    }

    /** Reference a specific binding within the frame. */
    public static FrameRef of(ContentID bodyCid, CompoundKey key) {
        return new FrameRef(bodyCid, Optional.of(key), Optional.empty());
    }

    /** Reference a portion of a specific binding's content within the frame. */
    public static FrameRef of(ContentID bodyCid, CompoundKey key, Selector portion) {
        return new FrameRef(bodyCid, Optional.of(key), Optional.of(portion));
    }

    @Override
    public Variant variant() {
        return Variant.FRAME;
    }

    @Override
    public byte[] toRefBytes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(PREFIX_FRAME & 0xFF);
        out.writeBytes(bodyCid.encodeBinary());
        if (key.isPresent()) {
            Reference.writeSeparator(out);
            byte[] keyBytes = key.get().toCborTree(Scope.BODY).EncodeToBytes();
            out.writeBytes(keyBytes);
            if (portion.isPresent()) {
                Reference.writeSeparator(out);
                out.writeBytes(portion.get().text().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        return out.toByteArray();
    }

    @Override
    public String encodeText() {
        StringBuilder sb = new StringBuilder();
        sb.append('#');
        sb.append(Encoding.DEFAULT.encode(bodyCid.encodeBinary()));
        if (key.isPresent()) {
            sb.append('\\');
            sb.append(key.get().toCanonicalString());
            if (portion.isPresent()) {
                sb.append('\\');
                sb.append(portion.get().text());
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return encodeText();
    }

    /**
     * Decode a FrameRef from its full binary form (including the leading prefix byte).
     *
     * <p>Package-private; called by {@link Reference#fromRefBytes(byte[])}.
     */
    static FrameRef fromRefBytesPayload(byte[] bytes) {
        if (bytes.length < 1 || bytes[0] != PREFIX_FRAME) {
            throw new IllegalArgumentException(
                    "FrameRef payload must start with '#' (0x23)");
        }
        HashID.Slice cidSlice = Reference.readMultihash(bytes, 1);
        ContentID bodyCid = new ContentID(cidSlice.bytes());
        int pos = cidSlice.next();
        if (pos == bytes.length) {
            return new FrameRef(bodyCid, Optional.empty(), Optional.empty());
        }
        if (bytes[pos] != SEPARATOR) {
            throw new IllegalArgumentException(
                    "FrameRef expected separator (0x5C) after CID, got 0x"
                            + String.format("%02x", bytes[pos] & 0xFF));
        }
        pos++;
        // Decode the CompoundKey CBOR; it self-delimits via CBOR's length encoding.
        CBORObject keyCbor = decodeOneCbor(bytes, pos);
        CompoundKey key = CompoundKey.fromCborTree(keyCbor);
        int afterKey = pos + keyCbor.EncodeToBytes().length;
        if (afterKey == bytes.length) {
            return new FrameRef(bodyCid, Optional.of(key), Optional.empty());
        }
        if (bytes[afterKey] != SEPARATOR) {
            throw new IllegalArgumentException(
                    "FrameRef expected separator (0x5C) after key, got 0x"
                            + String.format("%02x", bytes[afterKey] & 0xFF));
        }
        int portionStart = afterKey + 1;
        String portionText = new String(
                bytes, portionStart, bytes.length - portionStart,
                java.nio.charset.StandardCharsets.UTF_8);
        Selector portion = Selector.fromText(portionText);
        return new FrameRef(bodyCid, Optional.of(key), Optional.of(portion));
    }

    /**
     * Parse a FrameRef from text form: {@code #<CID>[\<key>[\<portion>]]}.
     */
    static FrameRef parseText(String text) {
        if (text == null || text.isEmpty() || text.charAt(0) != '#') {
            throw new IllegalArgumentException(
                    "FrameRef text must start with '#', got: " + text);
        }
        String body = text.substring(1);
        int firstSep = body.indexOf('\\');
        if (firstSep < 0) {
            ContentID cid = new ContentID(io.ipfs.multibase.Multibase.decode(body));
            return new FrameRef(cid, Optional.empty(), Optional.empty());
        }
        String cidText = body.substring(0, firstSep);
        String afterCid = body.substring(firstSep + 1);
        ContentID cid = new ContentID(io.ipfs.multibase.Multibase.decode(cidText));
        int secondSep = afterCid.indexOf('\\');
        if (secondSep < 0) {
            CompoundKey key = CompoundKey.fromCanonicalString(afterCid);
            return new FrameRef(cid, Optional.of(key), Optional.empty());
        }
        String keyText = afterCid.substring(0, secondSep);
        String portionText = afterCid.substring(secondSep + 1);
        CompoundKey key = CompoundKey.fromCanonicalString(keyText);
        Selector portion = Selector.fromText(portionText);
        return new FrameRef(cid, Optional.of(key), Optional.of(portion));
    }

    /**
     * Decode a single CBOR object starting at {@code offset} in {@code buf}, returning
     * the parsed object. The caller is responsible for tracking how many bytes were
     * consumed (via {@code result.EncodeToBytes().length} or by encoding the buffer slice
     * length).
     */
    private static CBORObject decodeOneCbor(byte[] buf, int offset) {
        // Decode a single CBOR object starting at offset. CBOR is self-delimiting,
        // so we decode from a slice and the decoder consumes exactly one object's worth.
        // The remaining bytes (if any) are recovered by re-encoding the parsed object
        // and tracking its byte length in the caller.
        byte[] slice = new byte[buf.length - offset];
        System.arraycopy(buf, offset, slice, 0, slice.length);
        try (java.io.ByteArrayInputStream stream = new java.io.ByteArrayInputStream(slice)) {
            return CBORObject.Read(stream);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Failed to decode CBOR object", e);
        }
    }
}
