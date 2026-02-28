package dev.everydaythings.graph.ui.input;

import org.jetbrains.annotations.NotNull;

/**
 * A physical key on the keyboard - either a character or a special key.
 *
 * <p>This is UI-agnostic; both GLFW key events and Lanterna KeyStrokes
 * can be mapped to this representation.
 */
public sealed interface PhysicalKey {

    /**
     * A printable character key.
     */
    record Char(char ch) implements PhysicalKey {
        @NotNull @Override
        public String toString() {
            return String.valueOf(ch);
        }
    }

    /**
     * A special (non-character) key.
     */
    record Special(SpecialKey key) implements PhysicalKey {
        @NotNull @Override
        public String toString() {
            return key.name();
        }
    }

    // Convenience factories
    static PhysicalKey of(char ch) {
        return new Char(ch);
    }

    static PhysicalKey of(SpecialKey key) {
        return new Special(key);
    }
}
