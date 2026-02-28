package dev.everydaythings.graph.expression;

import dev.everydaythings.graph.expression.ExpressionToken.*;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.runtime.Eval;
import dev.everydaythings.graph.runtime.LibrarianHandle;
import lombok.extern.log4j.Log4j2;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Unified expression input — the primary interaction mechanism for all items.
 *
 * <p>EvalInput is a runtime object (not a component) that manages the
 * complete input state machine across all UI tiers. It replaces both
 * {@code InputController} (ui) and {@code ExpressionInput} (core).
 *
 * <p>The input is a sequence of <b>resolved tokens</b> — references to Items,
 * literal values, operators. As the user types, the system resolves names via
 * the lookup function (backed by TokenDictionary). When the user accepts, the
 * tokens are dispatched through {@link Eval}.
 *
 * <h2>Architecture</h2>
 * <ul>
 *   <li><b>Item</b> defines WHAT: vocabulary (verbs), token dictionary (completions)</li>
 *   <li><b>EvalInput</b> manages HOW: typing, cursor, completions, history, dispatch</li>
 *   <li><b>Renderers</b> show WHERE: CLI tab-complete, TUI dropdown, GUI chips, 3D bodies</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * EvalInput input = EvalInput.builder()
 *     .lookup(text -> dict.prefix(text, 10).toList())
 *     .librarian(librarianHandle)
 *     .context(focusedItem)
 *     .prompt("> ")
 *     .onChange(snapshot -> renderer.render(snapshot))
 *     .build();
 *
 * // Wire platform events to methods:
 * input.type('a');
 * input.tab();       // accept completion
 * input.accept();    // dispatch expression
 * }</pre>
 */
@Log4j2
public class EvalInput {

    // ==================================================================================
    // State
    // ==================================================================================

    private final List<ExpressionToken> tokens = new ArrayList<>();
    private final StringBuilder pendingText = new StringBuilder();
    private int cursor;

    private List<Posting> completions = List.of();
    private List<CompletionEntry> completionEntries = List.of();
    private int selectedCompletion = -1;
    private boolean showCompletions;

    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1;
    private String savedPending;  // saved pending text when browsing history

    private String prompt = "> ";
    private String hint = "";
    private String error;

    /** The display text of the last accepted input (captured before tokens.clear()). */
    private String lastSubmittedText;

    /** Cached expression context for completion narrowing. */
    private ExpressionContext expressionContext;
    private int contextTokenCount = -1;

    // ==================================================================================
    // Configuration
    // ==================================================================================

    private Function<String, List<Posting>> lookup;
    private LibrarianHandle librarianHandle;
    private Item context;
    private Item session;
    private int minLookupLength = 1;

    private Consumer<EvalInputSnapshot> onChange;
    private Consumer<Item> onNavigate;
    private Consumer<Eval.EvalResult> onResult;
    private Consumer<String> onError;

    private EvalInput() {}

    // ==================================================================================
    // Builder
    // ==================================================================================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final EvalInput input = new EvalInput();

        public Builder lookup(Function<String, List<Posting>> lookup) {
            input.lookup = lookup;
            return this;
        }

        public Builder librarian(LibrarianHandle handle) {
            input.librarianHandle = handle;
            return this;
        }

        public Builder context(Item context) {
            input.context = context;
            return this;
        }

        public Builder session(Item session) {
            input.session = session;
            return this;
        }

        public Builder prompt(String prompt) {
            input.prompt = prompt;
            return this;
        }

        public Builder hint(String hint) {
            input.hint = hint;
            return this;
        }

        public Builder minLookupLength(int min) {
            input.minLookupLength = min;
            return this;
        }

        public Builder onChange(Consumer<EvalInputSnapshot> callback) {
            input.onChange = callback;
            return this;
        }

        public Builder onNavigate(Consumer<Item> callback) {
            input.onNavigate = callback;
            return this;
        }

