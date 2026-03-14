package dev.everydaythings.graph.frame;

import dev.everydaythings.graph.item.Type;

import com.upokecenter.cbor.CBOREncodeOptions;
import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.crypt.Signing;
import dev.everydaythings.graph.ui.scene.Scene;

import java.util.*;

/**
 * A DAG-structured component for multi-writer sync scenarios.
 *
 * <p>A Dag is a directed acyclic graph of operations, where each operation
 * references its parent(s) by content ID. This enables:
 * <ul>
 *   <li>Concurrent operations from multiple devices</li>
 *   <li>Deterministic merge via CRDT semantics</li>
 *   <li>Cryptographic verification of history</li>
 * </ul>
 *
 * <p>Use Dag when you need:
 * <ul>
 *   <li>Multi-device sync (same identity on laptop and phone)</li>
 *   <li>Collaborative editing</li>
 *   <li>Conflict resolution via LWW or custom CRDT</li>
 * </ul>
 *
 * <p>For simpler single-writer scenarios, consider {@link Log} instead.
 *
 * <p>Subclasses must implement:
 * <ul>
 *   <li>{@link #encodeOp}/{@link #decodeOp} - operation serialization</li>
 *   <li>{@link #fold} - how operations update materialized state</li>
 * </ul>
 *
 * @param <E> The operation type
 * @see Log
 */
@Type(value = Dag.KEY, glyph = "🕸️")
public abstract class Dag<E> implements Canonical {

    // === TYPE DEFINITION ===
    public static final String KEY = "cg:type/dag";

    /* ======================== Canonical fields ========================= */

    @Canon(isBody = true, isRecord = false, order = 1)
    @Scene(visible = false)
    protected int version = 1;

    /** Current heads (event CIDs) – lives in RECORD. */
    @Canon(isBody = false, isRecord = true, order = 100)
    @Scene(visible = false)
    protected List<byte[]> heads = new ArrayList<>();

    public List<byte[]> heads() { return List.copyOf(heads); }

    /* ========================= Event envelope ========================= */

    /**
     * Verifier for event signatures.
     */
    public interface Verifier {
        boolean verify(byte[] keyRef, byte[] bodyCbor, byte[] sig);
    }

    /**
     * An event in the DAG - an operation with metadata and cryptographic binding.
     */
    public static final class Event {
        public final int ver;
        public final byte[] authorKeyRef;
        public final long seq;
        public final List<byte[]> prev;
        public final CBORObject op;
        public final Long nbf;
        public final byte[] bodyCbor;
        public final byte[] cid;
        public final byte[] sig;

        public Event(int ver, byte[] authorKeyRef, long seq, List<byte[]> prev,
                     CBORObject op, Long nbf, byte[] bodyCbor, byte[] cid, byte[] sig) {
            this.ver = ver;
            this.authorKeyRef = authorKeyRef;
            this.seq = seq;
            this.prev = List.copyOf(prev);
            this.op = op;
            this.nbf = nbf;
            this.bodyCbor = bodyCbor;
            this.cid = cid;
            this.sig = sig;
        }
    }

    /* ========================== Append / Accept ======================== */

    /** Create, sign, and apply a new event from the current heads. */
    public final Event append(E op, long seq, Signing.Signer signer, Signing.Hasher hasher) {
        Objects.requireNonNull(op);
        Objects.requireNonNull(signer);
        Objects.requireNonNull(hasher);

        List<byte[]> parents = new ArrayList<>(heads); // snapshot

        CBORObject body = CBORObject.NewMap();
        body.set(CBORObject.FromInt32(1), CBORObject.FromInt32(version));
        body.set(CBORObject.FromInt32(2), CBORObject.FromByteArray(signer.keyRef()));
        body.set(CBORObject.FromInt32(3), CBORObject.FromInt64(seq));

        CBORObject prevArr = CBORObject.NewArray();
        for (byte[] p : parents) prevArr.Add(CBORObject.FromByteArray(p));
        body.set(CBORObject.FromInt32(4), prevArr);

        CBORObject opCbor = encodeOp(op);
        body.set(CBORObject.FromInt32(5), opCbor);

        Long nbf = notBeforeFor(op);
        if (nbf != null) body.set(CBORObject.FromInt32(6), CBORObject.FromInt64(nbf));

        byte[] bodyCbor = body.EncodeToBytes(CBOREncodeOptions.DefaultCtap2Canonical);
        byte[] cid = hasher.cid(bodyCbor);
        byte[] sig = signer.signRaw(bodyCbor);

        Event ev = new Event(version, signer.keyRef(), seq, parents, opCbor, nbf, bodyCbor, cid, sig);
        accept(ev, hasher, (ref, b, s) -> true, /*allowOutOfOrder*/ false);
        return ev;
    }

