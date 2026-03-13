package dev.everydaythings.graph.ui.input;

import dev.everydaythings.graph.parse.ExpressionToken;
import dev.everydaythings.graph.dispatch.VerbEntry;
import dev.everydaythings.graph.dispatch.Vocabulary;
import dev.everydaythings.graph.dispatch.ActionResult;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.runtime.Librarian;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Unified input controller - the brain of the input system.
 *
 * <p>This controller handles all input logic in a UI-agnostic way:
 * <ul>
 *   <li>Text accumulation and cursor movement</li>
 *   <li>Token parsing and resolution</li>
 *   <li>Completion lookup and selection</li>
 *   <li>Dispatch to vocabulary when input is complete</li>
 * </ul>
 *
 * <p>Renderers don't implement logic - they just:
 * <ol>
 *   <li>Convert physical events to {@link InputAction}</li>
 *   <li>Call {@link #handle(InputAction)}</li>
 *   <li>Render the resulting {@link InputState}</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>{@code
 * InputController controller = new InputController()
 *     .withLookup(text -> librarian.tokenIndex().lookup(text).toList())
 *     .withDispatch(tokens -> librarian.dispatch(...))
 *     .onStateChange(state -> renderer.render(state));
 *
 * // In event loop:
 * InputAction action = keyBindings.resolve(keyChord);
 * if (action != null) {
 *     controller.handle(action);
 * }
 * }</pre>
 */
public class InputController {

    // ==================================================================================
    // State
    // ==================================================================================

    private List<ExpressionToken> tokens = new ArrayList<>();
    private StringBuilder pendingText = new StringBuilder();
    private int cursorPosition = 0;
    private List<Posting> completions = List.of();
    private int selectedCompletion = -1;
    private boolean showCompletions = false;
    private String prompt = "> ";
    private String hint = null;
    private String error = null;

    // History
    private List<String> history = new ArrayList<>();
    private int historyIndex = -1;
    private String savedInput = null;

    // ==================================================================================
    // Configuration
    // ==================================================================================

    private Function<String, List<Posting>> lookupFunction;
    private Function<List<ExpressionToken>, InputResult> dispatchFunction;
    private Consumer<InputState> stateChangeListener;
    private int minLookupLength = 1;
    private Librarian librarian;

    // ==================================================================================
    // Configuration API
    // ==================================================================================

    /**
     * Set the lookup function for completions.
     */
    public InputController withLookup(Function<String, List<Posting>> lookup) {
        this.lookupFunction = lookup;
        return this;
    }

    /**
     * Set the dispatch function for when input is complete.
     */
    public InputController withDispatch(Function<List<ExpressionToken>, InputResult> dispatch) {
        this.dispatchFunction = dispatch;
        return this;
    }

    /**
     * Set the state change listener.
     */
    public InputController onStateChange(Consumer<InputState> listener) {
        this.stateChangeListener = listener;
        return this;
    }

    /**
     * Set minimum characters before lookup triggers.
     */
    public InputController withMinLookupLength(int length) {
        this.minLookupLength = length;
        return this;
    }

    /**
     * Set the librarian for vocabulary-based dispatch.
     */
    public InputController withLibrarian(Librarian librarian) {
        this.librarian = librarian;
        return this;
    }

    /**
     * Set the prompt string.
     */
    public InputController withPrompt(String prompt) {
        this.prompt = prompt;
        notifyStateChange();
        return this;
    }

    /**
     * Set the hint text.
     */
    public InputController withHint(String hint) {
        this.hint = hint;
        notifyStateChange();
        return this;
    }

    // ==================================================================================
    // Action Handling
    // ==================================================================================

    /**
     * Handle an input action.
     *
     * @param action The action to process
     * @return Result if input was completed/dispatched, empty otherwise
     */
    public Optional<InputResult> handle(InputAction action) {
        error = null; // Clear previous error

        return switch (action) {
            case InputAction.Char c -> {
                handleChar(c.c());
                yield Optional.empty();
            }
            case InputAction.Backspace b -> {
                handleBackspace();
                yield Optional.empty();
            }
            case InputAction.DeleteWord d -> {
                handleDeleteWord();
                yield Optional.empty();
            }
            case InputAction.Delete d -> {
                handleDelete();
                yield Optional.empty();
            }
            case InputAction.CursorLeft l -> {
                handleCursorLeft();
                yield Optional.empty();
            }
            case InputAction.CursorRight r -> {
                handleCursorRight();
                yield Optional.empty();
            }
            case InputAction.CursorHome h -> {
                handleCursorHome();
                yield Optional.empty();
            }
            case InputAction.CursorEnd e -> {
                handleCursorEnd();
                yield Optional.empty();
            }
            case InputAction.CompletionUp u -> {
                handleCompletionUp();
                yield Optional.empty();
            }
            case InputAction.CompletionDown d -> {
                handleCompletionDown();
                yield Optional.empty();
            }
            case InputAction.Accept a -> handleAccept();
            case InputAction.Tab t -> {
                handleTab();
                yield Optional.empty();
            }
            case InputAction.Cancel c -> {
                handleCancel();
                yield Optional.empty();
            }
            case InputAction.TokenBoundary b -> {
                handleTokenBoundary();
                yield Optional.empty();
            }
            case InputAction.HistoryPrev p -> {
                handleHistoryPrev();
                yield Optional.empty();
            }
            case InputAction.HistoryNext n -> {
                handleHistoryNext();
                yield Optional.empty();
            }
        };
    }

    // ==================================================================================
    // Action Handlers
    // ==================================================================================

    private void handleChar(char c) {
        pendingText.insert(cursorPosition, c);
        cursorPosition++;
        updateCompletions();
        notifyStateChange();
    }

    private void handleBackspace() {
        if (cursorPosition > 0) {
            pendingText.deleteCharAt(cursorPosition - 1);
            cursorPosition--;
            updateCompletions();
        } else if (!tokens.isEmpty()) {
            // Delete last token
            tokens.remove(tokens.size() - 1);
        }
        notifyStateChange();
    }

    private void handleDeleteWord() {
        if (cursorPosition > 0) {
            // Find word boundary
            int pos = cursorPosition - 1;
            // Skip trailing spaces
            while (pos > 0 && Character.isWhitespace(pendingText.charAt(pos))) {
                pos--;
            }
            // Skip word characters
            while (pos > 0 && !Character.isWhitespace(pendingText.charAt(pos - 1))) {
                pos--;
            }
            pendingText.delete(pos, cursorPosition);
            cursorPosition = pos;
            updateCompletions();
        }
        notifyStateChange();
    }

    private void handleDelete() {
        if (cursorPosition < pendingText.length()) {
            pendingText.deleteCharAt(cursorPosition);
            updateCompletions();
        }
        notifyStateChange();
    }

    private void handleCursorLeft() {
        if (cursorPosition > 0) {
            cursorPosition--;
        }
        notifyStateChange();
    }

    private void handleCursorRight() {
        if (cursorPosition < pendingText.length()) {
            cursorPosition++;
        }
        notifyStateChange();
    }

    private void handleCursorHome() {
        cursorPosition = 0;
        notifyStateChange();
    }

    private void handleCursorEnd() {
        cursorPosition = pendingText.length();
        notifyStateChange();
    }

    private void handleCompletionUp() {
        if (!completions.isEmpty()) {
            showCompletions = true;
            if (selectedCompletion <= 0) {
                selectedCompletion = completions.size() - 1;
            } else {
                selectedCompletion--;
            }
        }
        notifyStateChange();
    }

    private void handleCompletionDown() {
        if (!completions.isEmpty()) {
            showCompletions = true;
            if (selectedCompletion >= completions.size() - 1) {
                selectedCompletion = 0;
            } else {
                selectedCompletion++;
            }
        }
        notifyStateChange();
    }

    private Optional<InputResult> handleAccept() {
        // If completion selected, accept it
        if (showCompletions && selectedCompletion >= 0 && selectedCompletion < completions.size()) {
            acceptCompletion(completions.get(selectedCompletion));
            return Optional.empty();
        }

        // Otherwise, complete the input
        return completeInput();
    }

    private void handleTab() {
        if (completions.isEmpty()) {
            // No completions - do nothing or trigger lookup
            updateCompletions();
        } else if (completions.size() == 1) {
            // Single completion - accept it
            acceptCompletion(completions.get(0));
        } else {
            // Multiple completions - show/cycle
            showCompletions = true;
            if (selectedCompletion < 0) {
                selectedCompletion = 0;
            } else {
                selectedCompletion = (selectedCompletion + 1) % completions.size();
            }
        }
        notifyStateChange();
    }

    private void handleCancel() {
        if (showCompletions) {
            // Dismiss completions
            showCompletions = false;
            selectedCompletion = -1;
        } else {
            // Clear input
            tokens.clear();
            pendingText.setLength(0);
            cursorPosition = 0;
            completions = List.of();
        }
        notifyStateChange();
    }

    private void handleTokenBoundary() {
        String text = pendingText.toString().trim();
        if (text.isEmpty()) {
            return;
        }

        // Try to parse as operator or literal
        ExpressionToken token = tryParseToken(text);
        if (token != null) {
            tokens.add(token);
            pendingText.setLength(0);
            cursorPosition = 0;
            completions = List.of();
            selectedCompletion = -1;
            showCompletions = false;
        }
        // If not parseable, keep as pending text
        notifyStateChange();
    }

    private void handleHistoryPrev() {
        if (history.isEmpty()) return;

        if (historyIndex < 0) {
            // Save current input
            savedInput = pendingText.toString();
            historyIndex = history.size() - 1;
        } else if (historyIndex > 0) {
            historyIndex--;
        }

        restoreFromHistory();
        notifyStateChange();
    }

    private void handleHistoryNext() {
        if (historyIndex < 0) return;

        if (historyIndex >= history.size() - 1) {
            // Restore saved input
            historyIndex = -1;
            pendingText.setLength(0);
            pendingText.append(savedInput != null ? savedInput : "");
            cursorPosition = pendingText.length();
        } else {
            historyIndex++;
            restoreFromHistory();
        }
        notifyStateChange();
    }

    // ==================================================================================
    // Completion Logic
    // ==================================================================================

    private void updateCompletions() {
        String text = pendingText.toString();
        String lookupText = extractLookupText(text);

        if (lookupText.length() >= minLookupLength && lookupFunction != null) {
            completions = lookupFunction.apply(lookupText);
            showCompletions = !completions.isEmpty();
            selectedCompletion = completions.isEmpty() ? -1 : 0;
        } else {
            completions = List.of();
            showCompletions = false;
            selectedCompletion = -1;
        }
    }

    private String extractLookupText(String text) {
        // Extract the last "word" for lookup
        // This handles cases like "? predicate" where we want to lookup "predicate"
        int lastSpace = text.lastIndexOf(' ');
        return lastSpace >= 0 ? text.substring(lastSpace + 1) : text;
    }

    /**
     * Accept a completion by index.
     *
     * <p>Used for mouse click on completion list.
     *
     * @param index The index of the completion to accept
     * @return true if accepted, false if index out of range
     */
    public boolean acceptCompletionAt(int index) {
        if (index >= 0 && index < completions.size()) {
            acceptCompletion(completions.get(index));
            return true;
        }
        return false;
    }

    private void acceptCompletion(Posting posting) {
        String text = pendingText.toString();
        String lookupText = extractLookupText(text);

        // Handle prefix text (before the lookup word) as literal if non-empty
        int prefixEnd = text.length() - lookupText.length();
        if (prefixEnd > 0) {
            String prefix = text.substring(0, prefixEnd).trim();
            if (!prefix.isEmpty()) {
                ExpressionToken prefixToken = tryParseToken(prefix);
                if (prefixToken != null) {
                    tokens.add(prefixToken);
                } else {
                    tokens.add(ExpressionToken.LiteralToken.ofString(prefix));
                }
            }
        }

        // Add the completion as a RefToken
        tokens.add(ExpressionToken.RefToken.from(posting));

        // Clear pending
        pendingText.setLength(0);
        cursorPosition = 0;
        completions = List.of();
        selectedCompletion = -1;
        showCompletions = false;

        notifyStateChange();
    }

    // ==================================================================================
    // Input Completion
    // ==================================================================================

    private Optional<InputResult> completeInput() {
        // Handle any remaining pending text
        String text = pendingText.toString().trim();
        if (!text.isEmpty()) {
            ExpressionToken token = tryParseToken(text);
            if (token != null) {
                tokens.add(token);
            } else {
                tokens.add(ExpressionToken.LiteralToken.ofString(text));
            }
        }

        if (tokens.isEmpty()) {
            return Optional.empty();
        }

        // Save to history
        String inputText = currentState().displayText();
        if (!inputText.isBlank() && (history.isEmpty() || !history.get(history.size() - 1).equals(inputText))) {
            history.add(inputText);
        }
        historyIndex = -1;
        savedInput = null;

        // Dispatch
        InputResult result = dispatch(new ArrayList<>(tokens));

        // Clear state
        tokens.clear();
        pendingText.setLength(0);
        cursorPosition = 0;
        completions = List.of();
        selectedCompletion = -1;
        showCompletions = false;

        if (!result.success()) {
            error = result.errorMessage();
        }

        notifyStateChange();
        return Optional.of(result);
    }

    private InputResult dispatch(List<ExpressionToken> inputTokens) {
        // Use custom dispatch function if provided
        if (dispatchFunction != null) {
            return dispatchFunction.apply(inputTokens);
        }

        // Use vocabulary-based dispatch if librarian available
        if (librarian != null && !inputTokens.isEmpty()) {
            ExpressionToken first = inputTokens.get(0);
            String command = first.displayText();

            // Convert remaining tokens to string args
            List<String> args = inputTokens.stream()
                    .skip(1)
                    .map(ExpressionToken::displayText)
                    .toList();

            ActionResult actionResult = librarian.dispatch(command, args);
            return InputResult.from(actionResult);
        }

        return InputResult.success(inputTokens);
    }

    // ==================================================================================
    // Token Parsing
    // ==================================================================================

    private ExpressionToken tryParseToken(String text) {
        // Try operator
        ExpressionToken.OpToken op = ExpressionToken.OpToken.tryParse(text);
        if (op != null) return op;

        // Try literal
        ExpressionToken.LiteralToken lit = ExpressionToken.LiteralToken.tryParse(text);
        if (lit != null) return lit;

        // Try parens
        if ("(".equals(text)) return new ExpressionToken.OpenParen();
        if (")".equals(text)) return new ExpressionToken.CloseParen();

        return null;
    }

    // ==================================================================================
    // History
    // ==================================================================================

    private void restoreFromHistory() {
        if (historyIndex >= 0 && historyIndex < history.size()) {
            pendingText.setLength(0);
            pendingText.append(history.get(historyIndex));
            cursorPosition = pendingText.length();
            tokens.clear();
            completions = List.of();
            selectedCompletion = -1;
            showCompletions = false;
        }
    }

    /**
     * Add an entry to history.
     */
    public void addToHistory(String entry) {
        if (entry != null && !entry.isBlank()) {
            history.add(entry);
        }
    }

    // ==================================================================================
    // State Access
    // ==================================================================================

    /**
     * Get the current input state (immutable snapshot).
     */
    public InputState currentState() {
        return new InputState(
                List.copyOf(tokens),
                pendingText.toString(),
                cursorPosition,
                completions,
                selectedCompletion,
                showCompletions,
                prompt,
                hint,
                error
        );
    }

    /**
     * Reset to empty state.
     */
    public void reset() {
        tokens.clear();
        pendingText.setLength(0);
        cursorPosition = 0;
        completions = List.of();
        selectedCompletion = -1;
        showCompletions = false;
        error = null;
        notifyStateChange();
    }

    /**
     * Set the input text directly (for programmatic input).
     */
    public void setInput(String text) {
        tokens.clear();
        pendingText.setLength(0);
        pendingText.append(text != null ? text : "");
        cursorPosition = pendingText.length();
        updateCompletions();
        notifyStateChange();
    }

    private void notifyStateChange() {
        if (stateChangeListener != null) {
            stateChangeListener.accept(currentState());
        }
    }
}
