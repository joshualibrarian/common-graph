package dev.everydaythings.graph.game;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ScoreBoardTest {

    @Test
    void defaultScore_isZero() {
        ScoreBoard sb = new ScoreBoard();
        assertThat(sb.score(0)).isEqualTo(0);
        assertThat(sb.score(0, "wood")).isEqualTo(0);
    }

    @Test
    void add_accumulates() {
        ScoreBoard sb = new ScoreBoard();
        sb.add(0, 10);
        sb.add(0, 5);
        assertThat(sb.score(0)).isEqualTo(15);
    }

    @Test
    void set_overwrites() {
        ScoreBoard sb = new ScoreBoard();
        sb.add(0, 10);
        sb.set(0, 3);
        assertThat(sb.score(0)).isEqualTo(3);
    }

    @Test
    void multiCategory_tracksIndependently() {
        ScoreBoard sb = new ScoreBoard();
        sb.add(0, "wood", 3);
        sb.add(0, "brick", 2);
        sb.add(0, "ore", 1);

        assertThat(sb.score(0, "wood")).isEqualTo(3);
        assertThat(sb.score(0, "brick")).isEqualTo(2);
        assertThat(sb.score(0, "ore")).isEqualTo(1);
    }

    @Test
    void multiPlayer_tracksIndependently() {
        ScoreBoard sb = new ScoreBoard();
        sb.add(0, 100);
        sb.add(1, 200);
        sb.add(2, 150);

        assertThat(sb.score(0)).isEqualTo(100);
        assertThat(sb.score(1)).isEqualTo(200);
        assertThat(sb.score(2)).isEqualTo(150);
    }

    @Test
    void allScores_returnsUnmodifiableView() {
        ScoreBoard sb = new ScoreBoard();
        sb.add(0, "wood", 3);
        sb.add(0, "brick", 2);

        var scores = sb.allScores(0);
        assertThat(scores).containsEntry("wood", 3).containsEntry("brick", 2);
        assertThatThrownBy(() -> scores.put("ore", 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void leaderboard_sortsByScoreDescending() {
        ScoreBoard sb = new ScoreBoard();
        sb.set(0, 100);
        sb.set(1, 300);
        sb.set(2, 200);

        assertThat(sb.leaderboard(ScoreBoard.DEFAULT)).containsExactly(1, 2, 0);
    }

    @Test
    void categories_returnsAllKnown() {
        ScoreBoard sb = new ScoreBoard();
        sb.add(0, "wood", 1);
        sb.add(0, "brick", 1);
        sb.add(1, "ore", 1);

        assertThat(sb.categories()).containsExactlyInAnyOrder("wood", "brick", "ore");
    }

    @Test
    void clear_resetsEverything() {
        ScoreBoard sb = new ScoreBoard();
        sb.add(0, 100);
        sb.add(1, "wood", 5);
        sb.clear();

        assertThat(sb.score(0)).isEqualTo(0);
        assertThat(sb.score(1, "wood")).isEqualTo(0);
        assertThat(sb.categories()).isEmpty();
    }

    @Test
    void add_negativeValues_decreaseScore() {
        ScoreBoard sb = new ScoreBoard();
        sb.add(0, 10);
        sb.add(0, -3);
        assertThat(sb.score(0)).isEqualTo(7);
    }
}
