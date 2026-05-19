package dev.everydaythings.graph.bridges.keri;

import dev.everydaythings.graph.encoding.Encoding;
import dev.everydaythings.graph.id.ItemRef;

import java.util.Base64;

/**
 * CESR codec — Composable Event Streaming Representation, KERI's wire format.
 *
 * <p>CESR encodes cryptographic primitives (public keys, signatures, digests,
 * identifiers) as fixed-length textual strings where a short leading code
 * selects the primitive's type and the rest is base64-url-encoded raw payload.
 * Above the primitive layer, KERI Key Event Log (KEL) events are JSON objects
 * carrying these primitive strings in well-known fields, followed by
 * concatenated CESR-textual signature attachments.
 *
 * <p>This class is the v1 codec for KERI's JSON-mode (qb64 textual): it
 * encodes and decodes primitives, decomposes a stream into events plus their
 * attachments, and exposes the structured event objects to higher-level
 * Frame ↔ KEL translation in {@link translator.KelTranslator}.
 *
 * <p>Binary CESR (qb2) is deferred to a v2 codec (see task #231).
 *
 * <p>The matter codes that drive this codec live as constants on
 * {@link MatterCode} (e.g. {@link MatterCode#SHA2_256}); per-code metadata
 * — raw byte length, target algorithm, multihash type — comes from
 * {@link MatterCode#spec}.
 */
public final class Cesr implements Encoding {

    /** Singleton instance; CESR is stateless. */
    public static final Cesr INSTANCE = new Cesr();

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private Cesr() {}

    // ==================================================================================
    // Encoding interface
    // ==================================================================================

    @Override
    public ItemRef encoding() {
        return ItemRef.iid(Encoding.CesrJson.KEY);
    }

    @Override
    public byte formatCode() {
        return (byte) Encoding.CesrJson.FORMAT_CODE;
    }

    // ==================================================================================
    // Primitive codec — code || base64Url(payload)
    // ==================================================================================

    /**
     * Encode a typed primitive: prepend {@code code} to the base64-url
     * (no-padding) representation of {@code raw}.
     *
     * @throws IllegalArgumentException if {@code raw.length} doesn't match
     *         {@link MatterCode#rawLength} for {@code code}, or if {@code code}
     *         isn't a known matter code
     */
    public static String encodePrimitive(String code, byte[] raw) {
        int expected = MatterCode.rawLength(code);
        if (raw.length != expected) {
            throw new IllegalArgumentException(
                    "CESR primitive " + code + " expects " + expected
                            + " raw bytes, got " + raw.length);
        }
        return code + URL_ENCODER.encodeToString(raw);
    }

    /**
     * Decode a single CESR primitive string.  Identifies the code by prefix,
     * decodes the remaining base64-url payload, and validates the length.
     *
     * @throws IllegalArgumentException if the prefix is unknown or the payload
     *         length is wrong for the prefix
     */
    public static Primitive decodePrimitive(String qb64) {
        String code = MatterCode.identify(qb64);
        if (code == null) {
            throw new IllegalArgumentException("Unknown CESR primitive code in: " + summarize(qb64));
        }
        int expectedQb64Length = MatterCode.qb64Length(code);
        if (qb64.length() != expectedQb64Length) {
            throw new IllegalArgumentException(
                    "CESR primitive " + code + " expects qb64 length " + expectedQb64Length
                            + ", got " + qb64.length());
        }
        byte[] payload = URL_DECODER.decode(qb64.substring(code.length()));
        int expectedRaw = MatterCode.rawLength(code);
        if (payload.length != expectedRaw) {
            throw new IllegalArgumentException(
                    "CESR primitive " + code + " decoded to " + payload.length
                            + " bytes, expected " + expectedRaw);
        }
        return new Primitive(code, payload);
    }

    /** A decoded CESR primitive: code + raw bytes. */
    public record Primitive(String code, byte[] raw) {}

    // ==================================================================================
    // Internals
    // ==================================================================================

    private static String summarize(String qb64) {
        return qb64.length() <= 16 ? qb64 : qb64.substring(0, 16) + "…";
    }
}
