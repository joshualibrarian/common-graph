package dev.everydaythings.graph.game.poker;

import dev.everydaythings.graph.item.id.ItemID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class PokerGameTest {

    private PokerGame game;

    @BeforeEach
    void setUp() {
        game = PokerGame.create(4).withBlinds(5, 10);
        game.joinAs(0, ItemID.random(), "Alice");
        game.joinAs(1, ItemID.random(), "Bob");
        game.joinAs(2, ItemID.random(), "Carol");
        game.joinAs(3, ItemID.random(), "Dave");
    }

    @Test
    void createAndJoin() {
        assertThat(game.seatedCount()).isEqualTo(4);
        assertThat(game.isGameOver()).isFalse();
        assertThat(game.isHandInProgress()).isFalse();
    }

    @Test
    void dealStartsHand() {
        game.doDeal();
        assertThat(game.isHandInProgress()).isTrue();
        assertThat(game.currentPhase()).isEqualTo(PokerGame.PHASE_PREFLOP);

        // Each player should have 2 hole cards
        for (int i = 0; i < 4; i++) {
            assertThat(game.holeCards(i)).hasSize(2);
        }

        // Blinds should be posted
        assertThat(game.pot()).isEqualTo(15); // 5 + 10
    }

    @Test
    void chipStacksInitialized() {
        game.doDeal();
        // Buy-in is 1000; blinds deducted from SB and BB
        int totalChips = 0;
        for (int i = 0; i < 4; i++) {
            totalChips += game.chips(i);
        }
        assertThat(totalChips + game.pot()).isEqualTo(4000);
    }

    @Test
    void callAndCheck() {
        game.doDeal();

        // All players call or check through preflop
        int seat = game.currentSeat();
        game.doCall(seat);

        // The game should advance to next player
        assertThat(game.currentSeat()).isNotEqualTo(seat);
    }

    @Test
    void foldReducesActivePlayers() {
        game.doDeal();
        int seat = game.currentSeat();
        game.doFold(seat);
        assertThat(game.hasFolded(seat)).isTrue();
        assertThat(game.activeSeatCount()).isEqualTo(3);
    }

    @Test
    void allFoldAwardsPot() {
        game.doDeal();
        int pot = game.pot();

        // Fold all but one
        for (int i = 0; i < 3; i++) {
            int seat = game.currentSeat();
            game.doFold(seat);
        }

        // Hand should be over, pot awarded
        assertThat(game.isHandInProgress()).isFalse();
        assertThat(game.lastWinner()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void phases() {
        assertThat(game.phases()).containsExactly(
                "preflop", "flop", "turn", "river", "showdown");
    }

    @Test
    void headsUpBlinds() {
        PokerGame headsUp = PokerGame.create(2).withBlinds(5, 10);
        headsUp.joinAs(0, ItemID.random(), "Alice");
        headsUp.joinAs(1, ItemID.random(), "Bob");

        headsUp.doDeal();
        assertThat(headsUp.isHandInProgress()).isTrue();
        // Each player has 2 cards
        assertThat(headsUp.holeCards(0)).hasSize(2);
        assertThat(headsUp.holeCards(1)).hasSize(2);
        assertThat(headsUp.pot()).isEqualTo(15);
    }

    @Test
    void communityCardsGrow() {
        game.doDeal();

        // Play through preflop — everyone calls
        playThroughBettingRound();

        if (game.isHandInProgress()) {
            assertThat(game.currentPhase()).isEqualTo(PokerGame.PHASE_FLOP);
            assertThat(game.communityCards()).hasSize(3);

            playThroughBettingRound();
            if (game.isHandInProgress()) {
                assertThat(game.currentPhase()).isEqualTo(PokerGame.PHASE_TURN);
                assertThat(game.communityCards()).hasSize(4);

                playThroughBettingRound();
                if (game.isHandInProgress()) {
                    assertThat(game.currentPhase()).isEqualTo(PokerGame.PHASE_RIVER);
                    assertThat(game.communityCards()).hasSize(5);
                }
            }
        }
    }

    @Test
    void statusText() {
        assertThat(game.statusText()).contains("Ready");

        game.doDeal();
        assertThat(game.statusText()).contains("turn").contains("pot");
    }

    @Test
    void multipleHands() {
        game.doDeal();

        // Fold all but one to end hand quickly
        for (int i = 0; i < 3; i++) {
            game.doFold(game.currentSeat());
        }

        assertThat(game.isHandInProgress()).isFalse();

        // Deal another hand
        game.doDeal();
        assertThat(game.isHandInProgress()).isTrue();
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private void playThroughBettingRound() {
        String startPhase = game.currentPhase();
        int attempts = 0;
        while (game.isHandInProgress() && game.currentPhase().equals(startPhase) && attempts < 20) {
            int seat = game.currentSeat();
            if (game.hasFolded(seat) || game.isAllIn(seat)) break;

            if (game.currentBet() > 0) {
                game.doCall(seat);
            } else {
                game.doCheck(seat);
            }
            attempts++;
        }
    }
}
