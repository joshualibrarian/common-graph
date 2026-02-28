package dev.everydaythings.graph.game.spades;

import dev.everydaythings.graph.game.card.PlayingCard;
import dev.everydaythings.graph.item.id.ItemID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class SpadesGameTest {

    private SpadesGame game;

    @BeforeEach
    void setUp() {
        game = SpadesGame.create();
        game.joinAs(0, ItemID.random(), "Alice");
        game.joinAs(1, ItemID.random(), "Bob");
        game.joinAs(2, ItemID.random(), "Carol");
        game.joinAs(3, ItemID.random(), "Dave");
    }

    @Test
    void createAndJoin() {
        assertThat(game.seatedCount()).isEqualTo(4);
        assertThat(game.isGameOver()).isFalse();
        assertThat(game.currentPhase()).isEqualTo(SpadesGame.PHASE_DEAL);
    }

    @Test
    void dealDistributes13Each() {
        game.doDeal();
        assertThat(game.currentPhase()).isEqualTo(SpadesGame.PHASE_BID);

        for (int i = 0; i < 4; i++) {
            assertThat(game.hand(i)).hasSize(13);
        }
    }

    @Test
    void biddingPhase() {
        game.doDeal();
        assertThat(game.currentPhase()).isEqualTo(SpadesGame.PHASE_BID);

        // All players bid
        game.doBid(0, 3);
        assertThat(game.bid(0)).isEqualTo(3);
        assertThat(game.currentPhase()).isEqualTo(SpadesGame.PHASE_BID);

        game.doBid(1, 4);
        game.doBid(2, 3);
        game.doBid(3, 3);

        // After all bids, should be in play phase
        assertThat(game.currentPhase()).isEqualTo(SpadesGame.PHASE_PLAY);
    }

    @Test
    void nilBid() {
        game.doDeal();
        game.doBid(0, 0); // nil
        assertThat(game.bid(0)).isEqualTo(0);
    }

    @Test
    void playATrick() {
        game.doDeal();

        // Bid
        game.doBid(0, 3);
        game.doBid(1, 3);
        game.doBid(2, 3);
        game.doBid(3, 3);

        // Play one trick — the lead player plays first
        int lead = game.currentSeat();
        List<PlayingCard> hand = game.hand(lead);

        // Find a non-spade to lead with
        PlayingCard leadCard = null;
        for (PlayingCard c : hand) {
            if (c.suit() != PlayingCard.Suit.SPADES) {
                leadCard = c;
                break;
            }
        }
        if (leadCard == null) leadCard = hand.get(0); // all spades, unlikely but handle

        game.doPlayCard(lead, leadCard.ordinal());
        assertThat(game.currentTrick()).hasSize(1);

        // Other 3 players follow
        for (int i = 1; i < 4; i++) {
            int seat = game.currentSeat();
            List<PlayingCard> seatHand = game.hand(seat);
            // Play first legal card
            PlayingCard playCard = seatHand.get(0);
            game.doPlayCard(seat, playCard.ordinal());
        }

        // Trick complete
        assertThat(game.currentTrick()).isEmpty();
        assertThat(game.tricksPlayed()).isEqualTo(1);
    }

    @Test
    void teamAssignment() {
        assertThat(SpadesGame.teamOf(0)).isEqualTo(0);
        assertThat(SpadesGame.teamOf(1)).isEqualTo(1);
        assertThat(SpadesGame.teamOf(2)).isEqualTo(0);
        assertThat(SpadesGame.teamOf(3)).isEqualTo(1);
    }

    @Test
    void fullRoundScoring() {
        game.doDeal();

        // Bid
        game.doBid(0, 3);
        game.doBid(1, 3);
        game.doBid(2, 3);
        game.doBid(3, 3);

        // Play all 13 tricks
        for (int trick = 0; trick < 13; trick++) {
            for (int i = 0; i < 4; i++) {
                int seat = game.currentSeat();
                List<PlayingCard> hand = game.hand(seat);
                // Play first available card
                game.doPlayCard(seat, hand.get(0).ordinal());
            }
        }

        // Round should be scored
        // Total tricks: 13 distributed among 4 players
        int totalTricks = 0;
        for (int i = 0; i < 4; i++) {
            totalTricks += game.tricksWon(i);
        }
        assertThat(totalTricks).isEqualTo(13);

        // Scores should be non-zero (positive or negative depending on bids)
        int totalScore = game.teamScore(0) + game.teamScore(1);
        // At least some scoring happened
        assertThat(totalScore).isNotZero();
    }

    @Test
    void phases() {
        assertThat(game.phases()).containsExactly("deal", "bid", "play");
    }

    @Test
    void statusText() {
        assertThat(game.statusText()).contains("Ready");

        game.doDeal();
        assertThat(game.statusText()).contains("bid");
    }

    @Test
    void spadesBrokenTracking() {
        game.doDeal();
        assertThat(game.isSpadesBroken()).isFalse();
    }
}
