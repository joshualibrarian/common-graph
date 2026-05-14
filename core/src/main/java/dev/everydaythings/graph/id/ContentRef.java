package dev.everydaythings.graph.id;

import dev.everydaythings.graph.encoding.TextBase;

import java.io.ByteArrayOutputStream;
import java.util.Objects;

/**
 * Reference to raw content, addressed by content hash.
 *
 * <p>Text form: {@code ~<CID>}.
 *
 * <p>Binary form (inside Tag 6): {@code 0x7E <multihash-CID>}.
 *
 * <p>Used for opaque content blobs — bytes addressed solely by their hash, with no
 * structural interpretation. For structured frame bodies, use {@link FrameRef}.
 *
 * @param cid the content's hash identity
 */
public record ContentRef(ContentID cid) implements Reference {

    public ContentRef {
        Objects.requireNonNull(cid, "cid");
    }

    public static ContentRef of(ContentID cid) {
        return new ContentRef(cid);
    }

    @Override
    public Variant variant() {
        return Variant.CONTENT;
    }

    @Override
    public byte[] toRefBytes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(PREFIX_CONTENT & 0xFF);
        out.writeBytes(cid.encodeBinary());
        return out.toByteArray();
    }

    @Override
    public String encodeText() {
        return "~" + TextBase.Base32Lower.encode(cid.encodeBinary());
    }

    @Override
    public String toString() {
        return encodeText();
    }

    /**
     * Decode a ContentRef from its full binary form (including the leading prefix byte).
     *
     * <p>Package-private; called by {@link Reference#fromRefBytes(byte[])}.
     */
    static ContentRef fromRefBytesPayload(byte[] bytes) {
        if (bytes.length < 1 || bytes[0] != PREFIX_CONTENT) {
            throw new IllegalArgumentException(
                    "ContentRef payload must start with '~' (0x7E)");
        }
        HashID.Slice cidSlice = Reference.readMultihash(bytes, 1);
        if (cidSlice.next() != bytes.length) {
            throw new IllegalArgumentException(
                    "ContentRef has unexpected trailing bytes after CID");
        }
        return new ContentRef(new ContentID(cidSlice.bytes()));
    }

    /**
     * Parse a ContentRef from text form: {@code ~<CID>}.
     */
    static ContentRef parseText(String text) {
        if (text == null || text.isEmpty() || text.charAt(0) != '~') {
            throw new IllegalArgumentException(
                    "ContentRef text must start with '~', got: " + text);
        }
        String body = text.substring(1);
        if (body.indexOf('\\') >= 0) {
            throw new IllegalArgumentException(
                    "ContentRef has no sub-parts, '\\' is not allowed");
        }
        ContentID cid = new ContentID(io.ipfs.multibase.Multibase.decode(body));
        return new ContentRef(cid);
    }
}
