package dev.everydaythings.graph.crypt;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.frame.InspectEntry;
import dev.everydaythings.graph.frame.Inspectable;
import dev.everydaythings.graph.item.Factory;
import dev.everydaythings.graph.item.Implements;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Type;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.Sememe;

import lombok.AllArgsConstructor;

import java.security.MessageDigest;
import java.util.*;

/**
 * Append-only log for keys (add, set-current, tombstone).
 *
 * <p>KeyLog tracks the public key history for a Signer.
 * It supports:
 * <ul>
 *   <li>Adding new keys (signing or encryption)</li>
 *   <li>Setting keys as current for specific purposes (sign, encrypt, authenticate)</li>
 *   <li>Tombstoning compromised or retired keys</li>
 * </ul>
 *
 * <p>Unlike the private key (stored in Vault), KeyLog content is syncable
 * and can be shared to prove identity and key history.
 *
 * <p>Operations are applied in-memory via {@link #apply(Op)}. When a
 * Library is available, operations can be persisted as frames via FrameChain.
 */
@Implements(KeyLog.TypeSeed.KEY)
@Type(glyph = "\uD83D\uDD11", icon = "/icons/key.png")
public class KeyLog implements Canonical, Inspectable {

    // === TYPE DEFINITION ===
    public static final String KEY = TypeSeed.KEY;

    public static class TypeSeed {
        public static final String KEY = "cg.sememe:keylog";
        @Item.Seed public static final Sememe SEED = new Sememe(KEY)
                .gloss("en", "append-only public key history")
                .word(PartOfSpeech.NOUN, GrammaticalFeature.Lemma.SEED, "en", "keylog");
    }

    /**
     * Create a new empty KeyLog.
     */
    @Factory(label = "Empty", glyph = "\uD83D\uDD11", primary = true,
            doc = "New empty key log")
    public static KeyLog create() {
        return new KeyLog();
    }

    /* ============== materialized state (transient, not encoded) ============== */
    private final Map<String, GraphPublicKey> keys = new LinkedHashMap<>();
    private final Set<String> tombstoned = new HashSet<>();
    // purpose mask -> (keyCid -> LWW info)
    private final Map<Integer, Map<String, Lww>> currentByPurpose = new HashMap<>();

    /** Sequence counter for LWW ordering. */
    private long sequence = 0;

    private record Lww(long seq, boolean current) {}

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    /* ================================= ops ================================== */
    public sealed interface Op permits AddKey, SetCurrent, TombstoneKey {}

    /**
     * Add a new key to the log (signing or encryption).
     */
    public static final class AddKey implements Op {
        public final GraphPublicKey key;

