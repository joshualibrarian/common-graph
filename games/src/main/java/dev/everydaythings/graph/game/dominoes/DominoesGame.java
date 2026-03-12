package dev.everydaythings.graph.game.dominoes;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.game.*;
import dev.everydaythings.graph.item.action.ActionContext;
import dev.everydaythings.graph.item.component.Param;
import dev.everydaythings.graph.item.component.Type;
import dev.everydaythings.graph.item.component.Verb;
import dev.everydaythings.graph.game.GameVocabulary;
import dev.everydaythings.graph.trust.Signing;
import dev.everydaythings.graph.ui.scene.Scene;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.*;

/**
 * Mexican Train Dominoes — a domino game of connecting matching pip values.
 *
 * <p>Players take turns placing tiles on their personal train or the shared
 * mexican train. If a player can't play, they draw from the boneyard.
 * If they still can't play, they pass and their train becomes "open"
 * (other players can play on it).
 *
 * <p>A round ends when someone empties their hand or no one can play.
 * The winner gets the sum of all other players' remaining pips.
 * The full game cycles through starting doubles (12→0 for double-twelve,
 * or 6→0 for double-six).
 *
 * <p>Composes two traits:
 * <ul>
 *   <li>{@link Zoned} — boneyard, hands, trains (player + mexican)</li>
 *   <li>{@link Scored} — cumulative pip scoring across rounds</li>
 * </ul>
 */
