package dev.everydaythings.graph.expression;

import dev.everydaythings.graph.expression.ExpressionToken.*;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.item.component.expression.EvaluationContext;
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

        // Local component handle → NameToken (for expression evaluation)
        // Global dictionary match → RefToken (for item reference)
        if (isLocalPosting(posting)) {
            tokens.add(new NameToken(posting.token()));
        } else {
            tokens.add(enrichedRefToken(posting));
        }
        pendingText.setLength(0);
        cursor = 0;
        clearCompletions();
        pruneCandidates();
        notifyChange();
    }

    /**
     * Check if a posting is a local component handle (from context item's vocabulary).
     */
    private boolean isLocalPosting(Posting posting) {
        return context != null
                && posting.scope() != null
                && posting.scope().equals(context.iid())
                && posting.target().equals(context.iid());
    }

    // ==================================================================================
    // Token Boundary (Space key)
    // ==================================================================================

    /**
     * Handle token boundary (Space key).
     *
     * <p>Resolution order ensures expressions work correctly:
     * <ol>
     *   <li>Parentheses and commas — structural tokens</li>
     *   <li>Literals (numbers, booleans, quoted strings) — before dictionary so "5" is always a number</li>
     *   <li>Symbolic operators (+, -, *, etc.) — before dictionary so "+" becomes OpToken, not RefToken</li>
     *   <li>Dictionary lookup — for words (verbs, nouns, function names)</li>
     *   <li>Fallback — insert space, keep as pending text</li>
     * </ol>
     */
    public void tokenBoundary() {
        clearError();
        String trimmed = pendingText.toString().trim();
        if (trimmed.isEmpty()) {
            type(' ');
            return;
        }

        // Split at character-class boundaries — handles "5+2", "5meter", etc.
        List<String> parts = splitRawTokens(trimmed);

        // If it splits into multiple pieces, resolve them all
        if (parts.size() > 1) {
            resolveOrCommit(trimmed);
            resetPending();
            return;
        }

        // Single token — try to resolve it specifically

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
        if (",".equals(trimmed)) {
            tokens.add(new CommaToken());
            resetPending();
            return;
        }

        // Check for literal (number, boolean, quoted string) — before dictionary
        // so "5" always becomes LiteralToken, never a dictionary entry
        LiteralToken litToken = LiteralToken.tryParse(trimmed);
        if (litToken != null) {
            tokens.add(litToken);
            resetPending();
            return;
        }

        // Check for symbolic operators (+, -, *, /, etc.) — before dictionary
        // so "+" becomes OpToken (enabling expression detection), not RefToken.
        // Word-form operators (in, contains) go through dictionary first so they
        // can also serve as prepositions in verb frames.
        if (!startsWithLetter(trimmed)) {
            OpToken opToken = OpToken.tryParse(trimmed);
            if (opToken != null) {
                tokens.add(opToken);
                resetPending();
                return;
            }
        }

        // Dictionary lookup — for words like "create", "sqrt", "in"
        List<Posting> exactMatches = findAllExactMatches(trimmed);
        if (!exactMatches.isEmpty()) {
            if (exactMatches.size() == 1) {
                // Unambiguous — resolve immediately
                acceptCompletion(exactMatches.get(0));
            } else {
                // Ambiguous — create a CandidateToken carrying all possibilities
                tokens.add(new ExpressionToken.CandidateToken(trimmed, exactMatches));
                resetPending();
                pruneCandidates();
            }
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

        // Final pruning pass with the complete token list
        pruneCandidates();

        // If CandidateTokens remain after pruning:
        // - In expression context (operators present): auto-resolve to NameTokens
        //   because bare names are valid (variable/parameter references)
        // - In command context (no operators): block and ask user to disambiguate
        if (hasUnresolvedCandidates()) {
            if (hasOperators()) {
                autoResolveCandidates();
            } else {
                error = "Ambiguous tokens — select a meaning for highlighted words";
                notifyChange();
                return Optional.of(Eval.EvalResult.error(error));
            }
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
        List<Posting> all = findAllExactMatches(text);
        return all.isEmpty() ? null : all.get(0);
    }

    /**
     * Find ALL exact matches for text — from completions and/or lookup.
     * Returns postings sorted by weight (highest first), deduplicated by target.
     */
    private List<Posting> findAllExactMatches(String text) {
        List<Posting> matches = new ArrayList<>();

        // Check context item's local vocabulary first (component handles — highest priority)
        if (context != null) {
            context.vocabulary().exactMatch(text).ifPresent(matches::add);
        }

        // Check current completions
        for (Posting p : completions) {
            if (p.token().equalsIgnoreCase(text)) {
                matches.add(p);
            }
        }

        // If no matches yet (typed fast, or min length not met), try global lookup
        if (matches.isEmpty() && lookup != null) {
            try {
                List<Posting> results = lookup.apply(text);
                if (results != null) {
                    for (Posting p : results) {
                        if (p.token().equalsIgnoreCase(text)) {
                            matches.add(p);
                        }
                    }
                }
            } catch (Exception e) {
                logger.debug("Exact match lookup failed for '{}'", text, e);
            }
        }

        // Deduplicate by target — same item via different scopes appears once (highest weight wins)
        if (matches.size() > 1) {
            Map<ItemID, Posting> byTarget = new LinkedHashMap<>();
            for (Posting p : matches) {
                byTarget.merge(p.target(), p, (existing, extra) ->
                        existing.weight() >= extra.weight() ? existing : extra);
            }
            matches = new ArrayList<>(byTarget.values());
            matches.sort(Comparator.comparing(Posting::weight).reversed());
        }

        return matches;
    }

    /**
     * Resolve each word in text, using the same priority order as tokenBoundary:
     * literal → symbolic operator → dictionary → word operator → name token.
     *
     * <p>TODO: Unify with {@link ExpressionLexer} — these are two tokenization paths
     * producing the same {@link ExpressionToken} types but with different resolution
     * semantics. EvalInput resolves interactively (with dictionary/vocabulary lookup),
     * ExpressionLexer resolves from raw strings (no dictionary). They should share a
     * common tokenization core, with dictionary resolution as an optional layer.
     */
    private void resolveOrCommit(String text) {
        for (String word : splitRawTokens(text)) {
            // Structural (already split by splitRawTokens)
            if ("(".equals(word)) { tokens.add(new OpenParen()); continue; }
            if (")".equals(word)) { tokens.add(new CloseParen()); continue; }
            if (",".equals(word)) { tokens.add(new CommaToken()); continue; }

            // Literal (number, boolean, quoted string)
            LiteralToken lit = LiteralToken.tryParse(word);
            if (lit != null) { tokens.add(lit); continue; }

            // Symbolic operator (+, -, *, /, etc.)
            if (!startsWithLetter(word)) {
                OpToken op = OpToken.tryParse(word);
                if (op != null) { tokens.add(op); continue; }
            }

            // Dictionary lookup — may produce CandidateToken for ambiguous words
            List<Posting> matches = findAllExactMatches(word);
            if (!matches.isEmpty()) {
                if (matches.size() == 1) {
                    Posting match = matches.get(0);
                    if (isLocalPosting(match)) {
                        tokens.add(new NameToken(match.token()));
                    } else {
                        tokens.add(enrichedRefToken(match));
                    }
                } else {
                    tokens.add(new ExpressionToken.CandidateToken(word, matches));
                }
                continue;
            }

            // Word-form operator (in, contains) — after dictionary
            OpToken op = OpToken.tryParse(word);
            if (op != null) { tokens.add(op); continue; }

            // Unresolved text → name token (for function calls, variables)
            tokens.add(new NameToken(word));
        }
    }

    /**
     * Prune CandidateTokens using the current expression context.
     *
     * <p>After each new token is added, re-examine all CandidateTokens. For each:
     * <ul>
     *   <li>If the expression has a verb and the candidate is also a verb,
     *       eliminate the verb candidates (you already have one)</li>
     *   <li>If only one candidate survives pruning, promote to RefToken</li>
     *   <li>If zero candidates survive, revert to NameToken</li>
     * </ul>
     */
    private void pruneCandidates() {
        if (librarianHandle == null) return;

        boolean changed = false;
        ExpressionContext ctx = getOrUpdateContext();

        for (int i = 0; i < tokens.size(); i++) {
            if (!(tokens.get(i) instanceof ExpressionToken.CandidateToken candidate)) {
                continue;
            }

            // Filter candidates using expression context (same logic as completion filtering)
            List<Posting> surviving = ctx.filter(candidate.candidates(),
                    iid -> librarianHandle.get(iid));

            if (surviving.size() == 1) {
                // Auto-resolve — exactly one candidate survives
                tokens.set(i, enrichedRefToken(surviving.get(0)));
                changed = true;
            } else if (surviving.isEmpty()) {
                // All pruned — revert to unresolved name
                tokens.set(i, new ExpressionToken.NameToken(candidate.text()));
                changed = true;
            } else if (surviving.size() < candidate.candidates().size()) {
                // Narrowed but still ambiguous — update candidates
                tokens.set(i, candidate.narrow(surviving));
                changed = true;
            }
        }

        if (changed) {
            invalidateContext();
            notifyChange();
        }
    }

    /**
     * Check whether there are any unresolved CandidateTokens in the token list.
     */
    public boolean hasUnresolvedCandidates() {
        for (ExpressionToken token : tokens) {
            if (token instanceof ExpressionToken.CandidateToken) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check whether the token list contains operators (indicating expression context).
     *
     * <p>When operators are present, the input is a mathematical/assignment expression
     * rather than a verb-frame command. In expression context, ambiguous tokens are
     * treated as bare names (variable/parameter references).
     */
    private boolean hasOperators() {
        for (ExpressionToken token : tokens) {
            if (token instanceof ExpressionToken.OpToken) {
                return true;
            }
        }
        return false;
    }

    /**
     * Auto-resolve remaining CandidateTokens to NameTokens.
     *
     * <p>Used in expression context where bare names are valid references
     * (variables, parameters). The expression parser handles NameTokens
     * by treating them as local references on the focused item.
     */
    private void autoResolveCandidates() {
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i) instanceof ExpressionToken.CandidateToken candidate) {
                tokens.set(i, new ExpressionToken.NameToken(candidate.text()));
            }
        }
    }

    /**
     * Called after any text change — updates completions and notifies.
     *
     * <p>Eagerly splits pending text at character-class boundaries so
     * completed sub-tokens appear immediately (e.g., typing "+" after "5"
     * commits the "5" as a LiteralToken and keeps "+" as the new pending text).
     */
    private void afterTextChange() {
        if (pendingText.length() > 0) {
            // Check last character for structural tokens (parens, commas)
            char lastChar = pendingText.charAt(pendingText.length() - 1);
            if (lastChar == '(' || lastChar == ')' || lastChar == ',') {
                String before = pendingText.substring(0, pendingText.length() - 1).trim();
                pendingText.setLength(0);
                cursor = 0;
                if (!before.isEmpty()) resolveOrCommit(before);
                resolveOrCommit(String.valueOf(lastChar));
                clearCompletions();
                notifyChange();
                return;
            }

            // Eagerly split at character-class boundaries (digit→symbol, etc.)
            // but NOT at whitespace — whitespace splitting happens on accept/boundary.
            if (pendingText.length() >= 2) {
                String text = pendingText.toString();
                // Check if the last char is a different class than the preceding non-space char
                int lastIdx = text.length() - 1;
                int prevIdx = lastIdx - 1;
                // Skip back over whitespace to find previous meaningful char
                while (prevIdx >= 0 && Character.isWhitespace(text.charAt(prevIdx))) prevIdx--;
                if (prevIdx >= 0) {
                    int prevCls = charClass(text.charAt(prevIdx));
                    int lastCls = charClass(lastChar);
                    if (prevCls != lastCls && prevCls >= 0 && lastCls >= 0) {
                        // Class boundary — commit everything before, keep last class run
                        String beforeBoundary = text.substring(0, prevIdx + 1).trim();
                        String afterBoundary = text.substring(prevIdx + 1).trim();
                        pendingText.setLength(0);
                        cursor = 0;
                        if (!beforeBoundary.isEmpty()) resolveOrCommit(beforeBoundary);
                        pendingText.append(afterBoundary);
                        cursor = afterBoundary.length();
                        updateCompletions();
                        notifyChange();
                        return;
                    }
                }
            }
        }

        updateCompletions();
        notifyChange();
    }

    /** Classify a character: 0=digit, 1=letter, 2=symbol, -1=structural/whitespace. */
    private static int charClass(char ch) {
        if (Character.isDigit(ch) || ch == '.') return 0;
        if (Character.isLetter(ch) || ch == '_') return 1;
        if (ch == '(' || ch == ')' || ch == ',' || Character.isWhitespace(ch)) return -1;
        return 2; // symbol
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

                // Prepend local component handle matches (highest priority)
                if (context != null) {
                    List<Posting> local = context.vocabulary().prefixMatch(lookupText);
                    if (!local.isEmpty()) {
                        List<Posting> merged = new ArrayList<>(local);
                        merged.addAll(completions);
                        completions = merged;
                    }
                }

                // Deduplicate by target — same item via different tokens appears only once.
                // Multiple matching tokens boost the merged posting's weight.
                if (completions.size() > 1) {
                    Map<ItemID, Posting> merged = new LinkedHashMap<>();
                    for (Posting p : completions) {
                        merged.merge(p.target(), p, (existing, extra) ->
                                Posting.builder()
                                        .token(existing.token())
                                        .scope(existing.scope())
                                        .target(existing.target())
                                        .weight(Math.min(1.0f, existing.weight() + extra.weight()))
                                        .build());
                    }
                    completions = merged.values().stream()
                            .sorted(Comparator.comparing(Posting::weight).reversed())
                            .toList();
                }

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
     * Commit text as a token — same priority order as tokenBoundary:
     * literal → symbolic operator → dictionary → word operator → name token.
     */
    private void commitPendingText(String text) {
        // Split at character-class boundaries and resolve each piece
        resolveOrCommit(text);
    }

    private void resetPending() {
        pendingText.setLength(0);
        cursor = 0;
        clearCompletions();
        notifyChange();
    }

    /**
     * Create a RefToken from a posting, using the item's displayToken when available.
     *
     * <p>For items where the posting token is terse (e.g., "m" for meter),
     * the resolved item's displayToken provides a clearer label.
     */
    private RefToken enrichedRefToken(Posting posting) {
        if (librarianHandle != null) {
            try {
                var item = librarianHandle.get(posting.target());
                if (item.isPresent()) {
                    String display = item.get().displayToken();
                    if (display != null && !display.isBlank()) {
                        return RefToken.of(posting.target(), display);
                    }
                }
            } catch (Exception e) {
                // Fall through to default
            }
        }
        return RefToken.from(posting);
    }

    /**
     * Check if text starts with a letter (word-like vs symbolic).
     * Used to distinguish symbolic operators (+, -, *) from word-form operators (in, contains).
     */
    private static boolean startsWithLetter(String text) {
        return !text.isEmpty() && Character.isLetter(text.charAt(0));
    }

    /**
     * Split raw text into sub-tokens at character-class boundaries.
     *
     * <p>Character classes: DIGIT (0-9, decimal point in number context),
     * LETTER (Unicode letters/digits after first letter), SYMBOL (operators),
     * STRUCTURAL (parens, commas — always their own token).
     * Whitespace is consumed as a separator.
     *
     * <p>Examples:
     * <ul>
     *   <li>"5+2" → ["5", "+", "2"]</li>
     *   <li>"5meter" → ["5", "meter"]</li>
     *   <li>"sqrt(144)" → ["sqrt", "(", "144", ")"]</li>
     *   <li>"3.14*r" → ["3.14", "*", "r"]</li>
     *   <li>"x>=5" → ["x", ">=", "5"]</li>
     * </ul>
     */
    static List<String> splitRawTokens(String text) {
        if (text == null || text.isEmpty()) return List.of();

        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int cls = -1; // current class: 0=digit, 1=letter, 2=symbol

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);

            // Whitespace — flush and skip
            if (Character.isWhitespace(ch)) {
                if (!current.isEmpty()) { result.add(current.toString()); current.setLength(0); }
                cls = -1;
                continue;
            }

            // Structural characters — always their own token
            if (ch == '(' || ch == ')' || ch == ',') {
                if (!current.isEmpty()) { result.add(current.toString()); current.setLength(0); }
                result.add(String.valueOf(ch));
                cls = -1;
                continue;
            }

            // Decimal point in number context: "3.14" stays together
            if (ch == '.' && cls == 0 && i + 1 < text.length() && Character.isDigit(text.charAt(i + 1))) {
                current.append(ch);
                continue;
            }

            int newCls;
            if (Character.isDigit(ch)) newCls = 0;
            else if (Character.isLetter(ch) || (ch == '_')) newCls = 1;
            else newCls = 2; // symbol

            // Digit continuation in word context: "x2" stays as "x2"
            if (newCls == 0 && cls == 1) newCls = 1;

            if (cls != -1 && newCls != cls) {
                // Class boundary — flush
                result.add(current.toString());
                current.setLength(0);
            }
            current.append(ch);
            cls = newCls;
        }
        if (!current.isEmpty()) result.add(current.toString());

        return result;
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
