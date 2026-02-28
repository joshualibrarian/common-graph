package dev.everydaythings.graph.ui.scene.surface;

import dev.everydaythings.graph.expression.CompletionEntry;
import dev.everydaythings.graph.expression.EvalInputSnapshot;
import dev.everydaythings.graph.expression.ExpressionToken;
import dev.everydaythings.graph.ui.scene.Scene;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Input field surface — renders the prompt, tokens, pending text, and completions.
 *
 * <p>InputSurface is a first-class surface that represents the input field
 * at the bottom of the ItemModel layout. It renders using existing primitives
 * (text, box, editable) so all platform renderers handle it automatically.
 *
 * <p>State comes from {@link EvalInputSnapshot}, the immutable snapshot that
 * {@link dev.everydaythings.graph.expression.EvalInput} produces on every
 * state change.
 *
 * <h2>Visual Structure</h2>
 * <pre>
 * ┌─────────────────────────────────────────────┐
 * │ 📚 Librarian> [token1] [token2] pending_  │  ← input row
 * ├─────────────────────────────────────────────┤
 * │ ▸ completion1                               │  ← completions (if visible)
 * │   completion2                               │
 * │   completion3                               │
 * └─────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Empty input with prompt
 * InputSurface.empty("📚 Librarian> ", "Type to search...")
 *
 * // From EvalInput snapshot
 * InputSurface.fromSnapshot(evalInput.snapshot())
 * }</pre>
 */
@Getter
public class InputSurface extends SurfaceSchema<Void> {

    /** The prompt text (e.g., "📚 Librarian> "). */
    @Canon(order = 20)
    private String prompt;

    /** Display texts of resolved tokens (chips). */
    @Canon(order = 21)
    private List<String> tokenTexts;

    /** Unresolved text being typed. */
    @Canon(order = 22)
    private String pendingText;

    /** Cursor position within pendingText. */
    @Canon(order = 23)
    private int cursor;

    /** Hint text shown when input is empty. */
    @Canon(order = 24)
    private String hint;

    /** Error message, or null. */
    @Canon(order = 25)
    private String error;

    /** Enriched completion entries for display. */
    @Canon(order = 26)
    private List<CompletionEntry> completions;

    /** Index of highlighted completion (-1 = none). */
    @Canon(order = 27)
    private int selectedCompletion = -1;

    /** Whether the completion dropdown should be visible. */
    @Canon(order = 28)
    private boolean showCompletions;

    public InputSurface() {
        this.tokenTexts = List.of();
        this.completions = List.of();
        this.pendingText = "";
    }

    // ==================== Factory Methods ====================

    /**
     * Create an empty input with just a prompt and hint.
     */
    public static InputSurface empty(String prompt, String hint) {
        InputSurface s = new InputSurface();
        s.prompt = prompt;
        s.hint = hint;
        return s;
    }

    /**
     * Populate from an EvalInputSnapshot.
     */
    public static InputSurface fromSnapshot(EvalInputSnapshot snapshot) {
        InputSurface s = new InputSurface();
        s.prompt = snapshot.prompt();
        s.hint = snapshot.hint();
        s.error = snapshot.error();
        s.pendingText = snapshot.pendingText() != null ? snapshot.pendingText() : "";
        s.cursor = snapshot.cursor();
        s.showCompletions = snapshot.showCompletions();
        s.selectedCompletion = snapshot.selectedCompletion();

        // Extract token display texts
        List<String> tokens = new ArrayList<>();
        for (ExpressionToken token : snapshot.tokens()) {
            tokens.add(token.displayText());
        }
        s.tokenTexts = tokens;

        // Use enriched completion entries
        s.completions = snapshot.completionEntries();

        return s;
    }

    // ==================== Rendering ====================

    @Override
    public void render(SurfaceRenderer out) {
        emitCommonProperties(out);

        // Outer container: vertical (input row + optional completions)
        out.gap("0.25em");
        out.beginBox(Scene.Direction.VERTICAL, List.of("input-surface"));

        renderInputRow(out);

        if (error != null && !error.isEmpty()) {
            out.text(error, List.of("error"));
        }

        if (showCompletions && !completions.isEmpty()) {
            renderCompletions(out);
        }

        out.endBox();
    }

    /**
     * Render the main input row: prompt + tokens + pending text.
     */
    private void renderInputRow(SurfaceRenderer out) {
        out.gap("0.25em");
        out.beginBox(Scene.Direction.HORIZONTAL, List.of("input-row"));

        // Prompt
        if (prompt != null && !prompt.isEmpty()) {
            out.text(prompt, List.of("prompt"));
        }

        // Resolved token chips
        for (String tokenText : tokenTexts) {
            out.beginBox(Scene.Direction.HORIZONTAL, List.of("token-chip"));
            out.text(tokenText, List.of("token"));
            out.endBox();
        }

        // Pending text with cursor, or hint
        boolean hasContent = !tokenTexts.isEmpty() || (pendingText != null && !pendingText.isEmpty());
        if (pendingText != null && !pendingText.isEmpty()) {
            out.editable(true);
            // Insert cursor marker into text
            String display = insertCursor(pendingText, cursor);
            out.text(display, List.of("pending"));
        } else if (!hasContent && hint != null && !hint.isEmpty()) {
            out.text(hint, List.of("hint", "muted"));
        } else {
            // Empty pending — mark as editable; platform renders cursor
            out.editable(true);
        }

        out.endBox();
    }

    /**
     * Render the completions dropdown with enriched entries.
     */
    private void renderCompletions(SurfaceRenderer out) {
        out.gap("0.125em");
        out.beginBox(Scene.Direction.VERTICAL, List.of("completions"));

        for (int i = 0; i < completions.size(); i++) {
            CompletionEntry entry = completions.get(i);
            List<String> rowStyles = new ArrayList<>();
            rowStyles.add("completion");
            if (i == selectedCompletion) {
                rowStyles.add("completion-selected");
            }

            out.gap("0.5em");
            out.beginBox(Scene.Direction.HORIZONTAL, rowStyles);
            if (entry.emoji() != null && !entry.emoji().isEmpty()) {
                out.text(entry.emoji(), List.of("completion-emoji"));
            }
            out.text(entry.token(), List.of("completion-token"));
            if (entry.typeName() != null && !entry.typeName().isEmpty()) {
                out.text(entry.typeName(), List.of("completion-type", "muted"));
            }
            out.endBox();
        }

        out.endBox();
    }

    /**
     * Insert a visual cursor marker into text at the given position.
     */
    private static String insertCursor(String text, int pos) {
        if (text == null || text.isEmpty()) return "▏";
        int clampedPos = Math.max(0, Math.min(pos, text.length()));
        if (clampedPos >= text.length()) {
            return text + "▏";
        }
        return text.substring(0, clampedPos) + "▏" + text.substring(clampedPos);
    }
}
