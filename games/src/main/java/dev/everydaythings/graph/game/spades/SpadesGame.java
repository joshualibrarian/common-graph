package dev.everydaythings.graph.game.spades;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.game.*;
import dev.everydaythings.graph.game.card.PlayingCard;
import dev.everydaythings.graph.dispatch.ActionContext;
import dev.everydaythings.graph.item.Param;
import dev.everydaythings.graph.item.Type;
import dev.everydaythings.graph.item.Verb;
import dev.everydaythings.graph.game.GameVocabulary;
import dev.everydaythings.graph.crypt.Signing;
import dev.everydaythings.graph.ui.scene.Scene;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.*;

/**
 * Spades — a 4-player partnership trick-taking card game.
 *
 * <p>Players are divided into two partnerships (seats 0+2 vs seats 1+3).
 * Each round: 13 cards are dealt, players bid how many tricks they'll take,
 * then play 13 tricks. Spades are always trump.
 *
 * <p>Scoring:
 * <ul>
 *   <li>Met bid: bid × 10 + overtricks (bags)</li>
 *   <li>Failed bid: -bid × 10</li>
 *   <li>Nil bid: +100 if zero tricks, -100 otherwise</li>
 *   <li>Every 10 bags: -100 penalty</li>
 *   <li>Game to 500 points (or configurable)</li>
 * </ul>
 *
 * <p>Composes three traits:
 * <ul>
 *   <li>{@link Zoned} — deck, hands, trick, team won piles</li>
 *   <li>{@link Scored} — per-team scoring with bag tracking</li>
 *   <li>{@link Phased} — DEAL → BID → PLAY</li>
 * </ul>
 */