        public AddKey(GraphPublicKey k) {
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
     *   <li>4 - Consumed (single-use OPK used in X3DH)</li>
     * </ul>
     */
    public static final int REASON_CONSUMED = 4;
    @AllArgsConstructor
    public static final class TombstoneKey implements Op {
        public final byte[] keyCid;
        public final int reason;
    }

    /* ============================ apply ============================== */

    /**
     * Apply an operation to this KeyLog, updating materialized state.
     *
     * @param op the operation to apply
     */
    public void apply(Op op) {
        sequence++;
        if (op instanceof AddKey a) {
            byte[] keyRec = a.key.encodeBinary(Scope.BODY);
            String cid = hex(hash(keyRec));
            keys.put(cid, a.key);
        } else if (op instanceof SetCurrent s) {
            String cid = hex(s.keyCid);
            if (!keys.containsKey(cid) || tombstoned.contains(cid)) return;
            Map<String, Lww> lww = currentByPurpose.computeIfAbsent(s.purposeMask, k -> new HashMap<>());
            Lww cur = lww.get(cid);
            Lww incoming = new Lww(sequence, s.current);
            if (cur == null || incoming.seq > cur.seq) lww.put(cid, incoming);
        } else if (op instanceof TombstoneKey t) {
            String cid = hex(t.keyCid);
            tombstoned.add(cid);
            for (Map<String, Lww> m : currentByPurpose.values()) m.remove(cid);
        }
    }

    /**
     * Check if this KeyLog is empty (no operations applied).
     */
    public boolean isEmpty() {
        return keys.isEmpty();
    }

    public boolean isExpandable() {
        return !isEmpty();
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

    /* ===================== display ===================== */

    public String displayToken() {
        int n = keys.size();
        int dead = tombstoned.size();
        if (n == 0) return "keys (empty)";
        return "keys (" + n + (dead > 0 ? ", " + dead + " tombstoned" : "") + ")";
    }

    @Override
    public String toString() {
        if (keys.isEmpty()) {
            return "No keys registered.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(keys.size()).append(keys.size() == 1 ? " key" : " keys");
        if (!tombstoned.isEmpty()) {
            sb.append(" (").append(tombstoned.size()).append(" tombstoned)");
        }
        sb.append("\n\n");
        for (var entry : keys.entrySet()) {
            String shortId = entry.getKey().length() > 12
                    ? entry.getKey().substring(0, 12) + "\u2026" : entry.getKey();
            boolean dead = tombstoned.contains(entry.getKey());
            sb.append(dead ? "  \uD83D\uDEAB " : "  \uD83D\uDD11 ");
            sb.append(shortId);
            GraphPublicKey k = entry.getValue();
            sb.append("  ").append(((Enum<?>) k.algorithm()).name());
            if (k instanceof EncryptionPublicKey) sb.append(" (encrypt)");
            if (dead) sb.append("  (tombstoned)");
            sb.append("\n");
        }
        return sb.toString().stripTrailing();
    }

    /* ===================== convenience query helpers ===================== */

    /**
     * Get all keys in this log (including tombstoned).
     */
    public Map<String, GraphPublicKey> keys() {
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
     */
    public Optional<SigningPublicKey> currentSigningKey() {
        Set<String> cids = currentKeyCids(Purpose.SIGN);
        if (cids.isEmpty()) return Optional.empty();
        String cid = cids.iterator().next();
        GraphPublicKey key = keys.get(cid);
        if (key instanceof SigningPublicKey spk) return Optional.of(spk);
        return Optional.empty();
    }

    /**
     * Get the current encryption key, if one is set.
     */
    public Optional<EncryptionPublicKey> currentEncryptionKey() {
        Set<String> cids = currentKeyCids(Purpose.ENCRYPT);
        if (cids.isEmpty()) return Optional.empty();
        String cid = cids.iterator().next();
        GraphPublicKey key = keys.get(cid);
        if (key instanceof EncryptionPublicKey epk) return Optional.of(epk);
        return Optional.empty();
    }

    /**
     * Get all current encryption keys (multi-device scenarios).
     */
    public Set<EncryptionPublicKey> currentEncryptionKeys() {
        Set<String> cids = currentKeyCids(Purpose.ENCRYPT);
        Set<EncryptionPublicKey> result = new LinkedHashSet<>();
        for (String cid : cids) {
            GraphPublicKey key = keys.get(cid);
            if (key instanceof EncryptionPublicKey epk) result.add(epk);
        }
        return result;
    }

    /**
     * Get the current signed pre-key (SPK), if one is set.
     */
    public Optional<EncryptionPublicKey> currentPreKey() {
        Set<String> cids = currentKeyCids(Purpose.PRE_KEY);
        if (cids.isEmpty()) return Optional.empty();
        String cid = cids.iterator().next();
        GraphPublicKey key = keys.get(cid);
        if (key instanceof EncryptionPublicKey epk) return Optional.of(epk);
        return Optional.empty();
    }

    /**
     * Get all available (non-tombstoned, current) one-time pre-keys.
     */
    public List<EncryptionPublicKey> availableOneTimePreKeys() {
        Set<String> cids = currentKeyCids(Purpose.ONE_TIME_PRE_KEY);
        List<EncryptionPublicKey> result = new ArrayList<>();
        for (String cid : cids) {
            if (tombstoned.contains(cid)) continue;
            GraphPublicKey key = keys.get(cid);
            if (key instanceof EncryptionPublicKey epk) result.add(epk);
        }
        return result;
    }

    /**
     * Compute the CID (hex) for a key.
     */
    public static String keyCid(GraphPublicKey key) {
        byte[] keyRec = key.encodeBinary(Canonical.Scope.BODY);
        return hex(hash(keyRec));
    }

    /**
     * Compute the CID (raw bytes) for a key.
     */
    public static byte[] keyCidBytes(GraphPublicKey key) {
        byte[] keyRec = key.encodeBinary(Canonical.Scope.BODY);
        return hash(keyRec);
    }
}
