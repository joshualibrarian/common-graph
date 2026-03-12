package dev.everydaythings.graph.game.yahtzee;

import com.upokecenter.cbor.CBORObject;
import dev.everydaythings.graph.game.GameComponent;
import dev.everydaythings.graph.game.GameRandom;
import dev.everydaythings.graph.game.Phased;
import dev.everydaythings.graph.game.Randomized;
import dev.everydaythings.graph.game.ScoreBoard;
import dev.everydaythings.graph.game.Scored;
import dev.everydaythings.graph.game.dice.Die;
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
 * Yahtzee — a dice game of pattern matching and risk management.
 *
 * <p>Players take turns rolling 5 dice up to 3 times per turn, choosing
 * which dice to keep between rolls. After rolling, the player must assign
 * the result to one of 13 scoring categories. The game ends when all
 * players have filled all 13 categories.
 *
 * <p>Composes three traits:
 * <ul>
 *   <li>{@link Scored} — 13 categories + upper bonus tracked per player</li>
 *   <li>{@link Phased} — ROLL → KEEP → SCORE turn phases</li>
 *   <li>{@link Randomized} — dice rolls from deterministic event RNG</li>
 * </ul>
 */
@Type(value = "cg:type/yahtzee", glyph = "\uD83C\uDFB2")
@Scene(as = YahtzeeSurface.class)
public class YahtzeeGame extends GameComponent<YahtzeeGame.Op>
        implements Scored, Phased, Randomized {

    // ==================================================================================
    // Constants
    // ==================================================================================

    public static final int NUM_DICE = 5;
    public static final int MAX_ROLLS = 3;
    public static final int NUM_CATEGORIES = 13;

    public static final String PHASE_ROLL = "roll";
    public static final String PHASE_KEEP = "keep";
    public static final String PHASE_SCORE = "score";

    // ==================================================================================
    // Operations
    // ==================================================================================

    public sealed interface Op permits RollOp, KeepOp, ScoreOp {}

    /** Roll all unheld dice. */
    @EqualsAndHashCode
    public static final class RollOp implements Op {}

    /** Toggle which dice to hold between rolls. */
    @Getter @Accessors(fluent = true) @EqualsAndHashCode
    public static final class KeepOp implements Op {
        private final Set<Integer> indices;
        public KeepOp(Set<Integer> indices) {
            this.indices = Collections.unmodifiableSet(new HashSet<>(indices));
        }
    }

    /** Assign current dice to a scoring category. */
    @Getter @Accessors(fluent = true) @EqualsAndHashCode
    public static final class ScoreOp implements Op {
        private final int seat;
        private final String category;
        public ScoreOp(int seat, String category) {
            this.seat = seat;
            this.category = category;
        }
    }

    // ==================================================================================
    // Game State
    // ==================================================================================

    private final int maxSeats;
    private long sequence = 0;
    private final ScoreBoard scores = new ScoreBoard();
    private final int[] dice = new int[NUM_DICE];
    private final boolean[] kept = new boolean[NUM_DICE];
    private int rollsRemaining = MAX_ROLLS;
    private int currentSeat = 0;
    private String phase = PHASE_ROLL;
    private boolean gameOver = false;

    /** Tracks which categories each seat has used. */
    private final Map<Integer, Set<String>> usedCategories = new HashMap<>();


    // ==================================================================================
    // Constructors and Factories
    // ==================================================================================

    public YahtzeeGame() {
        this(4);
    }

    public YahtzeeGame(int maxPlayers) {
        this.maxSeats = maxPlayers;
    }

    public static YahtzeeGame create() {
        return create(2);
    }

    public static YahtzeeGame create(int maxPlayers) {
        return new YahtzeeGame(maxPlayers).withDemoSigner();
    }

    // ==================================================================================
    // Signer Setup
    // ==================================================================================

    public YahtzeeGame withSigner(Signing.Signer signer, Signing.Hasher hasher) {
        this.signer = signer;
        this.hasher = hasher;
        return this;
    }

    // ==================================================================================
    // GameComponent Abstract Methods
    // ==================================================================================

    @Override public int minPlayers() { return 1; }
    @Override public int maxPlayers() { return maxSeats; }

    @Override
    public Set<Integer> activePlayers() {
        if (gameOver || seatedCount() == 0) return Set.of();
        return Set.of(currentSeat);
    }

    @Override public boolean isGameOver() { return gameOver; }

    @Override
    public Optional<Integer> winner() {
        if (!gameOver) return Optional.empty();
        int bestSeat = -1;
        int bestScore = -1;
        for (int i = 0; i < seatedCount(); i++) {
            int total = totalScore(i);
            if (total > bestScore) {
                bestScore = total;
                bestSeat = i;
            }
        }
        // Check for tie
        int finalBest = bestScore;
        long withBest = 0;
        for (int i = 0; i < seatedCount(); i++) {
            if (totalScore(i) == finalBest) withBest++;
        }
        return withBest == 1 ? Optional.of(bestSeat) : Optional.empty();
    }

    // ==================================================================================
    // Trait Implementations
    // ==================================================================================

    @Override public ScoreBoard scoreBoard() { return scores; }

    @Override public String currentPhase() { return phase; }

    @Override public List<String> phases() {
        return List.of(PHASE_ROLL, PHASE_KEEP, PHASE_SCORE);
    }

    // ==================================================================================
    // Encode/Decode
    // ==================================================================================

    @Override
    protected CBORObject encodeOp(Op op) {
        CBORObject m = CBORObject.NewMap();
        switch (op) {
            case RollOp r -> m.Add("type", "roll");
            case KeepOp k -> {
                m.Add("type", "keep");
                CBORObject arr = CBORObject.NewArray();
                for (int idx : k.indices()) arr.Add(idx);
                m.Add("indices", arr);
            }
            case ScoreOp s -> {
                m.Add("type", "score");
                m.Add("seat", s.seat());
                m.Add("category", s.category());
            }
        }
        return m;
    }

    @Override
    protected Op decodeOp(CBORObject c) {
        String type = c.get("type").AsString();
        return switch (type) {
            case "roll" -> new RollOp();
            case "keep" -> {
                Set<Integer> indices = new HashSet<>();
                for (CBORObject idx : c.get("indices").getValues()) {
                    indices.add(idx.AsInt32());
                }
                yield new KeepOp(indices);
            }
            case "score" -> new ScoreOp(
                    c.get("seat").AsInt32(),
                    c.get("category").AsString()
            );
            default -> throw new IllegalArgumentException("Unknown op type: " + type);
        };
    }

    @Override
    protected void fold(Op op, Event ev) {
        switch (op) {
            case RollOp r -> foldRoll(ev);
            case KeepOp k -> foldKeep(k);
            case ScoreOp s -> foldScore(s);
        }
    }

    // ==================================================================================
    // Fold Logic
    // ==================================================================================

    private void foldRoll(Event ev) {
        if (rollsRemaining <= 0) return;

        GameRandom rng = GameRandom.fromEvent(ev);
        for (int i = 0; i < NUM_DICE; i++) {
            if (!kept[i]) {
                dice[i] = rng.nextInt(1, 7); // 1-6 inclusive
            }
        }
        rollsRemaining--;

        if (rollsRemaining > 0) {
            phase = PHASE_KEEP;
        } else {
            phase = PHASE_SCORE;
        }
    }

    private void foldKeep(KeepOp k) {
        Arrays.fill(kept, false);
        for (int idx : k.indices()) {
            if (idx >= 0 && idx < NUM_DICE) {
                kept[idx] = true;
            }
        }
        // Stay in KEEP — player can roll again or score from here
    }

    private void foldScore(ScoreOp s) {
        YahtzeeCategory cat = YahtzeeCategory.valueOf(s.category());
        int points = cat.score(dice);
        scores.add(s.seat(), cat.name(), points);

        // Track used categories
        usedCategories.computeIfAbsent(s.seat(), k -> new HashSet<>()).add(s.category());

        // Check for upper bonus
        checkUpperBonus(s.seat());

        // Advance to next player or end game
        advanceTurn();
    }

    private void checkUpperBonus(int seat) {
        int upperSum = 0;
        for (YahtzeeCategory cat : YahtzeeCategory.values()) {
            if (cat.isUpper()) {
                upperSum += scores.score(seat, cat.name());
            }
        }
        if (upperSum >= YahtzeeCategory.UPPER_BONUS_THRESHOLD) {
            // Only add once — check if already awarded
            if (scores.score(seat, "upper-bonus") == 0) {
                scores.add(seat, "upper-bonus", YahtzeeCategory.UPPER_BONUS);
            }
        }
    }

    private void advanceTurn() {
        // Reset dice state
        Arrays.fill(dice, 0);
        Arrays.fill(kept, false);
        rollsRemaining = MAX_ROLLS;
        phase = PHASE_ROLL;

        // Check if all categories filled for all players
        boolean allDone = true;
        for (int i = 0; i < seatedCount(); i++) {
            Set<String> used = usedCategories.getOrDefault(i, Set.of());
            if (used.size() < NUM_CATEGORIES) {
                allDone = false;
                break;
            }
        }

        if (allDone) {
            gameOver = true;
            return;
        }

        // Move to next player who still has categories
        for (int attempt = 0; attempt < seatedCount(); attempt++) {
            currentSeat = (currentSeat + 1) % seatedCount();
            Set<String> used = usedCategories.getOrDefault(currentSeat, Set.of());
            if (used.size() < NUM_CATEGORIES) {
                break;
            }
        }
    }

    // ==================================================================================
    // Game Actions
    // ==================================================================================

    /**
     * Roll all unheld dice.
     */
    @Verb(value = GameVocabulary.Roll.KEY, doc = "Roll the dice")
    public String roll(ActionContext ctx) {
        int seat = authorizedSeat(ctx);
        if (!PHASE_ROLL.equals(phase) && !PHASE_KEEP.equals(phase)) {
            return "Cannot roll in phase: " + phase;
        }
        if (rollsRemaining <= 0) {
            return "No rolls remaining — must score";
        }

        Signing.Signer s = resolveSigner(ctx);
        Signing.Hasher h = resolveHasher();
        append(new RollOp(), ++sequence, s, h);
        return diceString();
    }

    /**
     * Choose which dice to keep before re-rolling.
     */
    @Verb(value = GameVocabulary.Keep.KEY, doc = "Choose dice to keep")
    public String keep(ActionContext ctx,
                       @Param(value = "indices", doc = "Dice positions to keep (0-4)") String indicesStr) {
        authorizedSeat(ctx);
        if (!PHASE_KEEP.equals(phase)) {
            return "Cannot keep in phase: " + phase;
        }

        Set<Integer> indices = parseIndices(indicesStr);
        Signing.Signer s = resolveSigner(ctx);
        Signing.Hasher h = resolveHasher();
        append(new KeepOp(indices), ++sequence, s, h);
        return "Keeping dice at: " + indices + " → " + diceString();
    }

    /**
     * Assign current dice to a scoring category.
     */
    @Verb(value = GameVocabulary.Score.KEY, doc = "Score in a category")
    public String score(ActionContext ctx,
                        @Param(value = "category", doc = "Scoring category name") String category) {
        int seat = authorizedSeat(ctx);
        if (seat < 0) seat = currentSeat;

        if (dice[0] == 0) {
            return "Must roll first";
        }

        // Validate category
        YahtzeeCategory cat;
        try {
            cat = YahtzeeCategory.valueOf(category.toUpperCase().replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            return "Unknown category: " + category;
        }

        Set<String> used = usedCategories.getOrDefault(seat, Set.of());
        if (used.contains(cat.name())) {
            return "Category " + cat.displayName() + " already used";
        }

        int points = cat.score(dice);
        Signing.Signer s = resolveSigner(ctx);
        Signing.Hasher h = resolveHasher();
        append(new ScoreOp(seat, cat.name()), ++sequence, s, h);
        return "Scored " + points + " in " + cat.displayName();
    }

    // ==================================================================================
    // Direct Play Methods (for tests)
    // ==================================================================================

    /**
     * Roll dice directly (bypasses verb dispatch).
     */
    public String doRoll() {
        if (rollsRemaining <= 0) return "No rolls remaining";
        append(new RollOp(), ++sequence, signer, hasher);
        return diceString();
    }

    /**
     * Keep specific dice directly.
     */
    public String doKeep(Set<Integer> indices) {
        append(new KeepOp(indices), ++sequence, signer, hasher);
        return "Keeping: " + indices;
    }

    /**
     * Score in a category directly.
     */
    public String doScore(String categoryName) {
        return doScore(currentSeat, categoryName);
    }

    /**
     * Score for a specific seat directly.
     */
    public String doScore(int seat, String categoryName) {
        YahtzeeCategory cat = YahtzeeCategory.valueOf(categoryName);
        Set<String> used = usedCategories.getOrDefault(seat, Set.of());
        if (used.contains(cat.name())) {
            return "Already used: " + cat.displayName();
        }
        int points = cat.score(dice);
        append(new ScoreOp(seat, cat.name()), ++sequence, signer, hasher);
        return "Scored " + points + " in " + cat.displayName();
    }

    // ==================================================================================
    // Queries
    // ==================================================================================

    /** Current dice values (array of 5, each 1-6, or 0 if not yet rolled). */
    public int[] dice() {
        return Arrays.copyOf(dice, NUM_DICE);
    }

    /** Which dice are held for the next roll. */
    public boolean[] kept() {
        return Arrays.copyOf(kept, NUM_DICE);
    }

    /** Current dice as Die objects for rendering and binding. */
    public List<Die> diceViews() {
        List<Die> views = new ArrayList<>(NUM_DICE);
        for (int i = 0; i < NUM_DICE; i++) {
            views.add(Die.d6(dice[i], kept[i]));
        }
        return views;
    }

    /** Rolls remaining this turn. */
    public int rollsRemaining() { return rollsRemaining; }

    /** Current player's seat. */
    public int currentSeat() { return currentSeat; }

    /** Whether a category has been used by a player. */
    public boolean isCategoryUsed(int seat, String categoryName) {
        return usedCategories.getOrDefault(seat, Set.of()).contains(categoryName);
    }

    /** Available categories for a player. */
    public List<YahtzeeCategory> availableCategories(int seat) {
        Set<String> used = usedCategories.getOrDefault(seat, Set.of());
        List<YahtzeeCategory> available = new ArrayList<>();
        for (YahtzeeCategory cat : YahtzeeCategory.values()) {
            if (!used.contains(cat.name())) {
                available.add(cat);
            }
        }
        return available;
    }

    /** Total score for a seat across all categories including bonus. */
    public int totalScore(int seat) {
        int total = 0;
        for (YahtzeeCategory cat : YahtzeeCategory.values()) {
            total += scores.score(seat, cat.name());
        }
        total += scores.score(seat, "upper-bonus");
        return total;
    }

    /** Upper section subtotal for a seat. */
    public int upperTotal(int seat) {
        int total = 0;
        for (YahtzeeCategory cat : YahtzeeCategory.values()) {
            if (cat.isUpper()) {
                total += scores.score(seat, cat.name());
            }
        }
        return total;
    }

    /** Can the current player roll? */
    public boolean canRoll() {
        return !gameOver && rollsRemaining > 0 &&
               (PHASE_ROLL.equals(phase) || PHASE_KEEP.equals(phase));
    }

    /** Can the current player score? (dice must have been rolled) */
    public boolean canScore() {
        return !gameOver && dice[0] > 0;
    }

    /** Roll button label. */
    public String rollLabel() {
        if (rollsRemaining == MAX_ROLLS) return "Roll Dice";
        if (rollsRemaining == 0) return "No Rolls Left";
        return "Roll Again (" + rollsRemaining + ")";
    }

    // ==================================================================================
    // Scorecard View Model
    // ==================================================================================

    /** A row in the scorecard for surface rendering. */
    @Getter @Accessors(fluent = true) @EqualsAndHashCode
    public static class ScorecardRow {
        private final String label;
        private final String categoryKey;
        private final String scoredValue;
        private final String potentialValue;
        private final boolean available;
        private final boolean scored;
        private final boolean isUpper;
        private final boolean isSeparator;

        public ScorecardRow(String label, String categoryKey, String scoredValue,
                            String potentialValue, boolean available, boolean scored,
                            boolean isUpper, boolean isSeparator) {
            this.label = label;
            this.categoryKey = categoryKey;
            this.scoredValue = scoredValue;
            this.potentialValue = potentialValue;
            this.available = available;
            this.scored = scored;
            this.isUpper = isUpper;
            this.isSeparator = isSeparator;
        }

        /** Separator row factory. */
        public static ScorecardRow separator(String label) {
            return new ScorecardRow(label, "", "", "", false, false, false, true);
        }
    }

    /** Full scorecard for the current player, with potential scores for available categories. */
    public List<ScorecardRow> scorecardRows() {
        int seat = currentSeat;
        Set<String> used = usedCategories.getOrDefault(seat, Set.of());
        List<ScorecardRow> rows = new ArrayList<>();

        // Upper section
        for (YahtzeeCategory cat : YahtzeeCategory.values()) {
            if (!cat.isUpper()) continue;
            boolean isUsed = used.contains(cat.name());
            int potential = dice[0] > 0 ? cat.score(dice) : 0;
            rows.add(new ScorecardRow(
                    cat.displayName(),
                    cat.name(),
                    isUsed ? String.valueOf(scores.score(seat, cat.name())) : "",
                    !isUsed && dice[0] > 0 ? String.valueOf(potential) : "",
                    !isUsed && dice[0] > 0,
                    isUsed,
                    true,
                    false));
        }

        // Upper bonus row
        int upper = upperTotal(seat);
        int bonus = scores.score(seat, "upper-bonus");
        rows.add(new ScorecardRow(
                "Bonus (" + upper + "/63)",
                "",
                bonus > 0 ? "+35" : upper >= 63 ? "+35" : "",
                "",
                false, bonus > 0, true, false));

        // Separator
        rows.add(ScorecardRow.separator("Lower Section"));

        // Lower section
        for (YahtzeeCategory cat : YahtzeeCategory.values()) {
            if (cat.isUpper()) continue;
            boolean isUsed = used.contains(cat.name());
            int potential = dice[0] > 0 ? cat.score(dice) : 0;
            rows.add(new ScorecardRow(
                    cat.displayName(),
                    cat.name(),
                    isUsed ? String.valueOf(scores.score(seat, cat.name())) : "",
                    !isUsed && dice[0] > 0 ? String.valueOf(potential) : "",
                    !isUsed && dice[0] > 0,
                    isUsed,
                    false,
                    false));
        }

        // Total
        rows.add(ScorecardRow.separator("Total: " + totalScore(seat)));

        return rows;
    }

    /** Player score summaries for the score bar. */
    public List<String> playerScoreLabels() {
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < seatedCount(); i++) {
            String name = playerName(i).orElse("P" + (i + 1));
            labels.add(name + ": " + totalScore(i));
        }
        return labels;
    }

    /** Status text for display. */
    public String statusText() {
        if (gameOver) {
            Optional<Integer> w = winner();
            if (w.isPresent()) {
                String name = playerName(w.get()).orElse("Player " + (w.get() + 1));
                return name + " wins with " + totalScore(w.get()) + " points!";
            }
            return "Game over — tie!";
        }
        String name = playerName(currentSeat).orElse("Player " + (currentSeat + 1));
        return name + "'s turn — " + phase + " (" + rollsRemaining + " rolls left)";
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private String diceString() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < NUM_DICE; i++) {
            if (i > 0) sb.append(", ");
            sb.append(dice[i]);
            if (kept[i]) sb.append("*");
        }
        sb.append("]");
        return sb.toString();
    }

    private Set<Integer> parseIndices(String s) {
        Set<Integer> result = new HashSet<>();
        if (s == null || s.isBlank()) return result;
        for (String part : s.split("[,\\s]+")) {
            try {
                int idx = Integer.parseInt(part.trim());
                if (idx >= 0 && idx < NUM_DICE) {
                    result.add(idx);
                }
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }
}
