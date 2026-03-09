package dev.everydaythings.graph.ui.scene.surface;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.expression.CompletionEntry;
import dev.everydaythings.graph.expression.EvalInputSnapshot;
import dev.everydaythings.graph.expression.ExpressionToken;
import dev.everydaythings.graph.expression.ExpressionToken.RefToken;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.ui.scene.BoxBorder;
import dev.everydaythings.graph.ui.scene.Scene;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

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
 * ┌─────────────────────────────────────────────────────┐
 * │ 📚 Librarian> [🎯 create] [♟ chess] pending_     │  ← input row
 * ├─────────────────────────────────────────────────────┤
 * │ ▸ completion1                                       │  ← completions (if visible)
 * │   completion2                                       │
 * │   completion3                                       │
 * └─────────────────────────────────────────────────────┘
 * </pre>
 */
@Getter
@Scene.Rule(match = ".prompt", color = "#89B4FA")
@Scene.Rule(match = ".completion-selected", background = "#313244")
@Scene.Rule(match = ".completion-indicator", color = "#89B4FA", fontWeight = "bold")
@Scene.Rule(match = ".error", color = "#F38BA8")
public class InputSurface extends SurfaceSchema<Void> {

    /**
     * A token chip — carries display info for one committed token in the input.
     *
     * <p>Resolved tokens (RefToken) render as handle-like pills with emoji + label.
     * Unresolved tokens (literals, operators, parens) render as plain text.
     */
    public record TokenChip(
            String text,
            String emoji,
            boolean resolved
    ) implements Canonical {
        public TokenChip {
            if (text == null) text = "";
        }
    }

    /** The prompt text (e.g., "📚 Librarian> "). */
    @Canon(order = 20)
    private String prompt;

    /** Token chips with display metadata. */
    @Canon(order = 21)
    private List<TokenChip> chips;

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
        this.chips = List.of();
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
     * Populate from an EvalInputSnapshot (no emoji resolution).
     */
    public static InputSurface fromSnapshot(EvalInputSnapshot snapshot) {
        return fromSnapshot(snapshot, null);
    }

    /**
     * Populate from an EvalInputSnapshot with item resolution for emoji.
     *
     * @param snapshot the input state snapshot
     * @param resolver optional item resolver to look up emoji for RefTokens
     */
    public static InputSurface fromSnapshot(EvalInputSnapshot snapshot,
                                            Function<ItemID, Optional<Item>> resolver) {
        InputSurface s = new InputSurface();
        s.prompt = snapshot.prompt();
        s.hint = snapshot.hint();
        s.error = snapshot.error();
        s.pendingText = snapshot.pendingText() != null ? snapshot.pendingText() : "";
        s.cursor = snapshot.cursor();
        s.showCompletions = snapshot.showCompletions();
        s.selectedCompletion = snapshot.selectedCompletion();

        // Build token chips with resolution info
        List<TokenChip> chipList = new ArrayList<>();
        for (ExpressionToken token : snapshot.tokens()) {
            if (token instanceof RefToken ref && resolver != null) {
                String emoji = null;
                try {
                    Optional<Item> item = resolver.apply(ref.target());
                    if (item.isPresent()) {
                        emoji = item.get().emoji();
                    }
                } catch (Exception ignored) {}
                chipList.add(new TokenChip(ref.displayText(), emoji, true));
            } else if (token instanceof RefToken ref) {
                chipList.add(new TokenChip(ref.displayText(), null, true));
            } else {
                chipList.add(new TokenChip(token.displayText(), null, false));
            }
        }
        s.chips = chipList;

        // Use enriched completion entries
        s.completions = snapshot.completionEntries();

        return s;
    }

    /**
     * Get display texts for all chips (backwards-compatible).
     */
    public List<String> tokenTexts() {
        return chips.stream().map(TokenChip::text).toList();
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
     * Render the main input row: prompt + token chips + pending text.
     */
    private void renderInputRow(SurfaceRenderer out) {
        out.gap("0.25em");
        out.beginBox(Scene.Direction.HORIZONTAL, List.of("input-row"));

        // Prompt
        if (prompt != null && !prompt.isEmpty()) {
            out.text(prompt, List.of("prompt"));
        }

        // Token chips
        for (TokenChip chip : chips) {
            if (chip.resolved()) {
                // Resolved token: render as a handle-like pill with border-radius and background
                out.gap("0.3em");
                out.beginBox(Scene.Direction.HORIZONTAL,
                        List.of("token-chip", "resolved"),
                        BoxBorder.parse("0.1em solid #4A5568", "0.6em"),
                        "#2D3748", null, null, "0.1em 0.4em");
                if (chip.emoji() != null && !chip.emoji().isEmpty()) {
                    out.text(chip.emoji(), List.of("token-emoji"));
                }
                out.text(chip.text(), List.of("token"));
                out.endBox();
            } else {
                // Unresolved token: plain text (operators, parens, literals)
                out.beginBox(Scene.Direction.HORIZONTAL, List.of("token-chip"));
                out.text(chip.text(), List.of("token"));
                out.endBox();
            }
        }

        // Pending text with cursor, or hint
        boolean hasContent = !chips.isEmpty() || (pendingText != null && !pendingText.isEmpty());
        if (pendingText != null && !pendingText.isEmpty()) {
            out.editable(true);
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
            // Selection indicator: ▸ for selected, space for others
            boolean selected = (i == selectedCompletion);
            out.text(selected ? "▸ " : "  ", List.of(selected ? "completion-indicator" : "completion-spacer"));
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
        if (text == null || text.isEmpty()) return "\u258F";
        int clampedPos = Math.max(0, Math.min(pos, text.length()));
        if (clampedPos >= text.length()) {
            return text + "\u258F";
        }
        return text.substring(0, clampedPos) + "\u258F" + text.substring(clampedPos);
    }
}
