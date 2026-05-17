package dev.everydaythings.graph.encoding;


import com.upokecenter.cbor.CBOREncodeOptions;
import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;
import dev.everydaythings.graph.canonical.HashTree;
import dev.everydaythings.graph.canonical.Layout;
import dev.everydaythings.graph.canonical.Leaves;
import dev.everydaythings.graph.canonical.Node;
import dev.everydaythings.graph.canonical.CanonWalker;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.BindingTarget;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Datum;
import dev.everydaythings.graph.datum.Opaque;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.id.CompoundKey;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.id.HashID;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Common Graph's deterministic CBOR codec — bytes in, bytes out.
 *
 * <p>CgCbor handles only the wire-level concern: turning typed values into
 * canonical CG-CBOR byte sequences and back. The {@link CanonWalker} owns
 * "what does a value's structure look like" (encoder-agnostic), and
 * {@link HashTree} owns "what's the structural identity of a value"
 * (encoder-agnostic). CgCbor is one of potentially many encoders that target
 * the same Walker / HashTree protocol; another encoder (CG-JSON, MsgPack,
 * etc.) could land alongside without touching identity.
 *
 * <p>Two surfaces:
 *
 * <ul>
 *   <li><b>Encode</b> — {@link #encode(Object)} turns a typed value into bytes;
 *       {@link #encodeText(Object)} produces a multibase-wrapped text form.</li>
 *   <li><b>Decode</b> — {@link #decode(byte[])} turns bytes back into a typed
 *       value; {@link #decode(byte[], Class)} validates the type;
 *       {@link #decodeText(String)} for the text form.</li>
 * </ul>
 *
 * <p>The wire form is CG-CBOR-canonical: CG-CBOR sorts collections and emits
 * deterministically. That's a CG-CBOR-spec choice, not a Datum-identity
 * concern; an encoder that emits non-canonically would simply produce
 * additional ContentIDs for the same DatumRef (wasteful, not broken).
 *
 * <p>There is no {@code Scope} parameter. Body and Record are distinct types;
 * if you want body-only bytes, encode a Body; if you want full record bytes,
 * encode a Record.
 */
public final class CgCbor {

    private CgCbor() {}

    // ==================================================================================
    // CG-CBOR tag layout — definitive reference for wire-level tags.
    //
    // First, the framing.  CBOR's first byte of every data item is `MMMNNNNN`:
    //   • MMM (top 3 bits)   = major type (0..7) — the wire-level shape
    //   • NNNNN (bottom 5)   = additional info  — length / value / sub-type
    //
    // The eight major types:
    //   0  unsigned integer    1  negative integer
    //   2  byte string         3  text string
    //   4  array               5  map
    //   6  TAG (semantic wrapper around another value, with a tag number)
    //   7  simple values + floats — booleans, null, undefined live here
    //
    // The "tags" in the table below are all major-type-6 tags.  Each carries a
    // tag number identifying its semantics.  The two numbering systems are
    // orthogonal but easy to conflate visually:
    //
    //   • A Rational on the wire is MAJOR TYPE 6 (= "this is a tag") with
    //     TAG NUMBER 6 (= "this tag means Rational").  Two 6s, different
    //     positions in the initial byte's bit layout.  Not the same 6.
    //   • TAG NUMBER 7 (TAG_REF below) is unrelated to MAJOR TYPE 7 (booleans
    //     / null / floats).  Tag NUMBER vs major TYPE; different concepts.
    //
    // Tag numbers are arbitrary identifiers.  Tag numbers 0-23 fit in the
    // initial byte's additional-info bits (1-byte tags); 24..255 take a
    // 1-byte follow; higher tag numbers take more bytes.  CG keeps within
    // the single-byte range for compactness.
    //
    // Booleans, null, undefined, and floats are HANDLED but DON'T APPEAR HERE
    // because they're not tags.  Major-type-7 handling is covered separately;
    // see the section "Major type 7: simple values" below.
    //
    // Two groups in the tag table:
    //   • Standard CBOR tags (RFC 8949), Tags 0-5 — interop primitives we
    //     recognize on decode and emit when appropriate.  Not "ours" — we
    //     follow the standard.  Prefix: STD_*.
    //   • CG-CBOR tags (ours), Tags 6-13 — semantic primitives defined by
    //     the CG-CBOR spec.  Tag 6 (RATIONAL) extends the numeric primitives
    //     immediately after the standard CBOR numeric tags (2/3 bignum,
    //     4 decimal, 5 bigfloat); the remaining tags carry CG-native shapes.
    //     Prefix: TAG_*.
    // ==================================================================================

    // ----- Standard CBOR tags we recognize (RFC 8949) -----

    /** CBOR standard Tag 0 — text date-time string (RFC 3339). Accepted on decode. */
    public static final int STD_DATETIME    = 0;

    /** CBOR standard Tag 1 — epoch-based date-time (numeric). Emitted for {@code Instant}. */
    public static final int STD_INSTANT     = 1;

    /** CBOR standard Tag 2 — positive bignum (unsigned big integer as byte string). */
    public static final int STD_POS_BIGNUM  = 2;

    /** CBOR standard Tag 3 — negative bignum. */
    public static final int STD_NEG_BIGNUM  = 3;

    /** CBOR standard Tag 4 — decimal fraction {@code [exp, mantissa]}. Natural form for {@code BigDecimal}. */
    public static final int STD_DECIMAL     = 4;

    /** CBOR standard Tag 5 — bigfloat {@code [exp, mantissa]}. Accepted on decode. */
    public static final int STD_BIGFLOAT    = 5;

    // ----- CG-CBOR tags (ours) -----

    /** CG-CBOR Tag 6 — RATIONAL: rational number {@code [numerator, denominator]}. */
    public static final int TAG_RATIONAL  = 6;

    /** CG-CBOR Tag 7 — REF: reference (ItemRef / ContentRef / DatumRef). */
    public static final int TAG_REF       = 7;

    /** CG-CBOR Tag 8 — BODY: Datum body shape {@code [head, [bindings]]}. */
    public static final int TAG_BODY      = 8;

    /** CG-CBOR Tag 9 — RECORD: Datum record shape {@code [head, [bindings], signature]}. */
    public static final int TAG_RECORD    = 9;

    /** CG-CBOR Tag 10 — SIG: signature bytes (varsig-encoded). */
    public static final int TAG_SIG       = 10;

    /** CG-CBOR Tag 11 — KEY: cryptographic key bytes (multikey-encoded). */
    public static final int TAG_KEY       = 11;

    /** CG-CBOR Tag 12 — REDACTED: Merkle elision marker carrying the preserved hash. */
    public static final int TAG_REDACTED  = 12;

    /** CG-CBOR Tag 13 — ENCRYPTED: encrypted-envelope marker. */
    public static final int TAG_ENCRYPTED = 13;

    /** CG-CBOR tag for compressed-target envelopes (non-lossy, hash-preserving). */
    public static final int TAG_COMPRESSED = 14;

    // ==================================================================================
    // Major type 7: simple values + floats
    //
    // Not tags.  CBOR's major-type-7 single-byte encodings cover booleans,
    // null, undefined, and IEEE 754 floats.  No constants here — the CBOR
    // library handles these natively; toCbor/fromCbor dispatch directly on
    // the Java type (Boolean, null) without any tag wrapping.
    //
    //   sub-code 20 (0xF4)  false       — handled (toCbor / fromCbor)
    //   sub-code 21 (0xF5)  true        — handled (toCbor / fromCbor)
    //   sub-code 22 (0xF6)  null        — handled (toCbor emits Null, fromCbor returns null)
    //   sub-code 23 (0xF7)  undefined   — NOT handled; emit never, decode would throw
    //   sub-code 25 (0xF9)  float16     — NOT handled; CG forbids IEEE 754 floats
    //   sub-code 26 (0xFA)  float32     — NOT handled
    //   sub-code 27 (0xFB)  float64     — NOT handled; explicitly rejected by
    //                                     CANONICAL encode options (float64=false)
    //
    // CG-CBOR is float-free by policy: numeric magnitudes use Long, BigInteger,
    // BigDecimal, or Rational.  Incoming wire bytes containing a float would
    // hit the `default` branch of fromCbor's type switch and throw — that's
    // intentional.  See QuantityVocabulary for the numeric model.
    // ==================================================================================

    private static final CBOREncodeOptions CANONICAL =
            new CBOREncodeOptions("useIndefLengthStrings=false;allowduplicatekeys=false;float64=false");

    // ==================================================================================
    // Public API — encode
    // ==================================================================================

    /** Encode a value to canonical CG-CBOR bytes. */
    public static byte[] encode(Object value) {
        return toCbor(value).EncodeToBytes(CANONICAL);
    }

    /** Encode a value to text form: multibase-encoded CG-CBOR bytes. */
    public static String encodeText(Object value) {
        byte[] bytes = encode(value);
        return io.ipfs.multibase.Multibase.encode(io.ipfs.multibase.Multibase.Base.Base32, bytes);
    }

    // ==================================================================================
    // Public API — decode
    // ==================================================================================

    /**
     * Decode bytes to a typed value, dispatching on CBOR shape. Returns the
     * natural Java type for the encoded value: {@link Datum} (Body or Record)
     * for a Tag-12 array, {@link HashID} for a Tag-6 byte string,
     * {@link Instant} for a Tag-1 integer, primitives ({@link String},
     * {@link Long}, {@link Boolean}, {@code byte[]}) for bare CBOR primitives,
     * {@link List} for a bare CBOR array, etc.
     */
    public static Object decode(byte[] bytes) {
        return fromCbor(CBORObject.DecodeFromBytes(bytes));
    }

    /** Decode bytes to a typed value of the requested class, throwing on mismatch. */
    public static <T> T decode(byte[] bytes, Class<T> type) {
        Object value = decode(bytes);
        if (value == null || !type.isInstance(value)) {
            throw new IllegalArgumentException(
                    "Decoded value of type " + (value == null ? "null" : value.getClass().getName())
                            + " is not assignable to " + type.getName());
        }
        return type.cast(value);
    }

    /** Decode a text form back to a typed value. */
    public static Object decodeText(String text) {
        byte[] bytes = io.ipfs.multibase.Multibase.decode(text);
        return decode(bytes);
    }

    // ==================================================================================
    // Public API — introspection
    // ==================================================================================

    /** Pretty-printed representation of a value, primarily for debugging. */
    public static String prettyPrint(Object value) {
        StringBuilder sb = new StringBuilder();
        prettyPrintNode(CanonWalker.walk(value), sb, 0);
        return sb.toString();
    }

    /** Whether the bytes parse as a syntactically valid CBOR value. */
    public static boolean isValid(byte[] bytes) {
        try {
            CBORObject.DecodeFromBytes(bytes);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Open a streaming parser that buffers bytes fed via
     * {@link StreamParser#feed(byte[])} and dispatches each whole top-level
     * CBOR value to {@code onValue} (typed: Body, Record, HashID, String,
     * Number, Boolean, {@link Encrypted}).  Parse failures go to
     * {@code onError}.
     */
    public static StreamParser parseStream(Consumer<Object> onValue, Consumer<Throwable> onError) {
        return new CgCborStreamParser(onValue, onError);
    }

    // ==================================================================================
    // Encoding-interface adapter
    //
    // The static API above is the fast path for code that knows it wants
    // CG-CBOR-v1 specifically (bootstrap, ID derivation, etc.). The codec()
    // method returns a polymorphic {@link Encoding} reference, useful for code
    // that holds a configurable encoder (e.g. a Librarian's preferred encoding).
    // ==================================================================================

    /**
     * The CG-CBOR-v1 codec as an {@link Encoding} instance — for code that
     * holds a configurable encoder (such as a Librarian's preferred encoding).
     * The returned instance is a singleton adapter; it shares no state.
     */
    public static Encoding codec() {
        return CodecAdapter.INSTANCE;
    }

    private static final class CodecAdapter implements Encoding {
        static final CodecAdapter INSTANCE = new CodecAdapter();

        @Override public ItemRef encoding() { return ItemRef.iid(Encoding.CgCborV1.KEY); }
        @Override public byte formatCode() { return (byte) Encoding.CgCborV1.FORMAT_CODE; }
        @Override public byte[] encode(Object value) { return CgCbor.encode(value); }
        @Override public Object decode(byte[] bytes) { return CgCbor.decode(bytes); }
        @Override public String encodeText(Object value) { return CgCbor.encodeText(value); }
        @Override public Object decodeText(String text) { return CgCbor.decodeText(text); }
        @Override public Node walk(Object value) { return CanonWalker.walk(value); }
        @Override public Node walk(byte[] bytes) { return CanonWalker.walk(decode(bytes)); }
        @Override public String prettyPrint(Object value) { return CgCbor.prettyPrint(value); }
        @Override public boolean isValid(byte[] bytes) { return CgCbor.isValid(bytes); }
        @Override public StreamParser parseStream(Consumer<Object> onValue, Consumer<Throwable> onError) { return CgCbor.parseStream(onValue, onError); }
    }

    // ==================================================================================
    // Internal — type dispatch: Object → CBORObject
    // ==================================================================================

    /**
     * Build a {@link CBORObject} tree for the given value without serializing
     * to bytes. Useful for callers composing larger CBOR structures.
     */
    public static CBORObject toCbor(Object value) {
        if (value == null) return CBORObject.Null;
        return switch (value) {
            case String s    -> CBORObject.FromString(s);
            case Long l      -> CBORObject.FromInt64(l);
            case Integer i   -> CBORObject.FromInt64(i.longValue());
            case Boolean b   -> b ? CBORObject.True : CBORObject.False;
            case byte[] b    -> CBORObject.FromByteArray(b);
            case Instant i   -> CBORObject.FromCBORObjectAndTag(
                                    CBORObject.FromInt64(i.toEpochMilli()), STD_INSTANT);
            case java.math.BigInteger bi -> encodeBigInteger(bi);
            case java.math.BigDecimal bd -> encodeBigDecimal(bd);
            case dev.everydaythings.graph.value.Rational r -> encodeRational(r);
            case HashID r        -> encodeRef(r);
            case Datum d         -> encodeDatum(d);
            case Opaque op       -> encodeOpaque(op);
            case BindingTarget t -> encodeBindingTarget(t);
            case List<?> list -> {
                CBORObject arr = CBORObject.NewArray();
                for (Object e : list) arr.Add(toCbor(e));
                yield arr;
            }
            case java.util.Map<?,?> m -> {
                CBORObject map = CBORObject.NewMap();
                for (java.util.Map.Entry<?,?> e : m.entrySet()) {
                    map.Add(toCbor(e.getKey()), toCbor(e.getValue()));
                }
                yield map;
            }
            default -> encodeGeneric(value);
        };
    }

    /**
     * Generic annotation-driven encode. Two paths:
     *
     * <ul>
     *   <li>{@code @Encode byte[]} → CBOR byte string (terminal leaf).</li>
     *   <li>{@code @Encode String} → CBOR text string (terminal leaf).</li>
     *   <li>{@code @Encode <any-other-type>} → recursively encode the
     *       returned value (the codec applies the right framing for THAT
     *       type — transparent wrappers like Sememe / Text fall here).</li>
     *   <li>{@code @Layout(ARRAY|MAP)} → walk {@code @Order} fields and
     *       emit as CBOR array (or map).</li>
     * </ul>
     */
    private static CBORObject encodeGeneric(Object value) {
        Class<?> cls = value.getClass();

        // @Encode-annotated leaf or transparent wrapper
        java.lang.reflect.Method encode = Leaves.findAnyEncode(cls);
        if (encode != null) {
            Object result = Leaves.invokeEncode(value, encode);
            if (result instanceof byte[] b) return CBORObject.FromByteArray(b);
            if (result instanceof String s) return CBORObject.FromString(s);
            return toCbor(result);  // recurse — codec frames per type
        }

        // @Layout-annotated structure → walk @Order fields
        Layout layout = cls.getAnnotation(Layout.class);
        if (layout != null) {
            return encodeStructure(value, cls, layout);
        }

        throw new IllegalArgumentException(
                "CgCbor cannot encode value of type " + cls.getName());
    }

    /**
     * Walk {@code @Order} fields on the given class and emit the values as a
     * CBOR array (or map, when MAP layout). Used for structure types like
     * Binding and CompoundKey.
     */
    private static CBORObject encodeStructure(Object value, Class<?> cls, Layout layout) {
        List<Field> ordered = orderedFields(cls);
        if (layout.value() == Layout.Kind.MAP) {
            CBORObject map = CBORObject.NewMap();
            for (java.lang.reflect.Field f : ordered) {
                Object v = readField(value, f);
                if (v == null) continue;
                map.Add(CBORObject.FromString(f.getName()), toCbor(v));
            }
            return map;
        }
        // ARRAY layout: collect, trim trailing nulls, then emit. Trailing optional
        // fields take zero bytes when absent. Matches Walker.walkStructure exactly
        // so canonical hash and CBOR wire form stay aligned.
        List<Object> values = new ArrayList<>(ordered.size());
        for (Field f : ordered) {
            values.add(readField(value, f));
        }
        while (!values.isEmpty() && values.get(values.size() - 1) == null) {
            values.remove(values.size() - 1);
        }
        CBORObject arr = CBORObject.NewArray();
        for (Object v : values) arr.Add(toCbor(v));
        return arr;
    }

    private static List<Field> orderedFields(Class<?> cls) {
        List<Field> result = new ArrayList<>();
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(dev.everydaythings.graph.canonical.Order.class)) {
                    f.setAccessible(true);
                    result.add(f);
                }
            }
        }
        result.sort(java.util.Comparator.comparingInt(
                f -> f.getAnnotation(dev.everydaythings.graph.canonical.Order.class).value()));
        return result;
    }

    private static Object readField(Object value, java.lang.reflect.Field f) {
        try {
            return f.get(value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Cannot read @Order field " + f.getName() + " on "
                    + value.getClass().getName(), e);
        }
    }

    private static CBORObject encodeDatum(Datum d) {
        CBORObject arr = CBORObject.NewArray();
        arr.Add(toCbor(d.head()));
        CBORObject entries = CBORObject.NewArray();
        for (dev.everydaythings.graph.datum.DatumNode e : d.entries()) entries.Add(toCbor(e));
        arr.Add(entries);
        int tag;
        if (d instanceof Record r) {
            arr.Add(CBORObject.FromByteArray(r.signature()));
            tag = TAG_RECORD;
        } else {
            tag = TAG_BODY;
        }
        return CBORObject.FromCBORObjectAndTag(arr, tag);
    }

    /** Wrap a HashID's payload bytes as CBOR Tag REF. */
    private static CBORObject encodeRef(HashID ref) {
        return CBORObject.FromCBORObjectAndTag(
                CBORObject.FromByteArray(ref.toRefBytes()), TAG_REF);
    }

    /**
     * Encode a {@link java.math.BigInteger} as a CBOR integer if it fits in
     * an int64, otherwise as a CBOR-standard bignum (Tag 2 or Tag 3).
     */
    private static CBORObject encodeBigInteger(java.math.BigInteger bi) {
        return CBORObject.FromEInteger(
                com.upokecenter.numbers.EInteger.FromString(bi.toString()));
    }

    /**
     * Encode a {@link java.math.BigDecimal} as CBOR Tag 4
     * {@code [exp, mantissa]} (standard CBOR decimal fraction).
     */
    private static CBORObject encodeBigDecimal(java.math.BigDecimal bd) {
        CBORObject arr = CBORObject.NewArray();
        arr.Add(CBORObject.FromInt32(-bd.scale()));            // exponent = -scale
        arr.Add(encodeBigInteger(bd.unscaledValue()));         // mantissa
        return CBORObject.FromCBORObjectAndTag(arr, STD_DECIMAL);
    }

    /**
     * Encode a {@link dev.everydaythings.graph.value.Rational} as CBOR Tag
     * RATIONAL wrapping a 2-element array {@code [numerator, denominator]}.
     */
    private static CBORObject encodeRational(
            dev.everydaythings.graph.value.Rational r) {
        CBORObject arr = CBORObject.NewArray();
        arr.Add(encodeBigInteger(r.numerator()));
        arr.Add(encodeBigInteger(r.denominator()));
        return CBORObject.FromCBORObjectAndTag(arr, TAG_RATIONAL);
    }

    // ==================================================================================
    // CompoundKey decoding
    // ==================================================================================

    /**
     * Decode a CompoundKey from its CBOR array form: {@code [Tag6(head), [qualifiers]]}.
     * Element 0 is a Tag-6 ItemRef (the head). Element 1 is a CBOR array of
     * qualifiers — each Tag-6 (sememe) or CBOR text string (text qualifier).
     */
    public static CompoundKey decodeCompoundKey(CBORObject node) {
        if (node == null || node.getType() != CBORType.Array || node.size() != 2) {
            throw new IllegalArgumentException(
                    "CompoundKey requires a 2-element CBOR array [head, [qualifiers]], got "
                            + (node == null ? "null" : node.getType() + (node.getType() == CBORType.Array
                                    ? " of size " + node.size() : "")));
        }
        HashID head = expectIidRef(node.get(0), "CompoundKey head");
        CBORObject qualsArr = node.get(1);
        if (qualsArr.getType() != CBORType.Array) {
            throw new IllegalArgumentException(
                    "CompoundKey qualifiers must be a CBOR array, got " + qualsArr.getType());
        }
        List<dev.everydaythings.graph.datum.DatumNode> parts = new ArrayList<>(qualsArr.size());
        for (int i = 0; i < qualsArr.size(); i++) {
            parts.add(decodePart(qualsArr.get(i)));
        }
        return CompoundKey.of(head, parts);
    }

    /**
     * Decode a single CompoundKey part — sememe qualifier (Tag 6), text
     * qualifier (CBOR text string), or Opaque stand-in (Tag 11 / 13 / 14).
     */
    private static dev.everydaythings.graph.datum.DatumNode decodePart(CBORObject node) {
        if (node.isTagged()) {
            int tag = node.getMostOuterTag().ToInt32Checked();
            if (tag == TAG_REF) {
                return new CompoundKey.Sememe(expectItemRef(node, "CompoundKey sememe qualifier"));
            }
            if (Opaque.isOpaqueTag(tag)) {
                return Opaque.fromCborTree(node);
            }
        }
        if (node.getType() == CBORType.TextString) {
            return new CompoundKey.Text(node.AsString());
        }
        throw new IllegalArgumentException(
                "CompoundKey qualifier must be Tag 6 (sememe), text string, or "
                        + "Opaque tag (11/13/14); got: " + node.getType());
    }

    private static ItemRef expectItemRef(CBORObject node, String context) {
        if (!node.isTagged() || !node.HasMostOuterTag(TAG_REF)) {
            throw new IllegalArgumentException(
                    context + " must be Tag 6 (ItemRef), got: " + node);
        }
        HashID ref = HashID.fromCborTree(node);
        if (ref.variant() != HashID.Variant.ITEM) {
            throw new IllegalArgumentException(
                    context + " must be an ItemRef (prefix '@'), got: " + ref.variant());
        }
        return ((ItemRef) ref).iid();
    }

    /**
     * Decode an IID-family reference (ItemRef, TypeRef, or SchemaRef) — i.e.,
     * anything in the @/?/! family.  Used for compound-key heads and other
     * positions that accept the operational-mode variants.
     */
    private static HashID expectIidRef(CBORObject node, String context) {
        if (!node.isTagged() || !node.HasMostOuterTag(TAG_REF)) {
            throw new IllegalArgumentException(
                    context + " must be Tag 6 (ref), got: " + node);
        }
        HashID ref = HashID.fromCborTree(node);
        return switch (ref.variant()) {
            case ITEM, TYPE, SCHEMA -> ref;
            default -> throw new IllegalArgumentException(
                    context + " must be in the IID family (@ / ? / !), got: " + ref.variant());
        };
    }

    private static CBORObject encodeBindingTarget(Object t) {
        return switch (t) {
            case BindingTarget.RefTarget rt -> CBORObject.FromCBORObjectAndTag(
                    CBORObject.FromByteArray(rt.asReference().toRefBytes()), TAG_REF);
            case BindingTarget.FrameTarget ft -> encodeDatum(ft.body());
            default -> throw new IllegalArgumentException(
                    "Unsupported BindingTarget: " + t.getClass().getName());
        };
    }

    /**
     * Encode an {@link Opaque} variant.  Wire shape is uniform: {@code
     * Tag(X)[hash, payload?, refs]} — 2-array for Redacted (no payload),
     * 3-array for Compressed and Encrypted.
     */
    private static CBORObject encodeOpaque(Opaque op) {
        CBORObject arr = CBORObject.NewArray();
        arr.Add(CBORObject.FromByteArray(op.wrappedHash()));
        int tag;
        if (op instanceof Opaque.Compressed c) {
            arr.Add(CBORObject.FromByteArray(c.compressedPayload()));
            tag = TAG_COMPRESSED;
        } else if (op instanceof Opaque.Encrypted e) {
            arr.Add(CBORObject.FromByteArray(e.ciphertext()));
            tag = TAG_ENCRYPTED;
        } else {
            // Opaque.Redacted — sealed, only remaining case
            tag = TAG_REDACTED;
        }
        CBORObject refs = CBORObject.NewArray();
        for (HashID ref : op.recordRefs()) {
            refs.Add(encodeRef(ref));
        }
        arr.Add(refs);
        return CBORObject.FromCBORObjectAndTag(arr, tag);
    }

    // ==================================================================================
    // Internal — type dispatch: CBORObject → Object
    // ==================================================================================

    /**
     * Decode a {@link CBORObject} tree to its typed Java value — the inverse
     * of {@link #toCbor(Object)}. Package-private: streaming parsers in this
     * package call this directly to avoid re-encoding through bytes.
     */
    static Object fromCbor(CBORObject node) {
        if (node == null || node.isNull()) return null;
        if (node.isTagged()) {
            int tag = node.getMostOuterTag().ToInt32Checked();
            return switch (tag) {
                case STD_INSTANT     -> Instant.ofEpochMilli(node.UntagOne().AsInt64Value());
                case STD_POS_BIGNUM,
                     STD_NEG_BIGNUM  -> decodeBigInteger(node);
                case STD_DECIMAL     -> decodeBigDecimal(node);
                case TAG_RATIONAL    -> decodeRational(node);
                case TAG_REF         -> HashID.fromCborTree(node);
                case TAG_BODY        -> Body.fromCborTree(node);
                case TAG_RECORD      -> Record.fromCborTree(node);
                case TAG_REDACTED    -> Opaque.Redacted.fromCborTree(node);
                case TAG_COMPRESSED  -> Opaque.Compressed.fromCborTree(node);
                case TAG_ENCRYPTED   -> Opaque.Encrypted.fromCborTree(node);
                default -> throw new IllegalArgumentException(
                        "Unrecognized CBOR tag: " + tag);
            };
        }
        return switch (node.getType()) {
            case TextString -> node.AsString();
            case Integer    -> node.AsInt64Value();
            case Boolean    -> node.AsBoolean();
            case ByteString -> node.GetByteString();
            case Array      -> decodeArray(node);
            case Map        -> decodeMap(node);
            default -> throw new IllegalArgumentException(
                    "Cannot decode CBOR type: " + node.getType());
        };
    }

    /** Decode a CBOR integer or Tag-2/Tag-3 bignum to a {@link java.math.BigInteger}. */
    private static java.math.BigInteger decodeBigInteger(CBORObject node) {
        return new java.math.BigInteger(node.AsEIntegerValue().toString());
    }

    /** Decode a CBOR Tag-4 {@code [exp, mantissa]} to a {@link java.math.BigDecimal}. */
    private static java.math.BigDecimal decodeBigDecimal(CBORObject tagged) {
        CBORObject inner = tagged.UntagOne();
        if (inner.getType() != CBORType.Array || inner.size() != 2) {
            throw new IllegalArgumentException(
                    "Tag-4 (decimal) payload must be a 2-element array");
        }
        int exp = inner.get(0).AsInt32();
        java.math.BigInteger mantissa = decodeBigInteger(inner.get(1));
        return new java.math.BigDecimal(mantissa, -exp);
    }

    /** Decode a CBOR Tag-RATIONAL to a {@link dev.everydaythings.graph.value.Rational}. */
    private static dev.everydaythings.graph.value.Rational decodeRational(CBORObject tagged) {
        CBORObject inner = tagged.UntagOne();
        if (inner.getType() != CBORType.Array || inner.size() != 2) {
            throw new IllegalArgumentException(
                    "Tag-" + TAG_RATIONAL + " (rational) payload must be a 2-element array");
        }
        return new dev.everydaythings.graph.value.Rational(
                decodeBigInteger(inner.get(0)),
                decodeBigInteger(inner.get(1)));
    }

    private static List<Object> decodeArray(CBORObject node) {
        List<Object> out = new ArrayList<>(node.size());
        for (CBORObject e : node.getValues()) out.add(fromCbor(e));
        return List.copyOf(out);
    }

    private static java.util.Map<Object, Object> decodeMap(CBORObject node) {
        java.util.LinkedHashMap<Object, Object> out = new java.util.LinkedHashMap<>();
        for (CBORObject key : node.getKeys()) {
            out.put(fromCbor(key), fromCbor(node.get(key)));
        }
        return java.util.Collections.unmodifiableMap(out);
    }

    // ==================================================================================
    // Pretty-print
    // ==================================================================================

    private static void prettyPrintNode(Node node, StringBuilder sb, int indent) {
        switch (node) {
            case Node.Leaf l -> sb.append(formatLeaf(l));
            case Node.Hashed h -> sb.append("redacted(").append(h.hash().length).append(" bytes)");
            case Node.Array a -> {
                sb.append("[\n");
                for (Node child : a.elements()) {
                    indent(sb, indent + 1);
                    prettyPrintNode(child, sb, indent + 1);
                    sb.append(",\n");
                }
                indent(sb, indent);
                sb.append("]");
            }
            case Node.Map m -> {
                sb.append("{\n");
                for (Node.Entry e : m.entries()) {
                    indent(sb, indent + 1);
                    prettyPrintNode(e.key(), sb, indent + 1);
                    sb.append(": ");
                    prettyPrintNode(e.value(), sb, indent + 1);
                    sb.append(",\n");
                }
                indent(sb, indent);
                sb.append("}");
            }
        }
    }

    private static String formatLeaf(Node.Leaf l) {
        Object v = l.value();
        if (v == null) return "null";
        if (v instanceof String s) return "\"" + s + "\"";
        if (v instanceof byte[] bs) return "bytes(" + bs.length + ")";
        return v.toString();
    }

    private static void indent(StringBuilder sb, int level) {
        for (int i = 0; i < level; i++) sb.append("  ");
    }
}
