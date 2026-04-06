package dev.everydaythings.graph.ui.input;

import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * A key press with modifiers.
 *
 * <p>This is the UI-agnostic representation of a keyboard input event.
 * Both GLFW and Lanterna events can be translated to this form.
 */
@Getter
@Accessors(fluent = true)
public class KeyChord {

    private PhysicalKey key;
    private boolean ctrl;
    private boolean alt;
    private boolean shift;

    public KeyChord() {}

    public KeyChord key(PhysicalKey key) {
        this.key = key;
        return this;
    }

    public KeyChord ctrl(boolean ctrl) {
        this.ctrl = ctrl;
        return this;
    }

    public KeyChord alt(boolean alt) {
        this.alt = alt;
        return this;
    }

    public KeyChord shift(boolean shift) {
        this.shift = shift;
        return this;
    }

    // Convenience factories for common patterns

    public static KeyChord of(SpecialKey key) {
        return new KeyChord()
                .key(PhysicalKey.of(key))
                .ctrl(false)
                .alt(false)
                .shift(false);
    }

    public static KeyChord of(char ch) {
        return new KeyChord()
                .key(PhysicalKey.of(ch))
                .ctrl(false)
                .alt(false)
                .shift(false);
    }

    public static KeyChord ctrl(SpecialKey key) {
        return new KeyChord()
                .key(PhysicalKey.of(key))
                .ctrl(true)
                .alt(false)
                .shift(false);
    }

    public static KeyChord ctrl(char ch) {
        return new KeyChord()
                .key(PhysicalKey.of(ch))
                .ctrl(true)
                .alt(false)
                .shift(false);
    }

    public static KeyChord alt(SpecialKey key) {
        return new KeyChord()
                .key(PhysicalKey.of(key))
                .ctrl(false)
                .alt(true)
                .shift(false);
    }

    public static KeyChord alt(char ch) {
        return new KeyChord()
                .key(PhysicalKey.of(ch))
                .ctrl(false)
                .alt(true)
                .shift(false);
    }

    public static KeyChord shift(SpecialKey key) {
        return new KeyChord()
                .key(PhysicalKey.of(key))
                .ctrl(false)
                .alt(false)
                .shift(true);
    }

    /**
     * Check if this chord has any modifiers.
     */
    public boolean hasModifiers() {
        return ctrl || alt || shift;
    }

    /**
     * Check if this is a plain (unmodified) key.
     */
    public boolean isPlain() {
        return !hasModifiers();
    }

    /**
     * Check if this is a specific special key (with or without modifiers).
     */
    public boolean isKey(SpecialKey special) {
        return key instanceof PhysicalKey.Special s && s.key() == special;
    }

    /**
     * Check if this is a specific character (with or without modifiers).
     */
    public boolean isChar(char ch) {
        return key instanceof PhysicalKey.Char c && c.ch() == ch;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (ctrl) sb.append("Ctrl+");
        if (alt) sb.append("Alt+");
        if (shift) sb.append("Shift+");
        sb.append(key);
        return sb.toString();
    }
}
