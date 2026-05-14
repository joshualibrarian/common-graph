package dev.everydaythings.graph.encoding;

import dev.everydaythings.graph.canonical.Scope;

import com.upokecenter.cbor.CBOREncodeOptions;
import com.upokecenter.cbor.CBORObject;
import com.upokecenter.cbor.CBORType;
import dev.everydaythings.graph.canonical.Canonical;
import dev.everydaythings.graph.canonical.HashTree;
import dev.everydaythings.graph.canonical.Node;
import dev.everydaythings.graph.canonical.Walker;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.BindingTarget;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Datum;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.value.Literal;
import dev.everydaythings.graph.id.CompoundKey;
import dev.everydaythings.graph.id.ItemID;
import dev.everydaythings.graph.id.Reference;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Common Graph's deterministic CBOR codec — bytes in, bytes out.
 *
 * <p>CgCbor handles only the wire-level concern: turning typed values into
 * canonical CG-CBOR byte sequences and back. The {@link Walker} owns
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
 * additional ContentIDs for the same DatumID (wasteful, not broken).
 *
 * <p>There is no {@code Scope} parameter. Body and Record are distinct types;
 * if you want body-only bytes, encode a Body; if you want full record bytes,
 * encode a Record.
 */
public final class CgCbor {

    private CgCbor() {}

    // ==================================================================================
    // CBOR tag layout (wire-level)
    // ==================================================================================

    /** CBOR standard tag — epoch-time integer. */
    public static final int TAG_INSTANT  = 1;
    /** CG-CBOR Tag 6 — Reference (item / content / frame). */
    public static final int TAG_REF      = 6;
    /** CG-CBOR Tag 7 — typed value envelope. */
    public static final int TAG_VALUE    = 7;
    /** CG-CBOR Tag 8 — signed envelope. */
    public static final int TAG_SIG      = 8;
    /** CG-CBOR Tag 9 — quantity (magnitude + unit). */
    public static final int TAG_QTY      = 9;
    /** CG-CBOR Tag 10 — encrypted envelope. */
    public static final int TAG_ENCRYPTED = 10;
    /** CG-CBOR Tag 11 — Merkle elision (redaction marker). */
    public static final int TAG_REDACTED = 11;
    /** CG-CBOR Tag 12 — Datum (head + bindings, optionally with signature). */
    public static final int TAG_DATUM    = 12;

    private static final CBOREncodeOptions CANONICAL =
            CBOREncodeOptions.DefaultCtap2Canonical;

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
     * for a Tag-12 array, {@link Reference} for a Tag-6 byte string,
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
        prettyPrintNode(Walker.walk(value), sb, 0);
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

