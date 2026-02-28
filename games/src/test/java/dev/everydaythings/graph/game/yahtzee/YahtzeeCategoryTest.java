package dev.everydaythings.graph.game.yahtzee;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class YahtzeeCategoryTest {

    @Test
    void onesCountsFaceValue() {
        assertThat(YahtzeeCategory.ONES.score(new int[]{1, 1, 3, 4, 5})).isEqualTo(2);
        assertThat(YahtzeeCategory.ONES.score(new int[]{2, 3, 4, 5, 6})).isEqualTo(0);
    }

    @Test
    void sixesSumsSixes() {
        assertThat(YahtzeeCategory.SIXES.score(new int[]{6, 6, 6, 1, 2})).isEqualTo(18);
    }

    @Test
    void threeOfAKindSumsAll() {
        assertThat(YahtzeeCategory.THREE_OF_A_KIND.score(new int[]{3, 3, 3, 4, 5})).isEqualTo(18);
        assertThat(YahtzeeCategory.THREE_OF_A_KIND.score(new int[]{1, 2, 3, 4, 5})).isEqualTo(0);
    }

    @Test
    void fourOfAKindSumsAll() {
        assertThat(YahtzeeCategory.FOUR_OF_A_KIND.score(new int[]{4, 4, 4, 4, 2})).isEqualTo(18);
        assertThat(YahtzeeCategory.FOUR_OF_A_KIND.score(new int[]{3, 3, 3, 4, 5})).isEqualTo(0);
    }

    @Test
    void fullHouseIs25() {
        assertThat(YahtzeeCategory.FULL_HOUSE.score(new int[]{3, 3, 3, 2, 2})).isEqualTo(25);
        assertThat(YahtzeeCategory.FULL_HOUSE.score(new int[]{3, 3, 3, 3, 2})).isEqualTo(0);
        assertThat(YahtzeeCategory.FULL_HOUSE.score(new int[]{1, 2, 3, 4, 5})).isEqualTo(0);
    }

    @Test
    void smallStraightIs30() {
        assertThat(YahtzeeCategory.SMALL_STRAIGHT.score(new int[]{1, 2, 3, 4, 6})).isEqualTo(30);
        assertThat(YahtzeeCategory.SMALL_STRAIGHT.score(new int[]{2, 3, 4, 5, 1})).isEqualTo(30);
        assertThat(YahtzeeCategory.SMALL_STRAIGHT.score(new int[]{3, 4, 5, 6, 1})).isEqualTo(30);
        assertThat(YahtzeeCategory.SMALL_STRAIGHT.score(new int[]{1, 2, 4, 5, 6})).isEqualTo(0);
    }

    @Test
    void largeStraightIs40() {
        assertThat(YahtzeeCategory.LARGE_STRAIGHT.score(new int[]{1, 2, 3, 4, 5})).isEqualTo(40);
        assertThat(YahtzeeCategory.LARGE_STRAIGHT.score(new int[]{2, 3, 4, 5, 6})).isEqualTo(40);
        assertThat(YahtzeeCategory.LARGE_STRAIGHT.score(new int[]{1, 2, 3, 4, 6})).isEqualTo(0);
    }

    @Test
    void yahtzeeIs50() {
        assertThat(YahtzeeCategory.YAHTZEE.score(new int[]{5, 5, 5, 5, 5})).isEqualTo(50);
        assertThat(YahtzeeCategory.YAHTZEE.score(new int[]{5, 5, 5, 5, 4})).isEqualTo(0);
    }

    @Test
    void chanceSumsAll() {
        assertThat(YahtzeeCategory.CHANCE.score(new int[]{1, 2, 3, 4, 5})).isEqualTo(15);
        assertThat(YahtzeeCategory.CHANCE.score(new int[]{6, 6, 6, 6, 6})).isEqualTo(30);
    }

    @Test
    void upperSectionCategories() {
        for (YahtzeeCategory cat : YahtzeeCategory.values()) {
            if (cat.ordinal() <= YahtzeeCategory.SIXES.ordinal()) {
                assertThat(cat.isUpper()).isTrue();
            } else {
                assertThat(cat.isUpper()).isFalse();
            }
        }
    }

    @Test
    void thirteenCategories() {
        assertThat(YahtzeeCategory.values()).hasSize(13);
    }

    @Test
    void faceValues() {
        assertThat(YahtzeeCategory.ONES.faceValue()).isEqualTo(1);
        assertThat(YahtzeeCategory.SIXES.faceValue()).isEqualTo(6);
    }
}
