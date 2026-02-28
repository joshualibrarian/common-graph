package dev.everydaythings.graph.game.card;

import dev.everydaythings.graph.game.Piece;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class PlayingCardTest {

    @Test
    void fullDeckHas52UniqueCards() {
        List<PlayingCard> deck = PlayingCard.fullDeck();
        assertThat(deck).hasSize(52);
        assertThat(new HashSet<>(deck)).hasSize(52);
    }

    @Test
    void ordinalRoundTrips() {
        for (int i = 0; i < 52; i++) {
            PlayingCard card = PlayingCard.fromOrdinal(i);
            assertThat(card.ordinal()).isEqualTo(i);
        }
    }

    @Test
    void ordinalMatchesDeckOrder() {
        List<PlayingCard> deck = PlayingCard.fullDeck();
        for (int i = 0; i < 52; i++) {
            assertThat(deck.get(i).ordinal()).isEqualTo(i);
        }
    }

    @Test
    void fromOrdinalRejectsInvalid() {
        assertThatThrownBy(() -> PlayingCard.fromOrdinal(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlayingCard.fromOrdinal(52))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void labelFormat() {
        PlayingCard aceOfSpades = new PlayingCard(PlayingCard.Suit.SPADES, PlayingCard.Rank.ACE);
        assertThat(aceOfSpades.label()).isEqualTo("A\u2660");

        PlayingCard tenOfClubs = new PlayingCard(PlayingCard.Suit.CLUBS, PlayingCard.Rank.TEN);
        assertThat(tenOfClubs.label()).isEqualTo("10\u2663");

        PlayingCard kingOfHearts = new PlayingCard(PlayingCard.Suit.HEARTS, PlayingCard.Rank.KING);
        assertThat(kingOfHearts.label()).isEqualTo("K\u2665");
    }

    @Test
    void colorCategories() {
        PlayingCard heart = new PlayingCard(PlayingCard.Suit.HEARTS, PlayingCard.Rank.ACE);
        PlayingCard diamond = new PlayingCard(PlayingCard.Suit.DIAMONDS, PlayingCard.Rank.ACE);
        PlayingCard club = new PlayingCard(PlayingCard.Suit.CLUBS, PlayingCard.Rank.ACE);
        PlayingCard spade = new PlayingCard(PlayingCard.Suit.SPADES, PlayingCard.Rank.ACE);

        assertThat(heart.colorCategory()).isEqualTo("red");
        assertThat(diamond.colorCategory()).isEqualTo("red");
        assertThat(club.colorCategory()).isEqualTo("black");
        assertThat(spade.colorCategory()).isEqualTo("black");
    }

    @Test
    void comparableOrdersBySuitThenRank() {
        PlayingCard twoClubs = new PlayingCard(PlayingCard.Suit.CLUBS, PlayingCard.Rank.TWO);
        PlayingCard aceClubs = new PlayingCard(PlayingCard.Suit.CLUBS, PlayingCard.Rank.ACE);
        PlayingCard twoSpades = new PlayingCard(PlayingCard.Suit.SPADES, PlayingCard.Rank.TWO);

        assertThat(twoClubs).isLessThan(aceClubs);
        assertThat(twoClubs).isLessThan(twoSpades);
        assertThat(aceClubs).isLessThan(twoSpades);
    }

    @Test
    void equalityByValue() {
        PlayingCard a = new PlayingCard(PlayingCard.Suit.HEARTS, PlayingCard.Rank.QUEEN);
        PlayingCard b = new PlayingCard(PlayingCard.Suit.HEARTS, PlayingCard.Rank.QUEEN);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void allSuitsPresent() {
        List<PlayingCard> deck = PlayingCard.fullDeck();
        Set<PlayingCard.Suit> suits = new HashSet<>();
        for (PlayingCard card : deck) {
            suits.add(card.suit());
        }
        assertThat(suits).containsExactlyInAnyOrder(PlayingCard.Suit.values());
    }

    @Test
    void allRanksPresent() {
        List<PlayingCard> deck = PlayingCard.fullDeck();
        Set<PlayingCard.Rank> ranks = new HashSet<>();
        for (PlayingCard card : deck) {
            ranks.add(card.rank());
        }
        assertThat(ranks).containsExactlyInAnyOrder(PlayingCard.Rank.values());
    }

    @Test
    void rankValues() {
        assertThat(PlayingCard.Rank.TWO.value()).isEqualTo(2);
        assertThat(PlayingCard.Rank.TEN.value()).isEqualTo(10);
        assertThat(PlayingCard.Rank.JACK.value()).isEqualTo(11);
        assertThat(PlayingCard.Rank.QUEEN.value()).isEqualTo(12);
        assertThat(PlayingCard.Rank.KING.value()).isEqualTo(13);
        assertThat(PlayingCard.Rank.ACE.value()).isEqualTo(14);
    }

    // ---- Unicode Playing Card Characters ----

    @Test
    void unicodeAceOfSpades() {
        PlayingCard card = new PlayingCard(PlayingCard.Suit.SPADES, PlayingCard.Rank.ACE);
        assertThat(card.unicode()).isEqualTo("\uD83C\uDCA1"); // U+1F0A1
    }

    @Test
    void unicodeKingOfHearts() {
        PlayingCard card = new PlayingCard(PlayingCard.Suit.HEARTS, PlayingCard.Rank.KING);
        assertThat(card.unicode()).isEqualTo(Character.toString(0x1F0BE)); // U+1F0BE
    }

    @Test
    void unicodeQueenOfDiamonds() {
        PlayingCard card = new PlayingCard(PlayingCard.Suit.DIAMONDS, PlayingCard.Rank.QUEEN);
        assertThat(card.unicode()).isEqualTo(Character.toString(0x1F0CD)); // U+1F0CD, skips Knight at +12
    }

    @Test
    void unicodeTenOfClubs() {
        PlayingCard card = new PlayingCard(PlayingCard.Suit.CLUBS, PlayingCard.Rank.TEN);
        assertThat(card.unicode()).isEqualTo(Character.toString(0x1F0DA)); // U+1F0DA
    }

    @Test
    void unicodeAllCardsUnique() {
        Set<String> unicodes = new HashSet<>();
        for (PlayingCard card : PlayingCard.fullDeck()) {
            String u = card.unicode();
            assertThat(u).isNotEmpty();
            assertThat(u.codePointCount(0, u.length())).isEqualTo(1);
            unicodes.add(u);
        }
        assertThat(unicodes).hasSize(52);
    }

    @Test
    void unicodeSuitBases() {
        // Aces should be at base+1 for each suit
        assertThat(codePoint(PlayingCard.Suit.SPADES, PlayingCard.Rank.ACE)).isEqualTo(0x1F0A1);
        assertThat(codePoint(PlayingCard.Suit.HEARTS, PlayingCard.Rank.ACE)).isEqualTo(0x1F0B1);
        assertThat(codePoint(PlayingCard.Suit.DIAMONDS, PlayingCard.Rank.ACE)).isEqualTo(0x1F0C1);
        assertThat(codePoint(PlayingCard.Suit.CLUBS, PlayingCard.Rank.ACE)).isEqualTo(0x1F0D1);
    }

    @Test
    void unicodeSkipsKnight() {
        // Jack = offset 11, Queen = offset 13 (Knight at 12 skipped), King = offset 14
        assertThat(codePoint(PlayingCard.Suit.SPADES, PlayingCard.Rank.JACK)).isEqualTo(0x1F0AB);
        assertThat(codePoint(PlayingCard.Suit.SPADES, PlayingCard.Rank.QUEEN)).isEqualTo(0x1F0AD);
        assertThat(codePoint(PlayingCard.Suit.SPADES, PlayingCard.Rank.KING)).isEqualTo(0x1F0AE);
    }

    // ---- SVG Resource Paths ----

    @Test
    void svgPathFaceCards() {
        assertThat(card(PlayingCard.Suit.HEARTS, PlayingCard.Rank.ACE).svgPath())
                .isEqualTo("cards/fronts/hearts_ace.svg");
        assertThat(card(PlayingCard.Suit.SPADES, PlayingCard.Rank.JACK).svgPath())
                .isEqualTo("cards/fronts/spades_jack.svg");
        assertThat(card(PlayingCard.Suit.DIAMONDS, PlayingCard.Rank.QUEEN).svgPath())
                .isEqualTo("cards/fronts/diamonds_queen.svg");
        assertThat(card(PlayingCard.Suit.CLUBS, PlayingCard.Rank.KING).svgPath())
                .isEqualTo("cards/fronts/clubs_king.svg");
    }

    @Test
    void svgPathPipCards() {
        assertThat(card(PlayingCard.Suit.HEARTS, PlayingCard.Rank.TWO).svgPath())
                .isEqualTo("cards/fronts/hearts_2.svg");
        assertThat(card(PlayingCard.Suit.SPADES, PlayingCard.Rank.TEN).svgPath())
                .isEqualTo("cards/fronts/spades_10.svg");
        assertThat(card(PlayingCard.Suit.DIAMONDS, PlayingCard.Rank.SEVEN).svgPath())
                .isEqualTo("cards/fronts/diamonds_7.svg");
    }

    @Test
    void svgPathAllCardsUnique() {
        Set<String> paths = new HashSet<>();
        for (PlayingCard card : PlayingCard.fullDeck()) {
            paths.add(card.svgPath());
        }
        assertThat(paths).hasSize(52);
    }

    @Test
    void backImageKey() {
        PlayingCard card = new PlayingCard(PlayingCard.Suit.HEARTS, PlayingCard.Rank.ACE);
        assertThat(card.backImageKey()).isEqualTo("cards/backs/blue.svg");
    }

    // ---- Piece Interface Contract ----

    @Test
    void implementsPiece() {
        PlayingCard card = new PlayingCard(PlayingCard.Suit.SPADES, PlayingCard.Rank.ACE);
        assertThat(card).isInstanceOf(Piece.class);
    }

    @Test
    void pieceSymbolReturnsUnicode() {
        PlayingCard card = new PlayingCard(PlayingCard.Suit.SPADES, PlayingCard.Rank.ACE);
        assertThat(card.symbol()).isEqualTo(card.unicode());
    }

    @Test
    void pieceImageKeyReturnsSvgPath() {
        PlayingCard card = new PlayingCard(PlayingCard.Suit.HEARTS, PlayingCard.Rank.KING);
        assertThat(card.imageKey()).isEqualTo("cards/fronts/hearts_king.svg");
    }

    @Test
    void pieceModelKeyEmpty() {
        PlayingCard card = new PlayingCard(PlayingCard.Suit.CLUBS, PlayingCard.Rank.TWO);
        assertThat(card.modelKey()).isEmpty();
    }

    @Test
    void pieceModelColorDefault() {
        PlayingCard card = new PlayingCard(PlayingCard.Suit.CLUBS, PlayingCard.Rank.TWO);
        assertThat(card.modelColor()).isEqualTo(-1);
    }

    @Test
    void pieceIsNotEmpty() {
        PlayingCard card = new PlayingCard(PlayingCard.Suit.CLUBS, PlayingCard.Rank.TWO);
        assertThat(card.isEmpty()).isFalse();
    }

    // ---- Rank fileKey ----

    @Test
    void rankFileKeyFaceCards() {
        assertThat(PlayingCard.Rank.ACE.fileKey()).isEqualTo("ace");
        assertThat(PlayingCard.Rank.JACK.fileKey()).isEqualTo("jack");
        assertThat(PlayingCard.Rank.QUEEN.fileKey()).isEqualTo("queen");
        assertThat(PlayingCard.Rank.KING.fileKey()).isEqualTo("king");
    }

    @Test
    void rankFileKeyPipCards() {
        assertThat(PlayingCard.Rank.TWO.fileKey()).isEqualTo("2");
        assertThat(PlayingCard.Rank.FIVE.fileKey()).isEqualTo("5");
        assertThat(PlayingCard.Rank.TEN.fileKey()).isEqualTo("10");
    }

    // ---- toString uses label ----

    @Test
    void toStringUsesLabel() {
        PlayingCard card = new PlayingCard(PlayingCard.Suit.SPADES, PlayingCard.Rank.ACE);
        assertThat(card.toString()).isEqualTo("A\u2660");
        assertThat(card.toString()).isEqualTo(card.label());
    }

    // ---- Helpers ----

    private static PlayingCard card(PlayingCard.Suit suit, PlayingCard.Rank rank) {
        return new PlayingCard(suit, rank);
    }

    private static int codePoint(PlayingCard.Suit suit, PlayingCard.Rank rank) {
        String u = new PlayingCard(suit, rank).unicode();
        return u.codePointAt(0);
    }
}
