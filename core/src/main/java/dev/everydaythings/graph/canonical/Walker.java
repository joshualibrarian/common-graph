package dev.everydaythings.graph.canonical;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.BindingTarget;
import dev.everydaythings.graph.datum.Datum;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.id.CompoundKey;
import dev.everydaythings.graph.id.ContentRef;
import dev.everydaythings.graph.id.DatumRef;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.id.HashID;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Protocol-level walker: turns any value into a {@link Node} tree.
 *
 * <p>The walk is encoder-agnostic — there's one canonical way to expose a
 * value's structure for hashing, inspection, and identity. Encoders (CG-CBOR,
 * a hypothetical CG-JSON, etc.) consume the same Node tree when they want it,
 * but the walk itself is theirs to use, not theirs to define.
 *
 * <p>Walker owns:
 * <ul>
 *   <li>Type dispatch — which Java types produce which {@link Node} shapes.</li>
 *   <li>Leaf-type discriminators — the 1-byte prefix on each leaf's {@code rawBytes}
 *       that distinguishes kinds of atomic values. These are part of the identity
 *       protocol and must never change.</li>
 * </ul>
 *
 * <p>Walker does NOT touch encoder-specific wire bytes. It produces Node trees;
 * encoders take it from there.
 */
public final class Walker {

    private Walker() {}

    // ==================================================================================
    // Leaf-type discriminators (embedded as the first byte of every Leaf's rawBytes)
    //
    // These bytes are part of the identity protocol — every DatumRef containing a
    // leaf of that type depends on the byte value. They must remain stable. Add
    // new ones as needed; never renumber existing ones.
    // ==================================================================================

    /** Boolean leaf: {@code [0x01, 0x00|0x01]}. */
    public static final byte LEAF_BOOLEAN = 0x01;
    /** Integer leaf: {@code [0x02, <8-byte big-endian signed long>]}. */
    public static final byte LEAF_INTEGER = 0x02;
    /** String leaf: {@code [0x03, <UTF-8 bytes>]}. */
    public static final byte LEAF_STRING  = 0x03;
    /** Bytes leaf: {@code [0x04, <raw bytes>]}. */
    public static final byte LEAF_BYTES   = 0x04;
    /** Instant leaf: {@code [0x05, <8-byte big-endian signed millis>]}. */
    public static final byte LEAF_INSTANT = 0x05;
    /** ItemRef leaf: {@code [0x10, <multihash bytes>]}. */
    public static final byte LEAF_ITEM_ID = 0x10;
    /** HashID leaf: {@code [0x11, <ref-bytes (prefix + multihash + sub-parts)>]}. */
    public static final byte LEAF_REFERENCE = 0x11;
    /** BigInteger leaf: {@code [0x12, <two's-complement bytes>]}. */
    public static final byte LEAF_BIGINT = 0x12;

    // ==================================================================================
    // Public API
    // ==================================================================================

    /**
     * Walk a typed value as a {@link Node} tree.
     *
     * <p>Dispatch is on the runtime class of the value. Standard primitives
     * ({@link String}, {@link Long}, {@link Boolean}, {@code byte[]},
     * {@link Instant}) produce {@link Node.Leaf} nodes. CG types ({@link Datum},
     * {@link Binding}, {@link BindingTarget}, {@link HashID}, {@link ItemRef})
     * produce the appropriate composite or leaf forms.
     * {@link List} and {@link java.util.Map} produce {@link Node.Array} and
     * {@link Node.Map} respectively.
     */
    public static Node walk(Object value) {
        if (value == null) return Node.leaf(null, new byte[]{0x00});
        return switch (value) {
            case String s     -> leafString(s);
            case Long l       -> leafLong(l);
            case Integer i    -> leafLong(i.longValue());
            case Boolean b    -> leafBoolean(b);
            case byte[] bs    -> leafBytes(bs);
            case Instant ins  -> leafInstant(ins);
            case java.math.BigInteger bi -> leafBigInteger(bi);
            case java.math.BigDecimal bd -> walkBigDecimal(bd);
            case dev.everydaythings.graph.value.Rational r -> walkRational(r);
            case HashID r        -> leafReference(r);
            case Datum d         -> walkDatum(d);
            case BindingTarget t -> walkBindingTarget(t);
            case List<?> list    -> walkList(list);
            case java.util.Map<?,?> m -> walkMap(m);
            default -> walkGeneric(value);
        };
    }

