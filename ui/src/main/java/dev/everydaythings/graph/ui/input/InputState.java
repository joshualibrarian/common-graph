package dev.everydaythings.graph.ui.input;

import dev.everydaythings.graph.expression.ExpressionToken;
import dev.everydaythings.graph.language.Posting;

import java.util.List;

/**
 * Immutable snapshot of input state for rendering.
 *
 * <p>This is what renderers see - a complete picture of what to display.
 * The {@link InputController} produces these; renderers consume them.
 *
 * <p>Renderers should be able to fully render the input from this state alone,
 * without needing access to the controller or any mutable state.
 *
 * @param tokens Resolved tokens (chips) in order
 * @param pendingText Text being typed (not yet resolved)
 * @param cursorPosition Cursor position within pending text (0 = before first char)
 * @param completions Available completions for current input
 * @param selectedCompletion Index of selected completion (-1 = none)
 * @param showCompletions Whether to display completion list
 * @param prompt Optional prompt string (e.g., "> " or "search: ")
 * @param hint Optional hint text (e.g., "Type to search...")
 * @param error Optional error message
 */
public record InputState(
        List<ExpressionToken> tokens,
        String pendingText,
        int cursorPosition,
        List<Posting> completions,
        int selectedCompletion,
        boolean showCompletions,
        String prompt,
        String hint,
        String error
) {

    /**
     * Create an empty input state.
     */
    public static InputState empty() {
        return new InputState(
                List.of(),
                "",
                0,
                List.of(),
                -1,
                false,
                "> ",
                null,
                null
        );
    }

    /**
     * Create a state with just a prompt.
     */
    public static InputState forPrompt(String prompt) {
        return new InputState(
                List.of(),
                "",
                0,
                List.of(),
                -1,
                false,
                prompt,
                null,
                null
        );
    }

    // ==================================================================================
    // Builder-style modifiers (return new instances)
    // ==================================================================================

    public InputState withTokens(List<ExpressionToken> tokens) {
        return new InputState(tokens, pendingText, cursorPosition, completions,
                selectedCompletion, showCompletions, prompt, hint, error);
    }

    public InputState withPendingText(String pendingText) {
        return new InputState(tokens, pendingText, cursorPosition, completions,
                selectedCompletion, showCompletions, prompt, hint, error);
    }

    public InputState withCursorPosition(int cursorPosition) {
        return new InputState(tokens, pendingText, cursorPosition, completions,
                selectedCompletion, showCompletions, prompt, hint, error);
    }

    public InputState withCompletions(List<Posting> completions) {
        return new InputState(tokens, pendingText, cursorPosition, completions,
                selectedCompletion, showCompletions, prompt, hint, error);
    }

    public InputState withSelectedCompletion(int selectedCompletion) {
        return new InputState(tokens, pendingText, cursorPosition, completions,
                selectedCompletion, showCompletions, prompt, hint, error);
    }

    public InputState withShowCompletions(boolean showCompletions) {
        return new InputState(tokens, pendingText, cursorPosition, completions,
                selectedCompletion, showCompletions, prompt, hint, error);
    }

    public InputState withPrompt(String prompt) {
        return new InputState(tokens, pendingText, cursorPosition, completions,
                selectedCompletion, showCompletions, prompt, hint, error);
    }

    public InputState withHint(String hint) {
        return new InputState(tokens, pendingText, cursorPosition, completions,
                selectedCompletion, showCompletions, prompt, hint, error);
    }

    public InputState withError(String error) {
        return new InputState(tokens, pendingText, cursorPosition, completions,
                selectedCompletion, showCompletions, prompt, hint, error);
    }

    // ==================================================================================
    // Query methods
    // ==================================================================================

    /**
     * Check if there's any input (tokens or pending text).
     */
    public boolean hasInput() {
        return !tokens.isEmpty() || !pendingText.isEmpty();
    }

    /**
     * Check if completions are available and visible.
     */
    public boolean hasVisibleCompletions() {
        return showCompletions && !completions.isEmpty();
    }

    /**
     * Get the currently selected completion, if any.
     */
    public Posting currentCompletion() {
        if (selectedCompletion >= 0 && selectedCompletion < completions.size()) {
            return completions.get(selectedCompletion);
        }
        return null;
    }

    /**
     * Get display text for the full input (tokens + pending).
     */
    public String displayText() {
        StringBuilder sb = new StringBuilder();
        for (ExpressionToken token : tokens) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(token.displayText());
        }
        if (!pendingText.isEmpty()) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(pendingText);
        }
        return sb.toString();
    }
}
