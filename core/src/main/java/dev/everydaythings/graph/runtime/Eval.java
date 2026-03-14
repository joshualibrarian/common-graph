package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.parse.ExpressionParser;
import dev.everydaythings.graph.parse.ExpressionToken;
import dev.everydaythings.graph.frame.expression.EvaluationContext;
import dev.everydaythings.graph.frame.expression.Expression;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.dispatch.Vocabulary;
import dev.everydaythings.graph.dispatch.VerbEntry;
import dev.everydaythings.graph.dispatch.VerbInvoker;
import dev.everydaythings.graph.dispatch.ActionContext;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.item.user.Signer;
import dev.everydaythings.graph.dispatch.ParamSpec;
import dev.everydaythings.graph.language.DiscourseHistory;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.FrameAssembler;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.SemanticFrame;
import dev.everydaythings.graph.language.ThematicRole;
import lombok.extern.log4j.Log4j2;
import org.jline.reader.*;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Evaluate a command expression with item resolution.
 *
 * <p>This is the CLI mode where you evaluate a single command and exit:
 * <pre>
 * graph eval create Note
 * graph eval @journal append "Today..."
 * </pre>
 *
 * <p><strong>Key concept:</strong> Tokens are resolved to Items. Once resolved,
 * the strings don't matter anymore - we have ItemIDs. The input becomes a
 * list of resolved references, not strings.
 *
 * <p>Resolution flow:
 * <ol>
 *   <li>User types tokens (strings)</li>
 *   <li>Each token is resolved via TokenDictionary → Posting → ItemID</li>
 *   <li>First item determines verb or context</li>
 *   <li>Dispatch operates on resolved Items</li>
 * </ol>
 *
 * <p>Uses JLine3 for readline-style editing with resolution-aware completion.
 */
@Log4j2
public class Eval {

    private static final int MAX_EXPRESSION_DEPTH = 8;

    private final LibrarianHandle librarianHandle;
    private final Item context;
    /** Focused component handle within the context item (inner-to-outer dispatch). */
    private final String focusedComponent;
    /** Session-level item providing outermost vocabulary scope. */
    private final Item session;
    /** Discourse history for pronoun resolution ("it", "that", "this", "last"). */
    private final DiscourseHistory discourseHistory;
    private final boolean interactive;
    private final boolean jsonOutput;
    private final int depth;
    /** Evaluation context for expression evaluation (created lazily from context item). */
    private final EvaluationContext evaluationContext;

    private Eval(LibrarianHandle librarianHandle, Item context, String focusedComponent,
                 Item session, DiscourseHistory discourseHistory,
                 boolean interactive, boolean jsonOutput, int depth,
                 EvaluationContext evaluationContext) {
        this.librarianHandle = librarianHandle;
        this.context = context;
        this.focusedComponent = focusedComponent;
        this.session = session;
        this.discourseHistory = discourseHistory != null ? discourseHistory : new DiscourseHistory();
        this.interactive = interactive;
        this.jsonOutput = jsonOutput;
        this.depth = depth;
        this.evaluationContext = evaluationContext;
    }

    // ==================================================================================
    // Builder
    // ==================================================================================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private LibrarianHandle librarianHandle;
        private Item context;
        private String focusedComponent;
        private Item session;
        private DiscourseHistory discourseHistory;
        private boolean interactive = true;
        private boolean jsonOutput = false;
        private EvaluationContext evaluationContext;

        public Builder librarian(LibrarianHandle ref) {
            this.librarianHandle = ref;
            return this;
        }

        public Builder librarian(Librarian librarian) {
            this.librarianHandle = LibrarianHandle.wrap(librarian);
            return this;
        }

        public Builder context(Item item) {
            this.context = item;
            return this;
        }

        /** Set the focused component handle for inner-to-outer dispatch. */
        public Builder focusedComponent(String componentHandle) {
            this.focusedComponent = componentHandle;
            return this;
        }

        /** Set the session item (outermost dispatch scope). */
        public Builder session(Item session) {
            this.session = session;
            return this;
        }

        /** Set discourse history for pronoun resolution (shared across evals). */
        public Builder discourseHistory(DiscourseHistory history) {
            this.discourseHistory = history;
            return this;
        }

        public Builder interactive(boolean interactive) {
            this.interactive = interactive;
            return this;
        }

        public Builder jsonOutput(boolean jsonOutput) {
            this.jsonOutput = jsonOutput;
            return this;
        }

        /** Set an evaluation context for expression evaluation. */
        public Builder evaluationContext(EvaluationContext evalCtx) {
            this.evaluationContext = evalCtx;
            return this;
        }

