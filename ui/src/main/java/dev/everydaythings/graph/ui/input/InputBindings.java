package dev.everydaythings.graph.ui.input;

import java.util.Optional;

/**
 * Maps key chords to input actions.
 *
 * <p>This is the input equivalent of {@link KeyBindings} for navigation.
 * It translates physical key events (represented as {@link KeyChord})
 * to logical {@link InputAction}s.
 *
 * <p>Different rendering contexts might have different bindings:
 * <ul>
 *   <li>GUI might use Ctrl+Backspace for delete word</li>
 *   <li>TUI might use Alt+Backspace</li>
 *   <li>CLI might use readline-style bindings</li>
 * </ul>
 */
public interface InputBindings {

    /**
     * Resolve a key chord to an input action.
     *
     * @param chord The key chord
     * @param hasCompletions Whether completions are currently visible
     * @return The input action, or empty if no binding
     */
    Optional<InputAction> resolve(KeyChord chord, boolean hasCompletions);

    /**
     * Default input bindings suitable for most contexts.
     */
    static InputBindings defaults() {
        return DefaultInputBindings.INSTANCE;
    }
}

/**
 * Default implementation of input bindings.
 */
class DefaultInputBindings implements InputBindings {

    static final DefaultInputBindings INSTANCE = new DefaultInputBindings();

    private DefaultInputBindings() {}

    @Override
    public Optional<InputAction> resolve(KeyChord chord, boolean hasCompletions) {
        // Handle special keys first
        if (chord.key() instanceof PhysicalKey.Special special) {
            return resolveSpecial(special.key(), chord, hasCompletions);
        }

        // Handle character keys
        if (chord.key() instanceof PhysicalKey.Char charKey) {
            return resolveChar(charKey.ch(), chord, hasCompletions);
        }

        return Optional.empty();
    }

    private Optional<InputAction> resolveSpecial(
            SpecialKey special,
            KeyChord chord,
            boolean hasCompletions) {

        return switch (special) {
            // Navigation
            case UP -> hasCompletions
                    ? Optional.of(InputAction.completionUp())
                    : Optional.of(InputAction.historyPrev());
            case DOWN -> hasCompletions
                    ? Optional.of(InputAction.completionDown())
                    : Optional.of(InputAction.historyNext());
            case LEFT -> Optional.of(InputAction.cursorLeft());
            case RIGHT -> Optional.of(InputAction.cursorRight());
            case HOME -> Optional.of(InputAction.cursorHome());
            case END -> Optional.of(InputAction.cursorEnd());

            // Editing
            case BACKSPACE -> chord.ctrl()
                    ? Optional.of(InputAction.deleteWord())
                    : Optional.of(InputAction.backspace());
            case DELETE -> Optional.of(InputAction.delete());

            // Completion
            case TAB -> Optional.of(InputAction.tab());
            case ENTER -> Optional.of(InputAction.accept());
            case ESCAPE -> Optional.of(InputAction.cancel());

            // Not handled here
            default -> Optional.empty();
        };
    }

    private Optional<InputAction> resolveChar(char c, KeyChord chord, boolean hasCompletions) {
        // Ctrl+W = delete word (readline style)
        if (chord.ctrl() && (c == 'w' || c == 'W')) {
            return Optional.of(InputAction.deleteWord());
        }

        // Ctrl+U = clear line (readline style)
        if (chord.ctrl() && (c == 'u' || c == 'U')) {
            return Optional.of(InputAction.cancel());
        }

        // Ctrl+A = home (readline style)
        if (chord.ctrl() && (c == 'a' || c == 'A')) {
            return Optional.of(InputAction.cursorHome());
        }

        // Ctrl+E = end (readline style)
        if (chord.ctrl() && (c == 'e' || c == 'E')) {
            return Optional.of(InputAction.cursorEnd());
        }

        // Ctrl+K = delete to end (not implemented yet, treat as delete)
        if (chord.ctrl() && (c == 'k' || c == 'K')) {
            return Optional.of(InputAction.delete());
        }

        // Space = token boundary (commit operators/literals)
        if (c == ' ' && !chord.ctrl() && !chord.alt()) {
            return Optional.of(InputAction.tokenBoundary());
        }

        // Regular character input
        if (!chord.ctrl() && !chord.alt() && isPrintable(c)) {
            return Optional.of(InputAction.char_(c));
        }

        return Optional.empty();
    }

    private boolean isPrintable(char c) {
        return c >= ' ' && c < 127 || Character.isLetterOrDigit(c);
    }
}