@Type(value = "cg:type/spades", glyph = "\u2660")
@Scene(as = SpadesSurface.class)
public class SpadesGame extends GameComponent<SpadesGame.Op>
        implements Zoned<PlayingCard>, Scored, Phased {

    // ==================================================================================
    // Constants
    // ==================================================================================

    public static final String PHASE_DEAL = "deal";
    public static final String PHASE_BID = "bid";
    public static final String PHASE_PLAY = "play";

    public static final int WINNING_SCORE = 500;
    public static final int CARDS_PER_HAND = 13;

    // ==================================================================================
    // Operations
    // ==================================================================================

    public sealed interface Op permits DealOp, BidOp, PlayCardOp {}

    /** Deal cards for a new round. */
    @EqualsAndHashCode
    public static final class DealOp implements Op {}

    /** Place a bid (0 = nil). */
    @Getter @Accessors(fluent = true) @EqualsAndHashCode
    public static final class BidOp implements Op {
        private final int seat;
        private final int tricks;
        public BidOp(int seat, int tricks) { this.seat = seat; this.tricks = tricks; }
    }

    /** Play a card to the current trick. */
    @Getter @Accessors(fluent = true) @EqualsAndHashCode
    public static final class PlayCardOp implements Op {
        private final int seat;
        private final int cardOrdinal;
        public PlayCardOp(int seat, int cardOrdinal) { this.seat = seat; this.cardOrdinal = cardOrdinal; }
    }

    // ==================================================================================
    // Game State
    // ==================================================================================

    private long sequence = 0;
    private final ZoneMap<PlayingCard> zoneMap = new ZoneMap<>();
    private final ScoreBoard scores = new ScoreBoard();

    private String phase = PHASE_DEAL;
    private boolean gameOver = false;
    private int currentSeat = 0;
    private int leadSeat = 0;
    private int tricksPlayed = 0;
    private boolean spadesBroken = false;

    /** Bids per seat (-1 = not yet bid). */
    private final int[] bids = {-1, -1, -1, -1};

    /** Tricks won per seat this round. */
    private final int[] tricksWon = new int[4];

    /** Current trick cards (seat → card). */
    private final Map<Integer, PlayingCard> currentTrick = new LinkedHashMap<>();

    /** Lead suit for current trick. */
    private PlayingCard.Suit leadSuit = null;

    // ==================================================================================
    // Constructors and Factories
    // ==================================================================================

    public SpadesGame() {}

    public static SpadesGame create() {
        return new SpadesGame().withDemoSigner();
    }

    public SpadesGame withSigner(Signing.Signer signer, Signing.Hasher hasher) {
        this.signer = signer;
        this.hasher = hasher;
        return this;
    }

    // ==================================================================================
    // GameComponent Abstract Methods
    // ==================================================================================

    @Override public int minPlayers() { return 4; }
    @Override public int maxPlayers() { return 4; }

    @Override
    public Set<Integer> activePlayers() {
        if (gameOver || PHASE_DEAL.equals(phase)) return Set.of();
        return Set.of(currentSeat);
    }

    @Override public boolean isGameOver() { return gameOver; }

    @Override
    public Optional<Integer> winner() {
        if (!gameOver) return Optional.empty();
        // Team with higher score: team 0 = seats 0+2, team 1 = seats 1+3
        int team0 = scores.score(0, "score");
        int team1 = scores.score(1, "score");
        if (team0 > team1) return Optional.of(0);
        if (team1 > team0) return Optional.of(1);
        return Optional.empty();
    }

    // ==================================================================================
    // Trait Implementations
    // ==================================================================================

    @Override public ZoneMap<PlayingCard> zones() { return zoneMap; }
    @Override public ScoreBoard scoreBoard() { return scores; }
    @Override public String currentPhase() { return phase; }

    @Override
    public List<String> phases() {
        return List.of(PHASE_DEAL, PHASE_BID, PHASE_PLAY);
    }

    // ==================================================================================
    // Encode/Decode
    // ==================================================================================

    @Override
    protected CBORObject encodeOp(Op op) {
        CBORObject m = CBORObject.NewMap();
        switch (op) {
            case DealOp d -> m.Add("type", "deal");
            case BidOp b -> { m.Add("type", "bid"); m.Add("seat", b.seat()); m.Add("tricks", b.tricks()); }
            case PlayCardOp p -> { m.Add("type", "play"); m.Add("seat", p.seat()); m.Add("card", p.cardOrdinal()); }
        }
        return m;
    }

    @Override
    protected Op decodeOp(CBORObject c) {
        String type = c.get("type").AsString();
        return switch (type) {
            case "deal" -> new DealOp();
            case "bid" -> new BidOp(c.get("seat").AsInt32(), c.get("tricks").AsInt32());
            case "play" -> new PlayCardOp(c.get("seat").AsInt32(), c.get("card").AsInt32());
            default -> throw new IllegalArgumentException("Unknown op type: " + type);
        };
    }

    @Override
    protected void fold(Op op, Event ev) {
        switch (op) {
            case DealOp d -> foldDeal(ev);
            case BidOp b -> foldBid(b);
            case PlayCardOp p -> foldPlayCard(p);
        }
    }

    // ==================================================================================
    // Fold Logic
    // ==================================================================================

    private void foldDeal(Event ev) {
        phase = PHASE_BID;
        Arrays.fill(bids, -1);
        Arrays.fill(tricksWon, 0);
        tricksPlayed = 0;
        spadesBroken = false;
        currentTrick.clear();
        leadSuit = null;

        // Define zones (idempotent)
        if (zoneMap.zoneCount() == 0) {
            zoneMap.define("deck", ZoneVisibility.HIDDEN);
            zoneMap.definePerPlayer("hand", 4, ZoneVisibility.OWNER);
            zoneMap.define("trick", ZoneVisibility.PUBLIC);
            // Two team won piles
            zoneMap.define("won:0", ZoneVisibility.PUBLIC);
            zoneMap.define("won:1", ZoneVisibility.PUBLIC);
        } else {
            zoneMap.zone("deck").clear();
            zoneMap.zone("trick").clear();
            zoneMap.zone("won:0").clear();
            zoneMap.zone("won:1").clear();
            for (int i = 0; i < 4; i++) {
                zoneMap.zone("hand", i).clear();
            }
        }

        // Shuffle and deal
        GameRandom rng = GameRandom.fromEvent(ev);
        List<PlayingCard> deck = new ArrayList<>(PlayingCard.fullDeck());
        rng.shuffle(deck);

        for (int i = 0; i < 52; i++) {
            int seat = i % 4;
            zoneMap.zone("hand", seat).addTop(deck.get(i));
        }

        currentSeat = 0;
    }

    private void foldBid(BidOp b) {
        bids[b.seat()] = b.tricks();

        // Check if all players have bid
        boolean allBid = true;
        for (int bid : bids) {
            if (bid < 0) { allBid = false; break; }
        }

        if (allBid) {
            phase = PHASE_PLAY;
            currentSeat = leadSeat;
        } else {
            currentSeat = (b.seat() + 1) % 4;
        }
    }

    private void foldPlayCard(PlayCardOp p) {
        PlayingCard card = PlayingCard.fromOrdinal(p.cardOrdinal());
        zoneMap.zone("hand", p.seat()).remove(card);
        currentTrick.put(p.seat(), card);

        // Set lead suit
        if (currentTrick.size() == 1) {
            leadSuit = card.suit();
        }

        // Track spades broken
        if (card.suit() == PlayingCard.Suit.SPADES && leadSuit != PlayingCard.Suit.SPADES) {
            spadesBroken = true;
        }

        if (currentTrick.size() == 4) {
            // Trick complete — determine winner
            int winner = resolveTrickWinner();
            tricksWon[winner]++;
            tricksPlayed++;

            // Move trick cards to team won pile
            int team = teamOf(winner);
            for (PlayingCard c : currentTrick.values()) {
                zoneMap.zone("won:" + team).addTop(c);
            }

            currentTrick.clear();
            leadSuit = null;
            leadSeat = winner;
            currentSeat = winner;

            if (tricksPlayed == CARDS_PER_HAND) {
                scoreRound();
            }
        } else {
            currentSeat = (p.seat() + 1) % 4;
        }
    }

    private int resolveTrickWinner() {
        int winner = -1;
        PlayingCard winningCard = null;

        for (Map.Entry<Integer, PlayingCard> entry : currentTrick.entrySet()) {
            int seat = entry.getKey();
            PlayingCard card = entry.getValue();

            if (winningCard == null) {
                winner = seat;
                winningCard = card;
            } else if (card.suit() == PlayingCard.Suit.SPADES && winningCard.suit() != PlayingCard.Suit.SPADES) {
                // Spade trumps non-spade
                winner = seat;
                winningCard = card;
            } else if (card.suit() == winningCard.suit() && card.rank().value() > winningCard.rank().value()) {
                // Higher card of same suit
                winner = seat;
                winningCard = card;
            }
        }
        return winner;
    }

    private void scoreRound() {
        // Score each team
        for (int team = 0; team < 2; team++) {
            int seat1 = team;       // 0 or 1
            int seat2 = team + 2;   // 2 or 3

            int teamBid = 0;
            int teamTricks = tricksWon[seat1] + tricksWon[seat2];
            boolean hasNil = false;

            // Handle nil bids individually
            for (int seat : new int[]{seat1, seat2}) {
                if (bids[seat] == 0) {
                    // Nil bid
                    hasNil = true;
                    if (tricksWon[seat] == 0) {
                        scores.add(team, "score", 100);
                    } else {
                        scores.add(team, "score", -100);
                    }
                } else {
                    teamBid += bids[seat];
                }
            }

            // Score non-nil combined bid
            if (teamBid > 0) {
                if (teamTricks >= teamBid) {
                    int overtricks = teamTricks - teamBid;
                    if (hasNil) {
                        // Nil player's tricks don't count toward bid
                        // Recalculate: only count non-nil player's tricks
                        int nonNilTricks = 0;
                        for (int seat : new int[]{seat1, seat2}) {
                            if (bids[seat] > 0) nonNilTricks += tricksWon[seat];
                        }
                        overtricks = Math.max(0, nonNilTricks - teamBid);
                    }
                    scores.add(team, "score", teamBid * 10 + overtricks);
                    scores.add(team, "bags", overtricks);

                    // Bag penalty: every 10 bags
                    int totalBags = scores.score(team, "bags");
                    if (totalBags >= 10) {
                        scores.add(team, "score", -100);
                        scores.add(team, "bags", -10);
                    }
                } else {
                    scores.add(team, "score", -teamBid * 10);
                }
            }
        }

        // Check for game end
        int team0Score = scores.score(0, "score");
        int team1Score = scores.score(1, "score");
        if (team0Score >= WINNING_SCORE || team1Score >= WINNING_SCORE) {
            gameOver = true;
        } else {
            // Reset for next round
            phase = PHASE_DEAL;
            leadSeat = (leadSeat + 1) % 4;
        }
    }

    /**
     * Team index for a seat: seats 0,2 = team 0; seats 1,3 = team 1.
     */
    public static int teamOf(int seat) {
        return seat % 2;
    }

    // ==================================================================================
    // Game Actions (Verbs)
    // ==================================================================================

    @Verb(value = GameVocabulary.Deal.KEY, doc = "Deal cards for a new round")
    public String deal(ActionContext ctx) {
        if (!PHASE_DEAL.equals(phase)) return "Cannot deal now";
        Signing.Signer s = resolveSigner(ctx);
        Signing.Hasher h = resolveHasher();
        append(new DealOp(), ++sequence, s, h);
        return "Cards dealt";
    }

    @Verb(value = GameVocabulary.Bid.KEY, doc = "Bid number of tricks")
    public String bid(ActionContext ctx,
                      @Param(value = "tricks", doc = "Number of tricks (0 = nil)") int tricks) {
        int seat = authorizedSeat(ctx);
        if (seat < 0) seat = currentSeat;

        if (!PHASE_BID.equals(phase)) return "Not in bidding phase";
        if (tricks < 0 || tricks > CARDS_PER_HAND) return "Bid must be 0-13";

        Signing.Signer s = resolveSigner(ctx);
        Signing.Hasher h = resolveHasher();
        append(new BidOp(seat, tricks), ++sequence, s, h);
        return "Bid " + (tricks == 0 ? "nil" : tricks + " tricks");
    }

    @Verb(value = GameVocabulary.Play.KEY, doc = "Play a card")
    public String playCard(ActionContext ctx,
                           @Param(value = "card", doc = "Card ordinal") int cardOrdinal) {
        int seat = authorizedSeat(ctx);
        if (seat < 0) seat = currentSeat;

        if (!PHASE_PLAY.equals(phase)) return "Not in play phase";

        PlayingCard card = PlayingCard.fromOrdinal(cardOrdinal);
        String error = validatePlay(seat, card);
        if (error != null) return error;

        Signing.Signer s = resolveSigner(ctx);
        Signing.Hasher h = resolveHasher();
        append(new PlayCardOp(seat, cardOrdinal), ++sequence, s, h);
        return "Played " + card;
    }

    // ==================================================================================
    // Direct Play Methods (for tests)
    // ==================================================================================

    public String doDeal() {
        if (!PHASE_DEAL.equals(phase)) return "Cannot deal now";
        append(new DealOp(), ++sequence, signer, hasher);
        return "Dealt";
    }

    public String doBid(int seat, int tricks) {
        append(new BidOp(seat, tricks), ++sequence, signer, hasher);
        return "Bid " + tricks;
    }

    public String doPlayCard(int seat, int cardOrdinal) {
        append(new PlayCardOp(seat, cardOrdinal), ++sequence, signer, hasher);
        return "Played " + PlayingCard.fromOrdinal(cardOrdinal);
    }

    // ==================================================================================
    // Play Validation
    // ==================================================================================

    private String validatePlay(int seat, PlayingCard card) {
        List<PlayingCard> hand = zoneMap.zone("hand", seat).contents();
        if (!hand.contains(card)) return "You don't have that card";

        // If leading
        if (currentTrick.isEmpty()) {
            // Can't lead spades unless broken (or only have spades)
            if (card.suit() == PlayingCard.Suit.SPADES && !spadesBroken) {
                boolean onlySpades = hand.stream()
                        .allMatch(c -> c.suit() == PlayingCard.Suit.SPADES);
                if (!onlySpades) return "Spades not broken yet";
            }
            return null;
        }

        // Must follow suit if possible
        if (card.suit() != leadSuit) {
            boolean hasLeadSuit = hand.stream()
                    .anyMatch(c -> c.suit() == leadSuit);
            if (hasLeadSuit) return "Must follow suit (" + leadSuit + ")";
        }

        return null;
    }

    // ==================================================================================
    // Queries
    // ==================================================================================

    /** Current player's seat. */
    public int currentSeat() { return currentSeat; }

    /** Bid for a seat (-1 if not yet bid). */
    public int bid(int seat) { return bids[seat]; }

    /** Tricks won by a seat this round. */
    public int tricksWon(int seat) { return tricksWon[seat]; }

    /** Number of tricks played this round. */
    public int tricksPlayed() { return tricksPlayed; }

    /** Whether spades have been broken. */
    public boolean isSpadesBroken() { return spadesBroken; }

    /** Cards in current trick. */
    public Map<Integer, PlayingCard> currentTrick() {
        return Collections.unmodifiableMap(currentTrick);
    }

    /** Lead suit for current trick. */
    public PlayingCard.Suit leadSuit() { return leadSuit; }

    /** Team score. */
    public int teamScore(int team) {
        return scores.score(team, "score");
    }

    /** Team bags. */
    public int teamBags(int team) {
        return scores.score(team, "bags");
    }

    /** Hand for a seat. */
    public List<PlayingCard> hand(int seat) {
        if (zoneMap.zoneCount() == 0) return List.of();
        return zoneMap.zone("hand", seat).contents();
    }

    /** Status text for display. */
    public String statusText() {
        if (gameOver) {
            Optional<Integer> w = winner();
            if (w.isPresent()) {
                return "Team " + (w.get() + 1) + " wins!";
            }
            return "Game over — tie!";
        }
        if (PHASE_DEAL.equals(phase)) return "Ready for next round";

        String name = playerName(currentSeat).orElse("Player " + (currentSeat + 1));
        if (PHASE_BID.equals(phase)) {
            return name + "'s turn to bid";
        }
        return name + "'s turn to play (trick " + (tricksPlayed + 1) + "/13)";
    }

    public List<String> metaRows() {
        List<String> rows = new ArrayList<>();
        rows.add("Phase: " + phase.toUpperCase(Locale.ROOT));
        rows.add("Lead: " + seatLabel(leadSeat));
        rows.add("Current: " + seatLabel(currentSeat));
        rows.add("Spades broken: " + (spadesBroken ? "yes" : "no"));
        return rows;
    }

    public List<String> teamRows() {
        return List.of(
                "Team 1 (P1+P3): " + teamScore(0) + " pts, " + teamBags(0) + " bags",
                "Team 2 (P2+P4): " + teamScore(1) + " pts, " + teamBags(1) + " bags"
        );
    }

    public boolean hasCurrentTrick() {
        return !currentTrick.isEmpty();
    }

    public List<String> currentTrickRows() {
        List<String> rows = new ArrayList<>();
        for (Map.Entry<Integer, PlayingCard> entry : currentTrick.entrySet()) {
            rows.add(seatLabel(entry.getKey()) + ": " + entry.getValue());
        }
        return rows;
    }

    public List<String> seatRows() {
        List<String> rows = new ArrayList<>();
        for (int seat = 0; seat < seatedCount(); seat++) {
            String bidLabel = bids[seat] >= 0 ? String.valueOf(bids[seat]) : "-";
            rows.add(seatLabel(seat)
                    + " | hand: " + hand(seat).size()
                    + " | bid: " + bidLabel
                    + " | won: " + tricksWon[seat]);
        }
        return rows;
    }

    private String seatLabel(int seat) {
        return playerName(seat).orElse("P" + (seat + 1));
    }
}
