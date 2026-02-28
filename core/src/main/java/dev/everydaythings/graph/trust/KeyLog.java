package dev.everydaythings.graph.trust;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.item.component.Factory;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.component.Dag;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.ui.scene.Scene;
import lombok.AllArgsConstructor;

import java.security.MessageDigest;
import java.util.*;

/**
 * Append-only log for keys (add, set-current, tombstone).
 *
 * <p>KeyLog is a stream component that tracks the public key history for a Signer.
 * It supports:
 * <ul>
 *   <li>Adding new keys</li>
 *   <li>Setting keys as current for specific purposes (sign, encrypt, authenticate)</li>
 *   <li>Tombstoning compromised or retired keys</li>
 * </ul>
 *
 * <p>Unlike the private key (stored in Vault), KeyLog content is syncable
 * and can be shared to prove identity and key history.
 */
@Type(value = KeyLog.KEY, glyph = "🔑", icon = "/icons/key.png")
@Scene.Body(mesh = "/models/key-quaternius.glb", color = 0xC9B037)
public class KeyLog extends Dag<KeyLog.Op> {

    // === TYPE DEFINITION ===
    public static final String KEY = "cg:type/keylog";

    /**
     * Create a new empty KeyLog.
     *
     * <p>Used by the component system to instantiate KeyLog components.
     */
    @Factory(label = "Empty", glyph = "🔑", primary = true,
            doc = "New empty key log")
    public static KeyLog create() {
        return new KeyLog();
    }

    /* ============== materialized state (transient, not encoded) ============== */
    private final Map<String, SigningPublicKey> keys = new LinkedHashMap<>();
    private final Set<String> tombstoned = new HashSet<>();
    // purpose mask -> (keyCid -> LWW info)
    private final Map<Integer, Map<String, Lww>> currentByPurpose = new HashMap<>();

    private record Lww(long seq, byte[] authorRef, boolean current) {}

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    /* ================================= ops ================================== */
    public sealed interface Op permits AddKey, SetCurrent, TombstoneKey {}

    /**
     * Add a new key to the log.
     */
    public static final class AddKey implements Op {
        public final SigningPublicKey key;

        public AddKey(SigningPublicKey k) {
            this.key = k;
        }
    }

    /**
     * Set a key as current (or not) for specific purposes.
     *
     * <p>Uses a bitmask for purposes. Use {@link Purpose#mask(Purpose...)} to create.
     */
    public static final class SetCurrent implements Op {
        public final byte[] keyCid;
        public final int purposeMask;
        public final boolean current;

        public SetCurrent(byte[] keyCid, int purposeMask, boolean current) {
            this.keyCid = keyCid;
            this.purposeMask = purposeMask;
            this.current = current;
        }

        /**
         * Convenience constructor using Purpose enum.
         */
        public SetCurrent(byte[] keyCid, EnumSet<Purpose> purposes, boolean current) {
            this(keyCid, Purpose.mask(purposes), current);
        }

        /**
         * Convenience constructor for single purpose.
         */
        public SetCurrent(byte[] keyCid, Purpose purpose, boolean current) {
            this(keyCid, purpose.bit(), current);
        }

        /**
         * Get purposes as EnumSet.
         */
        public EnumSet<Purpose> purposes() {
            return Purpose.fromMask(purposeMask);
        }
    }

    /**
     * Tombstone a key (permanently mark as untrusted).
     *
     * <p>Reason codes:
     * <ul>
     *   <li>0 - Unspecified</li>
     *   <li>1 - Key compromised</li>
     *   <li>2 - Key retired (superseded)</li>
     *   <li>3 - Affiliation changed</li>
     * </ul>
     */
    @AllArgsConstructor
    public static final class TombstoneKey implements Op {
        public final byte[] keyCid;
        public final int reason;
    }

    /* ========================= op encode/decode ========================= */
    @Override
    protected CBORObject encodeOp(Op op) {
        CBORObject m = CBORObject.NewMap();
        switch (op) {
            case AddKey a -> {
                m.set(num(0), num(1)); // t=1
                m.set(num(1), Canonical.toCborTree(a.key, Canonical.Scope.RECORD));
            }
            case SetCurrent s -> {
                m.set(num(0), num(2));
                m.set(num(1), CBORObject.FromByteArray(s.keyCid));
                m.set(num(2), num(s.purposeMask));
                m.set(num(3), s.current ? CBORObject.True : CBORObject.False);
            }
            case TombstoneKey t -> {
                m.set(num(0), num(3));
                m.set(num(1), CBORObject.FromByteArray(t.keyCid));
                m.set(num(2), num(t.reason));
            }
            default ->
                throw new IllegalArgumentException("unknown op " + op.getClass());
        }
        return m;
    }

