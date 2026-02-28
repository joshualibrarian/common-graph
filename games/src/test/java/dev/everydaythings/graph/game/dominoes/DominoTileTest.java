package dev.everydaythings.graph.game.dominoes;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class DominoTileTest {

    @Test
    void fullSetHas28Tiles() {
        List<DominoTile> set = DominoTile.fullSet();
        assertThat(set).hasSize(28);
        assertThat(new HashSet<>(set)).hasSize(28);
    }

    @Test
    void normalizedOrder() {
        DominoTile tile = new DominoTile(5, 3);
        assertThat(tile.left()).isEqualTo(3);
        assertThat(tile.right()).isEqualTo(5);
    }

    @Test
    void ordinalRoundTrips() {
        for (int i = 0; i < 28; i++) {
            DominoTile tile = DominoTile.fromOrdinal(i);
            assertThat(tile.ordinal()).isEqualTo(i);
        }
    }

    @Test
    void ordinalMatchesSetOrder() {
        List<DominoTile> set = DominoTile.fullSet();
        for (int i = 0; i < 28; i++) {
            assertThat(set.get(i).ordinal()).isEqualTo(i);
        }
    }

    @Test
    void fromOrdinalRejectsInvalid() {
        assertThatThrownBy(() -> DominoTile.fromOrdinal(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DominoTile.fromOrdinal(28))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isDouble() {
        assertThat(new DominoTile(3, 3).isDouble()).isTrue();
        assertThat(new DominoTile(3, 4).isDouble()).isFalse();
    }

    @Test
    void pipCount() {
        assertThat(new DominoTile(3, 5).pipCount()).isEqualTo(8);
        assertThat(new DominoTile(0, 0).pipCount()).isEqualTo(0);
        assertThat(new DominoTile(6, 6).pipCount()).isEqualTo(12);
    }

    @Test
    void matches() {
        DominoTile tile = new DominoTile(3, 5);
        assertThat(tile.matches(3)).isTrue();
        assertThat(tile.matches(5)).isTrue();
        assertThat(tile.matches(4)).isFalse();
    }

    @Test
    void otherEnd() {
        DominoTile tile = new DominoTile(3, 5);
        assertThat(tile.otherEnd(3)).isEqualTo(5);
        assertThat(tile.otherEnd(5)).isEqualTo(3);
    }

    @Test
    void symbol() {
        assertThat(new DominoTile(3, 5).symbol()).isEqualTo("[3|5]");
        assertThat(new DominoTile(0, 0).symbol()).isEqualTo("[0|0]");
    }

    @Test
    void equality() {
        DominoTile a = new DominoTile(3, 5);
        DominoTile b = new DominoTile(5, 3); // reversed, should normalize
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void rejectsPipsOutOfRange() {
        assertThatThrownBy(() -> new DominoTile(-1, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DominoTile(3, 7))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
