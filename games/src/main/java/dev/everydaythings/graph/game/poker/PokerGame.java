package dev.everydaythings.graph.game.poker;

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
 * Texas Hold'em Poker — the most popular poker variant.
 *
 * <p>Each player receives 2 hole cards. Five community cards are dealt
 * in three stages (flop, turn, river). Players make the best 5-card hand
 * from any combination of their hole cards and the community cards.
 *
 * <p>Composes all four traits:
 * <ul>
 *   <li>{@link Zoned} — deck, community, muck, per-player hands</li>
 *   <li>{@link Scored} — chip stacks tracked per player</li>
 *   <li>{@link Phased} — PREFLOP → FLOP → TURN → RIVER → SHOWDOWN</li>
 *   <li>{@link Randomized} — deck shuffle from deterministic event RNG</li>
 * </ul>
 */
@Type(value = "cg:type/poker", glyph = "\uD83C\uDCA1")
@Scene(as = PokerSurface.class)
public class PokerGame extends GameComponent<PokerGame.Op>
        implements Zoned<PlayingCard>, Scored, Phased, Randomized {

    // ==================================================================================
    // Constants
    // ==================================================================================

    public static final String PHASE_PREFLOP = "preflop";
    public static final String PHASE_FLOP = "flop";
    public static final String PHASE_TURN = "turn";
    public static final String PHASE_RIVER = "river";
    public static final String PHASE_SHOWDOWN = "showdown";

    public static final int DEFAULT_BUY_IN = 1000;
    public static final int DEFAULT_SMALL_BLIND = 5;
    public static final int DEFAULT_BIG_BLIND = 10;

    // ==================================================================================
    // Operations
    // ==================================================================================

    public sealed interface Op permits DealOp, BetOp, CallOp, RaiseOp,
            CheckOp, FoldOp, AllInOp {}

    @EqualsAndHashCode
    public static final class DealOp implements Op {}

    @Getter @Accessors(fluent = true) @EqualsAndHashCode
    public static final class BetOp implements Op {
        private final int seat;
        private final int amount;
        public BetOp(int seat, int amount) { this.seat = seat; this.amount = amount; }
    }

    @Getter @Accessors(fluent = true) @EqualsAndHashCode
    public static final class CallOp implements Op {
        private final int seat;
        public CallOp(int seat) { this.seat = seat; }
    }

    @Getter @Accessors(fluent = true) @EqualsAndHashCode
    public static final class RaiseOp implements Op {
        private final int seat;
        private final int amount;
        public RaiseOp(int seat, int amount) { this.seat = seat; this.amount = amount; }
    }

    @Getter @Accessors(fluent = true) @EqualsAndHashCode
    public static final class CheckOp implements Op {
        private final int seat;
        public CheckOp(int seat) { this.seat = seat; }
    }

    @Getter @Accessors(fluent = true) @EqualsAndHashCode
    public static final class FoldOp implements Op {
        private final int seat;
        public FoldOp(int seat) { this.seat = seat; }
    }

    @Getter @Accessors(fluent = true) @EqualsAndHashCode
    public static final class AllInOp implements Op {
        private final int seat;
        public AllInOp(int seat) { this.seat = seat; }
    }

    // ==================================================================================
    // Game State
    // ==================================================================================

    private final int maxSeats;
    private long sequence = 0;
    private final ZoneMap<PlayingCard> zoneMap = new ZoneMap<>();
    private final ScoreBoard scores = new ScoreBoard();

    private String phase = PHASE_PREFLOP;
    private boolean handInProgress = false;
    private boolean gameOver = false;
    private int dealerSeat = 0;
    private int currentSeat = 0;
    private int pot = 0;
    private int currentBet = 0;
    private int smallBlind = DEFAULT_SMALL_BLIND;
    private int bigBlind = DEFAULT_BIG_BLIND;

    /** Per-seat bet in current round. */
    private final Map<Integer, Integer> roundBets = new HashMap<>();

    /** Players who have folded this hand. */
    private final Set<Integer> folded = new HashSet<>();

    /** Players who have acted in the current betting round. */
    private final Set<Integer> acted = new HashSet<>();

    /** Players who are all-in. */
    private final Set<Integer> allIn = new HashSet<>();

    /** Last hand winner (for display). */
    private int lastWinner = -1;

    // ==================================================================================
    // Constructors and Factories
    // ==================================================================================

    public PokerGame() {
        this(8);
    }

    public PokerGame(int maxPlayers) {
        this.maxSeats = maxPlayers;
    }

    public static PokerGame create() {
        return create(2);
    }

    public static PokerGame create(int maxPlayers) {
        return new PokerGame(maxPlayers).withDemoSigner();
    }

    public PokerGame withSigner(Signing.Signer signer, Signing.Hasher hasher) {
        this.signer = signer;
        this.hasher = hasher;
        return this;
    }

    public PokerGame withBlinds(int small, int big) {
        this.smallBlind = small;
        this.bigBlind = big;
        return this;
    }

    // ==================================================================================
    // GameComponent Abstract Methods
    // ==================================================================================

    @Override public int minPlayers() { return 2; }
    @Override public int maxPlayers() { return maxSeats; }

    @Override
    public Set<Integer> activePlayers() {
        if (gameOver || !handInProgress || PHASE_SHOWDOWN.equals(phase)) return Set.of();
        if (folded.contains(currentSeat) || allIn.contains(currentSeat)) return Set.of();
        return Set.of(currentSeat);
    }

    @Override public boolean isGameOver() { return gameOver; }

    @Override
    public Optional<Integer> winner() {
        if (!gameOver) return Optional.empty();
        int bestSeat = -1;
        int bestChips = -1;
        for (int i = 0; i < seatedCount(); i++) {
            int chips = scores.score(i, "chips");
            if (chips > bestChips) {
                bestChips = chips;
                bestSeat = i;
            }
        }
        return Optional.of(bestSeat);
    }

    // ==================================================================================
    // Trait Implementations
    // ==================================================================================

    @Override public ZoneMap<PlayingCard> zones() { return zoneMap; }
    @Override public ScoreBoard scoreBoard() { return scores; }
    @Override public String currentPhase() { return phase; }

    @Override
    public List<String> phases() {
        return List.of(PHASE_PREFLOP, PHASE_FLOP, PHASE_TURN, PHASE_RIVER, PHASE_SHOWDOWN);
    }

    // ==================================================================================
    // Encode/Decode
    // ==================================================================================

    @Override
    protected CBORObject encodeOp(Op op) {
        CBORObject m = CBORObject.NewMap();
        switch (op) {
            case DealOp d -> m.Add("type", "deal");
            case BetOp b -> { m.Add("type", "bet"); m.Add("seat", b.seat()); m.Add("amount", b.amount()); }
            case CallOp c -> { m.Add("type", "call"); m.Add("seat", c.seat()); }
            case RaiseOp r -> { m.Add("type", "raise"); m.Add("seat", r.seat()); m.Add("amount", r.amount()); }
            case CheckOp c -> { m.Add("type", "check"); m.Add("seat", c.seat()); }
            case FoldOp f -> { m.Add("type", "fold"); m.Add("seat", f.seat()); }
            case AllInOp a -> { m.Add("type", "allin"); m.Add("seat", a.seat()); }
        }
        return m;
    }

    @Override
    protected Op decodeOp(CBORObject c) {
        String type = c.get("type").AsString();
        return switch (type) {
            case "deal" -> new DealOp();
            case "bet" -> new BetOp(c.get("seat").AsInt32(), c.get("amount").AsInt32());
            case "call" -> new CallOp(c.get("seat").AsInt32());
            case "raise" -> new RaiseOp(c.get("seat").AsInt32(), c.get("amount").AsInt32());
            case "check" -> new CheckOp(c.get("seat").AsInt32());
            case "fold" -> new FoldOp(c.get("seat").AsInt32());
            case "allin" -> new AllInOp(c.get("seat").AsInt32());
            default -> throw new IllegalArgumentException("Unknown op type: " + type);
        };
    }

    @Override
    protected void fold(Op op, Event ev) {
        switch (op) {
            case DealOp d -> foldDeal(ev);
            case BetOp b -> foldBet(b);
            case CallOp c -> foldCall(c);
            case RaiseOp r -> foldRaise(r);
            case CheckOp c -> foldCheck(c);
            case FoldOp f -> foldFold(f);
            case AllInOp a -> foldAllIn(a);
        }
    }

    // ==================================================================================
    // Fold Logic
    // ==================================================================================

    private void foldDeal(Event ev) {
        handInProgress = true;
        phase = PHASE_PREFLOP;
        pot = 0;
        currentBet = 0;
        roundBets.clear();
        folded.clear();
        acted.clear();
        allIn.clear();

        // Define zones (idempotent after first hand)
        if (zoneMap.zoneCount() == 0) {
            zoneMap.define("deck", ZoneVisibility.HIDDEN);
            zoneMap.define("community", ZoneVisibility.PUBLIC);
            zoneMap.define("muck", ZoneVisibility.HIDDEN);
            zoneMap.definePerPlayer("hand", seatedCount(), ZoneVisibility.OWNER);

            // Initialize chip stacks
            for (int i = 0; i < seatedCount(); i++) {
                if (scores.score(i, "chips") == 0) {
                    scores.set(i, "chips", DEFAULT_BUY_IN);
                }
            }
        } else {
            // Clear zones for new hand
            zoneMap.zone("deck").clear();
            zoneMap.zone("community").clear();
            zoneMap.zone("muck").clear();
            for (int i = 0; i < seatedCount(); i++) {
                zoneMap.zone("hand", i).clear();
            }
        }

        // Shuffle and fill deck
        GameRandom rng = GameRandom.fromEvent(ev);
        List<PlayingCard> deck = new ArrayList<>(PlayingCard.fullDeck());
        rng.shuffle(deck);
        Zone<PlayingCard> deckZone = zoneMap.zone("deck");
        for (PlayingCard card : deck) {
            deckZone.addTop(card);
        }

        // Deal 2 hole cards to each player
        for (int round = 0; round < 2; round++) {
            for (int i = 0; i < seatedCount(); i++) {
                int seat = (dealerSeat + 1 + i) % seatedCount();
                dealTo(seat);
            }
        }

        // Post blinds
        int sbSeat = (dealerSeat + 1) % seatedCount();
        int bbSeat = (dealerSeat + 2) % seatedCount();
        if (seatedCount() == 2) {
            // Heads-up: dealer is small blind
            sbSeat = dealerSeat;
            bbSeat = (dealerSeat + 1) % seatedCount();
        }

        postBlind(sbSeat, smallBlind);
        postBlind(bbSeat, bigBlind);
        currentBet = bigBlind;

        // Action starts left of big blind
        currentSeat = (bbSeat + 1) % seatedCount();
    }

    private void dealTo(int seat) {
        zoneMap.zone("deck").removeTop().ifPresent(card ->
                zoneMap.zone("hand", seat).addTop(card));
    }

    private void postBlind(int seat, int amount) {
        int actual = Math.min(amount, scores.score(seat, "chips"));
        scores.add(seat, "chips", -actual);
        roundBets.merge(seat, actual, Integer::sum);
        pot += actual;
    }

    private void foldBet(BetOp b) {
        int amount = Math.min(b.amount(), scores.score(b.seat(), "chips"));
        scores.add(b.seat(), "chips", -amount);
        roundBets.merge(b.seat(), amount, Integer::sum);
        pot += amount;
        currentBet = roundBets.get(b.seat());
        acted.clear();
        acted.add(b.seat());
        advanceAction();
    }

    private void foldCall(CallOp c) {
        int toCall = currentBet - roundBets.getOrDefault(c.seat(), 0);
        int actual = Math.min(toCall, scores.score(c.seat(), "chips"));
        scores.add(c.seat(), "chips", -actual);
        roundBets.merge(c.seat(), actual, Integer::sum);
        pot += actual;
        acted.add(c.seat());
        advanceAction();
    }

    private void foldRaise(RaiseOp r) {
        int totalBet = r.amount();
        int alreadyIn = roundBets.getOrDefault(r.seat(), 0);
        int toAdd = Math.min(totalBet - alreadyIn, scores.score(r.seat(), "chips"));
        scores.add(r.seat(), "chips", -toAdd);
        roundBets.put(r.seat(), alreadyIn + toAdd);
        pot += toAdd;
        currentBet = roundBets.get(r.seat());
        acted.clear();
        acted.add(r.seat());
        advanceAction();
    }

    private void foldCheck(CheckOp c) {
        acted.add(c.seat());
        advanceAction();
    }

    private void foldFold(FoldOp f) {
        folded.add(f.seat());
        acted.add(f.seat());

        // Check if only one player left
        int remaining = 0;
        int lastSeat = -1;
        for (int i = 0; i < seatedCount(); i++) {
            if (!folded.contains(i)) {
                remaining++;
                lastSeat = i;
            }
        }
        if (remaining == 1) {
            awardPot(lastSeat);
            return;
        }

        advanceAction();
    }

    private void foldAllIn(AllInOp a) {
        int chips = scores.score(a.seat(), "chips");
        scores.set(a.seat(), "chips", 0);
        roundBets.merge(a.seat(), chips, Integer::sum);
        pot += chips;
        if (roundBets.get(a.seat()) > currentBet) {
            currentBet = roundBets.get(a.seat());
            acted.clear();
        }
        allIn.add(a.seat());
        acted.add(a.seat());
        advanceAction();
    }

    private void advanceAction() {
        // Find next player who can act
        for (int attempt = 0; attempt < seatedCount(); attempt++) {
            currentSeat = (currentSeat + 1) % seatedCount();
            if (!folded.contains(currentSeat) && !allIn.contains(currentSeat)
                    && !acted.contains(currentSeat)) {
                return; // Found someone who needs to act
            }
        }

        // Everyone has acted — end betting round
        endBettingRound();
    }

    private void endBettingRound() {
        roundBets.clear();
        acted.clear();
        currentBet = 0;

        // Check if only one non-folded player
        int remaining = 0;
        int lastSeat = -1;
        for (int i = 0; i < seatedCount(); i++) {
            if (!folded.contains(i)) {
                remaining++;
                lastSeat = i;
            }
        }
        if (remaining == 1) {
            awardPot(lastSeat);
            return;
        }

        // Advance phase
        switch (phase) {
            case PHASE_PREFLOP -> {
                phase = PHASE_FLOP;
                dealCommunity(3);
                currentSeat = nextActiveAfterDealer();
            }
            case PHASE_FLOP -> {
                phase = PHASE_TURN;
                dealCommunity(1);
                currentSeat = nextActiveAfterDealer();
            }
            case PHASE_TURN -> {
                phase = PHASE_RIVER;
                dealCommunity(1);
                currentSeat = nextActiveAfterDealer();
            }
            case PHASE_RIVER -> {
                phase = PHASE_SHOWDOWN;
                resolveShowdown();
            }
        }
    }

    private int nextActiveAfterDealer() {
        for (int i = 1; i <= seatedCount(); i++) {
            int seat = (dealerSeat + i) % seatedCount();
            if (!folded.contains(seat) && !allIn.contains(seat)) {
                return seat;
            }
        }
        return dealerSeat; // shouldn't happen
    }

    private void dealCommunity(int count) {
        Zone<PlayingCard> deckZone = zoneMap.zone("deck");
        Zone<PlayingCard> community = zoneMap.zone("community");
        for (int i = 0; i < count; i++) {
            deckZone.removeTop().ifPresent(community::addTop);
        }
    }

    private void resolveShowdown() {
        List<PlayingCard> community = zoneMap.zone("community").contents();

        int bestSeat = -1;
        PokerHand bestHand = null;

        for (int i = 0; i < seatedCount(); i++) {
            if (folded.contains(i)) continue;

            List<PlayingCard> hole = zoneMap.zone("hand", i).contents();
            List<PlayingCard> allCards = new ArrayList<>(hole);
            allCards.addAll(community);

            if (allCards.size() >= 7) {
                PokerHand hand = PokerHand.bestOfSeven(allCards);
                if (bestHand == null || hand.compareTo(bestHand) > 0) {
                    bestHand = hand;
                    bestSeat = i;
                }
            } else if (allCards.size() == 5) {
                PokerHand hand = PokerHand.evaluate(allCards);
                if (bestHand == null || hand.compareTo(bestHand) > 0) {
                    bestHand = hand;
                    bestSeat = i;
                }
            }
        }

        if (bestSeat >= 0) {
            awardPot(bestSeat);
        }
    }

    private void awardPot(int winnerSeat) {
        scores.add(winnerSeat, "chips", pot);
        pot = 0;
        lastWinner = winnerSeat;
        handInProgress = false;
        dealerSeat = (dealerSeat + 1) % seatedCount();

        // Check if only one player has chips
        int withChips = 0;
        for (int i = 0; i < seatedCount(); i++) {
            if (scores.score(i, "chips") > 0) withChips++;
        }
        if (withChips <= 1) {
            gameOver = true;
        }
    }

    // ==================================================================================
    // Game Actions (Verbs)
    // ==================================================================================

    @Verb(value = GameVocabulary.Deal.KEY, doc = "Deal a new hand")
    public String deal(ActionContext ctx) {
        if (handInProgress) return "Hand already in progress";
        Signing.Signer s = resolveSigner(ctx);
        Signing.Hasher h = resolveHasher();
        append(new DealOp(), ++sequence, s, h);
        return "Hand dealt";
    }

    @Verb(value = GameVocabulary.Bet.KEY, doc = "Place a bet")
    public String bet(ActionContext ctx,
                      @Param(value = "amount", doc = "Bet amount") int amount) {
        int seat = authorizedSeat(ctx);
        if (seat < 0) seat = currentSeat;
        Signing.Signer s = resolveSigner(ctx);
        Signing.Hasher h = resolveHasher();
        append(new BetOp(seat, amount), ++sequence, s, h);
        return "Bet " + amount;
    }

    @Verb(value = GameVocabulary.Call.KEY, doc = "Call the current bet")
    public String call(ActionContext ctx) {
        int seat = authorizedSeat(ctx);
        if (seat < 0) seat = currentSeat;
        Signing.Signer s = resolveSigner(ctx);
        Signing.Hasher h = resolveHasher();
        append(new CallOp(seat), ++sequence, s, h);
        return "Called";
    }

    @Verb(value = GameVocabulary.Raise.KEY, doc = "Raise the bet")
    public String raise(ActionContext ctx,
                        @Param(value = "amount", doc = "Total raise amount") int amount) {
        int seat = authorizedSeat(ctx);
        if (seat < 0) seat = currentSeat;
        Signing.Signer s = resolveSigner(ctx);
        Signing.Hasher h = resolveHasher();
        append(new RaiseOp(seat, amount), ++sequence, s, h);
        return "Raised to " + amount;
    }

    @Verb(value = GameVocabulary.Check.KEY, doc = "Check (pass without betting)")
    public String check(ActionContext ctx) {
        int seat = authorizedSeat(ctx);
        if (seat < 0) seat = currentSeat;
        Signing.Signer s = resolveSigner(ctx);
        Signing.Hasher h = resolveHasher();
        append(new CheckOp(seat), ++sequence, s, h);
        return "Checked";
    }

    @Verb(value = GameVocabulary.Fold.KEY, doc = "Fold your hand")
    public String fold(ActionContext ctx) {
        int seat = authorizedSeat(ctx);
        if (seat < 0) seat = currentSeat;
        Signing.Signer s = resolveSigner(ctx);
        Signing.Hasher h = resolveHasher();
        append(new FoldOp(seat), ++sequence, s, h);
        return "Folded";
    }

    // ==================================================================================
    // Direct Play Methods (for tests)
    // ==================================================================================

    public String doDeal() {
        if (handInProgress) return "Hand in progress";
        append(new DealOp(), ++sequence, signer, hasher);
        return "Dealt";
    }

    public String doBet(int seat, int amount) {
        append(new BetOp(seat, amount), ++sequence, signer, hasher);
        return "Bet " + amount;
    }

    public String doCall(int seat) {
        append(new CallOp(seat), ++sequence, signer, hasher);
        return "Called";
    }

    public String doRaise(int seat, int amount) {
        append(new RaiseOp(seat, amount), ++sequence, signer, hasher);
        return "Raised to " + amount;
    }

    public String doCheck(int seat) {
        append(new CheckOp(seat), ++sequence, signer, hasher);
        return "Checked";
    }

    public String doFold(int seat) {
        append(new FoldOp(seat), ++sequence, signer, hasher);
        return "Folded";
    }

    public String doAllIn(int seat) {
        append(new AllInOp(seat), ++sequence, signer, hasher);
        return "All in";
    }

    // ==================================================================================
    // Queries
    // ==================================================================================

    /** Current pot size. */
    public int pot() { return pot; }

    /** Current bet to match. */
    public int currentBet() { return currentBet; }

    /** Current player's seat. */
    public int currentSeat() { return currentSeat; }

    /** Whether a hand is in progress. */
    public boolean isHandInProgress() { return handInProgress; }

    /** Dealer seat position. */
    public int dealerSeat() { return dealerSeat; }

    /** Whether a player has folded. */
    public boolean hasFolded(int seat) { return folded.contains(seat); }

    /** Whether a player is all-in. */
    public boolean isAllIn(int seat) { return allIn.contains(seat); }

    /** A player's chip count. */
    public int chips(int seat) { return scores.score(seat, "chips"); }

    /** Community cards. */
    public List<PlayingCard> communityCards() {
        if (zoneMap.zoneCount() == 0) return List.of();
        return zoneMap.zone("community").contents();
    }

    /** A player's hole cards. */
    public List<PlayingCard> holeCards(int seat) {
        if (zoneMap.zoneCount() == 0) return List.of();
        return zoneMap.zone("hand", seat).contents();
    }

    /** Number of active (non-folded) players. */
    public int activeSeatCount() {
        int count = 0;
        for (int i = 0; i < seatedCount(); i++) {
            if (!folded.contains(i)) count++;
        }
        return count;
    }

    /** Last hand winner seat. */
    public int lastWinner() { return lastWinner; }

    /** Status text for display. */
    public String statusText() {
        if (gameOver) {
            Optional<Integer> w = winner();
            if (w.isPresent()) {
                String name = playerName(w.get()).orElse("Player " + (w.get() + 1));
                return name + " wins the game!";
            }
            return "Game over";
        }
        if (!handInProgress) {
            return "Ready for next hand";
        }
        String name = playerName(currentSeat).orElse("Player " + (currentSeat + 1));
        return name + "'s turn — " + phase + " (pot: " + pot + ")";
    }

    public List<String> metaRows() {
        List<String> rows = new ArrayList<>();
        rows.add("Phase: " + phase.toUpperCase(Locale.ROOT));
        rows.add("Pot: " + pot);
        rows.add("Current bet: " + currentBet);
        rows.add("Dealer: " + seatLabel(dealerSeat));
        rows.add("Current seat: " + seatLabel(currentSeat));
        return rows;
    }

    public boolean hasCommunityCards() {
        return !communityCards().isEmpty();
    }

    public List<String> communityRows() {
        List<String> rows = new ArrayList<>();
        for (PlayingCard card : communityCards()) {
            rows.add(card.toString());
        }
        return rows;
    }

    public List<String> playerRows() {
        List<String> rows = new ArrayList<>();
        for (int seat = 0; seat < seatedCount(); seat++) {
            int roundBet = roundBets.getOrDefault(seat, 0);
            rows.add(seatLabel(seat)
                    + " | chips: " + chips(seat)
                    + " | bet: " + roundBet
                    + " | hole: " + holeCards(seat).size()
                    + " | folded: " + (hasFolded(seat) ? "yes" : "no")
                    + " | all-in: " + (isAllIn(seat) ? "yes" : "no"));
        }
        return rows;
    }

    private String seatLabel(int seat) {
        return playerName(seat).orElse("P" + (seat + 1));
    }
}
