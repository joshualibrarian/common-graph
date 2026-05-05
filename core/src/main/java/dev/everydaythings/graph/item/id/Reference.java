package dev.everydaythings.graph.item.id;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical;

import java.io.ByteArrayOutputStream;
import java.util.Optional;

/**
 * Universal reference in Common Graph — sealed sum type with three variants.
 *
 * <p>A {@link Reference} addresses one of three kinds of things:
 * <ul>
 *   <li>{@link ItemRef} ({@code @<IID>[\<VID>]}) — references an item, optionally pinned to a specific version.</li>
 *   <li>{@link ContentRef} ({@code ~<CID>}) — references raw content bytes by content hash.</li>
 *   <li>{@link FrameRef} ({@code #<CID>[\<key>[\<portion>]]}) — references a specific frame body by its CID, optionally drilling into a binding-key and a portion of that binding's content.</li>
 * </ul>
 *
 * <h3>Binary encoding (inside CBOR Tag 6)</h3>
 * <pre>
 * &lt;prefix&gt; &lt;multihash&gt; [ 0x5C &lt;sub-part&gt; ]*
 *
 * prefix = 0x40 (@) | 0x7E (~) | 0x23 (#)
 * sub-part = &lt;multihash&gt;                 — when sub-part is a hash (e.g., VID)
 *          | &lt;CompoundKey&gt; bytes          — when sub-part is a key
 *          | &lt;portion-spec&gt; bytes         — when sub-part is a portion selector
 * </pre>
 *
 * <p>Multihash segments are self-delimiting (algorithm byte + length byte + digest),
 * so no length prefix is needed for hash sub-parts. The {@code \} separator (0x5C)
 * appears at structural positions only — between successive sub-parts.
 *
 * <h3>Text encoding</h3>
 * <pre>
 * @&lt;multibase(IID)&gt;[\&lt;multibase(VID)&gt;]                      — item ref
 * ~&lt;multibase(CID)&gt;                                            — content ref
 * #&lt;multibase(CID)&gt;[\&lt;key-text&gt;[\&lt;portion-text&gt;]]              — frame ref
 * </pre>
 *
 * <p>The leading prefix character ({@code @}, {@code ~}, or {@code #}) identifies
 * the variant. Sub-parts are separated by {@code \} (backslash).
 *
 * <h3>CBOR encoding</h3>
 * <p>Always wrapped as {@code Tag(6, byte-string)} where the byte-string is the
 * binary form described above.
 */
public sealed interface Reference extends Canonical
        permits ItemRef, ContentRef, FrameRef {

    /** Prefix byte for {@link ItemRef}: {@code '@'} (0x40). */
    byte PREFIX_ITEM    = 0x40;

    /** Prefix byte for {@link ContentRef}: {@code '~'} (0x7E). */
    byte PREFIX_CONTENT = 0x7E;

    /** Prefix byte for {@link FrameRef}: {@code '#'} (0x23). */
    byte PREFIX_FRAME   = 0x23;

    /** Universal sub-part separator byte: {@code '\\'} (0x5C). */
    byte SEPARATOR      = 0x5C;

    /**
     * The variant of this reference: ITEM, CONTENT, or FRAME.
     */
    Variant variant();

    /**
     * Encode the reference as binary bytes (the payload that goes inside CBOR Tag 6).
     */
    byte[] toRefBytes();

    /**
     * Encode as CBOR Tag 6 wrapping the binary form.
     */
    @Override
    default CBORObject toCborTree(Scope scope) {
        return CBORObject.FromCBORObjectAndTag(
                CBORObject.FromByteArray(toRefBytes()),
                CgTag.REF);
    }

    /**
     * The text form, with leading prefix character.
     */
    String encodeText();

    @Override
    default String encodeText(Scope scope) {
        return encodeText();
    }

    /**
     * Decode a Reference from its binary form (the payload inside CBOR Tag 6).
     *
     * <p>Dispatches on the leading prefix byte: {@code @} → {@link ItemRef},
     * {@code ~} → {@link ContentRef}, {@code #} → {@link FrameRef}.
     */
    static Reference fromRefBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Reference payload cannot be empty");
        }
        byte prefix = bytes[0];
        return switch (prefix) {
            case PREFIX_ITEM    -> ItemRef.fromRefBytesPayload(bytes);
            case PREFIX_CONTENT -> ContentRef.fromRefBytesPayload(bytes);
            case PREFIX_FRAME   -> FrameRef.fromRefBytesPayload(bytes);
            default -> throw new IllegalArgumentException(
                    "Unknown Reference prefix byte: 0x"
                            + String.format("%02x", prefix & 0xFF));
        };
    }

    /**
     * Decode a Reference from a CBOR Tag 6 wrapper.
     */
    static Reference fromCborTree(CBORObject node) {
        if (node == null) {
            throw new IllegalArgumentException("Reference CBOR cannot be null");
        }
        if (!node.isTagged() || !node.HasMostOuterTag(CgTag.REF)) {
            throw new IllegalArgumentException(
                    "Reference must be CBOR Tag " + CgTag.REF + ", got tag "
                            + (node.isTagged() ? node.getMostOuterTag() : "(none)"));
        }
        CBORObject inner = node.UntagOne();
        if (inner.getType() != com.upokecenter.cbor.CBORType.ByteString) {
            throw new IllegalArgumentException(
                    "Reference Tag-6 payload must be a byte string, got: " + inner.getType());
        }
        return fromRefBytes(inner.GetByteString());
    }

    /**
     * Parse a Reference from text form. Dispatches on the leading character.
     */
    static Reference parse(String text) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("Reference text cannot be empty");
        }
        char prefix = text.charAt(0);
        return switch (prefix) {
            case '@' -> ItemRef.parseText(text);
            case '~' -> ContentRef.parseText(text);
            case '#' -> FrameRef.parseText(text);
            default -> throw new IllegalArgumentException(
                    "Unknown Reference prefix character: '" + prefix + "'");
        };
    }

    /**
     * Helper for subtypes: read a self-delimiting multihash starting at the given offset.
     */
    static HashID.Slice readMultihash(byte[] buf, int off) {
        return HashID.splitLeadingMultihashFromByteArray(buf, off);
    }

    /**
     * Helper for subtypes: write a separator byte to a stream.
     */
    static void writeSeparator(ByteArrayOutputStream out) {
        out.write(SEPARATOR & 0xFF);
    }

    /**
     * The three variants of a Reference.
     */
    enum Variant {
        ITEM, CONTENT, FRAME
    }
}
