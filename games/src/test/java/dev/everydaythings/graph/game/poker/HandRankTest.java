package dev.everydaythings.graph.game.poker;

import dev.everydaythings.graph.game.card.PlayingCard;
import dev.everydaythings.graph.game.card.PlayingCard.Rank;
import dev.everydaythings.graph.game.card.PlayingCard.Suit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class HandRankTest {

    private PlayingCard card(Suit s, Rank r) {
        return new PlayingCard(s, r);
    }

    @Test
    void highCard() {
        PokerHand hand = PokerHand.evaluate(List.of(
                card(Suit.HEARTS, Rank.TWO),
                card(Suit.DIAMONDS, Rank.FIVE),
                card(Suit.CLUBS, Rank.EIGHT),
                card(Suit.SPADES, Rank.JACK),
                card(Suit.HEARTS, Rank.ACE)
        ));
        assertThat(hand.rank()).isEqualTo(HandRank.HIGH_CARD);
    }

    @Test
    void onePair() {
        PokerHand hand = PokerHand.evaluate(List.of(
                card(Suit.HEARTS, Rank.ACE),
                card(Suit.DIAMONDS, Rank.ACE),
                card(Suit.CLUBS, Rank.EIGHT),
                card(Suit.SPADES, Rank.FIVE),
                card(Suit.HEARTS, Rank.TWO)
        ));
        assertThat(hand.rank()).isEqualTo(HandRank.ONE_PAIR);
    }

    @Test
    void twoPair() {
        PokerHand hand = PokerHand.evaluate(List.of(
                card(Suit.HEARTS, Rank.ACE),
                card(Suit.DIAMONDS, Rank.ACE),
                card(Suit.CLUBS, Rank.KING),
                card(Suit.SPADES, Rank.KING),
                card(Suit.HEARTS, Rank.TWO)
        ));
        assertThat(hand.rank()).isEqualTo(HandRank.TWO_PAIR);
    }

    @Test
    void threeOfAKind() {
        PokerHand hand = PokerHand.evaluate(List.of(
                card(Suit.HEARTS, Rank.SEVEN),
                card(Suit.DIAMONDS, Rank.SEVEN),
                card(Suit.CLUBS, Rank.SEVEN),
                card(Suit.SPADES, Rank.KING),
                card(Suit.HEARTS, Rank.TWO)
        ));
        assertThat(hand.rank()).isEqualTo(HandRank.THREE_OF_A_KIND);
    }

    @Test
    void straight() {
        PokerHand hand = PokerHand.evaluate(List.of(
                card(Suit.HEARTS, Rank.FIVE),
                card(Suit.DIAMONDS, Rank.SIX),
                card(Suit.CLUBS, Rank.SEVEN),
                card(Suit.SPADES, Rank.EIGHT),
                card(Suit.HEARTS, Rank.NINE)
        ));
        assertThat(hand.rank()).isEqualTo(HandRank.STRAIGHT);
    }

    @Test
    void aceLowStraight() {
        PokerHand hand = PokerHand.evaluate(List.of(
                card(Suit.HEARTS, Rank.ACE),
                card(Suit.DIAMONDS, Rank.TWO),
                card(Suit.CLUBS, Rank.THREE),
                card(Suit.SPADES, Rank.FOUR),
                card(Suit.HEARTS, Rank.FIVE)
        ));
        assertThat(hand.rank()).isEqualTo(HandRank.STRAIGHT);
    }

    @Test
    void flush() {
        PokerHand hand = PokerHand.evaluate(List.of(
                card(Suit.HEARTS, Rank.TWO),
                card(Suit.HEARTS, Rank.FIVE),
                card(Suit.HEARTS, Rank.EIGHT),
                card(Suit.HEARTS, Rank.JACK),
                card(Suit.HEARTS, Rank.ACE)
        ));
        assertThat(hand.rank()).isEqualTo(HandRank.FLUSH);
    }

    @Test
    void fullHouse() {
        PokerHand hand = PokerHand.evaluate(List.of(
                card(Suit.HEARTS, Rank.KING),
                card(Suit.DIAMONDS, Rank.KING),
                card(Suit.CLUBS, Rank.KING),
                card(Suit.SPADES, Rank.QUEEN),
                card(Suit.HEARTS, Rank.QUEEN)
        ));
        assertThat(hand.rank()).isEqualTo(HandRank.FULL_HOUSE);
    }

    @Test
    void fourOfAKind() {
        PokerHand hand = PokerHand.evaluate(List.of(
                card(Suit.HEARTS, Rank.NINE),
                card(Suit.DIAMONDS, Rank.NINE),
                card(Suit.CLUBS, Rank.NINE),
                card(Suit.SPADES, Rank.NINE),
                card(Suit.HEARTS, Rank.ACE)
        ));
        assertThat(hand.rank()).isEqualTo(HandRank.FOUR_OF_A_KIND);
    }

    @Test
    void straightFlush() {
        PokerHand hand = PokerHand.evaluate(List.of(
                card(Suit.HEARTS, Rank.FIVE),
                card(Suit.HEARTS, Rank.SIX),
                card(Suit.HEARTS, Rank.SEVEN),
                card(Suit.HEARTS, Rank.EIGHT),
                card(Suit.HEARTS, Rank.NINE)
        ));
        assertThat(hand.rank()).isEqualTo(HandRank.STRAIGHT_FLUSH);
    }

    @Test
    void royalFlush() {
        PokerHand hand = PokerHand.evaluate(List.of(
                card(Suit.SPADES, Rank.TEN),
                card(Suit.SPADES, Rank.JACK),
                card(Suit.SPADES, Rank.QUEEN),
                card(Suit.SPADES, Rank.KING),
                card(Suit.SPADES, Rank.ACE)
        ));
        assertThat(hand.rank()).isEqualTo(HandRank.ROYAL_FLUSH);
    }

    @Test
    void handRanking() {
        PokerHand highCard = PokerHand.evaluate(List.of(
                card(Suit.HEARTS, Rank.TWO), card(Suit.DIAMONDS, Rank.FIVE),
                card(Suit.CLUBS, Rank.EIGHT), card(Suit.SPADES, Rank.JACK),
                card(Suit.HEARTS, Rank.ACE)
        ));
        PokerHand pair = PokerHand.evaluate(List.of(
                card(Suit.HEARTS, Rank.ACE), card(Suit.DIAMONDS, Rank.ACE),
                card(Suit.CLUBS, Rank.EIGHT), card(Suit.SPADES, Rank.FIVE),
                card(Suit.HEARTS, Rank.TWO)
        ));
        PokerHand flush = PokerHand.evaluate(List.of(
                card(Suit.HEARTS, Rank.TWO), card(Suit.HEARTS, Rank.FIVE),
                card(Suit.HEARTS, Rank.EIGHT), card(Suit.HEARTS, Rank.JACK),
                card(Suit.HEARTS, Rank.ACE)
        ));

        assertThat(pair).isGreaterThan(highCard);
        assertThat(flush).isGreaterThan(pair);
    }

    @Test
    void bestOfSevenPicksStrongest() {
        List<PlayingCard> seven = List.of(
                card(Suit.HEARTS, Rank.ACE),
                card(Suit.DIAMONDS, Rank.ACE),
                card(Suit.CLUBS, Rank.ACE),
                card(Suit.SPADES, Rank.KING),
                card(Suit.HEARTS, Rank.KING),
                card(Suit.DIAMONDS, Rank.TWO),
                card(Suit.CLUBS, Rank.THREE)
        );
        PokerHand best = PokerHand.bestOfSeven(seven);
        assertThat(best.rank()).isEqualTo(HandRank.FULL_HOUSE);
    }

    @Test
    void kickersBreakTies() {
        PokerHand pairAces = PokerHand.evaluate(List.of(
                card(Suit.HEARTS, Rank.ACE), card(Suit.DIAMONDS, Rank.ACE),
                card(Suit.CLUBS, Rank.KING), card(Suit.SPADES, Rank.QUEEN),
                card(Suit.HEARTS, Rank.JACK)
        ));
        PokerHand pairKings = PokerHand.evaluate(List.of(
                card(Suit.HEARTS, Rank.KING), card(Suit.DIAMONDS, Rank.KING),
                card(Suit.CLUBS, Rank.ACE), card(Suit.SPADES, Rank.QUEEN),
                card(Suit.HEARTS, Rank.JACK)
        ));
        assertThat(pairAces).isGreaterThan(pairKings);
    }
}
