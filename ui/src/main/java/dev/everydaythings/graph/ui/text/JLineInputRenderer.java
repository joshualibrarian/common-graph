package dev.everydaythings.graph.ui.text;

import dev.everydaythings.graph.parse.CompletionEntry;
import dev.everydaythings.graph.parse.EvalInputSnapshot;
import dev.everydaythings.graph.parse.ExpressionToken;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.ui.input.InputRenderer;
import dev.everydaythings.graph.ui.input.InputResult;
import dev.everydaythings.graph.ui.input.InputState;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.io.PrintWriter;
import java.util.List;

/**
 * Terminal-based input renderer using JLine.
 *
 * <p>Renders the input state to a terminal with ANSI styling:
 * <ul>
 *   <li>Prompt in cyan</li>
 *   <li>Resolved tokens as colored "chips"</li>
 *   <li>Pending text with cursor indicator</li>
 *   <li>Completions list below</li>
 * </ul>
 *
 * <p>Layout:
 * <pre>
 * prompt> [token1] [token2] pending_text|
 *   → completion1
 *     completion2
 *     completion3
 * </pre>
 *
 * <p>Supports both {@link EvalInputSnapshot} (preferred, from core) and
 * legacy {@link InputState} (from ui). The legacy path delegates to the
 * snapshot renderer.
 */
public class JLineInputRenderer implements InputRenderer {

    private final Terminal terminal;
    private final PrintWriter writer;

    // Track what we've rendered for efficient updates
    private int lastLineCount = 0;
    private boolean focused = false;

    // Style constants
    private static final AttributedStyle PROMPT_STYLE = AttributedStyle.DEFAULT
            .foreground(AttributedStyle.CYAN);
    private static final AttributedStyle TOKEN_STYLE = AttributedStyle.DEFAULT
            .foreground(AttributedStyle.YELLOW);
    private static final AttributedStyle PENDING_STYLE = AttributedStyle.DEFAULT;
    private static final AttributedStyle CURSOR_STYLE = AttributedStyle.DEFAULT
            .inverse();
    private static final AttributedStyle COMPLETION_STYLE = AttributedStyle.DEFAULT
            .foreground(AttributedStyle.WHITE);
    private static final AttributedStyle SELECTED_STYLE = AttributedStyle.DEFAULT
            .foreground(AttributedStyle.WHITE)
            .background(AttributedStyle.BLUE);
    private static final AttributedStyle HINT_STYLE = AttributedStyle.DEFAULT
            .foreground(AttributedStyle.BRIGHT)
            .italic();
    private static final AttributedStyle ERROR_STYLE = AttributedStyle.DEFAULT
            .foreground(AttributedStyle.RED);

    public JLineInputRenderer(Terminal terminal) {
        this.terminal = terminal;
        this.writer = terminal.writer();
    }

