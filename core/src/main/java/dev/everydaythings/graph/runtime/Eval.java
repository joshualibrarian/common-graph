package dev.everydaythings.graph.runtime;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.FrameBodyOld;
import dev.everydaythings.graph.frame.FrameRecordOld;
import dev.everydaythings.graph.frame.eval.FrameAssemblyContext;
import dev.everydaythings.graph.frame.eval.FrameAssemblyPipeline;
import dev.everydaythings.graph.frame.eval.FrameEvaluator;
import dev.everydaythings.graph.frame.eval.ParseContribution;
import dev.everydaythings.graph.frame.eval.Scope;
import dev.everydaythings.graph.item.ItemOld;
import dev.everydaythings.graph.item.user.SignerOld;
import dev.everydaythings.graph.parse.ExpressionToken;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.dispatch.Created;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.DiscourseHistory;
import dev.everydaythings.graph.language.Language;
import dev.everydaythings.graph.language.FrameAssembler;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.parse.TokenLattice;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    private final ItemOld context;
    /** Focused component handle within the context item (inner-to-outer dispatch). */
    private final String focusedComponent;
    /** Session-level item providing outermost vocabulary scope. */
    private final ItemOld session;
    /** Discourse history for pronoun resolution ("it", "that", "this", "last"). */
    private final DiscourseHistory discourseHistory;
    private final boolean interactive;
    private final boolean jsonOutput;
    private final int depth;
    /** Unified frame evaluator for expression evaluation via FrameBody trees. */
    private final FrameEvaluator frameEvaluator;
    /** Frame assembly pipeline — dispatches via onFrameAssembled. */
    private final FrameAssemblyPipeline assemblyPipeline;

    private Eval(LibrarianHandle librarianHandle, ItemOld context, String focusedComponent,
                 ItemOld session, DiscourseHistory discourseHistory,
                 boolean interactive, boolean jsonOutput, int depth) {
        this.librarianHandle = librarianHandle;
        this.context = context;
        this.focusedComponent = focusedComponent;
        this.session = session;
        this.discourseHistory = discourseHistory != null ? discourseHistory : new DiscourseHistory();
        this.interactive = interactive;
        this.jsonOutput = jsonOutput;
        this.depth = depth;
        this.frameEvaluator = new FrameEvaluator();
        this.assemblyPipeline = new FrameAssemblyPipeline();
    }

    // ==================================================================================
    // Builder
    // ==================================================================================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private LibrarianHandle librarianHandle;
        private ItemOld context;
        private String focusedComponent;
        private ItemOld session;
        private DiscourseHistory discourseHistory;
        private boolean interactive = true;
        private boolean jsonOutput = false;

        public Builder librarian(LibrarianHandle ref) {
            this.librarianHandle = ref;
            return this;
        }

        public Builder librarian(LibrarianOld librarian) {
            this.librarianHandle = LibrarianHandle.wrap(librarian);
            return this;
        }

        public Builder context(ItemOld item) {
            this.context = item;
            return this;
        }

        /** Set the focused component handle for inner-to-outer dispatch. */
        public Builder focusedComponent(String componentHandle) {
            this.focusedComponent = componentHandle;
            return this;
        }

        /** Set the session item (outermost dispatch scope). */
        public Builder session(ItemOld session) {
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

        public Eval build() {
            if (librarianHandle == null) {
                throw new IllegalStateException("LibrarianHandle is required");
            }
            return new Eval(librarianHandle, context, focusedComponent, session,
                    discourseHistory, interactive, jsonOutput, 0);
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
        public record Link(ItemID iid, String originalToken, Set<ItemID> features) implements ResolvedToken {
            public Link(ItemID iid, String originalToken) { this(iid, originalToken, Set.of()); }
        }
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
     * Look up an expression macro in the dispatch chain.
     *
     * <p>TODO: Expression macros will be EXPRESSION frames with indexed NAME
     * bindings, resolved via the Vocabulary scope stack. For now, returns empty.
     */
    private Optional<String> lookupExpressionInChain(String token) {
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
            return new ResolvedToken.Link(preferred.target(), token, preferred.features());
        }

        // Look for exact match in scoped results
        for (Posting p : postings) {
            if (p.token().equalsIgnoreCase(lookupToken)) {
                return new ResolvedToken.Link(p.target(), token, p.features());
            }
        }

        // Fall back to unscoped lookup for proper nouns indexed outside the scope chain
        if (postings.isEmpty()) {
            List<Posting> allPostings = librarianHandle.lookup(lookupToken).limit(10).toList();
            Posting allPreferred = preferredExactPosting(allPostings, lookupToken, hint);
            if (allPreferred != null) {
                return new ResolvedToken.Link(allPreferred.target(), token, allPreferred.features());
            }
            for (Posting p : allPostings) {
                if (p.token().equalsIgnoreCase(lookupToken)) {
                    return new ResolvedToken.Link(p.target(), token, p.features());
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
                // Check POS from the link first (fast path)
                boolean isPronoun = link.features().contains(PartOfSpeech.PRONOUN);

                // Fall back to checking the sememe's contribute() — no hardcoded IID checks
                if (!isPronoun) {
                    Optional<ItemOld> item = librarianHandle.get(link.iid());
                    if (item.isPresent() && item.get() instanceof Sememe sememe) {
                        ParseContribution contribution = sememe.contribute(null);
                        isPronoun = contribution.structuralRole() == ParseContribution.StructuralRole.PRONOUN;
                    }
                }

                if (isPronoun) {
                    Optional<ItemOld> item = librarianHandle.get(link.iid());
                    if (item.isPresent() && item.get() instanceof Sememe pronoun) {
                        Optional<ItemOld> referent = discourseHistory.resolve(pronoun, context);
                        if (referent.isPresent()) {
                            result.add(new ResolvedToken.Link(
                                    referent.get().iid(), link.originalToken()));
                            anyResolved = true;
                            continue;
                        }
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
    private void pushToHistory(ItemOld item) {
        if (item != null && discourseHistory != null) {
            discourseHistory.push(item);
        }
    }

    private boolean isCreateVerbToken(ResolvedToken token) {
        if (!(token instanceof ResolvedToken.Link link)) return false;
        return link.iid().equals(ItemID.fromString(CoreVocabulary.Create.KEY));
    }

    private Posting preferredExactPosting(List<Posting> postings, String token, ResolutionHint hint) {
        // No verb-based disambiguation — return null (no preference)
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
                    discourseHistory, interactive, jsonOutput, depth + 1);
            return child.evaluateCommand(expanded);
        }

        // Resolve all tokens to Items/literals
        List<ResolvedToken> resolved = resolveAll(args);
        logger.debug("Resolved {} tokens: {}", resolved.size(), resolved);

        // Resolve pronouns ("it", "that", "this", "last") to their referents
        resolved = resolvePronouns(resolved);

        return evaluateResolved(resolved);
    }

    // ==================================================================================
    // Lattice-based Evaluation (unified pipeline)
    // ==================================================================================

    /**
     * Evaluate raw text using the unified TokenLattice pipeline.
     *
     * <p>This is the ONE pipeline for both interactive and one-shot modes.
     * It tokenizes via the lattice (handling character-class boundaries,
     * multi-word tokens, operator splitting), resolves against the
     * TokenDictionary, infers the active language from posting scopes,
     * and dispatches through the language's parser.
     *
     * <p>For one-shot mode: ambiguous spans become errors.
     *
     * @param input the raw text to evaluate
     * @return the evaluation result
     */
    public EvalResult evaluateRaw(String input) {
        if (input == null || input.isBlank()) {
            return EvalResult.empty();
        }

        // Check for expression macro expansion
        String trimmed = input.trim();
        String firstWord = trimmed.contains(" ") ? trimmed.substring(0, trimmed.indexOf(' ')) : trimmed;
        Optional<String> expression = lookupExpressionInChain(firstWord);
        if (expression.isPresent()) {
            if (depth >= MAX_EXPRESSION_DEPTH) {
                return EvalResult.error("Expression recursion depth exceeded (max " + MAX_EXPRESSION_DEPTH + ")");
            }
            String expanded = expression.get();
            if (trimmed.contains(" ")) {
                expanded += " " + trimmed.substring(trimmed.indexOf(' ') + 1);
            }
            Eval child = new Eval(librarianHandle, context, focusedComponent, session,
                    discourseHistory, interactive, jsonOutput, depth + 1);
            return child.evaluateRaw(expanded);
        }

        // Build lattice — tokenize + resolve in one pass
        TokenLattice lattice = TokenLattice.build(trimmed, this::latticeLookup);

        // Check for ambiguity (one-shot mode = error)
        if (lattice.hasAmbiguity()) {
            List<TokenLattice.Span> ambiguous = lattice.ambiguousSpans();
            StringBuilder msg = new StringBuilder("Ambiguous input — select a meaning for: ");
            for (TokenLattice.Span span : ambiguous) {
                msg.append("\"").append(span.text()).append("\" (");
                for (int i = 0; i < Math.min(span.postings().size(), 3); i++) {
                    if (i > 0) msg.append(" or ");
                    msg.append(span.postings().get(i).target().encodeText());
                }
                msg.append(") ");
            }
            return EvalResult.error(msg.toString().trim());
        }

        // Convert lattice spans to ResolvedTokens
        List<ResolvedToken> resolved = convertSpansToTokens(lattice.bestPath());

        // Resolve pronouns
        resolved = resolvePronouns(resolved);

        // Evaluate — with inferred language if available
        return evaluateResolvedWithLanguage(resolved, lattice.inferLanguage());
    }

    /**
     * Lookup function for the TokenLattice — wraps the librarian's scoped lookup.
     */
    private List<Posting> latticeLookup(String token) {
        ItemID[] scopes = buildScopeChain();
        return librarianHandle.lookup(token, scopes).limit(10).toList();
    }

    /**
     * Convert lattice spans to ResolvedTokens for the semantic pipeline.
     */
    private List<ResolvedToken> convertSpansToTokens(List<TokenLattice.Span> spans) {
        List<ResolvedToken> tokens = new ArrayList<>();
        for (TokenLattice.Span span : spans) {
            tokens.add(convertSpan(span));
        }
        return tokens;
    }

    /**
     * Convert a single lattice span to a ResolvedToken.
     */
    private ResolvedToken convertSpan(TokenLattice.Span span) {
        switch (span.type()) {
            case WORD -> {
                Posting best = span.bestPosting();
                if (best != null) {
                    return new ResolvedToken.Link(best.target(), span.text(), best.features());
                }
                return new ResolvedToken.Unresolved(span.text());
            }
            case LITERAL -> {
                ExpressionToken.LiteralToken lit = ExpressionToken.LiteralToken.tryParse(span.text());
                if (lit != null) {
                    return new ResolvedToken.Literal(lit.value(), span.text());
                }
                return new ResolvedToken.Literal(span.text(), span.text());
            }
            case UNRESOLVED -> {
                // Try IID and handle formats before giving up
                String text = span.text();
                if (text.startsWith("iid:")) {
                    return new ResolvedToken.Link(ItemID.parse(text), text);
                }
                if (text.startsWith("@")) {
                    String handleText = text.substring(1);
                    List<Posting> handlePostings = librarianHandle.lookup(handleText).limit(1).toList();
                    if (!handlePostings.isEmpty()) {
                        Posting hp = handlePostings.getFirst();
                        return new ResolvedToken.Link(hp.target(), text, hp.features());
                    }
                }
                // Treat as literal string
                return new ResolvedToken.Literal(text, text);
            }
        }
        return new ResolvedToken.Unresolved(span.text());
    }

    /**
     * Evaluate resolved tokens with an inferred language (may be null).
     *
     * <p>If a language is inferred from posting scopes, use it instead of
     * the hardcoded active language. This enables true multilingual dispatch.
     */
    private EvalResult evaluateResolvedWithLanguage(List<ResolvedToken> resolved, ItemID inferredLanguageId) {
        if (resolved.isEmpty()) {
            return EvalResult.empty();
        }

        // Resolve the inferred language to a Language item, fall back to active
        Language language = null;
        if (inferredLanguageId != null) {
            language = librarianHandle.get(inferredLanguageId, Language.class).orElse(null);
        }
        if (language == null) {
            language = librarianHandle.activeLanguage();
        }

        Language.ParseResult parseResult = (language != null)
                ? language.parse(resolved, null, iid -> librarianHandle.get(iid), v -> 0)
                : new Language.ParseResult(
                    FrameAssembler.assembleAll(resolved, iid -> librarianHandle.get(iid), v -> 0),
                    List.of());

        return evaluateParseResult(parseResult);
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

        // Parse via the active language (delegates to FrameAssembler by default)
        Language activeLanguage = librarianHandle.activeLanguage();
        Language.ParseResult parseResult = (activeLanguage != null)
                ? activeLanguage.parse(resolved, null, iid -> librarianHandle.get(iid), v -> 0)
                : new Language.ParseResult(
                    FrameAssembler.assembleAll(resolved, iid -> librarianHandle.get(iid), v -> 0),
                    List.of());

        return evaluateParseResult(parseResult);
    }

    /**
     * Evaluate a ParseResult — the language-agnostic dispatch point.
     *
     * <p>Three states:
     * <ul>
     *   <li>Frames only → execute sequentially</li>
     *   <li>Unbound only → query or navigate</li>
     *   <li>Both → ambiguous, error</li>
     * </ul>
     */
    private EvalResult evaluateParseResult(Language.ParseResult parseResult) {
        if (parseResult.isAmbiguous()) {
            return EvalResult.error("Ambiguous: some tokens couldn't be placed. "
                    + "Use explicit language (\"as\", \"named\", \"and\") to clarify.");
        }

        if (parseResult.hasFrames()) {
            List<SemanticFrame> frames = parseResult.frames();
            if (frames.size() == 1) {
                return evaluateFrame(frames.getFirst());
            }
            EvalResult lastResult = EvalResult.empty();
            for (SemanticFrame frame : frames) {
                lastResult = evaluateFrame(frame);
                if (!lastResult.isSuccess()) return lastResult;
            }
            return lastResult;
        }

        if (parseResult.hasUnbound()) {
            return evaluateUnbound(parseResult.unbound());
        }

        return EvalResult.empty();
    }

    /**
     * Handle unbound tokens — navigate, query, or return literal.
     *
     * <p>Single resolved item → navigate to it.
     * Multiple resolved items → frame pattern query (intersection).
     */
    private EvalResult evaluateUnbound(List<ResolvedToken> resolved) {
        // Any resolved items → query (single or multiple)
        List<ResolvedToken.Link> links = resolved.stream()
                .filter(t -> t instanceof ResolvedToken.Link)
                .map(t -> (ResolvedToken.Link) t)
                .toList();

        if (!links.isEmpty()) {
            LibrarianOld librarian = librarianHandle instanceof LocalLibrarian local
                    ? local.librarian() : null;
            if (librarian != null) {
                QueryItem queryItem = new QueryItem(librarian, resolved);
                Set<ItemID> resultIds = queryItem.run();
                List<ItemOld> resultItems = resultIds.stream()
                        .map(id -> librarianHandle.get(id))
                        .flatMap(Optional::stream)
                        .toList();
                queryItem.resultItems(resultItems);
                return EvalResult.query(queryItem, resultItems, queryItem.extractPattern());
            }
            return EvalResult.error("Query requires a local librarian");
        }

        // No resolved items — literal or error
        ResolvedToken first = resolved.getFirst();
        if (first instanceof ResolvedToken.Unresolved u) {
            return EvalResult.error("Unknown: " + u.token());
        } else if (first instanceof ResolvedToken.Literal lit) {
            return EvalResult.value(lit.value());
        } else {
            return EvalResult.error("Could not evaluate expression");
        }
    }

    /**
     * Evaluate a semantic frame through the unified FrameEvaluator.
     *
     * <p>Converts the SemanticFrame to a FrameBody, finds the dispatch target
     * via inner-to-outer scope search (which becomes the Scope's owner),
     * and evaluates through FrameEvaluator. Same path as expressions —
     * a frame is a frame.
     *
     * <p>Inner-to-outer scope search for dispatch target:
     * <ol>
     *   <li>Focused component's verbs (if a component is focused)</li>
     *   <li>Bound items from input (explicit user intent: "create CHESS")</li>
     *   <li>Context item's vocabulary</li>
     *   <li>Session item's vocabulary (outermost scope)</li>
     *   <li>Librarian's vocabulary (system-level)</li>
     * </ol>
     */
    private EvalResult evaluateFrame(SemanticFrame frame) {
        FrameBodyOld frameBody = toFrameBody(frame);

        // Context filling: fill unfilled EXPECTS roles from context and signer
        frameBody = fillFromContext(frameBody);

        LibrarianOld librarian = librarianHandle instanceof LocalLibrarian local
                ? local.librarian() : null;
        if (librarian == null) {
            return EvalResult.error("No librarian available");
        }

        SignerOld signer = session instanceof SignerOld s ? s : librarian;
        Scope assemblyScope = Scope.of(librarian, context);
        FrameAssemblyContext ctx = assemblyPipeline.assemble(
                frameBody, assemblyScope, signer, session);
        if (ctx.handled()) {
            return mapResultToEvalResult(ctx.result(), frame);
        }

        // Try FrameEvaluator for pure computation (operators, functions).
        // These have IMPLEMENTED_BY frames → JavaRuntime resolves them.
        try {
            Scope evalScope = Scope.of(librarian, context);
            Object value = frameEvaluator.evaluate(frameBody, evalScope);
            if (value != null) {
                return EvalResult.value(value);
            }
        } catch (Exception e) {
            // Not a computable expression — fall through
            logger.debug("FrameEvaluator: {}", e.getMessage());
        }

        // Default: persist as an assertion.
        // Frame creation IS the action — typing "titled 'The Hobbit'" creates
        // a TITLE frame on the context item. No special handler needed.
        if (isAssertable(frameBody, librarian)) {
            persistFrame(frameBody, signer, librarian);
            Language lang = librarianHandle.activeLanguage();
            String expressed = lang != null
                    ? lang.express(frameBody, context != null ? context.iid() : null, librarian)
                    : "asserted";
            return EvalResult.value(expressed);
        }

        // Truly incomplete — treat as a query
        return evaluateStructuredQuery(frame);
    }

    /**
     * Check if a frame body is a complete assertion worth persisting.
     *
     * <p>A frame is assertable if its predicate is a known sememe (not garbage)
     * and it has at least one binding with user-provided content (not just
     * auto-filled context bindings).
     */
    private boolean isAssertable(FrameBodyOld body, LibrarianOld librarian) {
        if (body == null || body.predicate() == null) return false;
        if (body.frameBindings() == null || body.frameBindings().isEmpty()) return false;

        // Predicate must be a known item in the library
        if (librarian.get(body.predicate()).isEmpty()) return false;

        // Must have at least one non-context binding (something the user provided)
        ItemID contextIid = context != null ? context.iid() : null;
        for (Binding b : body.frameBindings()) {
            ItemID tid = b.targetId();
            // Skip bindings that are just the context item or the signer
            if (tid != null && contextIid != null && tid.equals(contextIid)) continue;
            if (session instanceof ItemOld s && tid != null && tid.equals(s.iid())) continue;
            // Found user-provided content
            return true;
        }
        return false;
    }

    /**
     * Persist a frame body and sign it — the default assertion action.
     */
    private void persistFrame(FrameBodyOld body, SignerOld signer, LibrarianOld librarian) {
        librarian.storeFrame(body);
        if (signer != null && signer.canSign()) {
            try {
                FrameRecordOld record = FrameRecordOld.create(body, signer);
                // Record is stored as part of storeFrame's pipeline
            } catch (Exception e) {
                logger.warn("Failed to sign frame: {}", e.getMessage());
            }
        }
    }

    /**
     * Execute an incomplete frame as a structured query.
     *
     * <p>Creates a {@link QueryItem} from the semantic frame, runs it against
     * the library indexes, and returns the matched items.
     */
    private EvalResult evaluateStructuredQuery(SemanticFrame frame) {
        LibrarianOld librarian = librarianHandle instanceof LocalLibrarian local
                ? local.librarian() : null;
        if (librarian == null) {
            return EvalResult.error("Query requires a local librarian");
        }

        // Normalize: move query quantifier bindings (all, any) to unbound roles
        SemanticFrame queryFrame = frame.forQuery();

        logger.debug("evaluateStructuredQuery: predicate={}, bindings={}, unboundRoles={}",
                queryFrame.verb().displayToken(), queryFrame.bindings().keySet(), queryFrame.unboundRoles());

        // If no bindings are filled, the predicate alone isn't a structural query —
        // fall back to unstructured intersection using the predicate as a search term.
        // "chess" → queryItems({chess.iid}), not byPredicate(chess.iid).
        // Structured queries only help when there's at least one filled role
        // (e.g., "authored by tolkien" → AUTHORED with AGENT=tolkien).
        if (queryFrame.bindings().isEmpty()) {
            List<Eval.ResolvedToken> terms = List.of(
                    new ResolvedToken.Link(queryFrame.verb().iid(), queryFrame.verb().displayToken()));
            QueryItem queryItem = new QueryItem(librarian, terms);
            Set<ItemID> resultIds = queryItem.run();
            List<ItemOld> resultItems = resultIds.stream()
                    .map(id -> librarianHandle.get(id))
                    .flatMap(Optional::stream)
                    .toList();
            queryItem.resultItems(resultItems);
            return EvalResult.query(queryItem, resultItems, queryItem.extractPattern());
        }

        QueryItem queryItem = new QueryItem(librarian, queryFrame);
        Set<ItemID> resultIds = queryItem.run();
        List<ItemOld> resultItems = resultIds.stream()
                .map(id -> librarianHandle.get(id))
                .flatMap(Optional::stream)
                .toList();
        queryItem.resultItems(resultItems);
        return EvalResult.query(queryItem, resultItems, queryItem.extractPattern());
    }


    /**
     * Convert a SemanticFrame to a FrameBody for unified evaluation.
     *
     * <p>The verb becomes the predicate. Bindings are converted from
     * Java objects (Item, String, Number, etc.) to BindingTargets.
     * Unmatched args become additional THEME bindings.
     */
    private FrameBodyOld toFrameBody(SemanticFrame frame) {
        List<Binding> bindings = new ArrayList<>();

        for (var entry : frame.bindings().entrySet()) {
            ItemID role = entry.getKey();
            Object value = entry.getValue();
            BindingTarget target = toBindingTarget(value);
            if (target != null) {
                bindings.add(new Binding(role, target));
            }
        }

        // Unmatched args → additional THEME bindings
        for (ResolvedToken token : frame.unmatchedArgs()) {
            BindingTarget target = resolvedTokenToTarget(token);
            if (target != null) {
                bindings.add(new Binding(ThematicRole.Theme.IID, target));
            }
        }

        return new FrameBodyOld(frame.verb().iid(), bindings);
    }

    /**
     * Convert a Java value to a BindingTarget.
     */
    private BindingTarget toBindingTarget(Object value) {
        if (value instanceof SemanticFrame nested) return BindingTarget.frame(toFrameBody(nested));
        if (value instanceof ItemOld item) return BindingTarget.iid(item.iid());
        if (value instanceof ItemID iid) return BindingTarget.iid(iid);
        if (value instanceof String s) return Literal.ofText(s);
        if (value instanceof Long l) return Literal.ofInteger(l);
        if (value instanceof Integer i) return Literal.ofInteger(i);
        if (value instanceof Boolean b) return Literal.ofBoolean(b);
        if (value instanceof Number n) return Literal.ofInteger(n.longValue());
        if (value != null) return Literal.ofText(String.valueOf(value));
        return null;
    }

    /**
     * Convert a ResolvedToken to a BindingTarget.
     */
    private BindingTarget resolvedTokenToTarget(ResolvedToken token) {
        return switch (token) {
            case ResolvedToken.Link ref -> BindingTarget.iid(ref.iid());
            case ResolvedToken.Literal lit -> toBindingTarget(lit.value());
            case ResolvedToken.Unresolved u -> Literal.ofText(u.token());
        };
    }

    /**
     * Fill unfilled EXPECTS roles from context.
     *
     * <p>When a predicate expects LOCATION and it's not provided, fill from the
     * current context item. When it expects AGENT and it's not provided, fill
     * from the session's principal. This enables bare commands like "enter" to
     * auto-fill LOCATION from the focused item and AGENT from the user.
     */
    private FrameBodyOld fillFromContext(FrameBodyOld body) {
        // Resolve the predicate to check its EXPECTS
        Sememe predicate = librarianHandle.get(body.predicate(), Sememe.class).orElse(null);
        if (predicate == null) return body;

        List<ItemID> expectedRoles = predicate.slotRoles();
        if (expectedRoles.isEmpty()) return body;

        List<Binding> additions = new ArrayList<>();

        ItemID locationRole = ItemID.fromString(ThematicRole.Location.KEY);
        ItemID agentRole = ItemID.fromString(ThematicRole.Agent.KEY);
        ItemID themeRole = ItemID.fromString(ThematicRole.Theme.KEY);
        for (ItemID role : expectedRoles) {
            // Skip roles that are already filled
            if (body.binding(role) != null) continue;

            // THEME → fill from context item (bare "commit" means "commit this item")
            if (role.equals(themeRole)) {
                if (context != null) {
                    additions.add(new Binding(role, BindingTarget.iid(context.iid())));
                }
            }
            // LOCATION → fill from context item
            else if (role.equals(locationRole)) {
                if (context != null) {
                    additions.add(new Binding(role, BindingTarget.iid(context.iid())));
                }
            }
            // AGENT → fill from principal/signer
            else if (role.equals(agentRole)) {
                ItemID principalId = librarianHandle.principalId();
                if (principalId != null) {
                    additions.add(new Binding(role, BindingTarget.iid(principalId)));
                } else {
                    additions.add(new Binding(role, BindingTarget.iid(librarianHandle.iid())));
                }
            }
        }

        if (additions.isEmpty()) return body;

        // Rebuild the body with the additional bindings
        List<Binding> allBindings = new ArrayList<>(body.frameBindings());
        allBindings.addAll(additions);
        return new FrameBodyOld(body.predicate(), allBindings);
    }

    /**
     * Map a raw evaluation result to an EvalResult for UI consumption.
     */
    private EvalResult mapResultToEvalResult(Object value, SemanticFrame frame) {
        if (value instanceof Created created) {
            pushToHistory(created.item());
            return EvalResult.created(created.item(), created.type());
        }
        if (value instanceof ItemOld item) {
            pushToHistory(item);
            return EvalResult.item(item);
        }

        // Wrap with TARGET if a prepositional phrase bound to GOAL was present
        Optional<ItemOld> prepTarget = frame.itemBinding(ThematicRole.Goal.IID);
        if (prepTarget.isPresent()) {
            return EvalResult.valueWithTarget(value, prepTarget.get());
        }

        return EvalResult.value(value);
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
            case EvalResult.ItemResult(ItemOld item) -> {
                showItemInfo(item);
                yield 0;
            }
            case EvalResult.Created(ItemOld item, ItemOld type) -> {
                showItemInfo(item);
                yield 0;
            }
            case EvalResult.Value(Object value) -> {
                if (value != null) printResult(value);
                yield 0;
            }
            case EvalResult.ValueWithTarget(Object value, ItemOld targetItem) -> {
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
            case EvalResult.QueryResult(var queryItem, var items, var pattern) -> {
                System.out.println("Query: " + items.size() + " results");
                for (ItemOld item : items) {
                    System.out.println("  " + item.displayToken() + " (" + item.iid().encodeText() + ")");
                }
                yield 0;
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

    private void showItemInfo(ItemOld item) {
        System.out.println(item.displayToken());
        System.out.println("  IID:  " + item.iid().encodeText());
        System.out.println("  Type: " + item.getClass().getSimpleName());

        // Show local tokens
        var vocab = item.vocabulary();
        if (vocab != null && vocab.localTokenCount() > 0) {
            System.out.println("  Local tokens: " + vocab.localTokenCount());
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
        if (value instanceof ItemOld item) {
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
                    discourseHistory, interactive, jsonOutput, depth + 1);
            return child.evaluateCommand(expanded);
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

        logger.warn("executeTokens: {} resolved tokens → evaluateResolved", resolved.size());

        return evaluateResolved(resolved);
    }

    /**
     * Get or create a Scope for the unified FrameEvaluator path.
     *
     * <p>The Scope provides the librarian (graph access) and owner item
     * (focused context) to the evaluator. Created fresh for each evaluation.
     */
    private Scope getOrCreateScope() {
        LibrarianOld librarian = librarianHandle instanceof LocalLibrarian local
                ? local.librarian() : null;
        if (librarian != null && context != null) {
            return Scope.of(librarian, context);
        } else if (librarian != null) {
            return Scope.of(librarian);
        }
        return Scope.of(null, null);
    }

    /**
     * Convert a UI ExpressionToken to an execution ResolvedToken.
     *
     * <p>This is a TRIVIAL passthrough — InputController already did all resolution.
     * No dictionary lookups, no disambiguation. Just type mapping.
     */
    private ResolvedToken convertToken(ExpressionToken token) {
        return switch (token) {
            case ExpressionToken.RefToken ref -> {
                    var features = ref.sourcePosting() != null
                            ? ref.sourcePosting().features() : java.util.Set.<ItemID>of();
                    yield new ResolvedToken.Link(ref.target(), ref.displayText(), features);
            }
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
     * Result of expression evaluation - structured for UI consumption.
     */
    public sealed interface EvalResult {
        record Empty() implements EvalResult {}
        record Value(Object value) implements EvalResult {}
        record ItemResult(ItemOld item) implements EvalResult {}
        /** An item was created — session should NOT navigate the current view. */
        record Created(ItemOld item, ItemOld type) implements EvalResult {}
        record ValueWithTarget(Object value, ItemOld targetItem) implements EvalResult {}
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
        /** Query results — an incomplete frame pattern matched against the library. */
        record QueryResult(QueryItem queryItem, List<ItemOld> items, Set<ItemID> pattern) implements EvalResult {}

        static EvalResult empty() { return new Empty(); }
        static EvalResult value(Object v) { return new Value(v); }
        static EvalResult item(ItemOld i) { return new ItemResult(i); }
        static EvalResult created(ItemOld i, ItemOld type) { return new Created(i, type); }
        static EvalResult valueWithTarget(Object v, ItemOld t) { return new ValueWithTarget(v, t); }
        static EvalResult error(String msg) { return new Error(msg); }
        static EvalResult ambiguous(List<Ambiguous.UnresolvedToken> tokens) { return new Ambiguous(tokens); }
        static EvalResult query(QueryItem qi, List<ItemOld> items, Set<ItemID> pattern) { return new QueryResult(qi, items, pattern); }

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
    public static int run(LibrarianOld librarian, List<String> args) {
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
