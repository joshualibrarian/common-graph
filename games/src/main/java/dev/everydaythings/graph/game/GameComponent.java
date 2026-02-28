package dev.everydaythings.graph.game;

import dev.everydaythings.graph.item.action.ActionContext;
import dev.everydaythings.graph.item.component.Param;
import dev.everydaythings.graph.item.component.Verb;
import dev.everydaythings.graph.item.component.Dag;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.user.Signer;
import dev.everydaythings.graph.trust.Signing;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Abstract base for any game — turn-based, simultaneous, or free-form.
 *
 * <p>Extends {@link Dag} for multi-writer sync, conflict resolution, and
 * verifiable history. Every game operation is a signed, content-addressed
 * event in the DAG — making games syncable, replayable, and forkable.
 *
 * <p>This is the universal game base. Concrete games compose capability
 * interfaces to declare what features they have:
 * <ul>
 *   <li>{@link Spatial} — board topology with pieces</li>
 *   <li>{@link Zoned} — named zones with visibility (hands, decks)</li>
 *   <li>{@link Scored} — per-player score/resource tracking</li>
 *   <li>{@link Phased} — sub-phases within turns</li>
 *   <li>{@link Randomized} — verifiable deterministic randomness</li>
 * </ul>
 *
 * <h3>Example: Chess</h3>
 * <pre>{@code
 * public class Chess extends GameComponent<Chess.Op>
 *         implements Spatial<ChessPiece> { ... }
 * }</pre>
 *
 * <h3>Example: Poker</h3>
 * <pre>{@code
 * public class Poker extends GameComponent<Poker.Op>
 *         implements Zoned<PlayingCard>, Scored, Phased, Randomized { ... }
 * }</pre>
 *
 * @param <Op> The operation type (moves, bets, draws, etc.)
 */
public abstract class GameComponent<Op> extends Dag<Op> {

    /**
     * Player seats — maps seat index (0-based) to player ItemID.
     * Null slot means no player has joined that seat yet.
     */
    protected final List<ItemID> playerSeats = new ArrayList<>();

    /**
     * Player display names — maps seat index to human-readable name.
     * Populated when players join via verb (from Signer.name()) or via joinAs overload.
     */
    protected final Map<Integer, String> playerNames = new LinkedHashMap<>();

    /** Signer for signing Dag operations. Set by subclass withSigner()/withDemoSigner(). */
    protected transient Signing.Signer signer;

    /** Hasher for content addressing Dag operations. */
    protected transient Signing.Hasher hasher;

    // ==================================================================================
    // Abstract Methods — Subclasses Must Implement
    // ==================================================================================

    /**
     * Minimum players required to start this game.
     * For chess: 2, for solitaire: 1.
     */
    public abstract int minPlayers();

    /**
     * Maximum players allowed.
     * For chess: 2, for poker: 10.
     */
    public abstract int maxPlayers();

    /**
     * Which seats can act right now?
     *
     * <p>For strict-turn games: return a singleton set (e.g., {@code Set.of(0)}).
     * For simultaneous action: return multiple seats.
     * When game is over: return empty set.
     *
     * @return set of seat indices that may act
     */
    public abstract Set<Integer> activePlayers();

    /**
     * Is the game finished?
     */
    public abstract boolean isGameOver();

    /**
     * Winner's seat index, if any.
     *
     * @return seat index of winner, or empty if draw or in-progress
     */
    public abstract Optional<Integer> winner();

    // ==================================================================================
    // Turn Queries
    // ==================================================================================

    /**
     * Can this seat act right now?
     */
    public boolean canAct(int seat) {
        return activePlayers().contains(seat);
    }

    /**
     * Can this player act right now?
     */
    public boolean canAct(ItemID playerId) {
        return seatOf(playerId).map(this::canAct).orElse(false);
    }

    /**
     * Is the game a draw?
     */
    public boolean isDraw() {
        return isGameOver() && winner().isEmpty();
    }

    // ==================================================================================
    // Player Management
    // ==================================================================================

    /**
     * Get all player IDs (may contain nulls for empty seats).
     */
    public List<ItemID> players() {
        return List.copyOf(playerSeats);
    }

    /**
     * Join the game at a specific seat.
     *
     * @param seat     seat index (0-based)
     * @param playerId player's ItemID
     * @throws IllegalArgumentException if seat is out of range
     * @throws IllegalStateException    if seat is already taken
     */
    public void joinAs(int seat, ItemID playerId) {
        joinAs(seat, playerId, null);
    }