    /**
     * Generic annotation-driven walk. Mirrors {@code CgCbor.encodeGeneric}:
     *
     * <ul>
     *   <li>{@code @Encode} → invoke, recursively walk the returned value
     *       (transparent wrappers like Sememe / Text fall here).</li>
     *   <li>{@code @Layout(ARRAY|MAP)} → walk {@code @Order} fields as an
     *       array (or map) Node.</li>
     * </ul>
     */
    private static Node walkGeneric(Object value) {
        Class<?> cls = value.getClass();
        java.lang.reflect.Method encode = Leaves.findAnyEncode(cls);
        if (encode != null) {
            Object result = Leaves.invokeEncode(value, encode);
            return walk(result);
        }
        Layout layout = cls.getAnnotation(Layout.class);
        if (layout != null) {
            return walkStructure(value, cls, layout);
        }
        throw new IllegalArgumentException(
                "Walker cannot walk value of type " + cls.getName());
    }

    private static Node walkStructure(Object value, Class<?> cls, Layout layout) {
        java.util.List<java.lang.reflect.Field> ordered = orderedFields(cls);
        if (layout.value() == Layout.Kind.MAP) {
            java.util.List<Node.Entry> entries = new ArrayList<>(ordered.size());
            for (java.lang.reflect.Field f : ordered) {
                Object v = readField(value, f);
                if (v == null) continue;
                entries.add(new Node.Entry(leafString(f.getName()), walk(v)));
            }
            return new Node.Map(entries);
        }
        // ARRAY layout: collect raw field values, trim trailing nulls so optional
        // trailing fields take zero canonical-encoding space when absent. A class
        // whose @Order fields are never null behaves identically to before.
        java.util.List<Object> values = new ArrayList<>(ordered.size());
        for (java.lang.reflect.Field f : ordered) {
            values.add(readField(value, f));
        }
        while (!values.isEmpty() && values.get(values.size() - 1) == null) {
            values.remove(values.size() - 1);
        }
        java.util.List<Node> children = new ArrayList<>(values.size());
        for (Object v : values) children.add(walk(v));
        return Node.array(children);
    }

