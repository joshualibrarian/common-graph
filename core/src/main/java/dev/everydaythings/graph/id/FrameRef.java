package dev.everydaythings.graph.id;

import dev.everydaythings.graph.canonical.Scope;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.encoding.TextBase;

import java.io.ByteArrayOutputStream;
import java.util.Objects;
import java.util.Optional;

/**
 * Reference to a specific Datum body, semantically addressed by its DatumID, optionally
 * drilling into a binding-key and a portion of that binding's content.
 *
 * <p>Text form:
 * <ul>
 *   <li>{@code #<DatumID>} — the whole frame body</li>
 *   <li>{@code #<DatumID>\<key>} — a specific binding within the frame</li>
 *   <li>{@code #<DatumID>\<key>\<portion>} — a portion of the binding's content</li>
 * </ul>
 *
 * <p>Binary form (inside Tag 6):
 * <pre>
 * 0x23 &lt;multihash-DatumID&gt; [ 0x5C &lt;key-bytes&gt; [ 0x5C &lt;portion-bytes&gt; ] ]
 * </pre>
 *
 * <p>The reference target is a {@link DatumID} — the semantic identity computed by
 * recursive Merkle hashing over a Datum's structure. References survive re-encoding
 * and redaction because DatumID is invariant under those transformations.
 *
 * @param bodyId  the frame body's structural identity (DatumID)
 * @param key     optional binding-key drill-down
 * @param portion optional portion-spec drill-down within the binding's content
 */
public record FrameRef(DatumID bodyId, Optional<CompoundKey> key, Optional<Selector> portion)
        implements Reference {

    public FrameRef {
        Objects.requireNonNull(bodyId, "bodyId");
        Objects.requireNonNull(key, "key (use Optional.empty() for whole-frame ref)");
        Objects.requireNonNull(portion, "portion (use Optional.empty() for no portion)");
        if (portion.isPresent() && key.isEmpty()) {
            throw new IllegalArgumentException(
                    "FrameRef cannot have a portion without a key");
        }
    }

    /** Reference the whole frame body. */
    public static FrameRef of(DatumID bodyId) {
        return new FrameRef(bodyId, Optional.empty(), Optional.empty());
    }

    /** Reference a specific binding within the frame. */
    public static FrameRef of(DatumID bodyId, CompoundKey key) {
        return new FrameRef(bodyId, Optional.of(key), Optional.empty());
    }

    /** Reference a portion of a specific binding's content within the frame. */
    public static FrameRef of(DatumID bodyId, CompoundKey key, Selector portion) {
        return new FrameRef(bodyId, Optional.of(key), Optional.of(portion));
    }

    @Override
    public Variant variant() {
        return Variant.FRAME;
    }

    @Override
    public byte[] toRefBytes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(PREFIX_FRAME & 0xFF);
        out.writeBytes(bodyId.encodeBinary());
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
        sb.append(TextBase.Base32Lower.encode(bodyId.encodeBinary()));
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
        HashID.Slice idSlice = Reference.readMultihash(bytes, 1);
        DatumID bodyId = new DatumID(idSlice.bytes());
        int pos = idSlice.next();
        if (pos == bytes.length) {
            return new FrameRef(bodyId, Optional.empty(), Optional.empty());
        }
        if (bytes[pos] != SEPARATOR) {
            throw new IllegalArgumentException(
                    "FrameRef expected separator (0x5C) after DatumID, got 0x"
                            + String.format("%02x", bytes[pos] & 0xFF));
        }
        pos++;
        // Decode the CompoundKey CBOR; it self-delimits via CBOR's length encoding.
        CBORObject keyCbor = decodeOneCbor(bytes, pos);
        CompoundKey key = CompoundKey.fromCborTree(keyCbor);
        int afterKey = pos + keyCbor.EncodeToBytes().length;
        if (afterKey == bytes.length) {
            return new FrameRef(bodyId, Optional.of(key), Optional.empty());
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
        return new FrameRef(bodyId, Optional.of(key), Optional.of(portion));
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
            DatumID id = new DatumID(io.ipfs.multibase.Multibase.decode(body));
            return new FrameRef(id, Optional.empty(), Optional.empty());
        }
        String idText = body.substring(0, firstSep);
        String afterId = body.substring(firstSep + 1);
        DatumID id = new DatumID(io.ipfs.multibase.Multibase.decode(idText));
        int secondSep = afterId.indexOf('\\');
        if (secondSep < 0) {
            CompoundKey key = CompoundKey.fromCanonicalString(afterId);
            return new FrameRef(id, Optional.of(key), Optional.empty());
        }
        String keyText = afterId.substring(0, secondSep);
        String portionText = afterId.substring(secondSep + 1);
        CompoundKey key = CompoundKey.fromCanonicalString(keyText);
        Selector portion = Selector.fromText(portionText);
        return new FrameRef(id, Optional.of(key), Optional.of(portion));
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
