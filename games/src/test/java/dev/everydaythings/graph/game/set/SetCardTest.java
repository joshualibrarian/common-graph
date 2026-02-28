package dev.everydaythings.graph.game.set;

import dev.everydaythings.graph.game.set.SetProperty.*;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class SetCardTest {

    // ==================================================================================
    // isValidSet — Valid Sets
    // ==================================================================================

    @Test
    void isValidSet_allDifferent_allDifferent_allDifferent_allDifferent() {
        SetCard a = new SetCard(Count.ONE, Shape.DIAMOND, Shading.SOLID, Color.RED);
        SetCard b = new SetCard(Count.TWO, Shape.OVAL, Shading.STRIPED, Color.GREEN);
        SetCard c = new SetCard(Count.THREE, Shape.SQUIGGLE, Shading.EMPTY, Color.PURPLE);
        assertThat(SetCard.isValidSet(a, b, c)).isTrue();
    }

    @Test
    void isValidSet_allSame_allSame_allSame_allDifferent() {
        SetCard a = new SetCard(Count.TWO, Shape.OVAL, Shading.SOLID, Color.RED);
        SetCard b = new SetCard(Count.TWO, Shape.OVAL, Shading.SOLID, Color.GREEN);
        SetCard c = new SetCard(Count.TWO, Shape.OVAL, Shading.SOLID, Color.PURPLE);
        assertThat(SetCard.isValidSet(a, b, c)).isTrue();
    }

    @Test
    void isValidSet_mixedSameAndDifferent() {
        // Count: all same (ONE), Shape: all different, Shading: all same (SOLID), Color: all different
        SetCard a = new SetCard(Count.ONE, Shape.DIAMOND, Shading.SOLID, Color.RED);
        SetCard b = new SetCard(Count.ONE, Shape.OVAL, Shading.SOLID, Color.GREEN);
        SetCard c = new SetCard(Count.ONE, Shape.SQUIGGLE, Shading.SOLID, Color.PURPLE);
        assertThat(SetCard.isValidSet(a, b, c)).isTrue();
    }

    // ==================================================================================
    // isValidSet — Invalid Sets
    // ==================================================================================

    @Test
    void isValidSet_twoSameOneDifferent_rejects() {
        // Count: TWO, TWO, ONE — invalid (two same, one different)
        SetCard a = new SetCard(Count.TWO, Shape.DIAMOND, Shading.SOLID, Color.RED);
        SetCard b = new SetCard(Count.TWO, Shape.OVAL, Shading.STRIPED, Color.GREEN);
        SetCard c = new SetCard(Count.ONE, Shape.SQUIGGLE, Shading.EMPTY, Color.PURPLE);
        assertThat(SetCard.isValidSet(a, b, c)).isFalse();
    }

    @Test
    void isValidSet_invalidOnColor() {
        // Everything valid except color: RED, RED, GREEN
        SetCard a = new SetCard(Count.ONE, Shape.DIAMOND, Shading.SOLID, Color.RED);
        SetCard b = new SetCard(Count.TWO, Shape.OVAL, Shading.STRIPED, Color.RED);
        SetCard c = new SetCard(Count.THREE, Shape.SQUIGGLE, Shading.EMPTY, Color.GREEN);
        assertThat(SetCard.isValidSet(a, b, c)).isFalse();
    }

    @Test
    void isValidSet_invalidOnShape() {
        // Shape: DIAMOND, DIAMOND, OVAL — invalid
        SetCard a = new SetCard(Count.ONE, Shape.DIAMOND, Shading.SOLID, Color.RED);
        SetCard b = new SetCard(Count.TWO, Shape.DIAMOND, Shading.STRIPED, Color.GREEN);
        SetCard c = new SetCard(Count.THREE, Shape.OVAL, Shading.EMPTY, Color.PURPLE);
        assertThat(SetCard.isValidSet(a, b, c)).isFalse();
    }

    // ==================================================================================
    // Ordinal Encoding
    // ==================================================================================

    @Test
    void ordinalRoundtrip_allCards() {
        List<SetCard> deck = SetCard.fullDeck();
        for (SetCard card : deck) {
            int ord = card.ordinal();
            assertThat(ord).isBetween(0, 80);
            SetCard decoded = SetCard.fromOrdinal(ord);
            assertThat(decoded).isEqualTo(card);
        }
    }

    @Test
    void ordinal_uniqueForEachCard() {
        List<SetCard> deck = SetCard.fullDeck();
        HashSet<Integer> ordinals = new HashSet<>();
        for (SetCard card : deck) {
            assertThat(ordinals.add(card.ordinal())).isTrue();
        }
    }

    @Test
    void fromOrdinal_outOfRange_throws() {
        assertThatIllegalArgumentException().isThrownBy(() -> SetCard.fromOrdinal(-1));
        assertThatIllegalArgumentException().isThrownBy(() -> SetCard.fromOrdinal(81));
    }

    // ==================================================================================
    // Full Deck
    // ==================================================================================

    @Test
    void fullDeck_has81Cards() {
        assertThat(SetCard.fullDeck()).hasSize(81);
    }

    @Test
    void fullDeck_allUnique() {
        List<SetCard> deck = SetCard.fullDeck();
        assertThat(new HashSet<>(deck)).hasSize(81);
    }

    // ==================================================================================
    // Symbol and Display
    // ==================================================================================

    @Test
    void symbol_isNotEmpty() {
        for (SetCard card : SetCard.fullDeck()) {
            assertThat(card.symbol()).isNotEmpty();
        }
    }

    @Test
    void colorName_matchesEnum() {
        assertThat(new SetCard(Count.ONE, Shape.DIAMOND, Shading.SOLID, Color.RED).colorName())
                .isEqualTo("red");
        assertThat(new SetCard(Count.ONE, Shape.DIAMOND, Shading.SOLID, Color.GREEN).colorName())
                .isEqualTo("green");
        assertThat(new SetCard(Count.ONE, Shape.DIAMOND, Shading.SOLID, Color.PURPLE).colorName())
                .isEqualTo("purple");
    }

    @Test
    void toString_containsProperties() {
        SetCard card = new SetCard(Count.THREE, Shape.SQUIGGLE, Shading.EMPTY, Color.PURPLE);
        String s = card.toString();
        assertThat(s).containsIgnoringCase("empty");
        assertThat(s).containsIgnoringCase("purple");
        assertThat(s).containsIgnoringCase("squiggle");
        assertThat(s).contains("3");
    }
}
