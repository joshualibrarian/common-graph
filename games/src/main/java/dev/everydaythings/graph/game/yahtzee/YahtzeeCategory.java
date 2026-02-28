package dev.everydaythings.graph.game.yahtzee;

/**
 * The 13 scoring categories in Yahtzee.
 *
 * <p>Each category knows how to score a set of 5 dice. Upper section
 * categories (ONES through SIXES) sum matching face values. Lower
 * section categories check for patterns.
 */
public enum YahtzeeCategory {

    // Upper section
    ONES("Ones"),
    TWOS("Twos"),
    THREES("Threes"),
    FOURS("Fours"),
    FIVES("Fives"),
    SIXES("Sixes"),

    // Lower section
    THREE_OF_A_KIND("Three of a Kind"),
    FOUR_OF_A_KIND("Four of a Kind"),
    FULL_HOUSE("Full House"),
    SMALL_STRAIGHT("Small Straight"),
    LARGE_STRAIGHT("Large Straight"),
    YAHTZEE("Yahtzee"),
    CHANCE("Chance");

    private final String displayName;

    YahtzeeCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() { return displayName; }

    /**
     * Score the given dice for this category.
     *
     * @param dice array of 5 dice values (each 1-6)
     * @return the score, or 0 if the pattern doesn't match
     */
    public int score(int[] dice) {
        return switch (this) {
            case ONES -> sumOf(dice, 1);
            case TWOS -> sumOf(dice, 2);
            case THREES -> sumOf(dice, 3);
            case FOURS -> sumOf(dice, 4);
            case FIVES -> sumOf(dice, 5);
            case SIXES -> sumOf(dice, 6);
            case THREE_OF_A_KIND -> hasOfAKind(dice, 3) ? sumAll(dice) : 0;
            case FOUR_OF_A_KIND -> hasOfAKind(dice, 4) ? sumAll(dice) : 0;
            case FULL_HOUSE -> isFullHouse(dice) ? 25 : 0;
            case SMALL_STRAIGHT -> hasSmallStraight(dice) ? 30 : 0;
            case LARGE_STRAIGHT -> hasLargeStraight(dice) ? 40 : 0;
            case YAHTZEE -> hasOfAKind(dice, 5) ? 50 : 0;
            case CHANCE -> sumAll(dice);
        };
    }

    /**
     * Whether this category is in the upper section (ONES through SIXES).
     */
    public boolean isUpper() {
        return ordinal() <= SIXES.ordinal();
    }

    /**
     * The target face value for upper section categories (1-6).
     * Only meaningful for upper section categories.
     */
    public int faceValue() {
        return ordinal() + 1;
    }

    /**
     * Upper section bonus threshold. If the sum of all upper section
     * scores reaches this value, a +35 bonus is awarded.
     */
    public static final int UPPER_BONUS_THRESHOLD = 63;

    /** Upper section bonus value. */
    public static final int UPPER_BONUS = 35;

    // ==================================================================================
    // Scoring Helpers
    // ==================================================================================

    private static int sumOf(int[] dice, int face) {
        int sum = 0;
        for (int d : dice) {
            if (d == face) sum += d;
        }
        return sum;
    }

    private static int sumAll(int[] dice) {
        int sum = 0;
        for (int d : dice) sum += d;
        return sum;
    }

    private static int[] counts(int[] dice) {
        int[] c = new int[7]; // index 0 unused, 1-6 for face values
        for (int d : dice) c[d]++;
        return c;
    }

    private static boolean hasOfAKind(int[] dice, int n) {
        int[] c = counts(dice);
        for (int i = 1; i <= 6; i++) {
            if (c[i] >= n) return true;
        }
        return false;
    }

    private static boolean isFullHouse(int[] dice) {
        int[] c = counts(dice);
        boolean hasThree = false;
        boolean hasTwo = false;
        for (int i = 1; i <= 6; i++) {
            if (c[i] == 3) hasThree = true;
            if (c[i] == 2) hasTwo = true;
        }
        return hasThree && hasTwo;
    }

    private static boolean hasSmallStraight(int[] dice) {
        int[] c = counts(dice);
        // Check for 4 consecutive: 1234, 2345, 3456
        return (c[1] >= 1 && c[2] >= 1 && c[3] >= 1 && c[4] >= 1) ||
               (c[2] >= 1 && c[3] >= 1 && c[4] >= 1 && c[5] >= 1) ||
               (c[3] >= 1 && c[4] >= 1 && c[5] >= 1 && c[6] >= 1);
    }

    private static boolean hasLargeStraight(int[] dice) {
        int[] c = counts(dice);
        // Check for 5 consecutive: 12345 or 23456
        return (c[1] >= 1 && c[2] >= 1 && c[3] >= 1 && c[4] >= 1 && c[5] >= 1) ||
               (c[2] >= 1 && c[3] >= 1 && c[4] >= 1 && c[5] >= 1 && c[6] >= 1);
    }
}
