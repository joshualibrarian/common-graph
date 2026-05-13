package dev.everydaythings.graph.encoding;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.BindingTarget;
import dev.everydaythings.graph.datum.Datum;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.CompoundKey;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.id.Reference;

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
    // These bytes are part of the identity protocol — every DatumID containing a
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
    /** ItemID leaf: {@code [0x10, <multihash bytes>]}. */
    public static final byte LEAF_ITEM_ID = 0x10;
    /** Reference leaf: {@code [0x11, <ref-bytes (prefix + multihash + sub-parts)>]}. */
    public static final byte LEAF_REFERENCE = 0x11;

    // ==================================================================================
    // Public API
    // ==================================================================================

    /**
     * Walk a typed value as a {@link Node} tree.
     *
     * <p>Dispatch is on the runtime class of the value. Standard primitives
     * ({@link String}, {@link Long}, {@link Boolean}, {@code byte[]},
     * {@link Instant}) produce {@link Node.Leaf} nodes. CG types ({@link Datum},
     * {@link Binding}, {@link BindingTarget}, {@link Reference}, {@link ItemID},
     * {@link Literal}) produce the appropriate composite or leaf forms.
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
            case ItemID id    -> leafItemID(id);
            case Reference r  -> leafReference(r);
            case Datum d      -> walkDatum(d);
            case Binding b    -> walkBinding(b);
            case Literal lit  -> walkLiteral(lit);
            case BindingTarget t  -> walkBindingTarget(t);
            case CompoundKey.Sememe s  -> leafItemID(s.id());
            case CompoundKey.Literal l -> leafString(l.value());
            case List<?> list -> walkList(list);
            case java.util.Map<?,?> m -> walkMap(m);
            default -> throw new IllegalArgumentException(
                    "Walker cannot walk value of type " + value.getClass().getName());
        };
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
        for (Binding b : d.bindings()) bindingNodes.add(walkBinding(b));
        return Node.array(walk(d.head()), Node.array(bindingNodes));
    }

    // ==================================================================================
    // Internal — composite walkers
    // ==================================================================================

    private static Node walkDatum(Datum d) {
        List<Node> children = new ArrayList<>(3);
        children.add(walk(d.head()));
        List<Node> bindingNodes = new ArrayList<>(d.bindings().size());
        for (Binding b : d.bindings()) bindingNodes.add(walkBinding(b));
        children.add(Node.array(bindingNodes));
        if (d instanceof Record r) {
            children.add(leafBytes(r.signature()));
        }
        return Node.array(children);
    }

    private static Node walkBinding(Binding b) {
        List<Node> qualifierNodes = new ArrayList<>(b.qualifiers().size());
        for (CompoundKey.FrameToken q : b.qualifiers()) qualifierNodes.add(walk(q));
        return Node.array(
                leafItemID(b.role()),
                Node.array(qualifierNodes),
                walkBindingTarget(b.target()));
    }

    private static Node walkBindingTarget(BindingTarget t) {
        return switch (t) {
            case BindingTarget.RefTarget rt -> leafRefBytes(rt.asRef(), rt.asRef().toRefBytes());
            case BindingTarget.FrameTarget ft -> walkBodyFromOld(ft);
            case BindingTarget.RedactedTarget rt -> new Node.Hashed(rt.wrappedHash());
            case Literal lit -> walkLiteral(lit);
            default -> throw new IllegalArgumentException(
                    "Unsupported BindingTarget: " + t.getClass().getName());
        };
    }

    private static Node walkBodyFromOld(BindingTarget.FrameTarget ft) {
        // FrameTarget currently wraps the legacy FrameBodyOld type. Until it
        // migrates to wrap a Datum directly, the body's contribution to the
        // walk is its canonical encoded form treated as opaque bytes.
        return leafBytes(ft.body().encodeBinary(Canonical.Scope.BODY));
    }

    private static Node walkLiteral(Literal lit) {
        ItemID type = lit.valueType();
        if (Literal.TYPE_TEXT.equals(type))    return leafString(lit.asText());
        if (Literal.TYPE_INTEGER.equals(type)) return leafLong(lit.asInteger());
        if (Literal.TYPE_BOOLEAN.equals(type)) return leafBoolean(lit.asBoolean());
        if (Literal.TYPE_BYTES.equals(type))   return leafBytes(lit.asBytes());
        if (Literal.TYPE_INSTANT.equals(type)) return leafInstant(lit.asInstantMillis());
        throw new IllegalArgumentException(
                "Literal valueType " + type + " is not a primitive encoding type");
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

    private static Node.Leaf leafItemID(ItemID id) {
        return Node.leaf(id, prefixed(LEAF_ITEM_ID, id.encodeBinary()));
    }

    private static Node.Leaf leafReference(Reference ref) {
        return leafRefBytes(ref, ref.toRefBytes());
    }

    /**
     * Reference-leaf helper for cases where the typed value isn't a
     * {@link Reference} (e.g., the legacy {@link dev.everydaythings.graph.item.id.Ref}
     * held inside {@link BindingTarget.RefTarget}).
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