    /**
     * Join the game at a specific seat with a display name.
     *
     * @param seat        seat index (0-based)
     * @param playerId    player's ItemID
     * @param displayName human-readable name (null to omit)
     * @throws IllegalArgumentException if seat is out of range
     * @throws IllegalStateException    if seat is already taken
     */
    public void joinAs(int seat, ItemID playerId, String displayName) {
        if (seat < 0 || seat >= maxPlayers()) {
            throw new IllegalArgumentException(
                    "Invalid seat: " + seat + " (game allows " + maxPlayers() + " seats)");
        }

        while (playerSeats.size() <= seat) {
            playerSeats.add(null);
        }

        if (playerSeats.get(seat) != null) {
            throw new IllegalStateException(
                    "Seat " + seat + " is already taken by " + playerSeats.get(seat));
        }

        playerSeats.set(seat, playerId);
        if (displayName != null) {
            playerNames.put(seat, displayName);
        }
    }

    /**
     * Get the display name for a player at the given seat.
     *
     * @param seat seat index (0-based)
     * @return display name, or empty if not set or seat is invalid
     */
    public Optional<String> playerName(int seat) {
        return Optional.ofNullable(playerNames.get(seat));
    }

    /**
     * Join the game at the first available seat.
     *
     * @param playerId player's ItemID
     * @return seat index assigned, or empty if full
     */
    public Optional<Integer> join(ItemID playerId) {
        return joinWithName(playerId, null);
    }

