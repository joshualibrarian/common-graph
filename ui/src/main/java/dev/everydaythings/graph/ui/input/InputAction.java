package dev.everydaythings.graph.ui.input;

/**
 * Logical input actions for the unified input controller.
 *
 * <p>These are UI-agnostic actions that the InputController understands.
 * Physical key events (GLFW key events, Lanterna KeyStroke, ANSI escapes)
 * are translated to InputActions before being processed.
 *
 * <p>This is the input equivalent of {@link NavAction} for navigation.
 */
public sealed interface InputAction {

    // ==================================================================================
    // Text Input
    // ==================================================================================

    /**
     * Type a character.
     */
    record Char(char c) implements InputAction {}

    /**
     * Delete character before cursor (or last token if no pending text).
     */
    record Backspace() implements InputAction {}

    /**
     * Delete word before cursor.
     */
    record DeleteWord() implements InputAction {}

    /**
     * Delete character after cursor.
     */
    record Delete() implements InputAction {}

    // ==================================================================================
    // Cursor Movement
    // ==================================================================================

    /**
     * Move cursor left (within pending text or to previous token).
     */
    record CursorLeft() implements InputAction {}

    /**
     * Move cursor right (within pending text or to next token).
     */
    record CursorRight() implements InputAction {}

    /**
     * Move cursor to start of input.
     */
    record CursorHome() implements InputAction {}

    /**
     * Move cursor to end of input.
     */
    record CursorEnd() implements InputAction {}

    // ==================================================================================
    // Completion Navigation
    // ==================================================================================

    /**
     * Move selection up in completion list.
     */
    record CompletionUp() implements InputAction {}

    /**
     * Move selection down in completion list.
     */
    record CompletionDown() implements InputAction {}

    /**
     * Accept current completion (or complete input if no suggestions).
     */
    record Accept() implements InputAction {}

    /**
     * Request completion without accepting (show/cycle completions).
     */
    record Tab() implements InputAction {}

    // ==================================================================================
    // Control
    // ==================================================================================

    /**
     * Cancel current input / dismiss completions.
     */
    record Cancel() implements InputAction {}

    /**
     * Commit a token boundary (space key - may auto-complete operators/literals).
     */
    record TokenBoundary() implements InputAction {}

    // ==================================================================================
    // History
    // ==================================================================================

    /**
     * Navigate to previous history entry.
     */
    record HistoryPrev() implements InputAction {}

    /**
     * Navigate to next history entry.
     */
    record HistoryNext() implements InputAction {}

    // ==================================================================================
    // Factory methods for convenience
    // ==================================================================================

    static InputAction char_(char c) {
        return new Char(c);
    }

    static InputAction backspace() {
        return new Backspace();
    }

    static InputAction deleteWord() {
        return new DeleteWord();
    }

    static InputAction delete() {
        return new Delete();
    }

    static InputAction cursorLeft() {
        return new CursorLeft();
    }

    static InputAction cursorRight() {
        return new CursorRight();
    }

    static InputAction cursorHome() {
        return new CursorHome();
    }

    static InputAction cursorEnd() {
        return new CursorEnd();
    }

    static InputAction completionUp() {
        return new CompletionUp();
    }

    static InputAction completionDown() {
        return new CompletionDown();
    }

    static InputAction accept() {
        return new Accept();
    }

    static InputAction tab() {
        return new Tab();
    }

    static InputAction cancel() {
        return new Cancel();
    }

    static InputAction tokenBoundary() {
        return new TokenBoundary();
    }

    static InputAction historyPrev() {
        return new HistoryPrev();
    }

    static InputAction historyNext() {
        return new HistoryNext();
    }
}
