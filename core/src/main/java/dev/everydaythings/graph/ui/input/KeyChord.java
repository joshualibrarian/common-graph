package dev.everydaythings.graph.ui.input;

/**
 * A key press with modifiers.
 *
 * <p>This is the UI-agnostic representation of a keyboard input event.
 * Both GLFW and Lanterna events can be translated to this form.
 */
public record KeyChord(
        PhysicalKey key,
        boolean ctrl,
        boolean alt,
        boolean shift
) {
    // Convenience factories for common patterns

    public static KeyChord of(SpecialKey key) {
        return new KeyChord(PhysicalKey.of(key), false, false, false);
    }

    public static KeyChord of(char ch) {
        return new KeyChord(PhysicalKey.of(ch), false, false, false);
    }

    public static KeyChord ctrl(SpecialKey key) {
        return new KeyChord(PhysicalKey.of(key), true, false, false);
    }

    public static KeyChord ctrl(char ch) {
        return new KeyChord(PhysicalKey.of(ch), true, false, false);
    }

    public static KeyChord alt(SpecialKey key) {
        return new KeyChord(PhysicalKey.of(key), false, true, false);
    }

    public static KeyChord alt(char ch) {
        return new KeyChord(PhysicalKey.of(ch), false, true, false);
    }

    public static KeyChord shift(SpecialKey key) {
        return new KeyChord(PhysicalKey.of(key), false, false, true);
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