    /**
     * Join the game at the first available seat with a display name.
     *
     * @param playerId    player's ItemID
     * @param displayName human-readable name (null to omit)
     * @return seat index assigned, or empty if full
     */
    public Optional<Integer> joinWithName(ItemID playerId, String displayName) {
        for (int i = 0; i < maxPlayers(); i++) {
            while (playerSeats.size() <= i) {
                playerSeats.add(null);
            }
            if (playerSeats.get(i) == null) {
                playerSeats.set(i, playerId);
                if (displayName != null) {
                    playerNames.put(i, displayName);
                }
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    /**
     * Find which seat a player is in.
     *
     * @param playerId player to find
     * @return seat index, or empty if player is not in the game
     */
    public Optional<Integer> seatOf(ItemID playerId) {
        for (int i = 0; i < playerSeats.size(); i++) {
            if (playerId.equals(playerSeats.get(i))) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    /**
     * Get the player at a given seat.
     *
     * @param seat seat index (0-based)
     * @return player ItemID, or empty if seat is empty or invalid
     */
    public Optional<ItemID> playerAt(int seat) {
        if (seat < 0 || seat >= playerSeats.size()) {
            return Optional.empty();
        }
        return Optional.ofNullable(playerSeats.get(seat));
    }

    /**
     * Number of players currently seated.
     */
    public int seatedCount() {
        return (int) playerSeats.stream().filter(Objects::nonNull).count();
    }

    /**
     * Check if all seats are filled (up to maxPlayers).
     */
    public boolean isFull() {
        return seatedCount() >= maxPlayers();
    }

    /**
     * Check if enough players are present to start (minPlayers threshold).
     * Game must also have no moves yet.
     */
    public boolean isReadyToStart() {
        return seatedCount() >= minPlayers() && isEmpty();
    }

    // ==================================================================================
    // Signer Resolution
    // ==================================================================================

    /**
     * Resolve a signer from the ActionContext, falling back to the transient signer.
     *
     * @param ctx the action context (may be null for direct calls)
     * @return a signer
     * @throws IllegalStateException if no signer is available
     */
    protected Signing.Signer resolveSigner(ActionContext ctx) {
        if (ctx != null) {
            Optional<Signer> s = ctx.callerSigner();
            if (s.isPresent()) return s.get();
        }
        if (signer != null) return signer;
        throw new IllegalStateException("No signer configured");
    }

    /**
     * Resolve a hasher, falling back to the default hasher.
     */
    protected Signing.Hasher resolveHasher() {
        return hasher != null ? hasher : Signing.defaultHasher();
    }

    // ==================================================================================
    // Authorization Helpers
    // ==================================================================================

    /**
     * Get the caller's seat and verify it's their turn.
     *
     * <p>Skips authorization only when ctx is null or caller is null
     * (convenience calls from tests that bypass the verb system).
     * All verb-dispatched calls with a real caller must be seated.
     *
     * @param ctx the action context
     * @return the caller's seat index, or -1 if authorization was skipped
     * @throws SecurityException if the caller is not seated or it's not their turn
     */
    protected int authorizedSeat(ActionContext ctx) {
        if (ctx == null || ctx.caller() == null) return -1;
        int seat = requireSeat(ctx);
        if (!canAct(seat)) {
            throw new SecurityException("Not your turn (seat " + seat + ")");
        }
        return seat;
    }

    /**
     * Get the caller's seat (no turn check). For resign/draw that work any time.
     *
     * <p>Skips authorization only when ctx is null or caller is null.
     *
     * @param ctx the action context
     * @return the caller's seat index, or -1 if authorization was skipped
     * @throws SecurityException if the caller is not seated in the game
     */
    protected int requireSeat(ActionContext ctx) {
        if (ctx == null || ctx.caller() == null) return -1;
        return seatOf(ctx.caller())
                .orElseThrow(() -> new SecurityException(
                        "Not a player in this game: " + ctx.caller()));
    }

    // ==================================================================================
    // Leave (data method)
    // ==================================================================================

    /**
     * Remove a player from their seat.
     *
     * @param playerId the player to remove
     * @return the vacated seat index, or empty if the player wasn't seated
     */
    public Optional<Integer> leave(ItemID playerId) {
        for (int i = 0; i < playerSeats.size(); i++) {
            if (playerId.equals(playerSeats.get(i))) {
                playerSeats.set(i, null);
                playerNames.remove(i);
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    // ==================================================================================
    // Join/Leave Verbs
    // ==================================================================================

    /**
     * Join the game via verb dispatch.
     *
     * <p>Uses the caller's identity from ActionContext to assign a seat.
     * If seat is provided, joins that specific seat; otherwise joins the first available.
     *
     * @param ctx  the action context (provides caller identity)
     * @param seat optional seat number (null for first available)
     * @return status message
     */
    @Verb(value = GameVocabulary.JOIN, doc = "Join the game")
    public String joinVerb(ActionContext ctx,
                           @Param(value = "seat", required = false) Integer seat) {
        ItemID callerId = ctx.caller();
        if (callerId == null) {
            throw new IllegalStateException("Cannot join anonymously — no caller identity");
        }

        // Check if already seated
        if (seatOf(callerId).isPresent()) {
            throw new IllegalStateException("Already in the game at seat " + seatOf(callerId).get());
        }

        // Extract display name from caller's signer if available
        String displayName = ctx.callerSigner()
                .map(Signer::name)
                .orElse(null);

        if (seat != null) {
            joinAs(seat, callerId, displayName);
            return "Joined at seat " + seat;
        } else {
            return joinWithName(callerId, displayName)
                    .map(s -> "Joined at seat " + s)
                    .orElseThrow(() -> new IllegalStateException("Game is full"));
        }
    }

    /**
     * Leave the game via verb dispatch.
     *
     * @param ctx the action context (provides caller identity)
     * @return status message
     */
    @Verb(value = GameVocabulary.LEAVE, doc = "Leave the game")
    public String leaveVerb(ActionContext ctx) {
        ItemID callerId = ctx.caller();
        if (callerId == null) {
            throw new IllegalStateException("Cannot leave anonymously — no caller identity");
        }

        return leave(callerId)
                .map(s -> "Left seat " + s)
                .orElseThrow(() -> new IllegalStateException("Not in the game"));
    }

    // ==================================================================================
    // Demo Signer/Hasher (testing only)
    // ==================================================================================

    /**
     * Use a demo signer for testing (not cryptographically secure).
     *
     * <p>Returns {@code this} for fluent chaining. Subclasses should override
     * to return their concrete type if they expose this in factory methods.
     */
    @SuppressWarnings("unchecked")
    public <T extends GameComponent<Op>> T withDemoSigner() {
        this.signer = new DemoSigner();
        this.hasher = new DemoHasher();
        return (T) this;
    }

    /**
     * Simple demo signer — produces deterministic "signatures" for testing.
     * NOT FOR PRODUCTION USE.
     */
    protected static class DemoSigner implements Signing.Signer {
        private static final byte[] KEY_REF = "demo-game-player".getBytes();

        @Override
        public byte[] keyRef() {
            return KEY_REF;
        }

        @Override
        public byte[] signRaw(byte[] data) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update("demo-sig:".getBytes());
                return md.digest(data);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Simple demo hasher for testing.
     */
    protected static class DemoHasher implements Signing.Hasher {
        @Override
        public byte[] cid(byte[] data) {
            try {
                return MessageDigest.getInstance("SHA-256").digest(data);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