@Type(value = "cg:type/dominoes", glyph = "\uD83C\uDC04")
@Scene(as = DominoesSurface.class)
public class DominoesGame extends GameComponent<DominoesGame.Op>
        implements Zoned<DominoTile>, Scored {

    // ==================================================================================
    // Operations
    // ==================================================================================

    public sealed interface Op permits StartOp, PlayOp, DrawOp, PassOp {}

    /** Start the game: shuffle tiles, deal hands, place starting double. */
    @EqualsAndHashCode
    public static final class StartOp implements Op {}

    /** Play a tile on a train. */
    @Getter @Accessors(fluent = true) @EqualsAndHashCode
    public static final class PlayOp implements Op {
        private final int seat;
        private final int tileOrdinal;
        private final String targetTrain;

        public PlayOp(int seat, int tileOrdinal, String targetTrain) {
            this.seat = seat;
            this.tileOrdinal = tileOrdinal;
            this.targetTrain = targetTrain;
        }
    }

    /** Draw a tile from the boneyard. */
    @Getter @Accessors(fluent = true) @EqualsAndHashCode
    public static final class DrawOp implements Op {
        private final int seat;
        public DrawOp(int seat) { this.seat = seat; }
    }

    /** Pass (marks own train as open). */
    @Getter @Accessors(fluent = true) @EqualsAndHashCode
    public static final class PassOp implements Op {
        private final int seat;
        public PassOp(int seat) { this.seat = seat; }
    }

    // ==================================================================================
    // Game State
    // ==================================================================================

    private final int maxSeats;
    private long sequence = 0;
    private final ZoneMap<DominoTile> zoneMap = new ZoneMap<>();
    private final ScoreBoard scores = new ScoreBoard();

    private int currentSeat = 0;
    private boolean started = false;
    private boolean roundOver = false;
    private boolean gameOver = false;

    /** The current starting double value for this round (6 down to 0). */
    private int startingDouble = DominoTile.MAX_PIPS;

    /** The open end value for each train. Key: train name, Value: pip value at the open end. */
    private final Map<String, Integer> trainEnds = new HashMap<>();

    /** Which player trains are "open" (anyone can play on them). */
    private final Set<String> openTrains = new HashSet<>();

    /** Whether the current player has drawn this turn (can only draw once). */
    private boolean hasDrawnThisTurn = false;

    // ==================================================================================
    // Constructors and Factories
    // ==================================================================================

    public DominoesGame() {
        this(4);
    }

    public DominoesGame(int maxPlayers) {
        this.maxSeats = maxPlayers;
    }

    public static DominoesGame create() {
        return create(2);
    }

    public static DominoesGame create(int maxPlayers) {
        return new DominoesGame(maxPlayers).withDemoSigner();
    }

    public DominoesGame withSigner(Signing.Signer signer, Signing.Hasher hasher) {
        this.signer = signer;
        this.hasher = hasher;
        return this;
    }

    // ==================================================================================
    // GameComponent Abstract Methods
    // ==================================================================================

    @Override public int minPlayers() { return 2; }
    @Override public int maxPlayers() { return maxSeats; }

    @Override
    public Set<Integer> activePlayers() {
        if (gameOver || roundOver || !started) return Set.of();
        return Set.of(currentSeat);
    }

    @Override public boolean isGameOver() { return gameOver; }

    @Override
    public Optional<Integer> winner() {
        if (!gameOver) return Optional.empty();
        // Lowest total score wins
        int bestSeat = -1;
        int bestScore = Integer.MAX_VALUE;
        for (int i = 0; i < seatedCount(); i++) {
            int s = scores.score(i);
            if (s < bestScore) {
                bestScore = s;
                bestSeat = i;
            }
        }
        int finalBest = bestScore;
        long count = 0;
        for (int i = 0; i < seatedCount(); i++) {
            if (scores.score(i) == finalBest) count++;
        }
        return count == 1 ? Optional.of(bestSeat) : Optional.empty();
    }

    // ==================================================================================
    // Trait Implementations
    // ==================================================================================

    @Override public ZoneMap<DominoTile> zones() { return zoneMap; }
    @Override public ScoreBoard scoreBoard() { return scores; }

    // ==================================================================================
    // Encode/Decode
    // ==================================================================================

    @Override
    protected CBORObject encodeOp(Op op) {
        CBORObject m = CBORObject.NewMap();
        switch (op) {
            case StartOp s -> m.Add("type", "start");
            case PlayOp p -> {
                m.Add("type", "play");
                m.Add("seat", p.seat());
                m.Add("tile", p.tileOrdinal());
                m.Add("train", p.targetTrain());
            }
            case DrawOp d -> {
                m.Add("type", "draw");
                m.Add("seat", d.seat());
            }
            case PassOp p -> {
                m.Add("type", "pass");
                m.Add("seat", p.seat());
            }
        }
        return m;
    }

    @Override
    protected Op decodeOp(CBORObject c) {
        String type = c.get("type").AsString();
        return switch (type) {
            case "start" -> new StartOp();
            case "play" -> new PlayOp(
                    c.get("seat").AsInt32(),
                    c.get("tile").AsInt32(),
                    c.get("train").AsString()
            );
            case "draw" -> new DrawOp(c.get("seat").AsInt32());
            case "pass" -> new PassOp(c.get("seat").AsInt32());
            default -> throw new IllegalArgumentException("Unknown op type: " + type);
        };
    }

    @Override
    protected void fold(Op op, Event ev) {
        switch (op) {
            case StartOp s -> foldStart(ev);
            case PlayOp p -> foldPlay(p);
            case DrawOp d -> foldDraw(d);
            case PassOp p -> foldPass(p);
        }
    }

    // ==================================================================================
    // Fold Logic
    // ==================================================================================

    private void foldStart(Event ev) {
        started = true;
        roundOver = false;

        // Define zones
        zoneMap.define("boneyard", ZoneVisibility.HIDDEN);
        zoneMap.definePerPlayer("hand", seatedCount(), ZoneVisibility.OWNER);
        zoneMap.definePerPlayer("train", seatedCount(), ZoneVisibility.PUBLIC);
        zoneMap.define("mexican", ZoneVisibility.PUBLIC);

        // Shuffle full set
        GameRandom rng = GameRandom.fromEvent(ev);
        List<DominoTile> tiles = new ArrayList<>(DominoTile.fullSet());
        rng.shuffle(tiles);

        // Find and remove the starting double
        DominoTile startDouble = new DominoTile(startingDouble, startingDouble);
        tiles.remove(startDouble);

        // Deal tiles — number depends on player count
        int handSize = handSizeForPlayers(seatedCount());
        for (int seat = 0; seat < seatedCount(); seat++) {
            Zone<DominoTile> hand = zoneMap.zone("hand", seat);
            for (int i = 0; i < handSize && !tiles.isEmpty(); i++) {
                hand.addTop(tiles.remove(0));
            }
        }

        // Remaining tiles go to boneyard
        Zone<DominoTile> boneyard = zoneMap.zone("boneyard");
        for (DominoTile tile : tiles) {
            boneyard.addTop(tile);
        }

        // Initialize all train ends to the starting double value
        for (int i = 0; i < seatedCount(); i++) {
            trainEnds.put("train:" + i, startingDouble);
        }
        trainEnds.put("mexican", startingDouble);

        openTrains.clear();
        currentSeat = 0;
        hasDrawnThisTurn = false;
    }

    private void foldPlay(PlayOp p) {
        DominoTile tile = DominoTile.fromOrdinal(p.tileOrdinal());
        Zone<DominoTile> hand = zoneMap.zone("hand", p.seat());
        hand.remove(tile);

        // Update train end
        int currentEnd = trainEnds.get(p.targetTrain());
        int newEnd = tile.otherEnd(currentEnd);
        trainEnds.put(p.targetTrain(), newEnd);

        // Add to train zone
        Zone<DominoTile> train = zoneMap.zone(p.targetTrain());
        train.addTop(tile);

        // Playing on own train closes it
        String ownTrain = "train:" + p.seat();
        if (p.targetTrain().equals(ownTrain)) {
            openTrains.remove(ownTrain);
        }

        // Check if hand is empty (round winner)
        if (hand.size() == 0) {
            endRound(p.seat());
            return;
        }

        // Double played? Player must play again (or draw+play or pass)
        if (!tile.isDouble()) {
            advanceTurn();
        }
        hasDrawnThisTurn = false;
    }

    private void foldDraw(DrawOp d) {
        Zone<DominoTile> boneyard = zoneMap.zone("boneyard");
        boneyard.removeTop().ifPresent(drawn -> {
            Zone<DominoTile> hand = zoneMap.zone("hand", d.seat());
            hand.addTop(drawn);
        });
        hasDrawnThisTurn = true;
    }

    private void foldPass(PassOp p) {
        // Mark own train as open
        openTrains.add("train:" + p.seat());
        advanceTurn();
    }

    private void advanceTurn() {
        hasDrawnThisTurn = false;

        // Check if anyone can play
        boolean anyoneCanPlay = false;
        for (int attempt = 0; attempt < seatedCount(); attempt++) {
            currentSeat = (currentSeat + 1) % seatedCount();
            if (canCurrentPlayerAct()) {
                anyoneCanPlay = true;
                break;
            }
        }

        if (!anyoneCanPlay && zoneMap.zone("boneyard").size() == 0) {
            // Nobody can play and boneyard is empty — end round
            // Winner is the player with fewest pips
            int bestSeat = 0;
            int bestPips = Integer.MAX_VALUE;
            for (int i = 0; i < seatedCount(); i++) {
                int pips = handPipCount(i);
                if (pips < bestPips) {
                    bestPips = pips;
                    bestSeat = i;
                }
            }
            endRound(bestSeat);
        }
    }

    private boolean canCurrentPlayerAct() {
        Zone<DominoTile> hand = zoneMap.zone("hand", currentSeat);
        if (hand.size() == 0) return false;

        // Can play on own train, mexican, or open trains
        for (DominoTile tile : hand.contents()) {
            if (canPlayTile(tile, currentSeat)) return true;
        }

        // Can draw from boneyard
        return zoneMap.zone("boneyard").size() > 0;
    }

    private boolean canPlayTile(DominoTile tile, int seat) {
        // Own train
        if (tile.matches(trainEnds.get("train:" + seat))) return true;
        // Mexican train
        if (tile.matches(trainEnds.get("mexican"))) return true;
        // Open trains
        for (String open : openTrains) {
            if (tile.matches(trainEnds.get(open))) return true;
        }
        return false;
    }

    private void endRound(int roundWinnerSeat) {
        roundOver = true;

        // Winner gets sum of all other players' remaining pips
        // (In Mexican Train, all players' remaining pips are added to their own score)
        for (int i = 0; i < seatedCount(); i++) {
            if (i != roundWinnerSeat) {
                int pips = handPipCount(i);
                scores.add(i, pips);
            }
        }

        // Advance to next starting double
        startingDouble--;
        if (startingDouble < 0) {
            gameOver = true;
        }
    }

    private int handPipCount(int seat) {
        Zone<DominoTile> hand = zoneMap.zone("hand", seat);
        int total = 0;
        for (DominoTile tile : hand.contents()) {
            total += tile.pipCount();
        }
        return total;
    }

    private int handSizeForPlayers(int players) {
        return switch (players) {
            case 2 -> 15;
            case 3 -> 13;
            default -> 10; // 4+ players
        };
    }

    // ==================================================================================
    // Game Actions
    // ==================================================================================

    /**
     * Start the game (deal tiles, place starting double).
     */
    @Verb(value = GameVocabulary.Deal.KEY, doc = "Start the game")
    public String start(ActionContext ctx) {
        if (started) return "Game already started";
        Signing.Signer s = resolveSigner(ctx);
        Signing.Hasher h = resolveHasher();
        append(new StartOp(), ++sequence, s, h);
        return "Game started with double-" + startingDouble + " as center";
    }

    /**
     * Play a tile on a train.
     */
    @Verb(value = GameVocabulary.Play.KEY, doc = "Play a tile")
    public String play(ActionContext ctx,
                       @Param(value = "tile", doc = "Tile ordinal") int tileOrdinal,
                       @Param(value = "train", doc = "Target train name") String train) {
        int seat = authorizedSeat(ctx);
        if (seat < 0) seat = currentSeat;

        DominoTile tile = DominoTile.fromOrdinal(tileOrdinal);

        // Validate
        Zone<DominoTile> hand = zoneMap.zone("hand", seat);
        if (!hand.contents().contains(tile)) {
            return "You don't have that tile";
        }
        if (!isValidTarget(seat, train)) {
            return "Cannot play on train: " + train;
        }
        int end = trainEnds.get(train);
        if (!tile.matches(end)) {
            return "Tile " + tile + " doesn't match end value " + end;
        }

        Signing.Signer s = resolveSigner(ctx);
        Signing.Hasher h = resolveHasher();
        append(new PlayOp(seat, tileOrdinal, train), ++sequence, s, h);
        return "Played " + tile + " on " + train;
    }

    /**
     * Draw a tile from the boneyard.
     */
    @Verb(value = GameVocabulary.Draw.KEY, doc = "Draw from boneyard")
    public String draw(ActionContext ctx) {
        int seat = authorizedSeat(ctx);
        if (seat < 0) seat = currentSeat;

        if (hasDrawnThisTurn) return "Already drew this turn";
        if (zoneMap.zone("boneyard").size() == 0) return "Boneyard is empty";

        Signing.Signer s = resolveSigner(ctx);
        Signing.Hasher h = resolveHasher();
        append(new DrawOp(seat), ++sequence, s, h);
        return "Drew a tile";
    }

    /**
     * Pass (marks your train as open).
     */
    @Verb(value = GameVocabulary.Pass.KEY, doc = "Pass your turn")
    public String pass(ActionContext ctx) {
        int seat = authorizedSeat(ctx);
        if (seat < 0) seat = currentSeat;

        Signing.Signer s = resolveSigner(ctx);
        Signing.Hasher h = resolveHasher();
        append(new PassOp(seat), ++sequence, s, h);
        return "Passed — train is now open";
    }

    // ==================================================================================
    // Direct Play Methods (for tests)
    // ==================================================================================

    public String doStart() {
        if (started) return "Already started";
        append(new StartOp(), ++sequence, signer, hasher);
        return "Started";
    }

    public String doPlay(int seat, int tileOrdinal, String train) {
        append(new PlayOp(seat, tileOrdinal, train), ++sequence, signer, hasher);
        return "Played " + DominoTile.fromOrdinal(tileOrdinal);
    }

    public String doDraw(int seat) {
        append(new DrawOp(seat), ++sequence, signer, hasher);
        return "Drew";
    }

    public String doPass(int seat) {
        append(new PassOp(seat), ++sequence, signer, hasher);
        return "Passed";
    }

    // ==================================================================================
    // Queries
    // ==================================================================================

    /** Whether the game has been started. */
    public boolean isStarted() { return started; }

    /** Whether the current round is over. */
    public boolean isRoundOver() { return roundOver; }

    /** Current player's seat. */
    public int currentSeat() { return currentSeat; }

    /** The starting double value for the current round. */
    public int startingDouble() { return startingDouble; }

    /** Get the open end value of a train. */
    public int trainEnd(String trainName) {
        return trainEnds.getOrDefault(trainName, -1);
    }

    /** Whether a train is open (anyone can play on it). */
    public boolean isTrainOpen(String trainName) {
        return openTrains.contains(trainName);
    }

    /** Whether the current player has drawn this turn. */
    public boolean hasDrawnThisTurn() { return hasDrawnThisTurn; }

    /** Tiles in a player's hand. */
    public List<DominoTile> hand(int seat) {
        return zoneMap.zone("hand", seat).contents();
    }

    /** Number of tiles in the boneyard. */
    public int boneyardSize() {
        return zoneMap.zone("boneyard").size();
    }

    /** Status text for display. */
    public String statusText() {
        if (gameOver) {
            Optional<Integer> w = winner();
            if (w.isPresent()) {
                String name = playerName(w.get()).orElse("Player " + (w.get() + 1));
                return name + " wins!";
            }
            return "Game over — tie!";
        }
        if (roundOver) {
            return "Round over — starting double-" + (startingDouble + 1) + " round next";
        }
        if (!started) return "Waiting to start";
        String name = playerName(currentSeat).orElse("Player " + (currentSeat + 1));
        return name + "'s turn";
    }

    public List<String> metaRows() {
        List<String> rows = new ArrayList<>();
        rows.add("Current seat: " + seatLabel(currentSeat));
        rows.add("Starting double: " + startingDouble);
        rows.add("Boneyard: " + boneyardSize() + " tiles");
        rows.add("Drew this turn: " + (hasDrawnThisTurn ? "yes" : "no"));
        return rows;
    }

    public List<String> trainRows() {
        List<String> rows = new ArrayList<>();
        if (!started || zoneMap.zoneCount() == 0) {
            rows.add("No trains yet");
            return rows;
        }

        for (int seat = 0; seat < seatedCount(); seat++) {
            String train = "train:" + seat;
            rows.add(trainLabel(train)
                    + " | end: " + trainEnd(train)
                    + " | tiles: " + zoneMap.zone(train).size()
                    + " | " + (isTrainOpen(train) ? "open" : "closed"));
        }
        rows.add("Mexican"
                + " | end: " + trainEnd("mexican")
                + " | tiles: " + zoneMap.zone("mexican").size()
                + " | open");
        return rows;
    }

    public List<String> playerRows() {
        List<String> rows = new ArrayList<>();
        for (int seat = 0; seat < seatedCount(); seat++) {
            rows.add(seatLabel(seat) + " | hand: " + hand(seat).size() + " | score: " + scores.score(seat));
        }
        return rows;
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private boolean isValidTarget(int seat, String train) {
        // Can always play on own train
        if (train.equals("train:" + seat)) return true;
        // Can always play on mexican train
        if (train.equals("mexican")) return true;
        // Can play on open trains
        return openTrains.contains(train);
    }

    private String seatLabel(int seat) {
        return playerName(seat).orElse("P" + (seat + 1));
    }

    private String trainLabel(String train) {
        if (train.startsWith("train:")) {
            int seat = Integer.parseInt(train.substring("train:".length()));
            return seatLabel(seat) + "'s train";
        }
        return train;
    }
}