    private static java.util.List<java.lang.reflect.Field> orderedFields(Class<?> cls) {
        java.util.List<java.lang.reflect.Field> result = new ArrayList<>();
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(Order.class)) {
                    f.setAccessible(true);
                    result.add(f);
                }
            }
        }
        result.sort(java.util.Comparator.comparingInt(
                f -> f.getAnnotation(Order.class).value()));
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

    /**
     * Walk just the body-part of a Datum (head + bindings, no signature). For a
     * {@link dev.everydaythings.graph.datum.Body} this is the same as
     * {@link #walk(Object)}; for a {@link Record} this drops the signature so
     * the tree matches what a Body with the same head + bindings would produce.
     *
     * <p>Used as the signing payload for a Record: the signature attests to the
     * body-part, not to itself.
     */
    public static Node walkBodyPart(Datum d) {
        List<Node> bindingNodes = new ArrayList<>(d.bindings().size());
        for (Binding b : d.bindings()) bindingNodes.add(walk(b));
        return Node.array(walk(d.head()), Node.array(bindingNodes));
    }

    // ==================================================================================
    // Internal — composite walkers
    // ==================================================================================

    private static Node walkDatum(Datum d) {
        List<Node> children = new ArrayList<>(3);
        children.add(walk(d.head()));
        List<Node> bindingNodes = new ArrayList<>(d.bindings().size());
        for (Binding b : d.bindings()) bindingNodes.add(walk(b));
        children.add(Node.array(bindingNodes));
        if (d instanceof Record r) {
            children.add(leafBytes(r.signature()));
        }
        return Node.array(children);
    }

    private static Node walkBindingTarget(Object t) {
        return switch (t) {
            case BindingTarget.RefTarget rt -> leafRefBytes(rt.asReference(), rt.asReference().toRefBytes());
            case BindingTarget.FrameTarget ft -> walkDatum(ft.body());
            case BindingTarget.RedactedTarget rt -> new Node.Hashed(rt.wrappedHash());
            default -> throw new IllegalArgumentException(
                    "Unsupported BindingTarget: " + t.getClass().getName());
        };
    }

    private static Node walkList(List<?> list) {
        List<Node> children = new ArrayList<>(list.size());
        for (Object e : list) children.add(walk(e));
        return Node.array(children);
    }

    private static Node walkMap(java.util.Map<?,?> map) {
        List<Node.Entry> entries = new ArrayList<>(map.size());
        for (java.util.Map.Entry<?,?> e : map.entrySet()) {
            entries.add(Node.entry(walk(e.getKey()), walk(e.getValue())));
        }
        return Node.map(entries);
    }

    // ==================================================================================
    // Leaf factories — encoding-tagged byte forms
    // ==================================================================================

    private static Node.Leaf leafString(String s) {
        byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
        return Node.leaf(s, prefixed(LEAF_STRING, utf8));
    }

    private static Node.Leaf leafLong(long v) {
        byte[] bytes = ByteBuffer.allocate(8).putLong(v).array();
        return Node.leaf(v, prefixed(LEAF_INTEGER, bytes));
    }

    private static Node.Leaf leafBoolean(boolean v) {
        return Node.leaf(v, new byte[]{LEAF_BOOLEAN, v ? (byte) 0x01 : (byte) 0x00});
    }

    private static Node.Leaf leafBytes(byte[] bytes) {
        return Node.leaf(bytes.clone(), prefixed(LEAF_BYTES, bytes));
    }

    private static Node.Leaf leafInstant(Instant instant) {
        byte[] bytes = ByteBuffer.allocate(8).putLong(instant.toEpochMilli()).array();
        return Node.leaf(instant, prefixed(LEAF_INSTANT, bytes));
    }

    private static Node.Leaf leafBigInteger(java.math.BigInteger bi) {
        return Node.leaf(bi, prefixed(LEAF_BIGINT, bi.toByteArray()));
    }

    private static Node walkBigDecimal(java.math.BigDecimal bd) {
        // Canonical form: [exp, mantissa] — exp = -scale, mantissa is a BigInteger.
        return Node.array(
                leafLong(-bd.scale()),
                leafBigInteger(bd.unscaledValue()));
    }

    private static Node walkRational(dev.everydaythings.graph.value.Rational r) {
        return Node.array(
                leafBigInteger(r.numerator()),
                leafBigInteger(r.denominator()));
    }

    private static Node.Leaf leafItemID(ItemRef id) {
        return Node.leaf(id, prefixed(LEAF_ITEM_ID, id.encodeBinary()));
    }

    private static Node.Leaf leafReference(HashID ref) {
        return leafRefBytes(ref, ref.toRefBytes());
    }

    /**
     * HashID-leaf helper. Carries an arbitrary "value" alongside the
     * ref bytes so callers can use any object as the leaf's typed value.
     */
    private static Node.Leaf leafRefBytes(Object value, byte[] refBytes) {
        return Node.leaf(value, prefixed(LEAF_REFERENCE, refBytes));
    }

    private static byte[] prefixed(byte tag, byte[] payload) {
        byte[] out = new byte[payload.length + 1];
        out[0] = tag;
        System.arraycopy(payload, 0, out, 1, payload.length);
        return out;
    }
}