    /**
     * Render an {@link EvalInputSnapshot} — the primary rendering path.
     */
    public void render(EvalInputSnapshot snapshot) {
        if (!focused) return;

        clearPreviousRender();

        AttributedStringBuilder line = new AttributedStringBuilder();

        // Prompt
        if (snapshot.prompt() != null) {
            line.styled(PROMPT_STYLE, snapshot.prompt());
        }

        // Resolved tokens as chips
        for (ExpressionToken token : snapshot.tokens()) {
            line.styled(TOKEN_STYLE, "[");
            line.styled(TOKEN_STYLE, token.displayText());
            line.styled(TOKEN_STYLE, "] ");
        }

        // Pending text with cursor
        String pending = snapshot.pendingText();
        int cursor = snapshot.cursor();

        if (pending.isEmpty()) {
            if (snapshot.hint() != null && snapshot.tokens().isEmpty()) {
                line.styled(HINT_STYLE, snapshot.hint());
            }
            line.styled(CURSOR_STYLE, " ");
        } else {
            if (cursor > 0) {
                line.styled(PENDING_STYLE, pending.substring(0, cursor));
            }
            if (cursor < pending.length()) {
                line.styled(CURSOR_STYLE, String.valueOf(pending.charAt(cursor)));
                if (cursor + 1 < pending.length()) {
                    line.styled(PENDING_STYLE, pending.substring(cursor + 1));
                }
            } else {
                line.styled(CURSOR_STYLE, " ");
            }
        }

        writer.print("\r");
        line.toAnsi(terminal).chars().forEach(c -> writer.print((char) c));
        writer.print("\033[K");
        writer.println();

        int lines = 1;

        // Error message
        if (snapshot.error() != null) {
            AttributedStringBuilder errorLine = new AttributedStringBuilder();
            errorLine.styled(ERROR_STYLE, "  ✗ " + snapshot.error());
            errorLine.toAnsi(terminal).chars().forEach(c -> writer.print((char) c));
            writer.print("\033[K");
            writer.println();
            lines++;
        }

        // Completions (use enriched entries for display)
        if (snapshot.hasVisibleCompletions()) {
            List<CompletionEntry> entries = snapshot.completionEntries();
            int selected = snapshot.selectedCompletion();

            for (int i = 0; i < Math.min(entries.size(), maxCompletions()); i++) {
                CompletionEntry entry = entries.get(i);
                AttributedStringBuilder compLine = new AttributedStringBuilder();

                String prefix = (i == selected) ? "  → " : "    ";
                AttributedStyle style = (i == selected) ? SELECTED_STYLE : COMPLETION_STYLE;

                compLine.styled(style, prefix);
                if (entry.emoji() != null && !entry.emoji().isEmpty()) {
                    compLine.styled(style, entry.emoji() + " ");
                }
                compLine.styled(style, entry.token());
                if (entry.typeName() != null && !entry.typeName().isEmpty()) {
                    compLine.styled(AttributedStyle.DEFAULT.faint(), "  " + entry.typeName());
                }

                compLine.toAnsi(terminal).chars().forEach(c -> writer.print((char) c));
                writer.print("\033[K");
                writer.println();
                lines++;
            }
        }

        lastLineCount = lines;
        writer.flush();
    }

    /**
     * Legacy render path — delegates to snapshot renderer.
     */
    @Override
    public void render(InputState state) {
        render(new EvalInputSnapshot(
                state.tokens(),
                state.pendingText(),
                state.cursorPosition(),
                state.completions(),
                state.completions().stream()
                        .map(p -> CompletionEntry.plain(p.token(), p.target()))
                        .toList(),
                state.selectedCompletion(),
                state.showCompletions(),
                state.prompt(),
                state.hint(),
                state.error()
        ));
    }

    /**
     * Clear the lines from previous render.
     */
    private void clearPreviousRender() {
        if (lastLineCount > 0) {
            // Move cursor up and clear lines
            for (int i = 0; i < lastLineCount; i++) {
                writer.print("\033[A");  // Cursor up
                writer.print("\033[2K"); // Clear entire line
            }
            writer.print("\r");  // Return to column 0
        }
    }

    @Override
    public void focus() {
        focused = true;
        // Enable raw mode for character-by-character input
        // (This is typically done by the caller via terminal.enterRawMode())
    }

    @Override
    public void blur() {
        focused = false;
    }

    @Override
    public boolean supportsCompletions() {
        return true;
    }

    @Override
    public int maxCompletions() {
        // Use a reasonable portion of terminal height
        int height = terminal.getHeight();
        return Math.max(5, Math.min(10, height / 3));
    }

    @Override
    public void onComplete(InputResult result) {
        // Clear the completion display
        clearPreviousRender();
        lastLineCount = 0;

        // Show result briefly if there's output
        if (result.success() && result.value() != null) {
            Object value = result.value();
            if (value instanceof String s && !s.isEmpty()) {
                writer.println("  → " + s);
            }
        } else if (!result.success() && result.errorMessage() != null) {
            AttributedStringBuilder errorLine = new AttributedStringBuilder();
            errorLine.styled(ERROR_STYLE, "  ✗ " + result.errorMessage());
            errorLine.toAnsi(terminal).chars().forEach(c -> writer.print((char) c));
            writer.println();
        }
        writer.flush();
    }

    @Override
    public void dispose() {
        clearPreviousRender();
        writer.flush();
    }

    /**
     * Get the terminal for external access.
     */
    public Terminal terminal() {
        return terminal;
    }
}
