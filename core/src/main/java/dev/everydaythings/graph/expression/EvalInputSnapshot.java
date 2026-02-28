package dev.everydaythings.graph.expression;

import dev.everydaythings.graph.language.Posting;

import java.util.List;
import java.util.Optional;

/**
 * Immutable snapshot of the expression input state.
 *
 * <p>This is what renderers consume. Every mutation of {@link EvalInput}
 * produces a new snapshot — renderers never see mutable state.
 *
 * <p>Contains everything a renderer needs to display the input field:
 * resolved token chips, pending text with cursor, completion dropdown,
 * prompt text, and any error/hint messages.
 *
 * @param tokens             resolved tokens (chips) built so far
 * @param pendingText        unresolved text being typed
 * @param cursor             cursor position within pendingText (0 = before first char)
 * @param completions        available completions for current lookup (for acceptance logic)
 * @param completionEntries  enriched completions for display (emoji + type name)
 * @param selectedCompletion index of highlighted completion (-1 = none)
 * @param showCompletions    whether the completion dropdown should be visible
 * @param prompt             prompt text (from focused item)
 * @param hint               hint text (shown when input is empty)
 * @param error              error message (null = no error)
 */
public record EvalInputSnapshot(
        List<ExpressionToken> tokens,
        String pendingText,
        int cursor,
        List<Posting> completions,
        List<CompletionEntry> completionEntries,
        int selectedCompletion,
        boolean showCompletions,
        String prompt,
        String hint,
        String error
) {

    /**
     * Compact constructor — defensively copy lists.
     */
    public EvalInputSnapshot {
        tokens = List.copyOf(tokens);
        completions = List.copyOf(completions);
        completionEntries = List.copyOf(completionEntries);
    }

    /**
     * Whether the input has any content (tokens or pending text).
     */
    public boolean hasInput() {
        return !tokens.isEmpty() || (pendingText != null && !pendingText.isEmpty());
    }

    /**
     * Whether completions should be shown in the UI.
     */
    public boolean hasVisibleCompletions() {
        return showCompletions && !completions.isEmpty();
    }

    /**
     * Get the currently selected completion, if any.
     */
    public Optional<Posting> currentCompletion() {
        if (selectedCompletion >= 0 && selectedCompletion < completions.size()) {
            return Optional.of(completions.get(selectedCompletion));
        }
        return Optional.empty();
    }

    /**
     * Build the full display text (all token display texts + pending).
     */
    public String displayText() {
        var sb = new StringBuilder();
        for (ExpressionToken token : tokens) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(token.displayText());
        }
        if (pendingText != null && !pendingText.isEmpty()) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(pendingText);
        }
        return sb.toString();
    }

    /**
     * Create an empty snapshot with the given prompt.
     */
    public static EvalInputSnapshot empty(String prompt, String hint) {
        return new EvalInputSnapshot(
                List.of(), "", 0, List.of(), List.of(), -1, false,
                prompt, hint, null
        );
    }
}