    @Override
    protected Op decodeOp(CBORObject c) {
        int t = c.get(num(0)).AsInt32();
        return switch (t) {
            case 1 -> new AddKey(Canonical.fromCborTree(c.get(num(1)), SigningPublicKey.class, Canonical.Scope.RECORD));
            case 2 -> new SetCurrent(c.get(num(1)).GetByteString(), c.get(num(2)).AsInt32(), c.get(num(3)).AsBoolean());
            case 3 -> new TombstoneKey(c.get(num(1)).GetByteString(), c.get(num(2)).AsInt32());
            default -> throw new IllegalArgumentException("bad t=" + t);
        };
    }

    private static CBORObject num(int i) {
        return CBORObject.FromInt32(i);
    }

    /* ============================ fold semantics ============================ */
    @Override
    protected void fold(Op op, Event ev) {
        if (op instanceof AddKey a) {
            byte[] keyRec = a.key.encodeBinary(Scope.BODY);
            String cid = hex(hash(keyRec));
            keys.put(cid, a.key);
            // do not set 'current' implicitly; that's a separate op
        } else if (op instanceof SetCurrent s) {
            String cid = hex(s.keyCid);
            if (!keys.containsKey(cid) || tombstoned.contains(cid)) return; // ignore for unknown/tombstoned
            Map<String, Lww> lww = currentByPurpose.computeIfAbsent(s.purposeMask, k -> new HashMap<>());
            Lww cur = lww.get(cid);
            Lww incoming = new Lww(ev.seq, ev.authorKeyRef, s.current);
            if (cur == null || newer(incoming, cur)) lww.put(cid, incoming);
        } else if (op instanceof TombstoneKey t) {
            String cid = hex(t.keyCid);
            tombstoned.add(cid);
            // clear from current for all purposes
            for (Map<String, Lww> m : currentByPurpose.values()) m.remove(cid);
        }
    }

    private static boolean newer(Lww a, Lww b) {
        if (a.seq != b.seq) return a.seq > b.seq;
        return lexi(a.authorRef, b.authorRef) > 0;
    }

    private static int lexi(byte[] x, byte[] y) {
        int n = Math.min(x.length, y.length);
        for (int i = 0; i < n; i++) {
            int d = (x[i] & 0xff) - (y[i] & 0xff);
            if (d != 0) return d;
        }
        return Integer.compare(x.length, y.length);
    }

    /* ============================== helpers ============================== */
    private static byte[] hash(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(bytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /* ===================== inspect entries ===================== */

    @Override
    public java.util.List<InspectEntry> inspectEntries() {
        java.util.List<InspectEntry> entries = new ArrayList<>();
        for (var entry : keys.entrySet()) {
            String shortId = entry.getKey().length() > 8
                    ? entry.getKey().substring(0, 8) + "\u2026" : entry.getKey();
            boolean dead = tombstoned.contains(entry.getKey());
            entries.add(new InspectEntry(
                    entry.getKey(),
                    shortId + (dead ? " (tombstoned)" : ""),
                    dead ? "\uD83D\uDEAB" : "\uD83D\uDD11",
                    entry.getValue()));
        }
        return entries;
    }

    /* ===================== convenience query helpers ===================== */

    /**
     * Get all keys in this log (including tombstoned).
     */
    public Map<String, SigningPublicKey> keys() {
        return Collections.unmodifiableMap(keys);
    }

    /**
     * Check if a key (by CID hex) has been tombstoned.
     */
    public boolean isTombstoned(String cidHex) {
        return tombstoned.contains(cidHex);
    }

    /**
     * Returns CIDs (hex) for keys marked current for the given purpose mask.
     */
    public Set<String> currentKeyCids(int purposeMask) {
        Map<String, Lww> m = currentByPurpose.getOrDefault(purposeMask, Map.of());
        Set<String> out = new LinkedHashSet<>();
        for (var e : m.entrySet()) {
            if (e.getValue().current) out.add(e.getKey());
        }
        return out;
    }

    /**
     * Returns CIDs (hex) for keys marked current for the given purpose.
     */
    public Set<String> currentKeyCids(Purpose purpose) {
        return currentKeyCids(purpose.bit());
    }

    /**
     * Get the current signing key, if one is set.
     *
     * @return The current signing key, or empty if none set
     */
    public Optional<SigningPublicKey> currentSigningKey() {
        Set<String> cids = currentKeyCids(Purpose.SIGN);
        if (cids.isEmpty()) return Optional.empty();
        // Return the first (should typically be only one)
        String cid = cids.iterator().next();
        return Optional.ofNullable(keys.get(cid));
    }

    /**
     * Compute the CID (hex) for a key.
     */
    public static String keyCid(SigningPublicKey key) {
        byte[] keyRec = key.encodeBinary(Canonical.Scope.BODY);
        return hex(hash(keyRec));
    }

    /**
     * Compute the CID (raw bytes) for a key.
     */
    public static byte[] keyCidBytes(SigningPublicKey key) {
        byte[] keyRec = key.encodeBinary(Canonical.Scope.BODY);
        return hash(keyRec);
    }
}
