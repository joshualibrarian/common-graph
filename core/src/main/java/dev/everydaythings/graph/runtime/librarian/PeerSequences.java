package dev.everydaythings.graph.runtime.librarian;

import dev.everydaythings.graph.ref.ItemRef;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-peer sequence state held by a {@link Librarian}.
 *
 * <p>Each pair of communicating parties (this librarian ↔ some peer iid)
 * has two counters:
 *
 * <ul>
 *   <li><b>Outgoing</b> — the sequence this librarian assigns to the next
 *       record it produces for that peer.  Monotonically increasing per
 *       peer; starts at 1.</li>
 *   <li><b>Last-seen incoming</b> — the highest sequence this librarian
 *       has observed on a record from that peer.  Used for replay /
 *       gap detection.</li>
 * </ul>
 *
 * <p>State is held in memory only.  Persistence across librarian restarts
 * is a future concern (would matter for long-lived peerings where replay
 * detection needs to survive restarts).  For ephemeral client sessions
 * each connection-pair gets a fresh counter on first contact.
 *
 * <p>Thread-safe: per-peer counters live in an AtomicLong, and the peer
 * map is a ConcurrentHashMap.
 */
public final class PeerSequences {

    /**
     * Per-peer counter pair.  Outgoing increments on every {@code next()};
     * lastSeenIncoming updates monotonically via {@code observe(seq)}.
     */
    public static final class Counters {
        private final AtomicLong outgoing = new AtomicLong(0);
        private final AtomicLong lastSeenIncoming = new AtomicLong(0);

        /** Increment and return the next outgoing sequence (starts at 1). */
        public long nextOutgoing() {
            return outgoing.incrementAndGet();
        }

        /** The current outgoing counter without incrementing. */
        public long currentOutgoing() {
            return outgoing.get();
        }

        /**
         * Record that an incoming sequence has been seen.  Updates the
         * last-seen value to {@code max(current, seq)} so repeated observes
         * of the same value are idempotent and out-of-order observes never
         * lower the counter.
         */
        public void observeIncoming(long seq) {
            long current;
            do {
                current = lastSeenIncoming.get();
                if (seq <= current) return;
            } while (!lastSeenIncoming.compareAndSet(current, seq));
        }

        public long lastSeenIncoming() {
            return lastSeenIncoming.get();
        }
    }

    private final ConcurrentHashMap<ItemRef, Counters> byPeer = new ConcurrentHashMap<>();

    /**
     * The counter pair for the given peer, creating a fresh one with
     * outgoing=0 and lastSeen=0 if this is the first time the peer has
     * been mentioned.
     */
    public Counters for_(ItemRef peerIid) {
        Objects.requireNonNull(peerIid, "peerIid");
        return byPeer.computeIfAbsent(peerIid, k -> new Counters());
    }

    /** Convenience: next outgoing sequence for this peer. */
    public long nextOutgoingFor(ItemRef peerIid) {
        return for_(peerIid).nextOutgoing();
    }

    /** Convenience: record an observed incoming sequence from this peer. */
    public void observeIncoming(ItemRef peerIid, long seq) {
        for_(peerIid).observeIncoming(seq);
    }

    /** Whether we have any state for this peer. */
    public boolean knows(ItemRef peerIid) {
        return byPeer.containsKey(peerIid);
    }
}
