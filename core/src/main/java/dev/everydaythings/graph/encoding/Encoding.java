package dev.everydaythings.graph.encoding;


import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.GrammaticalFeature;
import dev.everydaythings.graph.Language;
import dev.everydaythings.graph.PartOfSpeech;
import dev.everydaythings.graph.Seed;
import dev.everydaythings.graph.ThematicRole;
import dev.everydaythings.graph.canonical.Node;
import dev.everydaythings.graph.ref.ItemRef;

import java.io.InputStream;
import java.util.Optional;


/**
 * Encoding — the archetype of binary content encodings, AND the Java interface
 * that codec implementations satisfy.
 *
 * <p>An <i>encoding</i> is a specific binary representation for content — be it
 * Common Graph's deterministic CBOR profile, an image format like JPEG, a
 * structured document format like JSON, plain UTF-8 text, or any other byte
 * convention. Two parties exchanging data must agree on the encoding to
 * interpret each other's bytes.
 *
 * <p>This type plays two roles simultaneously:
 *
 * <ul>
 *   <li><b>Java interface</b>: defines the behavior of an encoding's codec —
 *       what it means to encode and decode content in this format.  A handful
 *       of core methods ({@link #encoding}, {@link #formatCode}, {@link
 *       #encode}, {@link #decode}) are abstract: every codec must answer
 *       them.  Optional-capability methods (text forms, streaming decode,
 *       pretty-print, validity check) are throwing-defaults — implementers
 *       override what they support.</li>
 *   <li><b>Seed-item archetype</b>: graph identity for "an encoding."  Each
 *       inner class (CgCborV1, ImageJpeg, etc.) is an <i>instance</i> of the
 *       Encoding archetype — a named encoding with a CG-assigned format code,
 *       MIME type, and other metadata.  The inner classes are seed-items,
 *       not Java implementations of the interface — they're <i>data about
 *       encodings</i>.</li>
 * </ul>
 *
 * <p>A concrete Java codec (e.g., a forthcoming {@code CgCbor} class) would
 * implement the {@code Encoding} interface AND have {@code @Seed.Embodies}
 * linking it as the Java implementation of one of these encoding instances
 * (e.g., {@link CgCborV1}).
 *
 * <p>Most encoding entries are <b>passthrough</b> — CG stores their bytes
 * opaquely and never parses them. Only a few (notably CG-CBOR-v1) have Java
 * codec implementations.
 */
@Seed.Item(key = Encoding.KEY)
public interface Encoding {

    /** Canonical key for the Encoding archetype. */
    String KEY = "cg.archetype:encoding";

    /** The archetype IID for Encoding. */
    ItemRef IID = ItemRef.fromString(KEY);

