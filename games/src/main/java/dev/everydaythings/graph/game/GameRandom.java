package dev.everydaythings.graph.game;

import dev.everydaythings.graph.item.component.Dag;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Deterministic, verifiable randomness for games built on Dag.
 *
 * <p>GameRandom derives its seed from Dag event content IDs (CIDs).
 * Since CIDs are cryptographic hashes of signed operations, the random
 * sequence is:
 * <ul>
 *   <li><b>Deterministic</b> — replaying the same events produces the same rolls</li>
 *   <li><b>Verifiable</b> — anyone with the Dag can recompute all random outcomes</li>
 *   <li><b>Unpredictable</b> — depends on both players' signed inputs</li>
 * </ul>
 *
 * <h3>Usage in fold()</h3>
 * <pre>{@code
 * case RollOp roll -> {
 *     GameRandom rng = GameRandom.fromEvent(ev);
 *     int die1 = rng.nextInt(1, 7);  // 1-6
 *     int die2 = rng.nextInt(1, 7);
 *     // ... apply dice result to game state
 * }
 * }</pre>
 *
 * <h3>Commit-Reveal for Shuffles</h3>
 *
 * <p>For card shuffles where the deck must be randomized before players
 * see their hands, use a two-phase commit-reveal protocol:
 * <ol>
 *   <li>Each player submits a sealed commitment (hash of their secret)</li>
 *   <li>After all commitments, players reveal their secrets</li>
 *   <li>Combined reveals seed the shuffle — no single player controls it</li>
 * </ol>
 *
 * Use {@link #fromCombinedSeeds(List)} for the multi-party case.
 *
 * @see Randomized
 */
public final class GameRandom {

    private final Random rng;

    private GameRandom(long seed) {
        this.rng = new Random(seed);
    }

    // ==================================================================================
    // Factory Methods
    // ==================================================================================

    /**
     * Create from a single Dag event.
     *
     * <p>Seed derived from the event's CID (hash of signed body).
     * Simplest case: one event triggers one random decision.
     */
    public static GameRandom fromEvent(Dag.Event ev) {
        Objects.requireNonNull(ev, "ev");
        return new GameRandom(seedFromBytes(ev.cid));
    }

    /**
     * Create from arbitrary seed bytes.
     */
    public static GameRandom fromSeed(byte[] seed) {
        Objects.requireNonNull(seed, "seed");
        return new GameRandom(seedFromBytes(seed));
    }

    /**
     * Create from multiple party seeds (commit-reveal protocol).
     *
     * <p>Combines all seeds by hashing them together. Seeds are sorted
     * lexicographically to ensure determinism regardless of submission order.
     */
    public static GameRandom fromCombinedSeeds(List<byte[]> seeds) {
        Objects.requireNonNull(seeds, "seeds");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            List<byte[]> sorted = new ArrayList<>(seeds);
            sorted.sort(GameRandom::compareBytes);
            for (byte[] s : sorted) {
                md.update(s);
            }
            return new GameRandom(seedFromBytes(md.digest()));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // ==================================================================================
    // Random Operations
    // ==================================================================================

    /** Next int in [0, bound). */
    public int nextInt(int bound) {
        return rng.nextInt(bound);
    }

    /** Next int in [origin, bound) — e.g., nextInt(1, 7) for a d6. */
    public int nextInt(int origin, int bound) {
        return origin + rng.nextInt(bound - origin);
    }

    /** Coin flip. */
    public boolean nextBoolean() {
        return rng.nextBoolean();
    }

    /** Next double in [0.0, 1.0). */
    public double nextDouble() {
        return rng.nextDouble();
    }

    /**
     * Shuffle a list in place using Fisher-Yates.
     * Deterministic given the same seed.
     */
    public <T> void shuffle(List<T> list) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            T tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }

    // ==================================================================================
    // Internal
    // ==================================================================================

    private static long seedFromBytes(byte[] bytes) {
        if (bytes.length >= 8) {
            return ByteBuffer.wrap(bytes).getLong();
        }
        byte[] padded = new byte[8];
        System.arraycopy(bytes, 0, padded, 0, bytes.length);
        return ByteBuffer.wrap(padded).getLong();
    }

    private static int compareBytes(byte[] a, byte[] b) {
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int d = (a[i] & 0xff) - (b[i] & 0xff);
            if (d != 0) return d;
        }
        return Integer.compare(a.length, b.length);
    }
}
