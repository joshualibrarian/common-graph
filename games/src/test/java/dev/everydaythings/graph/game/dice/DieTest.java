package dev.everydaythings.graph.game.dice;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class DieTest {

    @Test
    void d6Factory() {
        Die die = Die.d6(4);
        assertThat(die.sides()).isEqualTo(6);
        assertThat(die.faceValue()).isEqualTo(4);
        assertThat(die.held()).isFalse();
        assertThat(die.isD6()).isTrue();
        assertThat(die.isRolled()).isTrue();
    }

    @Test
    void blankDie() {
        Die die = Die.blank();
        assertThat(die.faceValue()).isEqualTo(0);
        assertThat(die.isRolled()).isFalse();
        assertThat(die.symbol()).isEqualTo("\u2B1C");
    }

    @Test
    void heldDie() {
        Die die = Die.d6(3, true);
        assertThat(die.held()).isTrue();
        assertThat(die.colorCategory()).isEqualTo("held");
    }

    @Test
    void freeDie() {
        Die die = Die.d6(3, false);
        assertThat(die.held()).isFalse();
        assertThat(die.colorCategory()).isEqualTo("free");
    }

    @Test
    void unicodeSymbols() {
        assertThat(Die.d6(1).symbol()).isEqualTo("\u2680"); // ⚀
        assertThat(Die.d6(2).symbol()).isEqualTo("\u2681"); // ⚁
        assertThat(Die.d6(3).symbol()).isEqualTo("\u2682"); // ⚂
        assertThat(Die.d6(4).symbol()).isEqualTo("\u2683"); // ⚃
        assertThat(Die.d6(5).symbol()).isEqualTo("\u2684"); // ⚄
        assertThat(Die.d6(6).symbol()).isEqualTo("\u2685"); // ⚅
    }

    @Test
    void withFaceCreatesNewDie() {
        Die die = Die.d6(3);
        Die updated = die.withFace(5);
        assertThat(updated.faceValue()).isEqualTo(5);
        assertThat(die.faceValue()).isEqualTo(3); // original unchanged
    }

    @Test
    void withHeldCreatesNewDie() {
        Die die = Die.d6(3);
        Die held = die.withHeld(true);
        assertThat(held.held()).isTrue();
        assertThat(die.held()).isFalse(); // original unchanged
    }

    @Test
    void imageKey() {
        Die die = Die.d6(4);
        assertThat(die.imageKey()).isEmpty();
    }

    @Test
    void modelKey() {
        Die die = Die.d6(4);
        assertThat(die.modelKey()).isEqualTo("dice/d6.glb");
    }

    @Test
    void invalidFaceValue() {
        assertThatThrownBy(() -> Die.d6(7))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Die.d6(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidSides() {
        assertThatThrownBy(() -> new Die(1, 1, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equality() {
        assertThat(Die.d6(4)).isEqualTo(Die.d6(4));
        assertThat(Die.d6(4)).isNotEqualTo(Die.d6(5));
        assertThat(Die.d6(4, true)).isNotEqualTo(Die.d6(4, false));
    }

    @Test
    void toStringShowsSymbol() {
        assertThat(Die.d6(4).toString()).contains("\u2683");
        assertThat(Die.d6(4, true).toString()).contains("*");
    }

    @Test
    void nonD6Symbol() {
        // d20 with face 15 — no Unicode symbol, uses number
        Die d20 = new Die(20, 15, false);
        assertThat(d20.symbol()).isEqualTo("15");
        assertThat(d20.isD6()).isFalse();
    }

    @Test
    void bodyColorDiffersWhenHeld() {
        Die free = Die.d6(3, false);
        Die held = Die.d6(3, true);
        assertThat(free.bodyColor()).isNotEqualTo(held.bodyColor());
        // Free: white-ish, Held: amber-tinted
        assertThat(free.bodyColor()).isEqualTo(0xFFFAFAFA);
        assertThat(held.bodyColor()).isEqualTo(0xFFFFF3E0);
    }

    @Test
    void modelColorMatchesBodyColor() {
        Die die = Die.d6(4, true);
        assertThat(die.modelColor()).isEqualTo(die.bodyColor());
    }

    @Test
    void pipColor() {
        Die die = Die.d6(3);
        assertThat(die.pipColor()).isEqualTo(0xFF1A1A1A);
    }

    // ==================================================================================
    // Pip Grid Positions
    // ==================================================================================

    @Test
    void pipPositions_blank() {
        Die die = Die.blank();
        assertThat(die.hasTL()).isFalse();
        assertThat(die.hasTC()).isFalse();
        assertThat(die.hasTR()).isFalse();
        assertThat(die.hasML()).isFalse();
        assertThat(die.hasMC()).isFalse();
        assertThat(die.hasMR()).isFalse();
        assertThat(die.hasBL()).isFalse();
        assertThat(die.hasBC()).isFalse();
        assertThat(die.hasBR()).isFalse();
    }

    @Test
    void pipPositions_face1() {
        Die die = Die.d6(1);
        assertThat(die.hasTL()).isFalse();
        assertThat(die.hasTC()).isFalse();
        assertThat(die.hasTR()).isFalse();
        assertThat(die.hasML()).isFalse();
        assertThat(die.hasMC()).isTrue();   // center pip only
        assertThat(die.hasMR()).isFalse();
        assertThat(die.hasBL()).isFalse();
        assertThat(die.hasBC()).isFalse();
        assertThat(die.hasBR()).isFalse();
    }

    @Test
    void pipPositions_face2() {
        Die die = Die.d6(2);
        assertThat(die.hasTL()).isFalse();
        assertThat(die.hasTR()).isTrue();   // top-right
        assertThat(die.hasMC()).isFalse();
        assertThat(die.hasBL()).isTrue();   // bottom-left
        assertThat(die.hasBR()).isFalse();
    }

    @Test
    void pipPositions_face3() {
        Die die = Die.d6(3);
        assertThat(die.hasTR()).isTrue();   // top-right
        assertThat(die.hasMC()).isTrue();   // center
        assertThat(die.hasBL()).isTrue();   // bottom-left
        // others false
        assertThat(die.hasTL()).isFalse();
        assertThat(die.hasTC()).isFalse();
        assertThat(die.hasML()).isFalse();
        assertThat(die.hasMR()).isFalse();
        assertThat(die.hasBC()).isFalse();
        assertThat(die.hasBR()).isFalse();
    }

    @Test
    void pipPositions_face4() {
        Die die = Die.d6(4);
        assertThat(die.hasTL()).isTrue();   // four corners
        assertThat(die.hasTR()).isTrue();
        assertThat(die.hasBL()).isTrue();
        assertThat(die.hasBR()).isTrue();
        // center + edges false
        assertThat(die.hasTC()).isFalse();
        assertThat(die.hasML()).isFalse();
        assertThat(die.hasMC()).isFalse();
        assertThat(die.hasMR()).isFalse();
        assertThat(die.hasBC()).isFalse();
    }

    @Test
    void pipPositions_face5() {
        Die die = Die.d6(5);
        assertThat(die.hasTL()).isTrue();   // four corners + center
        assertThat(die.hasTR()).isTrue();
        assertThat(die.hasMC()).isTrue();
        assertThat(die.hasBL()).isTrue();
        assertThat(die.hasBR()).isTrue();
        // edges false
        assertThat(die.hasTC()).isFalse();
        assertThat(die.hasML()).isFalse();
        assertThat(die.hasMR()).isFalse();
        assertThat(die.hasBC()).isFalse();
    }

    @Test
    void pipPositions_face6() {
        Die die = Die.d6(6);
        assertThat(die.hasTL()).isTrue();   // two columns of three
        assertThat(die.hasTR()).isTrue();
        assertThat(die.hasML()).isTrue();
        assertThat(die.hasMR()).isTrue();
        assertThat(die.hasBL()).isTrue();
        assertThat(die.hasBR()).isTrue();
        // center column false
        assertThat(die.hasTC()).isFalse();
        assertThat(die.hasMC()).isFalse();
        assertThat(die.hasBC()).isFalse();
    }

    @Test
    void standardSize() {
        assertThat(Die.STANDARD_SIZE_MM).isEqualTo(16.0);
    }
}