    @Seed.Frame(predicate = Language.Gloss.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
    String englishGloss =
            "a specific binary representation for content — bytes interpreted "
                    + "under a named convention (CBOR profile, image format, "
                    + "text encoding, etc.)";

    @Seed.Frame(predicate = Language.Lexeme.KEY,
          field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
    String englishNounLemma = "encoding";

    // ==================================================================================
    // Core methods — every codec MUST implement these.
    //
    // Abstract by design: a codec that doesn't answer encoding(), formatCode(),
    // encode(), and decode() isn't really a codec.  If a class wants to be
    // registered as an Encoding but can't satisfy these (e.g., a placeholder
    // for an encoding the system doesn't yet have a codec for), it should
    // throw with a clear message naming the missing capability.
    // ==================================================================================

    /** The encoding instance this codec implements (e.g., {@code ItemRef.iid(CgCborV1.KEY)}). */
    ItemRef encoding();

    /**
     * The CG-assigned one-byte FormatCode for this encoding.  Used in the
     * {@code .librarian/format} marker file and as the leading byte of a
     * ContentRef for compact encoding-self-description.
     */
    byte formatCode();

    /** Encode an object into bytes under this encoding. */
    byte[] encode(Object value);

    /** Decode bytes back to an object under this encoding. */
    Object decode(byte[] bytes);

    // ==================================================================================
    // Derived / optional capabilities.
    //
    // These are throwing-defaults — implementers override what they support.
    // Callers that need them should be prepared to handle the throw, or pick a
    // codec known to support the capability.
    // ==================================================================================

    /**
     * Decode bytes to a typed value of the requested class.  Default behavior
     * is "decode polymorphically and cast"; codecs override when a particular
     * type's wire shape is not self-describing under polymorphic decode (e.g.,
     * an untagged array form that would otherwise collide with another type).
     *
     * @throws IllegalArgumentException if the decoded value is not assignable
     *         to {@code type}, or if the codec cannot produce {@code type}.
     */
    default <T> T decode(byte[] bytes, Class<T> type) {
        Object value = decode(bytes);
        if (value == null || !type.isInstance(value)) {
            throw new IllegalArgumentException(
                    "Decoded value of type " + (value == null ? "null" : value.getClass().getName())
                            + " is not assignable to " + type.getName());
        }
        return type.cast(value);
    }

    /**
     * Encode an object into a text form under this encoding (typically a
     * multibase-wrapped form of the binary encoding).  Optional capability.
     */
    default String encodeText(Object value) {
        throw new UnsupportedOperationException(
                "encodeText() not supported by " + getClass().getSimpleName());
    }

    /** Decode a text form back to an object under this encoding.  Optional capability. */
    default Object decodeText(String text) {
        throw new UnsupportedOperationException(
                "decodeText() not supported by " + getClass().getSimpleName());
    }

    /**
     * Walk a value as a {@link Node} tree — the encoding-agnostic structural
     * view used for hashing, pretty-printing, querying, and anything else that
     * needs to recurse over structure without caring what encoding produced it.
     *
     * <p>The returned tree captures the value's <i>semantic</i> structure;
     * encoding-level framing (CBOR tags, length prefixes, header bytes) is the
     * codec's concern and is never reflected in the tree.  See {@link Node}.
     *
     * <p>Optional capability.  Codecs that can produce a Node tree from typed
     * values override; most defer to {@link
     * dev.everydaythings.graph.canonical.CanonWalker CanonWalker} (which is
     * itself codec-agnostic).
     */
    default Node walk(Object value) {
        throw new UnsupportedOperationException(
                "walk(Object) not supported by " + getClass().getSimpleName());
    }

    /**
     * Walk encoded bytes as a {@link Node} tree directly, without fully
     * reconstructing a typed Object value.  Useful for inspecting bytes
     * received from the wire or read from storage when reconstruction isn't
     * needed.  Optional capability.
     */
    default Node walk(byte[] bytes) {
        throw new UnsupportedOperationException(
                "walk(byte[]) not supported by " + getClass().getSimpleName());
    }

    /** Pretty-printed text rendering of a value, primarily for debugging.  Optional capability. */
    default String prettyPrint(Object value) {
        throw new UnsupportedOperationException(
                "prettyPrint() not supported by " + getClass().getSimpleName());
    }

    /** Check whether the given bytes parse as a valid encoded value.  Optional capability. */
    default boolean isValid(byte[] bytes) {
        throw new UnsupportedOperationException(
                "isValid() not supported by " + getClass().getSimpleName());
    }

    /**
     * Decode one top-level value from the stream — the streaming-decode
     * primitive used by Parley (and any other protocol that reads a series
     * of self-delimited values off a byte stream).
     *
     * <p>Semantics by outcome:
     * <ul>
     *   <li><b>Complete value</b>: returns {@code Optional.of(value)} and
     *       leaves the stream position past the consumed bytes.</li>
     *   <li><b>Short read</b> (stream doesn't contain enough bytes for a
     *       complete value): returns {@code Optional.empty()} and resets
     *       the stream position to where it was before the call.  The
     *       caller can re-attempt after appending more bytes.</li>
     *   <li><b>Decoded null</b> (the codec's representation of an explicit
     *       null value): also returns {@code Optional.empty()}, but with
     *       the stream position <i>advanced</i> past the consumed bytes.
     *       The two empty cases are distinguished by whether the stream's
     *       position moved.</li>
     *   <li><b>Malformed</b>: throws.  Stream position is undefined; the
     *       caller should treat the stream as poisoned.</li>
     * </ul>
     *
     * <p>The stream must support {@link InputStream#mark}/
     * {@link InputStream#reset} (a {@link java.io.ByteArrayInputStream
     * ByteArrayInputStream} is the canonical caller-provided type).
     *
     * <p>Optional capability — only codecs that can interpret their bytes
     * (e.g., CG-CBOR) override.  Wire-format-as-bytes codecs that have no
     * self-delimited frame structure leave this throwing.
     */
    default Optional<Object> decodeOne(InputStream in) {
        throw new UnsupportedOperationException(
                "decodeOne() not supported by " + getClass().getSimpleName());
    }

    // ==================================================================================
    // Predicates relating Encoding instances to their numeric/textual properties
    // ==================================================================================

    /**
     * Relates an encoding to its CG-assigned one-byte format code (the wire
     * byte prepended to a {@code ContentRef}'s multihash for compact addressing).
     *
     * <p>Not every encoding has a FormatCode — only common ones we want
     * compactly addressable. Encodings without one are referenced by IID.
     */
    @Seed.Item(key = FormatCode.KEY, head = CoreVocabulary.Predicate.KEY)
    final class FormatCode {
        public static final String KEY = "cg.predicate:format-code";
        private FormatCode() {}

        @Seed.Frame(predicate = Language.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the CG-assigned one-byte format code identifying a binary encoding";

        @Seed.Frame(predicate = Language.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "format code";
    }

    /**
     * Relates an encoding to its multicodec table code (for IPFS interop). Not
     * all encodings have a multicodec equivalent.
     */
    @Seed.Item(key = MulticodecCode.KEY, head = CoreVocabulary.Predicate.KEY)
    final class MulticodecCode {
        public static final String KEY = "cg.predicate:multicodec-code";
        private MulticodecCode() {}

        @Seed.Frame(predicate = Language.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the multicodec table code identifying an encoding (for IPFS interop)";
    }

    /** Relates an encoding to its IANA-registered MIME type string. */
    @Seed.Item(key = MimeType.KEY, head = CoreVocabulary.Predicate.KEY)
    final class MimeType {
        public static final String KEY = "cg.predicate:mime-type";
        private MimeType() {}

        @Seed.Frame(predicate = Language.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "the IANA-registered MIME type string identifying an encoding's content type";

        @Seed.Frame(predicate = Language.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "MIME type";
    }

    // ==================================================================================
    // Encoding instances — each is an instance of the Encoding archetype
    //
    // These are seed items (data about encodings), NOT Java implementations of
    // the Encoding interface. A concrete codec class would implement Encoding
    // AND link to one of these via @Seed.Embodies.
    // ==================================================================================

    /**
     * Unknown / unrecognized encoding sentinel. Used as the format-code when
     * ingested bytes have no recognized encoding tag; consumers handle as
     * opaque content.
     */
    @Seed.Item(key = Unknown.KEY, head = Encoding.KEY)
    final class Unknown {
        public static final String KEY = "cg.encoding:unknown";

        @Seed.Frame(predicate = FormatCode.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final long FORMAT_CODE = 0x00L;

        @Seed.Frame(predicate = MimeType.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final String MIME_TYPE = "application/octet-stream";

        private Unknown() {}

        @Seed.Frame(predicate = Language.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "unknown encoding sentinel — bytes whose format is unrecognized";
    }

    /** CG-CBOR v1 — Common Graph's deterministic CBOR profile. */
    @Seed.Item(key = CgCborV1.KEY, head = Encoding.KEY)
    final class CgCborV1 {
        public static final String KEY = "cg.encoding:cg-cbor-v1";

        @Seed.Frame(predicate = FormatCode.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final long FORMAT_CODE = 0x01L;

        @Seed.Frame(predicate = MimeType.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final String MIME_TYPE = "application/cbor";

        @Seed.Frame(predicate = MulticodecCode.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final long MULTICODEC_CODE = 0x71L;  // closest multicodec: dag-cbor

        private CgCborV1() {}

        @Seed.Frame(predicate = Language.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "Common Graph's deterministic CBOR profile (v1) — the default encoding for datums";

        @Seed.Frame(predicate = Language.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "CG-CBOR";
    }

    // ----- Common absorbed external encodings -----

    /** JPEG image. */
    @Seed.Item(key = ImageJpeg.KEY, head = Encoding.KEY)
    final class ImageJpeg {
        public static final String KEY = "cg.encoding:image/jpeg";

        @Seed.Frame(predicate = FormatCode.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final long FORMAT_CODE = 0x10L;

        @Seed.Frame(predicate = MimeType.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final String MIME_TYPE = "image/jpeg";

        private ImageJpeg() {}

        @Seed.Frame(predicate = Language.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "JPEG image format (ISO/IEC 10918)";

        @Seed.Frame(predicate = Language.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "JPEG";
    }

    /** PNG image. */
    @Seed.Item(key = ImagePng.KEY, head = Encoding.KEY)
    final class ImagePng {
        public static final String KEY = "cg.encoding:image/png";

        @Seed.Frame(predicate = FormatCode.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final long FORMAT_CODE = 0x11L;

        @Seed.Frame(predicate = MimeType.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final String MIME_TYPE = "image/png";

        private ImagePng() {}

        @Seed.Frame(predicate = Language.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "Portable Network Graphics image format";

        @Seed.Frame(predicate = Language.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "PNG";
    }

    /** PDF document. */
    @Seed.Item(key = ApplicationPdf.KEY, head = Encoding.KEY)
    final class ApplicationPdf {
        public static final String KEY = "cg.encoding:application/pdf";

        @Seed.Frame(predicate = FormatCode.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final long FORMAT_CODE = 0x12L;

        @Seed.Frame(predicate = MimeType.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final String MIME_TYPE = "application/pdf";

        private ApplicationPdf() {}

        @Seed.Frame(predicate = Language.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "Portable Document Format";

        @Seed.Frame(predicate = Language.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "PDF";
    }

    /** JSON. */
    @Seed.Item(key = ApplicationJson.KEY, head = Encoding.KEY)
    final class ApplicationJson {
        public static final String KEY = "cg.encoding:application/json";

        @Seed.Frame(predicate = FormatCode.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final long FORMAT_CODE = 0x13L;

        @Seed.Frame(predicate = MimeType.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final String MIME_TYPE = "application/json";

        @Seed.Frame(predicate = MulticodecCode.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final long MULTICODEC_CODE = 0x0200L;

        private ApplicationJson() {}

        @Seed.Frame(predicate = Language.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "JavaScript Object Notation";

        @Seed.Frame(predicate = Language.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "JSON";
    }

    /** Plain UTF-8 text. */
    @Seed.Item(key = TextPlainUtf8.KEY, head = Encoding.KEY)
    final class TextPlainUtf8 {
        public static final String KEY = "cg.encoding:text/plain";

        @Seed.Frame(predicate = FormatCode.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final long FORMAT_CODE = 0x14L;

        @Seed.Frame(predicate = MimeType.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final String MIME_TYPE = "text/plain";

        private TextPlainUtf8() {}

        @Seed.Frame(predicate = Language.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss = "Plain UTF-8 text";
    }

    /**
     * CESR JSON-mode — KERI's "Composable Event Streaming Representation" in
     * its textual (qb64) form: a JSON object carrying a {@code v} version-string
     * header followed by CESR-textual signature attachments (quad-base64 codes
     * plus payloads).
     *
     * <p>This is the wire format for KERI inception, rotation, delegation,
     * revocation, and interaction events when interoperating over JSON-friendly
     * channels.  Codec implementation lives in the {@code :bridges:keri} module.
     */
    @Seed.Item(key = CesrJson.KEY, head = Encoding.KEY)
    final class CesrJson {
        public static final String KEY = "cg.encoding:cesr-json";

        @Seed.Frame(predicate = FormatCode.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final long FORMAT_CODE = 0x20L;

        @Seed.Frame(predicate = MimeType.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final String MIME_TYPE = "application/cesr+json";

        private CesrJson() {}

        @Seed.Frame(predicate = Language.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "KERI's CESR encoding in JSON-with-textual-attachments mode — "
                        + "JSON event bodies followed by quad-base64 (qb64) "
                        + "signature attachments";

        @Seed.Frame(predicate = Language.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "CESR JSON";
    }

    /**
     * CESR binary mode — KERI's "Composable Event Streaming Representation" in
     * its compact binary form (qb2): events and attachments fully concatenated
     * as length-prefixed binary frames.  Roughly 4x smaller than {@link CesrJson}
     * on the wire.  Codec implementation lives in the {@code :bridges:keri}
     * module (deferred to v2 of the bridge).
     */
    @Seed.Item(key = CesrBinary.KEY, head = Encoding.KEY)
    final class CesrBinary {
        public static final String KEY = "cg.encoding:cesr-binary";

        @Seed.Frame(predicate = FormatCode.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final long FORMAT_CODE = 0x21L;

        @Seed.Frame(predicate = MimeType.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY))
        public static final String MIME_TYPE = "application/cesr";

        private CesrBinary() {}

        @Seed.Frame(predicate = Language.Gloss.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY}))
        static final String englishGloss =
                "KERI's CESR encoding in compact binary (qb2) mode";

        @Seed.Frame(predicate = Language.Lexeme.KEY,
              field = @Seed.Binding(role = ThematicRole.Value.KEY, qualifiers = {Language.English.KEY, PartOfSpeech.Noun.KEY, GrammaticalFeature.Lemma.KEY}))
        static final String englishNounLemma = "CESR binary";
    }
}
