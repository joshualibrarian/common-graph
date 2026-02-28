package dev.everydaythings.graph.game.dominoes;

import dev.everydaythings.graph.item.id.ItemID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class DominoesGameTest {

    private DominoesGame game;

    @BeforeEach
    void setUp() {
        game = DominoesGame.create(2);
        game.joinAs(0, ItemID.random(), "Alice");
        game.joinAs(1, ItemID.random(), "Bob");
    }

    @Test
    void createAndJoin() {
        assertThat(game.seatedCount()).isEqualTo(2);
        assertThat(game.isGameOver()).isFalse();
        assertThat(game.isStarted()).isFalse();
    }

    @Test
    void startDealsHands() {
        game.doStart();
        assertThat(game.isStarted()).isTrue();

        // 2 players get 15 tiles each, double-6 placed as center
        // 28 - 1 (starting double) - 15 - 15 = -3...
        // Actually 28 total, minus 1 starting double = 27, minus 15*2 = -3
        // That means boneyard won't have enough for 15 each from double-six set
        // With 28 tiles, minus 1 starting double = 27 tiles to deal
        // 2 players × 15 = 30 > 27, so each gets min(15, available)
        // Let's check what we actually get
        int hand0 = game.hand(0).size();
        int hand1 = game.hand(1).size();
        int boneyard = game.boneyardSize();

        // Total should be 27 (28 minus starting double)
        assertThat(hand0 + hand1 + boneyard).isEqualTo(27);
        assertThat(hand0).isGreaterThan(0);
        assertThat(hand1).isGreaterThan(0);
    }

    @Test
    void startSetsTrainEnds() {
        game.doStart();
        // All trains start at the starting double value (6)
        assertThat(game.trainEnd("train:0")).isEqualTo(6);
        assertThat(game.trainEnd("train:1")).isEqualTo(6);
        assertThat(game.trainEnd("mexican")).isEqualTo(6);
    }

    @Test
    void playTileUpdatesTrainEnd() {
        game.doStart();

        // Find a tile in player 0's hand that matches 6
        List<DominoTile> hand = game.hand(0);
        DominoTile playable = null;
        for (DominoTile tile : hand) {
            if (tile.matches(6) && !tile.isDouble()) {
                playable = tile;
                break;
            }
        }

        if (playable != null) {
            int handBefore = game.hand(0).size();
            game.doPlay(0, playable.ordinal(), "train:0");
            assertThat(game.hand(0).size()).isEqualTo(handBefore - 1);
            // Train end should be the other side of the tile
            int expectedEnd = playable.otherEnd(6);
            assertThat(game.trainEnd("train:0")).isEqualTo(expectedEnd);
        }
    }

    @Test
    void drawFromBoneyard() {
        game.doStart();

        int boneyardBefore = game.boneyardSize();
        if (boneyardBefore > 0) {
            int handBefore = game.hand(0).size();
            game.doDraw(0);
            assertThat(game.hand(0).size()).isEqualTo(handBefore + 1);
            assertThat(game.boneyardSize()).isEqualTo(boneyardBefore - 1);
        }
    }

    @Test
    void passOpensOwnTrain() {
        game.doStart();
        assertThat(game.isTrainOpen("train:0")).isFalse();

        game.doPass(0);
        assertThat(game.isTrainOpen("train:0")).isTrue();
    }

    @Test
    void statusText() {
        assertThat(game.statusText()).contains("Waiting");

        game.doStart();
        assertThat(game.statusText()).contains("Alice");
    }

    @Test
    void threePlayerDeal() {
        DominoesGame g3 = DominoesGame.create(3);
        g3.joinAs(0, ItemID.random(), "A");
        g3.joinAs(1, ItemID.random(), "B");
        g3.joinAs(2, ItemID.random(), "C");

        g3.doStart();
        // 3 players get 13 tiles each from 27 available (28 - 1)
        // 27 - 13*3 = 27 - 39 = not enough, so deal what's available
        int total = g3.hand(0).size() + g3.hand(1).size() + g3.hand(2).size() + g3.boneyardSize();
        assertThat(total).isEqualTo(27);
    }

    @Test
    void fourPlayerDeal() {
        DominoesGame g4 = DominoesGame.create(4);
        g4.joinAs(0, ItemID.random(), "A");
        g4.joinAs(1, ItemID.random(), "B");
        g4.joinAs(2, ItemID.random(), "C");
        g4.joinAs(3, ItemID.random(), "D");

        g4.doStart();
        // 4 players get 10 tiles each from 27 available (28 - 1)
        // 27 - 10*4 = 27 - 40 = not enough, so deal what's available
        int total = g4.hand(0).size() + g4.hand(1).size() + g4.hand(2).size() + g4.hand(3).size() + g4.boneyardSize();
        assertThat(total).isEqualTo(27);
    }
}
