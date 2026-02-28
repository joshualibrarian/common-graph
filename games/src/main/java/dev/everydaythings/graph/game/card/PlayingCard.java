package dev.everydaythings.graph.game.card;

import dev.everydaythings.graph.game.Piece;
import dev.everydaythings.graph.ui.scene.Scene;
import dev.everydaythings.graph.ui.scene.Scene.Direction;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A standard playing card from a 52-card deck.
 *
 * <p>Used by Poker, Spades, and any other game needing a standard deck.
 * Cards are immutable, comparable (by ordinal), and CBOR-encodable via ordinal (0-51).
 *
 * <p>Implements {@link Piece} for multi-fidelity rendering:
 * <ul>
 *   <li>Text: Unicode playing card character (U+1F0A1–U+1F0DE)</li>
 *   <li>2D: SVG card art via {@link #imageKey()}</li>
 *   <li>3D: Thin card body with SVG faces via {@code @Scene.Body} + {@code @Scene.Face}</li>
 * </ul>
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode
@Scene.Body(shape = "box", width = "63.5mm", height = "88.9mm", depth = "0.3mm",
            color = 0xFFFFFF, shading = "unlit")
@Scene.Container(direction = Direction.STACK, width = "100%", height = "100%", cornerRadius = "3mm")
public class PlayingCard implements Piece, Comparable<PlayingCard> {

    @Scene.Face("front")
    @Scene.Image(bind = "value", size = "fill", fit = "fill")
    static class Front {}

    @Scene.Face("back")
    @Scene.Image(image = "cards/backs/blue.svg", size = "fill", fit = "fill")
    static class Back {}

    @Scene.Image(bind = "value", size = "fill", fit = "contain")
    static class CardImage {}

    public enum Suit {
        CLUBS("\u2663", 0x1F0D0),
        DIAMONDS("\u2666", 0x1F0C0),
        HEARTS("\u2665", 0x1F0B0),
        SPADES("\u2660", 0x1F0A0);

        private final String symbol;
        private final int unicodeBase;

        Suit(String symbol, int unicodeBase) {
            this.symbol = symbol;
            this.unicodeBase = unicodeBase;
        }

        public String symbol() { return symbol; }
        public int unicodeBase() { return unicodeBase; }
    }

    public enum Rank {
        TWO("2", 2, 2),
        THREE("3", 3, 3),
        FOUR("4", 4, 4),
        FIVE("5", 5, 5),
        SIX("6", 6, 6),
        SEVEN("7", 7, 7),
        EIGHT("8", 8, 8),
        NINE("9", 9, 9),
        TEN("10", 10, 10),
        JACK("J", 11, 11),
        QUEEN("Q", 12, 13),   // offset 13: skips Knight at 12
        KING("K", 13, 14),
        ACE("A", 14, 1);

        private final String symbol;
        private final int value;
        private final int unicodeOffset;

        Rank(String symbol, int value, int unicodeOffset) {
            this.symbol = symbol;
            this.value = value;
            this.unicodeOffset = unicodeOffset;
        }

        public String symbol() { return symbol; }
        public int value() { return value; }
        public int unicodeOffset() { return unicodeOffset; }

        /**
         * File name component for SVG resources.
         * Face cards use lowercase name, pip cards use numeric value.
         */
        public String fileKey() {
            return switch (this) {
                case ACE   -> "ace";
                case JACK  -> "jack";
                case QUEEN -> "queen";
                case KING  -> "king";
                default    -> String.valueOf(value);
            };
        }
    }

    private final Suit suit;
    private final Rank rank;

    public PlayingCard(Suit suit, Rank rank) {
        this.suit = suit;
        this.rank = rank;
    }

    /**
     * Ordinal encoding for CBOR serialization (0-51).
     * Order: suit ordinal * 13 + rank ordinal.
     */
    public int ordinal() {
        return suit.ordinal() * 13 + rank.ordinal();
    }

    /**
     * Decode a card from its ordinal (0-51).
     */
    public static PlayingCard fromOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal > 51) {
            throw new IllegalArgumentException("Card ordinal must be 0-51, got: " + ordinal);
        }
        Suit suit = Suit.values()[ordinal / 13];
        Rank rank = Rank.values()[ordinal % 13];
        return new PlayingCard(suit, rank);
    }

    /**
     * Unicode Playing Cards block character (e.g., U+1F0A1 for A of Spades).
     */
    public String unicode() {
        return Character.toString(suit.unicodeBase() + rank.unicodeOffset());
    }

    /**
     * SVG resource path for 2D rendering.
     * Maps to Tek Eye naming: {@code cards/fronts/{suit}_{rank}.svg}.
     */
    public String svgPath() {
        return "cards/fronts/" + suit.name().toLowerCase() + "_" + rank.fileKey() + ".svg";
    }

    /**
     * Card back SVG resource path.
     */
    public String backImageKey() {
        return "cards/backs/blue.svg";
    }

    // ---- Piece interface ----

    @Override
    public String symbol() { return unicode(); }

    @Override
    public String imageKey() { return svgPath(); }

    @Override
    public String modelKey() { return ""; }

    @Override
    public String colorCategory() {
        return (suit == Suit.HEARTS || suit == Suit.DIAMONDS) ? "red" : "black";
    }

    @Override
    public int modelColor() { return -1; }

    /**
     * Human-readable label like "A\u2660", "K\u2665", "10\u2663".
     */
    public String label() {
        return rank.symbol() + suit.symbol();
    }

    /**
     * Generate a full 52-card deck in standard order.
     */
    public static List<PlayingCard> fullDeck() {
        List<PlayingCard> deck = new ArrayList<>(52);
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                deck.add(new PlayingCard(suit, rank));
            }
        }
        return Collections.unmodifiableList(deck);
    }

    @Override
    public int compareTo(PlayingCard other) {
        int suitCmp = this.suit.compareTo(other.suit);
        if (suitCmp != 0) return suitCmp;
        return this.rank.compareTo(other.rank);
    }

    @Override
    public String toString() {
        return label();
    }
}
