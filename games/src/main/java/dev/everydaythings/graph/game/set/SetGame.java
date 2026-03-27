package dev.everydaythings.graph.game.set;

import dev.everydaythings.graph.game.*;
import dev.everydaythings.graph.item.Implements;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Param;
import dev.everydaythings.graph.item.Verb;
import dev.everydaythings.graph.language.GrammaticalFeature;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.game.GameVocabulary;
import dev.everydaythings.graph.ui.scene.Scene;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.*;

/**
 * The card game Set — a simultaneous-action pattern-matching game.
 *
 * <p>12+ cards are dealt face-up on the tableau. All players simultaneously
 * race to spot three cards that form a "set" — where each of the four
 * properties is either all the same or all different across the three cards.
 *
 * <p>This demonstrates:
 * <ul>
 *   <li>{@link Zoned} — cards in deck, tableau, and per-player found piles</li>
 *   <li>{@link Scored} — points for valid sets, penalties for invalid calls</li>
 *   <li>{@link Randomized} — deck shuffle from Dag event randomness</li>
 *   <li>Simultaneous play — {@link #activePlayers()} returns ALL seated players</li>
 * </ul>
 *
 * @see SetCard
 * @see SetProperty
 */
@Implements(SetGame.KEY)
@Scene.Body(shape = "box", width = "60cm", height = "0", depth = "50cm", color = 0x2E7D32)
public class SetGame extends GameComponent<SetGame.Op>
        implements Zoned<SetCard>, Scored, Randomized {

    public static final String KEY = "cg.sememe:set-game";

    // ==================================================================================
    // Operations
    // ==================================================================================

    public sealed interface Op permits StartOp, CallSetOp, DealMoreOp {}

    /** Start the game: shuffle deck and deal initial tableau. */
    @EqualsAndHashCode
    public static final class StartOp implements Op {}

    /** A player claims three cards form a set. */
    @Getter @Accessors(fluent = true) @EqualsAndHashCode
    public static final class CallSetOp implements Op {
        private final int seat;
        private final int card1Ord;
        private final int card2Ord;
        private final int card3Ord;

        public CallSetOp(int seat, int card1Ord, int card2Ord, int card3Ord) {
            this.seat = seat;
            this.card1Ord = card1Ord;
            this.card2Ord = card2Ord;
            this.card3Ord = card3Ord;
        }
    }

    /** Request 3 more cards when no valid set exists. */
    @Getter @Accessors(fluent = true) @EqualsAndHashCode
    public static final class DealMoreOp implements Op {
        private final int seat;
        public DealMoreOp(int seat) { this.seat = seat; }
    }

    // ==================================================================================
    // State
    // ==================================================================================

    private final ZoneMap<SetCard> zoneMap = new ZoneMap<>();
    private final ScoreBoard scores = new ScoreBoard();
    private final int maxSeats;

    private boolean started = false;
    private boolean gameOver = false;

    // ==================================================================================
    // Factory
    // ==================================================================================

    public static SetGame create() {
        return create(4);
    }

    public static SetGame create(int maxPlayers) {
        return new SetGame(maxPlayers).withDemoSigner();
    }

    public SetGame(int maxPlayers) {
        this.maxSeats = maxPlayers;
        zoneMap.define("deck", ZoneVisibility.HIDDEN);
        zoneMap.define("tableau", ZoneVisibility.PUBLIC);
        zoneMap.definePerPlayer("found", maxPlayers, ZoneVisibility.PUBLIC);
    }

    // ==================================================================================
    // Apply
    // ==================================================================================

    @Override
    protected void apply(Op op) {
        opCount++;
        switch (op) {
            case StartOp start -> applyStart();
            case CallSetOp call -> foldCallSet(call);
            case DealMoreOp deal -> foldDealMore();
        }
    }

    private void applyStart() {
        if (started) return;
        started = true;

        GameRandom rng = GameRandom.fromSeed(opSeed());
        List<SetCard> deck = SetCard.fullDeck();
        zoneMap.zone("deck").addAll(deck);
        zoneMap.zone("deck").shuffle(rng);

        for (int i = 0; i < 12; i++) {
            zoneMap.transfer("deck", "tableau");
        }
    }

    private void foldCallSet(CallSetOp call) {
        if (gameOver || !started) return;

        SetCard c1 = SetCard.fromOrdinal(call.card1Ord());
        SetCard c2 = SetCard.fromOrdinal(call.card2Ord());
        SetCard c3 = SetCard.fromOrdinal(call.card3Ord());

        Zone<SetCard> tableau = zoneMap.zone("tableau");

        if (!tableau.contains(c1) || !tableau.contains(c2) || !tableau.contains(c3)) {
            return; // cards not in tableau
        }

        if (SetCard.isValidSet(c1, c2, c3)) {
            String foundZone = "found:" + call.seat();
            tableau.remove(c1);
            tableau.remove(c2);
            tableau.remove(c3);
            zoneMap.zone(foundZone).addTop(c1);
            zoneMap.zone(foundZone).addTop(c2);
            zoneMap.zone(foundZone).addTop(c3);
            scores.add(call.seat(), 1);

            while (tableau.size() < 12 && !zoneMap.zone("deck").isEmpty()) {
                zoneMap.transfer("deck", "tableau");
            }

            checkGameOver();
        } else {
            scores.add(call.seat(), -1);
        }
    }

    private void foldDealMore() {
        if (gameOver || !started) return;

        Zone<SetCard> tableau = zoneMap.zone("tableau");
        Zone<SetCard> deck = zoneMap.zone("deck");

        if (!hasValidSet(tableau.contents())) {
            for (int i = 0; i < 3 && !deck.isEmpty(); i++) {
                zoneMap.transfer("deck", "tableau");
            }
            checkGameOver();
        }
    }

    private void checkGameOver() {
        Zone<SetCard> tableau = zoneMap.zone("tableau");
        Zone<SetCard> deck = zoneMap.zone("deck");

        if (deck.isEmpty() && (tableau.isEmpty() || !hasValidSet(tableau.contents()))) {
            gameOver = true;
        }
    }

    // ==================================================================================
    // Set Detection
    // ==================================================================================

    /**
     * Check if any valid set exists among the given cards.
     *
     * <p>Brute force O(n^3) — for n &lt;= 21 (max realistic tableau),
     * that's at most 1771 checks of O(1) each.
     */
    public static boolean hasValidSet(List<SetCard> cards) {
        int n = cards.size();
        for (int i = 0; i < n - 2; i++) {
            for (int j = i + 1; j < n - 1; j++) {
                for (int k = j + 1; k < n; k++) {
                    if (SetCard.isValidSet(cards.get(i), cards.get(j), cards.get(k))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Find all valid sets in the current tableau.
     */
    public List<List<SetCard>> findAllSets() {
        List<SetCard> cards = zoneMap.zone("tableau").contents();
        List<List<SetCard>> result = new ArrayList<>();
        int n = cards.size();
        for (int i = 0; i < n - 2; i++) {
            for (int j = i + 1; j < n - 1; j++) {
                for (int k = j + 1; k < n; k++) {
                    if (SetCard.isValidSet(cards.get(i), cards.get(j), cards.get(k))) {
                        result.add(List.of(cards.get(i), cards.get(j), cards.get(k)));
                    }
                }
            }
        }
        return result;
    }

    // ==================================================================================
    // Game Actions (Verbs)
    // ==================================================================================

    @Verb(value = CoreVocabulary.Create.KEY, doc = "Start the game")
    public void start() {
        if (started) return;
        apply(new StartOp());
    }

    @Verb(value = GameVocabulary.Call.KEY, doc = "Call a set of three cards")
    public boolean callSet(
            @Param(value = "seat", doc = "Player seat") int seat,
            @Param(value = "card1", doc = "First card ordinal") int card1,
            @Param(value = "card2", doc = "Second card ordinal") int card2,
            @Param(value = "card3", doc = "Third card ordinal") int card3) {
        if (gameOver || !started) return false;
        apply(new CallSetOp(seat, card1, card2, card3));
        return true;
    }

    @Verb(value = GameVocabulary.Deal.KEY, doc = "Request more cards")
    public boolean dealMore(
            @Param(value = "seat", doc = "Requesting player seat") int seat) {
        if (gameOver || !started) return false;
        apply(new DealMoreOp(seat));
        return true;
    }

    // ==================================================================================
    // View Model
    // ==================================================================================

    /** A card ready for surface rendering. */
    @Getter @Accessors(fluent = true) @EqualsAndHashCode
    public static class CardView {
        private final String ordinal;
        private final String symbol;
        private final String colorName;
        private final String description;

        public CardView(String ordinal, String symbol, String colorName, String description) {
            this.ordinal = ordinal;
            this.symbol = symbol;
            this.colorName = colorName;
            this.description = description;
        }
    }

    /** A player's score label for surface rendering. */
    @Getter @Accessors(fluent = true) @EqualsAndHashCode
    public static class PlayerScore {
        private final String label;
        private final int score;

        public PlayerScore(String label, int score) {
            this.label = label;
            this.score = score;
        }
    }

    /** Tableau cards for {@code @Surface.Repeat}. */
    public List<CardView> tableauCards() {
        return tableau().stream()
                .map(c -> new CardView(
                        String.valueOf(c.ordinal()),
                        c.symbol(),
                        c.colorName(),
                        c.toString()))
                .toList();
    }

    /** Player scores for {@code @Surface.Repeat}. */
    public List<PlayerScore> playerScores() {
        List<PlayerScore> result = new ArrayList<>();
        for (int i = 0; i < seatedCount(); i++) {
            result.add(new PlayerScore("P" + i + ": " + scores.score(i), scores.score(i)));
        }
        return result;
    }

    /** Deck info label for binding. */
    public String deckLabel() {
        return "Deck: " + deckSize() + " cards";
    }

    // ==================================================================================
    // Queries
    // ==================================================================================

    public boolean isStarted() { return started; }

    public List<SetCard> tableau() {
        return zoneMap.zone("tableau").contents();
    }

    public int tableauSize() {
        return zoneMap.zone("tableau").size();
    }

    public int deckSize() {
        return zoneMap.zone("deck").size();
    }

    @Verb(value = CoreVocabulary.Describe.KEY, doc = "Describe game status")
    public String describeStatus() {
        if (!started) return "Waiting to start. " + seatedCount() + " players.";
        if (gameOver) {
            Optional<Integer> w = winner();
            if (w.isPresent()) return "Game over! Player " + w.get() + " wins with " + scores.score(w.get()) + " sets.";
            return "Game over! It's a tie.";
        }
        return "Tableau: " + tableauSize() + " cards | Deck: " + deckSize()
                + " | Sets in view: " + findAllSets().size();
    }

    @Verb(value = CoreVocabulary.Show.KEY, doc = "Show the tableau")
    public String renderTableau() {
        if (!started) return "Game not started.";

        StringBuilder sb = new StringBuilder();
        sb.append("=== TABLEAU ===\n");
        List<SetCard> cards = tableau();
        for (int i = 0; i < cards.size(); i++) {
            SetCard card = cards.get(i);
            sb.append(String.format("[%2d] %-6s %s\n",
                    card.ordinal(), card.symbol(), card));
        }
        sb.append("\nDeck: ").append(deckSize()).append(" cards remaining\n");

        // Scores
        if (seatedCount() > 0) {
            sb.append("Scores: ");
            for (int i = 0; i < seatedCount(); i++) {
                if (i > 0) sb.append(" | ");
                sb.append("P").append(i).append(": ").append(scores.score(i));
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    // ==================================================================================
    // GameComponent Abstract Methods
    // ==================================================================================

    @Override public int minPlayers() { return 1; }
    @Override public int maxPlayers() { return maxSeats; }

    @Override
    public Set<Integer> activePlayers() {
        if (gameOver || !started) return Set.of();
        // Simultaneous play: all seated players can act
        Set<Integer> active = new HashSet<>();
        for (int i = 0; i < seatedCount(); i++) {
            active.add(i);
        }
        return Collections.unmodifiableSet(active);
    }

    @Override
    public boolean isGameOver() {
        return gameOver;
    }

    @Override
    public Optional<Integer> winner() {
        if (!gameOver) return Optional.empty();
        List<Integer> leaders = scores.leaderboard(ScoreBoard.DEFAULT);
        if (leaders.isEmpty()) return Optional.empty();
        int topScore = scores.score(leaders.get(0));
        if (leaders.size() > 1 && scores.score(leaders.get(1)) == topScore) {
            return Optional.empty(); // tie
        }
        return Optional.of(leaders.get(0));
    }

    // ==================================================================================
    // Capability Interfaces
    // ==================================================================================

    @Override
    public ZoneMap<SetCard> zones() { return zoneMap; }

    @Override
    public ScoreBoard scoreBoard() { return scores; }

    @Override
    public String toString() {
        return renderTableau();
    }
}
