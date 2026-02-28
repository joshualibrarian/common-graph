package dev.everydaythings.graph.game.set;

/**
 * The four properties of a Set card, each with three possible values.
 *
 * <p>3^4 = 81 unique cards in a full deck.
 *
 * @see SetCard
 */
public final class SetProperty {

    public enum Count { ONE, TWO, THREE }

    public enum Shape { DIAMOND, OVAL, SQUIGGLE }

    public enum Shading { SOLID, STRIPED, EMPTY }

    public enum Color { RED, GREEN, PURPLE }

    private SetProperty() {}
}