    /**
     * Verify & apply an incoming event. Returns true if applied.
     * If parents are unknown and allowOutOfOrder==true, returns false (caller can retry later).
     */
    public final boolean accept(Event ev, Signing.Hasher hasher, Verifier verifier, boolean allowOutOfOrder) {
        Objects.requireNonNull(ev);
        Objects.requireNonNull(hasher);
        Objects.requireNonNull(verifier);

        // 1) CID check
        byte[] expect = hasher.cid(ev.bodyCbor);
        if (!Arrays.equals(expect, ev.cid)) throw new IllegalStateException("CID mismatch");

        // 2) Signature check
        if (!verifier.verify(ev.authorKeyRef, ev.bodyCbor, ev.sig))
            throw new IllegalStateException("Bad signature");

        // 3) Parents known?
        if (!ev.prev.isEmpty() && !containsAll(heads, ev.prev)) {
            if (allowOutOfOrder) return false;
            throw new IllegalStateException("Unknown parent(s) for event");
        }

        // 4) Fold op into materialized state
        E op = decodeOp(ev.op);
        fold(op, ev);

        // 5) Advance heads: remove parents, add this CID
        LinkedHashSet<BytesKey> hs = new LinkedHashSet<>();
        for (byte[] h : heads) hs.add(new BytesKey(h));
        for (byte[] p : ev.prev) hs.remove(new BytesKey(p));
        hs.add(new BytesKey(ev.cid));

        // deterministically sorted (lexicographic) to keep RECORD stable
        List<byte[]> newHeads = new ArrayList<>(hs.size());
        hs.stream().map(BytesKey::bytes).sorted(Dag::lexi).forEach(newHeads::add);
        heads = newHeads;

        afterEventApplied(ev);
        return true;
    }

    /* ============================ Subclass hooks ============================ */

    /**
     * Encode an operation to CBOR for storage.
     *
     * <p>The encoding must be deterministic (canonical) to ensure
     * consistent content addressing.
     *
     * @param op The operation to encode
     * @return CBOR representation
     */
    protected abstract CBORObject encodeOp(E op);

    /**
     * Decode an operation from CBOR.
     *
     * @param cbor The CBOR representation
     * @return The decoded operation
     */
    protected abstract E decodeOp(CBORObject cbor);

    /**
     * Fold an operation into materialized state.
     *
     * <p>This is where CRDT/LWW semantics are implemented. The event
     * provides metadata (seq, authorKeyRef) for conflict resolution.
     *
     * @param op The operation to fold
     * @param ev The event containing the operation (for metadata)
     */
    protected abstract void fold(E op, Event ev);

    /**
     * Check if this DAG is empty (no operations recorded).
     */
    public boolean isEmpty() {
        return heads.isEmpty();
    }

    public boolean isExpandable() {
        return !isEmpty();
    }

    /** Optional per-event not-before. */
    protected Long notBeforeFor(E op) { return null; }

    /** Optional side-effects after heads/state updated. */
    protected void afterEventApplied(Event ev) { /* no-op */ }

    /* =============================== Utils =============================== */

    /** Value-based wrapper for byte[] so we can use sets/maps correctly. */
    private static final class BytesKey {
        private final byte[] b;
        private final int hash;

        BytesKey(byte[] b) {
            this.b = Objects.requireNonNull(b, "b");
            this.hash = Arrays.hashCode(b);
        }

        byte[] bytes() { return b; }

        @Override
        public int hashCode() { return hash; }

        @Override
        public boolean equals(Object o) {
            return (o instanceof BytesKey other) && Arrays.equals(this.b, other.b);
        }
    }

    private static boolean containsAll(List<byte[]> superset, List<byte[]> subset) {
        Set<BytesKey> s = new HashSet<>(superset.size() * 2);
        for (byte[] x : superset) s.add(new BytesKey(x));
        for (byte[] y : subset) if (!s.contains(new BytesKey(y))) return false;
        return true;
    }

    private static int lexi(byte[] a, byte[] b) {
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int d = (a[i] & 0xff) - (b[i] & 0xff);
            if (d != 0) return d;
        }
        return Integer.compare(a.length, b.length);
    }
}
