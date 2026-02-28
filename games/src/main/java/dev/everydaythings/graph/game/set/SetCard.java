package dev.everydaythings.graph.game.set;

import dev.everydaythings.graph.game.set.SetProperty.*;

import java.util.ArrayList;
import java.util.List;

/**
 * A card in the Set card game.
 *
 * <p>Each card has 4 properties, each with 3 possible values,
 * giving 3^4 = 81 unique cards in a full deck.
 *
 * <p>A "set" is 3 cards where, for each property, the values are
 * either all the same or all different across the 3 cards.
 *
 * @see SetProperty
 * @see SetGame
 */
public record SetCard(Count count, Shape shape, Shading shading, Color color) {

    /**
     * Unique ordinal encoding (0-80) for CBOR serialization.
     *
     * <p>Encoding: count*27 + shape*9 + shading*3 + color
     */
    public int ordinal() {
        return count.ordinal() * 27 + shape.ordinal() * 9
                + shading.ordinal() * 3 + color.ordinal();
    }

    /**
     * Decode a card from its ordinal.
     */
    public static SetCard fromOrdinal(int ord) {
        if (ord < 0 || ord > 80) {
            throw new IllegalArgumentException("Ordinal must be 0-80, got: " + ord);
        }
        Color c = Color.values()[ord % 3]; ord /= 3;
        Shading sh = Shading.values()[ord % 3]; ord /= 3;
        Shape s = Shape.values()[ord % 3]; ord /= 3;
        Count cn = Count.values()[ord];
        return new SetCard(cn, s, sh, c);
    }

    /**
     * Generate the full 81-card deck (unshuffled).
     */
    public static List<SetCard> fullDeck() {
        List<SetCard> deck = new ArrayList<>(81);
        for (Count cn : Count.values())
            for (Shape s : Shape.values())
                for (Shading sh : Shading.values())
                    for (Color c : Color.values())
                        deck.add(new SetCard(cn, s, sh, c));
        return deck;
    }

    /**
     * Check if three cards form a valid set.
     *
     * <p>For each property, the three values must be either all the same
     * or all different. Uses the mod-3 trick: for values in {0, 1, 2},
     * (a + b + c) % 3 == 0 iff all-same or all-different.
     */
    public static boolean isValidSet(SetCard a, SetCard b, SetCard c) {
        return (a.count.ordinal() + b.count.ordinal() + c.count.ordinal()) % 3 == 0
                && (a.shape.ordinal() + b.shape.ordinal() + c.shape.ordinal()) % 3 == 0
                && (a.shading.ordinal() + b.shading.ordinal() + c.shading.ordinal()) % 3 == 0
                && (a.color.ordinal() + b.color.ordinal() + c.color.ordinal()) % 3 == 0;
    }

    /**
     * Unicode symbol for text rendering.
     *
     * <p>Renders as count × shape character with a shading modifier.
     * Example: three solid red diamonds → "♦♦♦"
     */
    public String symbol() {
        String shapeChar = switch (shape) {
            case DIAMOND -> "\u2666";  // ♦
            case OVAL -> "\u25CF";      // ●
            case SQUIGGLE -> "\u223F";  // ∿
        };
        String base = switch (shading) {
            case SOLID -> shapeChar;
            case STRIPED -> shapeChar + "\u0336";  // combining long stroke overlay
            case EMPTY -> "\u25C7\u25CB\u2242".substring(
                    shape.ordinal(), shape.ordinal() + 1);  // outline variants: ◇ ○ ≂
        };
        int n = count.ordinal() + 1;
        return base.repeat(n);
    }

    /**
     * Color name for CSS/material selection.
     */
    public String colorName() {
        return color.name().toLowerCase();
    }

    @Override
    public String toString() {
        int n = count.ordinal() + 1;
        return n + " " + shading.name().toLowerCase() + " " + color.name().toLowerCase()
                + " " + shape.name().toLowerCase() + (n > 1 ? "s" : "");
    }
}