        public Eval build() {
            if (librarianHandle == null) {
                throw new IllegalStateException("LibrarianHandle is required");
            }
            return new Eval(librarianHandle, context, focusedComponent, session,
                    discourseHistory, interactive, jsonOutput, 0, evaluationContext);
        }
    }

    // ==================================================================================
    // Execution
    // ==================================================================================

    /**
     * Run with pre-specified arguments.
     *
     * <p>If args are empty and interactive mode, prompts for input with completion.
     * Otherwise executes the given args directly.
     */
    public int run(List<String> args) {
        if (args.isEmpty() && interactive && System.console() != null) {
            return runInteractive();
        }
        return executeCommand(args);
    }

    /**
     * Run interactively - prompt for input with completion.
     */
    public int runInteractive() {
        try {
            String input = promptWithCompletion();
            if (input == null || input.isBlank()) {
                return 0; // User cancelled
            }

            // Parse input into args
            List<String> args = parseInput(input);
            return executeCommand(args);
        } catch (Exception e) {
            logger.error("Interactive input failed", e);
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    /**
     * Prompt for input with tab completion using JLine.
     */
    private String promptWithCompletion() throws IOException {
        Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .build();

        Completer completer = createCompleter();

        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(completer)
                .parser(new DefaultParser())
                .variable(LineReader.LIST_MAX, 50)
                .option(LineReader.Option.AUTO_LIST, true)
                .option(LineReader.Option.AUTO_MENU, true)
                .option(LineReader.Option.CASE_INSENSITIVE, true)
                .build();

        try {
            String prompt = buildPrompt();
            return reader.readLine(prompt);
        } catch (UserInterruptException | EndOfFileException e) {
            return null; // User cancelled (Ctrl+C or Ctrl+D)
        } finally {
            terminal.close();
        }
    }

    /**
     * Create a JLine completer that resolves tokens to Items.
     *
     * <p>When a completion is selected, we insert a special marker that
     * includes the ItemID, so we can skip re-resolution later.
     */
    private Completer createCompleter() {
        return (reader, line, candidates) -> {
            String word = line.word();
            if (word == null || word.length() < 1) {
                return;
            }

            // Get completions from token dictionary with scope chain
            ItemID[] scopes = buildScopeChain();
            List<Posting> postings = librarianHandle.lookup(word, scopes)
                    .limit(50)
                    .toList();

            for (Posting p : postings) {
                String display = p.token();
                String subtitle = p.displaySubtitle();

                // The VALUE we insert includes the IID so we can resolve directly
                // Format: token[iid:xxx] - we can parse this to get the resolved reference
                String value = p.token() + "[" + p.target().encodeText() + "]";

                Candidate candidate = new Candidate(
                        value,                         // value (includes IID)
                        display,                       // display (just the token)
                        categorize(p),                 // group by category
                        subtitle,                      // description
                        null,                          // suffix
                        p.target().encodeText(),       // key (the IID)
                        true                           // complete
                );
                candidates.add(candidate);
            }
        };
    }

    /**
     * Categorize a posting for grouping in completion menu.
     */
    private String categorize(Posting p) {
        // Could use p.colorCategory() or inspect the target
        String subtitle = p.displaySubtitle();
        if (subtitle != null && subtitle.contains("verb")) return "Verbs";
        if (subtitle != null && subtitle.contains("type")) return "Types";
        return "Items";
    }

    private String buildPrompt() {
        if (context != null) {
            String label = context.displayToken();
            if (label != null && !label.isBlank() && label.length() < 20) {
                return label + "> ";
            }
        }
        return "graph> ";
    }

    private List<String> parseInput(String input) {
        // Simple whitespace split for now
        // TODO: Handle quoted strings properly
        return List.of(input.trim().split("\\s+"));
    }

    // ==================================================================================
    // Token Resolution
    // ==================================================================================

    /**
     * A resolved token - either an Item reference or a literal value.
     */
    public sealed interface ResolvedToken {
        public record Link(ItemID iid, String originalToken) implements ResolvedToken {}
        public record Literal(Object value, String originalToken) implements ResolvedToken {}
        public record Unresolved(String token) implements ResolvedToken {}
    }

    private enum ResolutionHint {
        DEFAULT,
        CREATE_OBJECT
    }

    /**
     * Resolve a string token to an Item or literal.
     *
     * <p>Handles several formats:
     * <ul>
     *   <li>{@code token[iid:xxx]} - Pre-resolved from completion (IID embedded)</li>
     *   <li>{@code iid:xxx} - Direct IID reference</li>
     *   <li>{@code @handle} - Handle reference</li>
     *   <li>{@code "string"} or numbers - Literals</li>
     *   <li>Plain text - Token lookup</li>
     * </ul>
     */
    /**
     * Build the scope chain for token resolution.
     *
     * <p>Order determines priority: focused item first (proper nouns),
     * then session (session-scoped terms), then language (English words),
     * then null (universal symbols).
     */
    private ItemID[] buildScopeChain() {
        List<ItemID> scopes = new ArrayList<>();
        if (context != null) scopes.add(context.iid());
        if (session != null) scopes.add(session.iid());
        scopes.add(Language.ENGLISH);
        scopes.add(null);
        return scopes.toArray(new ItemID[0]);
    }

    /**
     * Look up an expression macro in the dispatch chain (context → session → librarian).
     */
    private Optional<String> lookupExpressionInChain(String token) {
        if (token == null) return Optional.empty();
        if (context != null) {
            var expr = context.vocabulary().lookupExpression(token);
            if (expr.isPresent()) return expr;
        }
        if (session != null) {
            var expr = session.vocabulary().lookupExpression(token);
            if (expr.isPresent()) return expr;
        }
        Vocabulary libVocab = librarianHandle.vocabulary();
        if (libVocab != null) {
            var expr = libVocab.lookupExpression(token);
            if (expr.isPresent()) return expr;
        }
        return Optional.empty();
    }

    private ResolvedToken resolve(String token) {
        return resolve(token, ResolutionHint.DEFAULT);
    }

    private ResolvedToken resolve(String token, ResolutionHint hint) {
        if (token == null || token.isBlank()) {
            return new ResolvedToken.Unresolved(token);
        }

        // Check for pre-resolved format: token[iid:xxx]
        // This comes from our JLine completer
        int bracketStart = token.indexOf('[');
        int bracketEnd = token.indexOf(']');
        if (bracketStart > 0 && bracketEnd > bracketStart) {
            String embeddedIid = token.substring(bracketStart + 1, bracketEnd);
            String displayToken = token.substring(0, bracketStart);
            if (embeddedIid.startsWith("iid:")) {
                return new ResolvedToken.Link(ItemID.fromString(embeddedIid), displayToken);
            }
        }

        // Direct IID reference
        if (token.startsWith("iid:")) {
            return new ResolvedToken.Link(ItemID.fromString(token), token);
        }

        // Handle reference (@handle)
        String lookupToken = token.startsWith("@") ? token.substring(1) : token;

        // Try to resolve via token dictionary with scope chain:
        // 1. Focused item (proper nouns, aliases)
        // 2. English (language words)
        // 3. null (universal symbols)
        ItemID[] scopes = buildScopeChain();
        List<Posting> postings = librarianHandle.lookup(lookupToken, scopes).limit(10).toList();

        Posting preferred = preferredExactPosting(postings, lookupToken, hint);
        if (preferred != null) {
            return new ResolvedToken.Link(preferred.target(), token);
        }

        // Look for exact match in scoped results
        for (Posting p : postings) {
            if (p.token().equalsIgnoreCase(lookupToken)) {
                return new ResolvedToken.Link(p.target(), token);
            }
        }

        // Fall back to unscoped lookup for proper nouns indexed outside the scope chain
        if (postings.isEmpty()) {
            List<Posting> allPostings = librarianHandle.lookup(lookupToken).limit(10).toList();
            Posting allPreferred = preferredExactPosting(allPostings, lookupToken, hint);
            if (allPreferred != null) {
                return new ResolvedToken.Link(allPreferred.target(), token);
            }
            for (Posting p : allPostings) {
                if (p.token().equalsIgnoreCase(lookupToken)) {
                    return new ResolvedToken.Link(p.target(), token);
                }
            }
        }

        // Try to parse as literal (number, boolean, quoted string)
        ExpressionToken.LiteralToken lit = ExpressionToken.LiteralToken.tryParse(token);
        if (lit != null) {
            return new ResolvedToken.Literal(lit.value(), token);
        }

        // Unresolved - treat as string literal
        return new ResolvedToken.Literal(token, token);
    }

    /**
     * Resolve all tokens in input.
     */
    private List<ResolvedToken> resolveAll(List<String> tokens) {
        List<ResolvedToken> resolved = new ArrayList<>();
        if (tokens.isEmpty()) return resolved;

        resolved.add(resolve(tokens.get(0)));
        boolean isCreateCommand = isCreateVerbToken(resolved.get(0));

        for (int i = 1; i < tokens.size(); i++) {
            ResolutionHint hint = (isCreateCommand && i == 1)
                    ? ResolutionHint.CREATE_OBJECT
                    : ResolutionHint.DEFAULT;
            resolved.add(resolve(tokens.get(i), hint));
        }
        return resolved;
    }

    /**
     * Replace pronoun sememes with their concrete referents from discourse history.
     *
     * <p>If "it" resolves to the most recently created item, the Link pointing
     * to the PronounSememe is replaced with a Link pointing to the referent.
     */
    private List<ResolvedToken> resolvePronouns(List<ResolvedToken> tokens) {
        List<ResolvedToken> result = new ArrayList<>(tokens.size());
        boolean anyResolved = false;

        for (ResolvedToken token : tokens) {
            if (token instanceof ResolvedToken.Link link) {
                Optional<Item> item = librarianHandle.get(link.iid());
                if (item.isPresent() && item.get() instanceof Sememe pronoun
                        && pronoun.pos().equals(PartOfSpeech.PRONOUN)) {
                    Optional<Item> referent = discourseHistory.resolve(pronoun, context);
                    if (referent.isPresent()) {
                        result.add(new ResolvedToken.Link(
                                referent.get().iid(), link.originalToken()));
                        anyResolved = true;
                        continue;
                    }
                }
            }
            result.add(token);
        }

        if (anyResolved) {
            logger.debug("Pronoun resolution: {}", result);
        }
        return anyResolved ? result : tokens;
    }

    /**
     * Push an item to discourse history after it was referenced in a result.
     */
    private void pushToHistory(Item item) {
        if (item != null && discourseHistory != null) {
            discourseHistory.push(item);
        }
    }

    private boolean isCreateVerbToken(ResolvedToken token) {
        if (!(token instanceof ResolvedToken.Link link)) return false;
        return link.iid().equals(ItemID.fromString(CoreVocabulary.Create.KEY));
    }

    private Posting preferredExactPosting(List<Posting> postings, String token, ResolutionHint hint) {
        if (hint != ResolutionHint.CREATE_OBJECT) return null;

        List<Posting> exact = postings.stream()
                .filter(p -> p.token().equalsIgnoreCase(token))
                .toList();
        if (exact.size() <= 1) return null;

        // For "create <noun>", prefer nouns that have a non-base CREATE verb (createable types).
        ItemID createId = ItemID.fromString(CoreVocabulary.Create.KEY);
        for (Posting p : exact) {
            Optional<Item> candidate = librarianHandle.get(p.target());
            if (candidate.isEmpty()) continue;
            Optional<VerbEntry> create = candidate.get().vocabulary().lookup(createId);
            if (create.isPresent() && create.get().method().getDeclaringClass() != Item.class) {
                return p;
            }
        }

        return null;
    }

    // ==================================================================================
    // Command Execution
    // ==================================================================================

    /**
     * Evaluate a command from string arguments, returning a structured result.
     *
     * <p>This is the unified entry point for all UI modes (CLI, TUI, GUI).
     * Resolves tokens, classifies by part of speech, and dispatches.
     *
     * <p>Composition rules (data-driven, based on part of speech):
     * <ul>
     *   <li>[Verb, Noun, ...args] → dispatch Verb on Noun</li>
     *   <li>[Noun, Verb, ...args] → dispatch Verb on Noun (bidirectional)</li>
     *   <li>[Verb, Literal, ...] → dispatch Verb on session context</li>
     *   <li>[Verb alone] → dispatch on context if it has the verb, else navigate to verb</li>
     *   <li>[Noun alone] → navigate to noun</li>
     * </ul>
     *
     * @param args The string tokens to evaluate
     * @return Structured result for UI consumption
     */
    public EvalResult evaluateCommand(List<String> args) {
        if (args.isEmpty()) {
            return EvalResult.empty();
        }

        // Check for expression macro expansion (first token matches an expression)
        String firstToken = args.get(0);
        Optional<String> expression = lookupExpressionInChain(firstToken);
        if (expression.isPresent()) {
            if (depth >= MAX_EXPRESSION_DEPTH) {
                return EvalResult.error("Expression recursion depth exceeded (max " + MAX_EXPRESSION_DEPTH + ")");
            }
            logger.debug("Expanding expression macro '{}' → '{}'", firstToken, expression.get());
            List<String> expanded = new ArrayList<>(List.of(expression.get().trim().split("\\s+")));
            // Append any remaining args after the trigger token
            if (args.size() > 1) {
                expanded.addAll(args.subList(1, args.size()));
            }
            Eval child = new Eval(librarianHandle, context, focusedComponent, session,
                    discourseHistory, interactive, jsonOutput, depth + 1, evaluationContext);
            return child.evaluateCommand(expanded);
        }

        // Resolve all tokens to Items/literals
        List<ResolvedToken> resolved = resolveAll(args);
        logger.debug("Resolved {} tokens: {}", resolved.size(), resolved);

        // Resolve pronouns ("it", "that", "this", "last") to their referents
        resolved = resolvePronouns(resolved);

        return evaluateResolved(resolved);
    }

    /**
     * Core evaluation logic shared by string-based and token-based paths.
     *
     * <p>Uses {@link FrameAssembler} to build a {@link SemanticFrame} from
     * resolved tokens in any order. If a verb is found, dispatches via the
     * frame; otherwise falls back to navigation/literal handling.
     *
     * <p>Supports multi-verb conjunction: "create chess and place in main"
     * splits into two frames executed sequentially, with the result of the
     * first available to the second.
     */
    private EvalResult evaluateResolved(List<ResolvedToken> resolved) {
        if (resolved.isEmpty()) {
            return EvalResult.empty();
        }

        // Check for multi-verb conjunction ("create X and place in Y")
        List<SemanticFrame> frames = FrameAssembler.assembleAll(
                resolved, iid -> librarianHandle.get(iid), this::headVerbScore);

        if (frames.isEmpty()) {
            // No verb found — fall back to navigation/literal handling
            return evaluateWithoutVerb(resolved);
        }

        if (frames.size() == 1) {
            return evaluateFrame(frames.get(0));
        }

        // Multi-verb: execute sequentially, threading results
        EvalResult lastResult = EvalResult.empty();
        for (SemanticFrame frame : frames) {
            lastResult = evaluateFrame(frame);
            if (!lastResult.isSuccess()) {
                return lastResult; // stop on first error
            }
        }
        return lastResult;
    }

    /**
     * Runtime head-verb score contribution used by FrameAssembler.
     *
     * <p>Higher score means "more likely to be executable now."
     * Scores mirror the inner-to-outer dispatch order.
     */
    private int headVerbScore(Sememe verb) {
        int score = 0;
        ItemID verbId = verb.iid();

        // Focused component verbs (innermost)
        if (focusedComponent != null && context != null) {
            if (context.vocabulary().verbsFor(focusedComponent)
                    .anyMatch(v -> v.sememeId().equals(verbId))) {
                score += 1200;
            }
        }

        // Context item verbs
        if (context != null && context.vocabulary().lookup(verbId).isPresent()) {
            score += 1000;
        }

        // Session item verbs
        if (session != null && session.vocabulary().lookup(verbId).isPresent()) {
            score += 800;
        }

        // Librarian/system scope
        Vocabulary libVocab = librarianHandle.vocabulary();
        if (libVocab != null && libVocab.lookup(verbId).isPresent()) {
            score += 700;
        }

        return score;
    }

    /**
     * Handle expressions with no verb — navigate to item or return literal.
     */
    private EvalResult evaluateWithoutVerb(List<ResolvedToken> resolved) {
        // Try to find a navigable item
        for (ResolvedToken token : resolved) {
            if (token instanceof ResolvedToken.Link link) {
                Optional<Item> item = librarianHandle.get(link.iid());
                if (item.isPresent()) {
                    pushToHistory(item.get());
                    return EvalResult.item(item.get());
                }
            }
        }

        // Fall back to first token as literal or error
        ResolvedToken first = resolved.get(0);
        if (first instanceof ResolvedToken.Unresolved u) {
            return EvalResult.error("Unknown: " + u.token());
        } else if (first instanceof ResolvedToken.Literal lit) {
            return EvalResult.value(lit.value());
        } else {
            return EvalResult.error("Could not evaluate expression");
        }
    }

    /**
     * Dispatch using an assembled semantic frame.
     *
     * <p>Inner-to-outer dispatch order:
     * <ol>
     *   <li>Focused component's verbs (if a component is focused)</li>
     *   <li>Bound items from input (explicit user intent: "create CHESS")</li>
     *   <li>Context item's vocabulary</li>
     *   <li>Session item's vocabulary (outermost scope)</li>
     *   <li>Librarian's vocabulary (system-level)</li>
     * </ol>
     *
     * <p>First match wins. Wraps the result with TARGET if a prepositional
     * phrase bound to TARGET was present.
     */
    private EvalResult evaluateFrame(SemanticFrame frame) {
        ItemID verbId = frame.verb().iid();
        logger.debug("evaluateFrame: verb={}, verbId={}, bindings={}, unmatchedArgs={}",
                frame.verb().displayToken(), verbId.encodeText(),
                frame.bindings().keySet(), frame.unmatchedArgs());

        // Inner-to-outer dispatch with explicit-intent priority:
        //   1. Focused component (innermost scope)
        //   2. Bound items from input (explicit user intent: "create CHESS")
        //   3. Context item
        //   4. Session item (outermost scope)
        //   5. Librarian (system-level)
        Item target = null;

        // 1. Focused component's verbs (innermost scope)
        if (focusedComponent != null && context != null) {
            if (context.vocabulary().verbsFor(focusedComponent)
                    .anyMatch(v -> v.sememeId().equals(verbId))) {
                target = context;
            }
        }

        // 2. Bound items from the frame (explicit user intent)
        if (target == null) {
            for (var entry : frame.bindings().entrySet()) {
                if (entry.getKey().equals(ThematicRole.Goal.SEED.iid())) continue;
                if (!(entry.getValue() instanceof Item item)) continue;
                if (item.vocabulary().lookup(verbId).isPresent()) {
                    target = item;
                    break;
                }
            }
        }

        // 3. Context item's vocabulary
        if (target == null && context != null
                && context.vocabulary().lookup(verbId).isPresent()) {
            target = context;
        }

        // 4. Session item's vocabulary (outermost scope)
        if (target == null && session != null
                && session.vocabulary().lookup(verbId).isPresent()) {
            target = session;
        }

        // 5. Librarian's vocabulary (system-level)
        if (target == null) {
            Vocabulary lv = librarianHandle.vocabulary();
            if (lv != null && lv.lookup(verbId).isPresent()) {
                target = librarianHandle.get(librarianHandle.iid()).orElse(null);
            }
        }

        // Last resort: first bound Item (dispatch even if it may not have the verb)
        if (target == null) {
            target = frame.bindings().values().stream()
                    .filter(v -> v instanceof Item)
                    .map(v -> (Item) v)
                    .findFirst().orElse(null);
        }

        // Verb alone with no bindings and no context → navigate to verb sememe
        if (target == null) {
            return EvalResult.item(frame.verb());
        }

        logger.debug("evaluateFrame: dispatch target={}", target.displayToken());

        // 2. Dispatch with typed bindings
        EvalResult result = dispatchVerbForResult(target, verbId, frame);

        // 3. Wrap with TARGET if present (only for Item targets)
        Optional<Item> prepTarget = frame.itemBinding(ThematicRole.Goal.SEED.iid());
        if (prepTarget.isPresent() && result instanceof EvalResult.Value(Object value)) {
            return EvalResult.valueWithTarget(value, prepTarget.get());
        }

        return result;
    }

    /**
     * Execute a command (legacy CLI path, returns exit code).
     *
     * <p>Delegates to {@link #evaluateCommand(List)} and maps the result
     * to an exit code with console output.
     */
    private int executeCommand(List<String> args) {
        EvalResult result = evaluateCommand(args);

        return switch (result) {
            case EvalResult.Empty() -> 0;
            case EvalResult.ItemResult(Item item) -> {
                showItemInfo(item);
                yield 0;
            }
            case EvalResult.Created(Item item) -> {
                showItemInfo(item);
                yield 0;
            }
            case EvalResult.Value(Object value) -> {
                if (value != null) printResult(value);
                yield 0;
            }
            case EvalResult.ValueWithTarget(Object value, Item targetItem) -> {
                // Legacy CLI path: print target info
                if (value != null) printResult(value);
                System.out.println("  → target: " + targetItem.displayToken());
                yield 0;
            }
            case EvalResult.Error(String message) -> {
                System.err.println("Error: " + message);
                yield 1;
            }
            case EvalResult.Ambiguous ambiguous -> {
                System.err.println("Ambiguous input — multiple meanings for:");
                for (var t : ambiguous.tokens()) {
                    System.err.println("  \"" + t.text() + "\" (" + t.candidates().size() + " candidates)");
                }
                yield 1;
            }
        };
    }

    private String resolvedToString(ResolvedToken token) {
        return switch (token) {
            case ResolvedToken.Link ref -> ref.iid().encodeText();
            case ResolvedToken.Literal lit -> String.valueOf(lit.value());
            case ResolvedToken.Unresolved u -> u.token();
        };
    }

    private void showItemInfo(Item item) {
        System.out.println(item.displayToken());
        System.out.println("  IID:  " + item.iid().encodeText());
        System.out.println("  Type: " + item.getClass().getSimpleName());

        // Show vocabulary (verbs)
        var vocab = item.vocabulary();
        if (vocab != null && vocab.size() > 0) {
            System.out.println("  Verbs:");
            for (var entry : vocab) {
                System.out.println("    " + entry.methodName());
            }
        }

    }

    private void printResult(Object value) {
        if (value == null) {
            return;
        }

        // Handle streams - iterate and print each element
        if (value instanceof Stream<?> stream) {
            stream.forEach(this::printResult);
            return;
        }

        // Handle collections - iterate and print each element
        if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                printResult(element);
            }
            return;
        }

        // Handle Optional
        if (value instanceof Optional<?> opt) {
            opt.ifPresent(this::printResult);
            return;
        }

        // Handle Items - print with label
        if (value instanceof Item item) {
            String label = item.displayToken();
            System.out.println(label != null ? label : item.iid().encodeText());
            return;
        }

        // Handle ItemIDs - print the encoded form
        if (value instanceof ItemID iid) {
            System.out.println(iid.encodeText());
            return;
        }

        // Default: toString
        System.out.println(value);
    }

    // ==================================================================================
    // Expression Token Integration
    // ==================================================================================

    /**
     * Execute an expression from UI-generated tokens.
     *
     * <p>This bridges the UI layer (InputController/ExpressionToken) with
     * the execution layer (ResolvedToken/dispatch). All UI modes (GUI, TUI, CLI)
     * can use this to execute expressions uniformly.
     *
     * @param tokens The tokens from InputController.accept()
     * @return The result of execution
     */
    public EvalResult executeTokens(List<ExpressionToken> tokens) {
        if (tokens.isEmpty()) {
            return EvalResult.empty();
        }

        // Check for expression macro expansion on first token
        String firstText = tokens.get(0).displayText();
        Optional<String> expression = lookupExpressionInChain(firstText);
        if (expression.isPresent()) {
            if (depth >= MAX_EXPRESSION_DEPTH) {
                return EvalResult.error("Expression recursion depth exceeded (max " + MAX_EXPRESSION_DEPTH + ")");
            }
            logger.debug("Expanding expression macro '{}' → '{}'", firstText, expression.get());
            List<String> expanded = new ArrayList<>(List.of(expression.get().trim().split("\\s+")));
            // Append remaining tokens as text
            for (int i = 1; i < tokens.size(); i++) {
                expanded.add(tokens.get(i).displayText());
            }
            Eval child = new Eval(librarianHandle, context, focusedComponent, session,
                    discourseHistory, interactive, jsonOutput, depth + 1, evaluationContext);
            return child.evaluateCommand(expanded);
        }

        // If the tokens look like a mathematical expression (contain operators,
        // parentheses, or are bare numerics), try the expression parser first.
        if (ExpressionParser.looksLikeExpression(tokens)) {
            var exprResult = ExpressionParser.tryParse(tokens);
            if (exprResult.isPresent()) {
                Expression expr = exprResult.get();
                EvaluationContext evalCtx = getOrCreateEvalContext();
                try {
                    Object value = expr.evaluate(evalCtx);
                    logger.debug("Expression evaluated: {} → {}", expr.toExpressionString(), value);
                    return EvalResult.value(value);
                } catch (Exception e) {
                    logger.debug("Expression evaluation failed: {}", e.getMessage());
                    // Expression parsed successfully but evaluation failed —
                    // report the error rather than falling through to verb dispatch
                    // (which would misinterpret operands as navigation targets).
                    return EvalResult.error(e.getMessage());
                }
            }
        }

        // Check for surviving CandidateTokens — InputController should have resolved
        // these, but if any survive, report ambiguity instead of guessing.
        List<EvalResult.Ambiguous.UnresolvedToken> ambiguous = null;
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i) instanceof ExpressionToken.CandidateToken candidate) {
                if (ambiguous == null) ambiguous = new ArrayList<>();
                ambiguous.add(new EvalResult.Ambiguous.UnresolvedToken(
                        i, candidate.text(), candidate.candidates()));
            }
        }
        if (ambiguous != null) {
            return EvalResult.ambiguous(ambiguous);
        }

        // Convert ExpressionTokens to ResolvedTokens
        List<ResolvedToken> resolved = new ArrayList<>();
        for (ExpressionToken token : tokens) {
            resolved.add(convertToken(token));
        }

        // Resolve pronouns
        resolved = resolvePronouns(resolved);

        logger.debug("Executing {} tokens: {}", resolved.size(), resolved);

        return evaluateResolved(resolved);
    }

    /**
     * Get or create an evaluation context for expression evaluation.
     *
     * <p>Persistent state (variables, functions) lives on the focused item
     * as ExpressionComponents. The context itself is ephemeral.
     */
    private EvaluationContext getOrCreateEvalContext() {
        if (evaluationContext != null) {
            return evaluationContext;
        }
        Librarian librarian = librarianHandle instanceof LocalLibrarian local
                ? local.librarian() : null;
        if (librarian != null && context != null) {
            return EvaluationContext.forItem(librarian, context);
        } else if (librarian != null) {
            return EvaluationContext.forLibrarian(librarian);
        }
        return new EvaluationContext(null, null);
    }

    /**
     * Convert a UI ExpressionToken to an execution ResolvedToken.
     *
     * <p>This is a TRIVIAL passthrough — InputController already did all resolution.
     * No dictionary lookups, no disambiguation. Just type mapping.
     */
    private ResolvedToken convertToken(ExpressionToken token) {
        return switch (token) {
            case ExpressionToken.RefToken ref ->
                    new ResolvedToken.Link(ref.target(), ref.displayText());
            case ExpressionToken.LiteralToken lit ->
                    new ResolvedToken.Literal(lit.value(), lit.displayText());
            case ExpressionToken.OpToken op ->
                    new ResolvedToken.Link(op.operatorId(), op.displayText());
            case ExpressionToken.CandidateToken candidate ->
                    // Still ambiguous — will be caught after conversion and reported as Ambiguous
                    new ResolvedToken.Unresolved(candidate.text());
            case ExpressionToken.NameToken name ->
                    new ResolvedToken.Unresolved(name.name());
            default ->
                    // Parens, commas — structural tokens treated as literals
                    new ResolvedToken.Literal(token.displayText(), token.displayText());
        };
    }

    /**
     * Dispatch a verb using typed bindings from a SemanticFrame.
     *
     * <p>Builds a {@code Map<ItemID, Object>} of bindings (excluding
     * the TARGET role and dispatch target item) and a positional overflow
     * list from unmatched tokens, then delegates to
     * {@link VerbInvoker#invokeWithBindings}.
     *
     * <p>Also performs a second pass ({@link #bindLiteralsToParams}) that
     * matches unmatched literals to verb parameters by role using ParamSpec
     * metadata — e.g., "Josh" in "create user Josh" gets matched to the
     * {@code @Param(role="THEME")} parameter.
     */
    private EvalResult dispatchVerbForResult(Item target, ItemID verbId, SemanticFrame frame) {
        var vocab = target.vocabulary();
        var verbEntry = vocab.lookup(verbId);

        if (verbEntry.isEmpty()) {
            return EvalResult.error("Verb not available on " + target.displayToken());
        }

        VerbEntry verb = verbEntry.get();
        logger.debug("Dispatching verb {} on {}", verb.methodName(), target.iid());

        // Build bindings: exclude TARGET role and the dispatch target itself
        Map<ItemID, Object> bindings = new LinkedHashMap<>();
        for (var entry : frame.bindings().entrySet()) {
            if (entry.getKey().equals(ThematicRole.Goal.SEED.iid())) continue;
            Object value = entry.getValue();
            if (value instanceof Item item && item.iid().equals(target.iid())) continue;
            bindings.put(entry.getKey(), value);
        }

        // Collect overflow: unmatched tokens as raw values
        List<Object> overflow = new ArrayList<>();
        for (ResolvedToken token : frame.unmatchedArgs()) {
            overflow.add(switch (token) {
                case ResolvedToken.Link ref -> ref.iid().encodeText();
                case ResolvedToken.Literal lit -> lit.value();
                case ResolvedToken.Unresolved u -> u.token();
            });
        }

        // Second pass: match unmatched literals to verb params by role
        bindLiteralsToParams(verb, bindings, overflow);

        Item principalItem = librarianHandle.principal().orElse(null);
        Signer principal = principalItem instanceof Signer s ? s : null;
        ItemID callerId = principalItem != null ? principalItem.iid() : null;
        Librarian librarian = librarianHandle instanceof LocalLibrarian local
                ? local.librarian() : null;
        ActionContext ctx = ActionContext.of(callerId, principal, target, librarian);

        VerbInvoker invoker = new VerbInvoker();
        var result = invoker.invokeWithBindings(verb, ctx, bindings, overflow);

        if (result.success()) {
            Object value = result.value();
            if (value instanceof Item item) {
                pushToHistory(item);
                // CREATE returns a new item — don't navigate the current view
                if (verbId.equals(ItemID.fromString(CoreVocabulary.Create.KEY))) {
                    return EvalResult.created(item);
                }
                return EvalResult.item(item);
            } else {
                return EvalResult.value(value);
            }
        } else {
            Throwable error = result.error();
            return EvalResult.error(error != null ? error.getMessage() : "unknown error");
        }
    }


    /**
     * Match overflow literals to verb parameters by role.
     *
     * <p>For each ParamSpec with a role that isn't already in bindings,
     * if there's an overflow value available, move it into bindings
     * under that role. This allows positional literals like "Josh" in
     * "create user Josh" to be matched to {@code @Param(role="THEME")}.
     */
    private void bindLiteralsToParams(
            VerbEntry verb,
            Map<ItemID, Object> bindings,
            List<Object> overflow) {

        if (overflow.isEmpty()) return;

        for (ParamSpec param : verb.params()) {
            if (param.role() == null) continue;
            ItemID roleId;
            try {
                roleId = ThematicRole.fromName(param.role()).iid();
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (bindings.containsKey(roleId)) continue;
            if (overflow.isEmpty()) break;

            bindings.put(roleId, overflow.remove(0));
        }
    }

    /**
     * Result of expression evaluation - structured for UI consumption.
     */
    public sealed interface EvalResult {
        record Empty() implements EvalResult {}
        record Value(Object value) implements EvalResult {}
        record ItemResult(Item item) implements EvalResult {}
        /** An item was created — session should NOT navigate the current view. */
        record Created(Item item) implements EvalResult {}
        record ValueWithTarget(Object value, Item targetItem) implements EvalResult {}
        record Error(String message) implements EvalResult {}
        /**
         * CandidateTokens survived all the way to dispatch — user must disambiguate.
         *
         * @param tokens per-token disambiguation info (index, text, remaining candidates)
         */
        record Ambiguous(List<UnresolvedToken> tokens) implements EvalResult {
            public record UnresolvedToken(int index, String text,
                                          List<dev.everydaythings.graph.language.Posting> candidates) {}
        }

        static EvalResult empty() { return new Empty(); }
        static EvalResult value(Object v) { return new Value(v); }
        static EvalResult item(Item i) { return new ItemResult(i); }
        static EvalResult created(Item i) { return new Created(i); }
        static EvalResult valueWithTarget(Object v, Item t) { return new ValueWithTarget(v, t); }
        static EvalResult error(String msg) { return new Error(msg); }
        static EvalResult ambiguous(List<Ambiguous.UnresolvedToken> tokens) { return new Ambiguous(tokens); }

        default boolean isSuccess() {
            return !(this instanceof Error) && !(this instanceof Ambiguous);
        }
    }

    // ==================================================================================
    // Static convenience methods
    // ==================================================================================

    /**
     * Run a one-shot command with a local librarian.
     */
    public static int run(Librarian librarian, List<String> args) {
        return builder()
                .librarian(librarian)
                .build()
                .run(args);
    }

    /**
     * Run a one-shot command with a local librarian at the default path.
     */
    public static int runDefault(List<String> args) {
        Path defaultPath = Path.of(
                System.getProperty("user.home"), ".librarian");

        try (var ref = LibrarianHandle.local(defaultPath)) {
            return builder()
                    .librarian(ref)
                    .build()
                    .run(args);
        }
    }
}