        @Override public ItemID encoding() { return Encoding.CgCborV1.IID; }
        @Override public byte formatCode() { return (byte) Encoding.CgCborV1.FORMAT_CODE; }
        @Override public byte[] encode(Object value) { return CgCbor.encode(value); }
        @Override public Object decode(byte[] bytes) { return CgCbor.decode(bytes); }
        @Override public String encodeText(Object value) { return CgCbor.encodeText(value); }
        @Override public Object decodeText(String text) { return CgCbor.decodeText(text); }
        @Override public Node walk(Object value) { return Walker.walk(value); }
        @Override public Node walk(byte[] bytes) { return Walker.walk(decode(bytes)); }
        @Override public String prettyPrint(Object value) { return CgCbor.prettyPrint(value); }
        @Override public boolean isValid(byte[] bytes) { return CgCbor.isValid(bytes); }
    }

    // ==================================================================================
    // Internal — type dispatch: Object → CBORObject
    // ==================================================================================

    private static CBORObject toCbor(Object value) {
        if (value == null) return CBORObject.Null;
        return switch (value) {
            case String s    -> CBORObject.FromString(s);
            case Long l      -> CBORObject.FromInt64(l);
            case Integer i   -> CBORObject.FromInt64(i.longValue());
            case Boolean b   -> b ? CBORObject.True : CBORObject.False;
            case byte[] b    -> CBORObject.FromByteArray(b);
            case Instant i   -> CBORObject.FromCBORObjectAndTag(
                                    CBORObject.FromInt64(i.toEpochMilli()), TAG_INSTANT);
            case ItemID id   -> CBORObject.FromByteArray(id.encodeBinary());
            case Reference r -> CBORObject.FromCBORObjectAndTag(
                                    CBORObject.FromByteArray(r.toRefBytes()), TAG_REF);
            case Datum d     -> encodeDatum(d);
            case Binding b   -> encodeBinding(b);
            case Literal lit -> encodeLiteral(lit);
            case BindingTarget t -> encodeBindingTarget(t);
            case CompoundKey.FrameToken t -> t.toCbor();
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
            default -> throw new IllegalArgumentException(
                    "CgCbor cannot encode value of type " + value.getClass().getName());
        };
    }

    private static CBORObject encodeDatum(Datum d) {
        CBORObject arr = CBORObject.NewArray();
        arr.Add(toCbor(d.head()));
        CBORObject bindings = CBORObject.NewArray();
        for (Binding b : d.bindings()) bindings.Add(encodeBinding(b));
        arr.Add(bindings);
        if (d instanceof Record r) {
            arr.Add(CBORObject.FromByteArray(r.signature()));
        }
        return CBORObject.FromCBORObjectAndTag(arr, TAG_DATUM);
    }

    private static CBORObject encodeBinding(Binding b) {
        CBORObject arr = CBORObject.NewArray();
        arr.Add(CBORObject.FromByteArray(b.role().encodeBinary()));
        CBORObject quals = CBORObject.NewArray();
        for (CompoundKey.FrameToken q : b.qualifiers()) quals.Add(q.toCbor());
        arr.Add(quals);
        arr.Add(encodeBindingTarget(b.target()));
        return arr;
    }

    private static CBORObject encodeBindingTarget(BindingTarget t) {
        return switch (t) {
            case BindingTarget.RefTarget rt -> CBORObject.FromCBORObjectAndTag(
                    CBORObject.FromByteArray(rt.asReference().toRefBytes()), TAG_REF);
            case BindingTarget.FrameTarget ft -> CBORObject.FromCBORObjectAndTag(
                    ft.body().toCborTree(Scope.BODY), TAG_DATUM);
            case BindingTarget.RedactedTarget rt -> CBORObject.FromCBORObjectAndTag(
                    CBORObject.FromByteArray(rt.wrappedHash()), TAG_REDACTED);
            case Literal lit -> encodeLiteral(lit);
            default -> throw new IllegalArgumentException(
                    "Unsupported BindingTarget: " + t.getClass().getName());
        };
    }

    private static CBORObject encodeLiteral(Literal lit) {
        CBORObject inner = CBORObject.DecodeFromBytes(lit.payload());
        if (Literal.TYPE_INSTANT.equals(lit.valueType())) {
            return CBORObject.FromCBORObjectAndTag(inner, TAG_INSTANT);
        }
        return inner;
    }

    // ==================================================================================
    // Internal — type dispatch: CBORObject → Object
    // ==================================================================================

    private static Object fromCbor(CBORObject node) {
        if (node == null || node.isNull()) return null;
        if (node.isTagged()) {
            int tag = node.getMostOuterTag().ToInt32Checked();
            return switch (tag) {
                case TAG_INSTANT  -> Instant.ofEpochMilli(node.UntagOne().AsInt64Value());
                case TAG_REF      -> Reference.fromCborTree(node);
                case TAG_DATUM    -> decodeDatum(node);
                case TAG_REDACTED -> BindingTarget.RedactedTarget.fromCborTree(node);
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

    private static Datum decodeDatum(CBORObject tagged) {
        CBORObject inner = tagged.UntagOne();
        if (inner.getType() != CBORType.Array) {
            throw new IllegalArgumentException(
                    "Tag-12 (Datum) payload must be an array, got " + inner.getType());
        }
        return switch (inner.size()) {
            case 2 -> Body.fromCborTree(tagged);
            case 3 -> Record.fromCborTree(tagged);
            default -> throw new IllegalArgumentException(
                    "Datum array must have 2 (Body) or 3 (Record) elements, got " + inner.size());
        };
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
