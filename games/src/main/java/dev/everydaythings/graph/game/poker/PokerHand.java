package dev.everydaythings.graph.game.poker;

import dev.everydaythings.graph.game.card.PlayingCard;

import java.util.*;

/**
 * A 5-card poker hand with evaluation and comparison.
 *
 * <p>Evaluates the best possible hand from 5 cards and supports
 * natural ordering (better hand = higher compareTo result).
 */
public class PokerHand implements Comparable<PokerHand> {

    private final List<PlayingCard> cards;
    private final HandRank rank;
    private final List<Integer> kickers; // tiebreaker values, highest first

    private PokerHand(List<PlayingCard> cards, HandRank rank, List<Integer> kickers) {
        this.cards = List.copyOf(cards);
        this.rank = rank;
        this.kickers = List.copyOf(kickers);
    }

    public HandRank rank() { return rank; }
    public List<PlayingCard> cards() { return cards; }
    public List<Integer> kickers() { return kickers; }

    /**
     * Evaluate a 5-card hand.
     */
    public static PokerHand evaluate(List<PlayingCard> cards) {
        if (cards.size() != 5) {
            throw new IllegalArgumentException("Poker hand must be exactly 5 cards, got " + cards.size());
        }

        int[] rankCounts = new int[15]; // indices 2-14 (2 through Ace)
        Map<PlayingCard.Suit, Integer> suitCounts = new EnumMap<>(PlayingCard.Suit.class);

        for (PlayingCard card : cards) {
            rankCounts[card.rank().value()]++;
            suitCounts.merge(card.suit(), 1, Integer::sum);
        }

        boolean isFlush = suitCounts.values().stream().anyMatch(c -> c >= 5);
        boolean isStraight = checkStraight(rankCounts);
        boolean isAceLowStraight = checkAceLowStraight(rankCounts);

        // Find groups
        List<Integer> quads = new ArrayList<>();
        List<Integer> trips = new ArrayList<>();
        List<Integer> pairs = new ArrayList<>();
        List<Integer> singles = new ArrayList<>();

        for (int r = 14; r >= 2; r--) {
            switch (rankCounts[r]) {
                case 4 -> quads.add(r);
                case 3 -> trips.add(r);
                case 2 -> pairs.add(r);
                case 1 -> singles.add(r);
            }
        }

        // Determine rank and kickers
        HandRank handRank;
        List<Integer> kickers = new ArrayList<>();

        if (isFlush && isStraight) {
            int highCard = straightHighCard(rankCounts);
            if (highCard == 14) {
                handRank = HandRank.ROYAL_FLUSH;
            } else {
                handRank = HandRank.STRAIGHT_FLUSH;
            }
            kickers.add(highCard);
        } else if (isFlush && isAceLowStraight) {
            handRank = HandRank.STRAIGHT_FLUSH;
            kickers.add(5); // Ace-low straight: 5 is high
        } else if (!quads.isEmpty()) {
            handRank = HandRank.FOUR_OF_A_KIND;
            kickers.addAll(quads);
            kickers.addAll(singles);
        } else if (!trips.isEmpty() && !pairs.isEmpty()) {
            handRank = HandRank.FULL_HOUSE;
            kickers.addAll(trips);
            kickers.addAll(pairs);
        } else if (isFlush) {
            handRank = HandRank.FLUSH;
            kickers.addAll(singles);
            kickers.addAll(pairs); // shouldn't have pairs in a flush-only, but safety
        } else if (isStraight) {
            handRank = HandRank.STRAIGHT;
            kickers.add(straightHighCard(rankCounts));
        } else if (isAceLowStraight) {
            handRank = HandRank.STRAIGHT;
            kickers.add(5);
        } else if (!trips.isEmpty()) {
            handRank = HandRank.THREE_OF_A_KIND;
            kickers.addAll(trips);
            kickers.addAll(singles);
        } else if (pairs.size() >= 2) {
            handRank = HandRank.TWO_PAIR;
            kickers.addAll(pairs);
            kickers.addAll(singles);
        } else if (pairs.size() == 1) {
            handRank = HandRank.ONE_PAIR;
            kickers.addAll(pairs);
            kickers.addAll(singles);
        } else {
            handRank = HandRank.HIGH_CARD;
            kickers.addAll(singles);
        }

        return new PokerHand(cards, handRank, kickers);
    }

    /**
     * Find the best 5-card hand from 7 cards (2 hole + 5 community).
     * Evaluates all C(7,5) = 21 combinations.
     */
    public static PokerHand bestOfSeven(List<PlayingCard> sevenCards) {
        if (sevenCards.size() != 7) {
            throw new IllegalArgumentException("Expected 7 cards, got " + sevenCards.size());
        }

        PokerHand best = null;
        // Generate all 21 combinations of 5 from 7
        for (int i = 0; i < 7; i++) {
            for (int j = i + 1; j < 7; j++) {
                // Skip cards i and j
                List<PlayingCard> hand = new ArrayList<>(5);
                for (int k = 0; k < 7; k++) {
                    if (k != i && k != j) {
                        hand.add(sevenCards.get(k));
                    }
                }
                PokerHand candidate = evaluate(hand);
                if (best == null || candidate.compareTo(best) > 0) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    @Override
    public int compareTo(PokerHand other) {
        int rankCmp = this.rank.compareTo(other.rank);
        if (rankCmp != 0) return rankCmp;

        // Compare kickers
        for (int i = 0; i < Math.min(kickers.size(), other.kickers.size()); i++) {
            int cmp = Integer.compare(kickers.get(i), other.kickers.get(i));
            if (cmp != 0) return cmp;
        }
        return 0;
    }

    @Override
    public String toString() {
        return rank.displayName() + " " + cards;
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private static boolean checkStraight(int[] rankCounts) {
        for (int start = 10; start >= 2; start--) {
            boolean isStraight = true;
            for (int i = start; i < start + 5; i++) {
                if (rankCounts[i] == 0) {
                    isStraight = false;
                    break;
                }
            }
            if (isStraight) return true;
        }
        return false;
    }

    /** Check for A-2-3-4-5 (wheel). */
    private static boolean checkAceLowStraight(int[] rankCounts) {
        return rankCounts[14] >= 1 && rankCounts[2] >= 1 &&
               rankCounts[3] >= 1 && rankCounts[4] >= 1 && rankCounts[5] >= 1;
    }

    private static int straightHighCard(int[] rankCounts) {
        for (int start = 10; start >= 2; start--) {
            boolean isStraight = true;
            for (int i = start; i < start + 5; i++) {
                if (rankCounts[i] == 0) {
                    isStraight = false;
                    break;
                }
            }
            if (isStraight) return start + 4;
        }
        return -1;
    }
}