        public Builder onResult(Consumer<Eval.EvalResult> callback) {
            input.onResult = callback;
            return this;
        }

        public Builder onError(Consumer<String> callback) {
            input.onError = callback;
            return this;
        }

        public EvalInput build() {
            return input;
        }
    }

    // ==================================================================================
    // Accessors
    // ==================================================================================

    /** Get an immutable snapshot of the current state (for rendering). */
    public EvalInputSnapshot snapshot() {
        return new EvalInputSnapshot(
                List.copyOf(tokens),
                pendingText.toString(),
                cursor,
                completions,
                completionEntries,
                selectedCompletion,
                showCompletions,
                prompt,
                hint,
                error
        );
    }

    /** Get current context item. */
    public Item context() {
        return context;
    }

    /** Get the resolved tokens. */
    public List<ExpressionToken> tokens() {
        return Collections.unmodifiableList(tokens);
    }

    /** Get the pending text. */
    public String pendingText() {
        return pendingText.toString();
    }

    /** Whether input is empty (no tokens and no pending text). */
    public boolean isEmpty() {
        return tokens.isEmpty() && pendingText.isEmpty();
    }

    /** The display text of the last accepted input (before tokens were cleared). */
    public String lastSubmittedText() {
        return lastSubmittedText;
    }

    // ==================================================================================
    // Context Management
    // ==================================================================================

    /** Update the focused item context. */
    public void setContext(Item item) {
        this.context = item;
    }

    /** Update the prompt text. */
    public void setPrompt(String prompt) {
        this.prompt = prompt;
        notifyChange();
    }

    /** Update the hint text. */
    public void setHint(String hint) {
        this.hint = hint;
    }

    /** Update the lookup function. */
    public void setLookup(Function<String, List<Posting>> lookup) {
        this.lookup = lookup;
    }

    // ==================================================================================
    // Text Editing
    // ==================================================================================

    /**
     * Insert a character at the cursor position.
     */
    public void type(char c) {
        clearError();
        pendingText.insert(cursor, c);
        cursor++;
        afterTextChange();
    }

    /**
     * Insert a string at the cursor position.
     */
    public void type(String text) {
        if (text == null || text.isEmpty()) return;
        clearError();
        pendingText.insert(cursor, text);
        cursor += text.length();
        afterTextChange();
    }

    /**
     * Delete the character before the cursor.
     *
     * <p>If pending text is empty, pops the last token instead.
     */
    public void backspace() {
        clearError();
        if (cursor > 0) {
            pendingText.deleteCharAt(cursor - 1);
            cursor--;
            afterTextChange();
        } else if (pendingText.isEmpty() && !tokens.isEmpty()) {
            tokens.remove(tokens.size() - 1);
            invalidateContext();
            notifyChange();
        }
    }

    /**
     * Delete the character after the cursor.
     */
    public void delete() {
        clearError();
        if (cursor < pendingText.length()) {
            pendingText.deleteCharAt(cursor);
            afterTextChange();
        }
    }

    /**
     * Delete the word before the cursor.
     */
    public void deleteWord() {
        clearError();
        if (cursor == 0) return;

        // Find word boundary: skip trailing spaces, then skip word chars
        int target = cursor;
        while (target > 0 && pendingText.charAt(target - 1) == ' ') target--;
        while (target > 0 && pendingText.charAt(target - 1) != ' ') target--;

        pendingText.delete(target, cursor);
        cursor = target;
        afterTextChange();
    }

    // ==================================================================================
    // Cursor Movement
    // ==================================================================================

    /** Move cursor one position left. */
    public void cursorLeft() {
        if (cursor > 0) {
            cursor--;
            notifyChange();
        }
    }

    /** Move cursor one position right. */
    public void cursorRight() {
        if (cursor < pendingText.length()) {
            cursor++;
            notifyChange();
        }
    }

    /** Move cursor to the beginning. */
    public void cursorHome() {
        if (cursor != 0) {
            cursor = 0;
            notifyChange();
        }
    }

    /** Move cursor to the end. */
    public void cursorEnd() {
        if (cursor != pendingText.length()) {
            cursor = pendingText.length();
            notifyChange();
        }
    }

    // ==================================================================================
    // Completions
    // ==================================================================================

    /** Move completion selection up. */
    public void completionUp() {
        if (showCompletions && !completions.isEmpty()) {
            selectedCompletion = Math.max(0, selectedCompletion - 1);
            notifyChange();
        } else {
            historyPrev();
        }
    }

    /** Move completion selection down. */
    public void completionDown() {
        if (showCompletions && !completions.isEmpty()) {
            selectedCompletion = Math.min(completions.size() - 1, selectedCompletion + 1);
            notifyChange();
        } else {
            historyNext();
        }
    }

    /**
     * Accept the selected completion, or show completions if hidden.
     *
     * <p>Tab behavior:
     * <ol>
     *   <li>If completions visible and one selected → accept it as a RefToken</li>
     *   <li>If completions hidden but text present → trigger lookup and show</li>
     * </ol>
     */
    public void tab() {
        clearError();
        if (showCompletions && selectedCompletion >= 0 && selectedCompletion < completions.size()) {
            acceptCompletion(completions.get(selectedCompletion));
        } else if (!pendingText.isEmpty()) {
            // Force show completions
            updateCompletions();
            showCompletions = true;
            notifyChange();
        }
    }

    /** Hide completions without selecting. */
    public void dismissCompletions() {
        if (showCompletions) {
            showCompletions = false;
            notifyChange();
        }
    }

    /**
     * Accept a specific completion posting.
     *
     * <p>If the pending text contains a space, text before the lookup word
     * becomes a literal token. The selected posting becomes a RefToken.
     */
    private void acceptCompletion(Posting posting) {
        String pending = pendingText.toString().trim();
        int lastSpace = pending.lastIndexOf(' ');

        // Text before the lookup word → literal token
        if (lastSpace >= 0) {
            String prefix = pending.substring(0, lastSpace).trim();
            if (!prefix.isEmpty()) {
                commitPendingText(prefix);
            }
        }

        tokens.add(RefToken.from(posting));
        pendingText.setLength(0);
        cursor = 0;
        clearCompletions();
        notifyChange();
    }

    // ==================================================================================
    // Token Boundary (Space key)
    // ==================================================================================

    /**
     * Handle token boundary (Space key).
     *
     * <p>If the pending text is a recognized operator or literal, commits it
     * as a token. Otherwise, inserts a space character.
     */
    public void tokenBoundary() {
        clearError();
        String trimmed = pendingText.toString().trim();
        if (trimmed.isEmpty()) {
            type(' ');
            return;
        }

        // Check for paren
        if ("(".equals(trimmed)) {
            tokens.add(new OpenParen());
            resetPending();
            return;
        }
        if (")".equals(trimmed)) {
            tokens.add(new CloseParen());
            resetPending();
            return;
        }

        // Try to auto-resolve against completions (exact match) first —
        // this lets sememe seeds like "and" (conjunction) take priority
        // over operator symbol parsing.
        Posting exactMatch = findExactMatch(trimmed);
        if (exactMatch != null) {
            acceptCompletion(exactMatch);
            return;
        }

        // Check for operator (&&, ||)
        OpToken opToken = OpToken.tryParse(trimmed);
        if (opToken != null) {
            tokens.add(opToken);
            resetPending();
            return;
        }

        // Check for literal (number, boolean, quoted string)
        LiteralToken litToken = LiteralToken.tryParse(trimmed);
        if (litToken != null) {
            tokens.add(litToken);
            resetPending();
            return;
        }

        // Not resolved — just insert the space
        type(' ');
    }

    // ==================================================================================
    // Accept (Enter key)
    // ==================================================================================

    /**
     * Accept the input — commit pending text and dispatch through Eval.
     *
     * @return the result of evaluation, or empty if input was empty
     */
    public Optional<Eval.EvalResult> accept() {
        clearError();

        // If completions visible and one selected, accept it first
        if (showCompletions && selectedCompletion >= 0 && selectedCompletion < completions.size()) {
            acceptCompletion(completions.get(selectedCompletion));
        }

        // Commit any remaining pending text, resolving words where possible
        String remaining = pendingText.toString().trim();
        if (!remaining.isEmpty()) {
            resolveOrCommit(remaining);
            pendingText.setLength(0);
            cursor = 0;
        }

        if (tokens.isEmpty()) {
            return Optional.empty();
        }

        // Capture display text before clearing (for history)
        String historyEntry = buildDisplayText();
        lastSubmittedText = historyEntry;

        // Save to history
        if (!historyEntry.isBlank() && (history.isEmpty() || !history.get(history.size() - 1).equals(historyEntry))) {
            history.add(historyEntry);
        }
        historyIndex = -1;

        // Dispatch through Eval
        List<ExpressionToken> expressionTokens = List.copyOf(tokens);
        tokens.clear();
        clearCompletions();
        notifyChange();

        return dispatch(expressionTokens);
    }

    /**
     * Cancel — dismiss completions or clear input.
     */
    public void cancel() {
        if (showCompletions) {
            dismissCompletions();
        } else if (!pendingText.isEmpty()) {
            pendingText.setLength(0);
            cursor = 0;
            clearCompletions();
            notifyChange();
        } else if (!tokens.isEmpty()) {
            tokens.clear();
            invalidateContext();
            notifyChange();
        }
    }

    // ==================================================================================
    // History
    // ==================================================================================

    /** Navigate to previous history entry. */
    public void historyPrev() {
        if (history.isEmpty()) return;

        if (historyIndex == -1) {
            // Save current input
            savedPending = pendingText.toString();
            historyIndex = history.size() - 1;
        } else if (historyIndex > 0) {
            historyIndex--;
        } else {
            return;  // at oldest
        }

        setFromHistory(history.get(historyIndex));
    }

    /** Navigate to next history entry. */
    public void historyNext() {
        if (historyIndex == -1) return;

        if (historyIndex < history.size() - 1) {
            historyIndex++;
            setFromHistory(history.get(historyIndex));
        } else {
            // Restore saved input
            historyIndex = -1;
            tokens.clear();
            pendingText.setLength(0);
            if (savedPending != null) {
                pendingText.append(savedPending);
            }
            cursor = pendingText.length();
            savedPending = null;
            clearCompletions();
            notifyChange();
        }
    }

    private void setFromHistory(String entry) {
        tokens.clear();
        pendingText.setLength(0);
        pendingText.append(entry);
        cursor = pendingText.length();
        clearCompletions();
        notifyChange();
    }

    // ==================================================================================
    // Full Reset
    // ==================================================================================

    /** Clear all state (tokens, pending, completions, error). */
    public void clear() {
        tokens.clear();
        pendingText.setLength(0);
        cursor = 0;
        clearCompletions();
        invalidateContext();
        error = null;
        notifyChange();
    }

    // ==================================================================================
    // Internals
    // ==================================================================================

    /**
     * Find an exact match for text in current completions, or via lookup.
     * Returns the best matching Posting, or null if no exact match found.
     */
    private Posting findExactMatch(String text) {
        // Check current completions first (already loaded from typing)
        for (Posting p : completions) {
            if (p.token().equalsIgnoreCase(text)) {
                return p;
            }
        }
        // If completions empty (typed fast, or min length not met), try lookup
        if (completions.isEmpty() && lookup != null) {
            try {
                List<Posting> results = lookup.apply(text);
                if (results != null) {
                    for (Posting p : results) {
                        if (p.token().equalsIgnoreCase(text)) {
                            return p;
                        }
                    }
                }
            } catch (Exception e) {
                logger.debug("Exact match lookup failed for '{}'", text, e);
            }
        }
        return null;
    }

    /**
     * Resolve each word in text against the lookup, committing as literal
     * only if no exact match is found.
     */
    private void resolveOrCommit(String text) {
        String[] words = text.split("\\s+");
        for (String word : words) {
            if (word.isEmpty()) continue;
            Posting match = findExactMatch(word);
            if (match != null) {
                tokens.add(RefToken.from(match));
            } else {
                commitPendingText(word);
            }
        }
    }

    /**
     * Called after any text change — updates completions and notifies.
     */
    private void afterTextChange() {
        // Check for paren typed at end
        if (pendingText.length() > 0) {
            char lastChar = pendingText.charAt(pendingText.length() - 1);
            if (lastChar == '(') {
                String before = pendingText.substring(0, pendingText.length() - 1).trim();
                pendingText.setLength(0);
                cursor = 0;
                if (!before.isEmpty()) commitPendingText(before);
                tokens.add(new OpenParen());
                clearCompletions();
                notifyChange();
                return;
            }
            if (lastChar == ')') {
                String before = pendingText.substring(0, pendingText.length() - 1).trim();
                pendingText.setLength(0);
                cursor = 0;
                if (!before.isEmpty()) commitPendingText(before);
                tokens.add(new CloseParen());
                clearCompletions();
                notifyChange();
                return;
            }
        }

        updateCompletions();
        notifyChange();
    }

    /**
     * Update completions based on current pending text.
     *
     * <p>After raw lookup, applies semantic narrowing via {@link ExpressionContext}
     * to filter completions based on what the expression already contains.
     */
    private void updateCompletions() {
        String lookupText = extractLookupText();
        if (lookupText.length() >= minLookupLength && lookup != null) {
            try {
                completions = lookup.apply(lookupText);
                if (completions == null) completions = List.of();

                // Apply semantic narrowing
                if (librarianHandle != null && !completions.isEmpty()) {
                    ExpressionContext ctx = getOrUpdateContext();
                    completions = ctx.filter(completions, iid -> librarianHandle.get(iid));
                }

                // Build enriched entries for display
                completionEntries = buildCompletionEntries(completions);
            } catch (Exception e) {
                logger.debug("Lookup failed for '{}': {}", lookupText, e.getMessage());
                completions = List.of();
                completionEntries = List.of();
            }
            selectedCompletion = completions.isEmpty() ? -1 : 0;
            showCompletions = !completions.isEmpty();
        } else {
            clearCompletions();
        }
    }

    /**
     * Resolve postings to enriched completion entries for display.
     */
    private List<CompletionEntry> buildCompletionEntries(List<Posting> postings) {
        return postings.stream().map(posting -> {
            if (librarianHandle != null) {
                try {
                    var item = librarianHandle.get(posting.target());
                    if (item.isPresent()) {
                        return new CompletionEntry(
                                posting.token(),
                                item.get().emoji(),
                                item.get().displayInfo().typeName(),
                                posting.target());
                    }
                } catch (Exception e) {
                    logger.trace("Failed to resolve completion target {}", posting.target(), e);
                }
            }
            return CompletionEntry.plain(posting.token(), posting.target());
        }).toList();
    }

    /**
     * Get or recompute the expression context for semantic narrowing.
     *
     * <p>Cached and only recomputed when the token count changes.
     */
    private ExpressionContext getOrUpdateContext() {
        int currentCount = tokens.size();
        if (expressionContext == null || contextTokenCount != currentCount) {
            if (librarianHandle != null) {
                expressionContext = ExpressionContext.analyze(
                        tokens, iid -> librarianHandle.get(iid));
            } else {
                expressionContext = ExpressionContext.EMPTY;
            }
            contextTokenCount = currentCount;
            if (traceEnabled()) {
                logger.info("[Parse] context tokens={} verb={} filledRoles={} unfilledRequired={} pending='{}'",
                        tokens.size(),
                        expressionContext.verb() != null ? expressionContext.verb().displayToken() : "none",
                        expressionContext.filledRoles(),
                        expressionContext.unfilledRequired(),
                        pendingText);
            }
        }
        return expressionContext;
    }

    /**
     * Extract the lookup text from pending — the last word only.
     *
     * <p>When the user types "foo bar", we look up "bar" so selecting a
     * completion replaces only "bar". "foo" becomes a separate token.
     */
    private String extractLookupText() {
        String text = pendingText.toString().trim();
        int lastSpace = text.lastIndexOf(' ');
        if (lastSpace >= 0 && lastSpace < text.length() - 1) {
            return text.substring(lastSpace + 1);
        }
        return text;
    }

    /**
     * Commit text as a token — tries exact match, operator, literal, then string literal.
     */
    private void commitPendingText(String text) {
        // Try exact match against completions first (same priority as tokenBoundary)
        Posting exactMatch = findExactMatch(text);
        if (exactMatch != null) {
            acceptCompletion(exactMatch);
            return;
        }

        OpToken op = OpToken.tryParse(text);
        if (op != null) {
            tokens.add(op);
            return;
        }

        LiteralToken lit = LiteralToken.tryParse(text);
        if (lit != null) {
            tokens.add(lit);
            return;
        }

        // Unresolved text → string literal
        if (!text.isBlank()) {
            tokens.add(LiteralToken.ofString(text));
        }
    }

    private void resetPending() {
        pendingText.setLength(0);
        cursor = 0;
        clearCompletions();
        notifyChange();
    }

    private void clearCompletions() {
        completions = List.of();
        completionEntries = List.of();
        selectedCompletion = -1;
        showCompletions = false;
    }

    /** Invalidate the cached expression context (call when tokens change). */
    private void invalidateContext() {
        contextTokenCount = -1;
        expressionContext = null;
    }

    private void clearError() {
        error = null;
    }

    private void notifyChange() {
        if (onChange != null) {
            onChange.accept(snapshot());
        }
    }

    private boolean traceEnabled() {
        return Boolean.getBoolean("cg.eval.trace")
                || "1".equals(System.getenv("CG_EVAL_TRACE"))
                || "true".equalsIgnoreCase(System.getenv("CG_EVAL_TRACE"));
    }

    private String buildDisplayText() {
        var sb = new StringBuilder();
        for (ExpressionToken token : tokens) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(token.displayText());
        }
        return sb.toString();
    }

    /**
     * Dispatch expression tokens through Eval.
     */
    private Optional<Eval.EvalResult> dispatch(List<ExpressionToken> expressionTokens) {
        if (librarianHandle == null) {
            error = "No librarian available";
            notifyChange();
            return Optional.of(Eval.EvalResult.error(error));
        }

        try {
            Eval eval = Eval.builder()
                    .librarian(librarianHandle)
                    .context(context)
                    .session(session)
                    .interactive(false)
                    .build();

            Eval.EvalResult result = eval.executeTokens(expressionTokens);

            // Handle navigation for item results
            if (result instanceof Eval.EvalResult.ItemResult(Item item)) {
                if (context == null || !item.iid().equals(context.iid())) {
                    if (onNavigate != null) {
                        onNavigate.accept(item);
                    }
                }
            }

            // Handle errors
            if (result instanceof Eval.EvalResult.Error(String message)) {
                error = message;
                notifyChange();
            }

            // Notify result callback
            if (onResult != null) {
                onResult.accept(result);
            }

            return Optional.of(result);

        } catch (Exception e) {
            logger.debug("Expression dispatch failed", e);
            error = e.getMessage() != null ? e.getMessage() : "Execution failed";
            notifyChange();
            if (onError != null) {
                onError.accept(error);
            }
            return Optional.of(Eval.EvalResult.error(error));
        }
    }
}
