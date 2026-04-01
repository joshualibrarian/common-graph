package dev.everydaythings.graph.language;

import dev.everydaythings.graph.frame.eval.ParseContribution;
import dev.everydaythings.graph.frame.eval.ParseContribution.Associativity;
import dev.everydaythings.graph.frame.eval.ParseContribution.Fixity;
import dev.everydaythings.graph.frame.eval.ParseContribution.StructuralRole;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Eval.ResolvedToken;
import lombok.extern.log4j.Log4j2;

import java.util.*;
import java.util.function.ToIntFunction;
import java.util.function.Function;

/**
 * Assembles a {@link SemanticFrame} from resolved tokens in any order.
 *
 * <p>The assembler is stateless. It scans resolved tokens to find:
 * <ol>
 *   <li>The verb (first {@link CoreVocabulary} found)</li>
 *   <li>Prepositional phrases ({@link PrepositionVocabulary} followed by an object)</li>
 *   <li>Bare nouns matched to the verb's slot roles by first-fit</li>
 * </ol>
 *
 * <p>Order doesn't matter: "create chess on librarian", "on librarian create chess",
 * and "chess create on librarian" all produce the same frame.
 *
 * <p>Conjunctions are supported within prepositional phrases:
 * "between bob and jane" produces a List binding for the COMITATIVE role.
 */
@Log4j2
public class FrameAssembler {

    /** A resolved token paired with its Item (if resolved) and its grammatical features. */
    private record Slot(ResolvedToken token, Item item, Set<ItemID> features) {}

    /**
     * Unified semantic analysis result for resolved tokens.
     *
     * <p>This is the single parser output used by both:
     * <ul>
     *   <li>Execution flow (via {@link #assemble(List, Function)})</li>
     *   <li>UI completion narrowing (via ExpressionContext)</li>
     * </ul>
     */
    public record Analysis(
            Sememe verb,
            Map<ItemID, Object> bindings,
            Map<String, List<Sememe>> modifiers,
            List<ResolvedToken> unmatchedArgs,
            List<ItemID> unboundRoles,
            boolean lastTokenIsDanglingPreposition,
            List<TermBinding> termBindings
    ) {}

    /**
     * Analyze resolved tokens into a semantic structure (single parsing flow).
     *
     * @param tokens   The resolved tokens from evaluation
     * @param resolver Resolves an ItemID to its Item (typically librarianHandle::get)
     * @return Analysis if a verb is present, else empty
     */
    public static Optional<Analysis> analyze(
            List<ResolvedToken> tokens,
            Function<ItemID, Optional<Item>> resolver) {
        return analyze(tokens, resolver, v -> 0);
    }

    /**
     * Analyze resolved tokens with score-based head verb selection.
     *
     * @param tokens          The resolved tokens from evaluation
     * @param resolver        Resolves an ItemID to its Item (typically librarianHandle::get)
     * @param headVerbScorer  Additional score contribution for head-verb selection
     * @return Analysis if a verb is present, else empty
     */
    public static Optional<Analysis> analyze(
            List<ResolvedToken> tokens,
            Function<ItemID, Optional<Item>> resolver,
            ToIntFunction<Sememe> headVerbScorer) {

        if (traceEnabled()) {
            logger.info("[Parse] analyze tokens={}", summarizeTokens(tokens));
        }

        if (tokens.isEmpty()) {
            return Optional.empty();
        }

        // Step 1: Resolve all Link tokens to (token, Item, POS) triples
        List<Slot> slots = new ArrayList<>();
        for (ResolvedToken token : tokens) {
            if (token instanceof ResolvedToken.Link link) {
                Optional<Item> item = resolver.apply(link.iid());
                Item resolved = item.orElse(null);
                Set<ItemID> features = link.features();
                if (features.isEmpty() && resolved != null) {
                    features = inferPOSFromItem(resolved);
                }
                slots.add(new Slot(token, resolved, features));
            } else {
                slots.add(new Slot(token, null, Set.of()));
            }
        }

        // Step 2: Pick head verb via score instead of first-hit.
        int verbIndex = selectHeadVerbIndex(slots, headVerbScorer != null ? headVerbScorer : v -> 0);
        Sememe verb = verbIndex >= 0 ? (Sememe) slots.get(verbIndex).item() : null;

        if (verb == null) {
            if (traceEnabled()) {
                logger.info("[Parse] no verb sememe found");
            }
            return Optional.empty();
        }

        // Track which indices are consumed
        Set<Integer> consumed = new HashSet<>();
        consumed.add(verbIndex);

        // Step 3: Find prepositional phrases (with conjunction support)
        Map<ItemID, Object> bindings = new LinkedHashMap<>();
        Map<Integer, ItemID> thematicByTokenIndex = new HashMap<>();

        for (int i = 0; i < slots.size(); i++) {
            if (consumed.contains(i)) continue;

            Item item = slots.get(i).item();
            if (slots.get(i).features().contains(PartOfSpeech.PREPOSITION) && item instanceof Sememe prep && prep.assignedRole() != null) {
                // Look for the next unconsumed token as the object
                int objectIndex = nextUnconsumed(slots, consumed, i + 1);

                if (objectIndex >= 0) {
                    Object firstValue = resolveSlotValue(slots.get(objectIndex));
                    if (firstValue != null) {
                        consumed.add(i);
                        consumed.add(objectIndex);

                        // Check for conjunction ("and") to build a list
                        List<Object> values = collectConjoinedValues(
                                slots, consumed, objectIndex + 1, firstValue);

                        if (values != null) {
                            bindings.put(prep.assignedRole(), values);
                        } else {
                            bindings.put(prep.assignedRole(), firstValue);
                        }
                        thematicByTokenIndex.put(objectIndex, prep.assignedRole());
                    }
                    // else: object doesn't resolve — skip, both go unmatched
                }
                // else: dangling preposition (no object follows) — goes unmatched
            }
        }

        // Step 3.5: Collect modifiers (adjectives → next noun, adverbs → verb)
        Map<String, List<Sememe>> modifiers = new LinkedHashMap<>();
        for (int i = 0; i < slots.size(); i++) {
            if (consumed.contains(i)) continue;

            Set<ItemID> slotFeats = slots.get(i).features();
            Item item = slots.get(i).item();
            if (slotFeats.contains(PartOfSpeech.ADVERB) && item instanceof Sememe s) {
                // Adverbs modify the verb
                modifiers.computeIfAbsent("verb", k -> new ArrayList<>()).add(s);
                consumed.add(i);
            } else if (slotFeats.contains(PartOfSpeech.ADJECTIVE) && item instanceof Sememe adj) {
                // Adjectives modify the next unconsumed noun/item
                int nextNoun = -1;
                for (int j = i + 1; j < slots.size(); j++) {
                    if (consumed.contains(j)) continue;
                    Item candidate = slots.get(j).item();
                    Set<ItemID> candidateFeats = slots.get(j).features();
                    if (candidate != null
                            && !(candidate instanceof Sememe cs && canBePredicate(cs, candidateFeats))
                            && !candidateFeats.contains(PartOfSpeech.PREPOSITION)
                            && !candidateFeats.contains(PartOfSpeech.CONJUNCTION)
                            && !candidateFeats.contains(PartOfSpeech.ADJECTIVE)
                            && !candidateFeats.contains(PartOfSpeech.ADVERB)) {
                        nextNoun = j;
                        break;
                    }
                }
                if (nextNoun >= 0) {
                    // Key by the noun's IID so dispatch can look up per-argument modifiers
                    String key = "iid:" + slots.get(nextNoun).item().iid().encodeText();
                    modifiers.computeIfAbsent(key, k -> new ArrayList<>()).add(adj);
                } else {
                    // No noun follows — attach to verb as a general qualifier
                    modifiers.computeIfAbsent("verb", k -> new ArrayList<>()).add(adj);
                }
                consumed.add(i);
            }
        }

        // Step 4: Match remaining resolved Items to argument slots (first-fit)
        List<ItemID> slotRoles = verb.slotRoles();
        Set<ItemID> filledRoles = new HashSet<>(bindings.keySet());

        for (int i = 0; i < slots.size(); i++) {
            if (consumed.contains(i)) continue;

            Item item = slots.get(i).item();
            if (item == null) continue; // literals and unresolved handled in step 5
            Set<ItemID> feats = slots.get(i).features();
            if (item instanceof Sememe s && canBePredicate(s, feats)) continue; // extra predicates go unmatched
            if (feats.contains(PartOfSpeech.PREPOSITION)) continue; // unconsumed prepositions go unmatched
            if (feats.contains(PartOfSpeech.CONJUNCTION)) continue; // unconsumed conjunctions go unmatched

            // Find first unfilled argument slot for this item
            for (ItemID role : slotRoles) {
                if (!filledRoles.contains(role)) {
                    bindings.put(role, item);
                    filledRoles.add(role);
                    consumed.add(i);
                    thematicByTokenIndex.put(i, role);

                    // Re-key modifiers from iid-based to role-based
                    String iidKey = "iid:" + item.iid().encodeText();
                    List<Sememe> mods = modifiers.remove(iidKey);
                    if (mods != null) {
                        modifiers.put("role:" + role.encodeText(), mods);
                    }
                    break;
                }
            }
            // If no slot matched, item stays unconsumed → goes to unmatchedArgs
        }

        // Step 5: Collect unmatched tokens
        List<ResolvedToken> unmatchedArgs = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            if (!consumed.contains(i)) {
                unmatchedArgs.add(slots.get(i).token());
            }
        }

        // Step 6: Compute unbound roles
        List<ItemID> unboundRoles = new ArrayList<>();
        for (ItemID role : slotRoles) {
            if (!filledRoles.contains(role)) {
                unboundRoles.add(role);
            }
        }

        // Step 7: Track dangling preposition at end for completion UI.
        boolean lastTokenIsDanglingPreposition = false;
        if (!slots.isEmpty()) {
            int lastIndex = slots.size() - 1;
            lastTokenIsDanglingPreposition =
                    slots.get(lastIndex).features().contains(PartOfSpeech.PREPOSITION)
                            && !consumed.contains(lastIndex);
        }

        // Step 8: Emit term-level bindings (ACTION / REFERENCE / QUALIFIER).
        List<TermBinding> termBindings = new ArrayList<>(slots.size());
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            ResolvedToken token = slot.token();
            Item item = slot.item();
            ItemID targetId = token instanceof ResolvedToken.Link link ? link.iid() : null;
            termBindings.add(new TermBinding(
                    i,
                    surface(token),
                    BindingRole.classify(item, token, slot.features()),
                    thematicByTokenIndex.get(i),
                    targetId,
                    consumed.contains(i)
            ));
        }

        // Make modifier lists unmodifiable
        Map<String, List<Sememe>> unmodifiableModifiers = new LinkedHashMap<>();
        for (var entry : modifiers.entrySet()) {
            unmodifiableModifiers.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }

        Analysis analysis = new Analysis(
                verb,
                Collections.unmodifiableMap(bindings),
                Collections.unmodifiableMap(unmodifiableModifiers),
                Collections.unmodifiableList(unmatchedArgs),
                Collections.unmodifiableList(unboundRoles),
                lastTokenIsDanglingPreposition,
                Collections.unmodifiableList(termBindings)
        );

        if (traceEnabled()) {
            logger.info("[Parse] verb={} bindings={} unboundRoles={} unmatched={} termBindings={}",
                    verb.displayToken(), bindings, unboundRoles, unmatchedArgs, termBindings);
        }

        return Optional.of(analysis);
    }

    /**
     * Assemble a SemanticFrame from resolved tokens (order-agnostic).
     *
     * @param tokens   The resolved tokens from evaluation
     * @param resolver Resolves an ItemID to its Item (typically librarianHandle::get)
     * @return The assembled frame, or empty if no verb found in tokens
     */
    public static Optional<SemanticFrame> assemble(
            List<ResolvedToken> tokens,
            Function<ItemID, Optional<Item>> resolver) {
        Optional<Analysis> analysis = analyze(tokens, resolver);
        if (analysis.isEmpty()) return Optional.empty();
        Analysis a = analysis.get();
        return Optional.of(new SemanticFrame(
                a.verb(),
                a.bindings(),
                a.modifiers(),
                a.unmatchedArgs(),
                a.unboundRoles()
        ));
    }

    /**
     * Detect and split multi-verb conjunction expressions.
     *
     * <p>Handles patterns like "create chess and place in main" by detecting
     * ConjunctionSememe between verb groups and splitting into separate
     * token lists, each assembled independently.
     *
     * @param tokens   The resolved tokens
     * @param resolver Item resolver
     * @return List of frames (1 if no conjunction, 2+ if verbs are conjoined)
     */
    public static List<SemanticFrame> assembleAll(
            List<ResolvedToken> tokens,
            Function<ItemID, Optional<Item>> resolver) {
        return assembleAll(tokens, resolver, v -> 0);
    }

    /**
     * Detect and split multi-verb conjunction expressions with head-verb scoring.
     */
    public static List<SemanticFrame> assembleAll(
            List<ResolvedToken> tokens,
            Function<ItemID, Optional<Item>> resolver,
            ToIntFunction<Sememe> headVerbScorer) {

        if (tokens.isEmpty()) return List.of();

        // Count verbs and find conjunction positions between them
        List<Integer> verbIndices = new ArrayList<>();
        List<Integer> conjIndices = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i) instanceof ResolvedToken.Link link) {
                Set<ItemID> feats = link.features();
                Optional<Item> resolved = resolver.apply(link.iid());
                if (feats.isEmpty() && resolved.isPresent()) {
                    feats = inferPOSFromItem(resolved.get());
                }
                if (resolved.isPresent() && resolved.get() instanceof Sememe sem
                        && canBePredicate(sem, feats)) {
                    verbIndices.add(i);
                } else if (feats.contains(PartOfSpeech.CONJUNCTION)) {
                    conjIndices.add(i);
                }
            }
        }

        // Only split if there are 2+ verbs with a conjunction between them
        if (verbIndices.size() < 2 || conjIndices.isEmpty()) {
            // Try expression parsing first — handles "x = 5 + 2", "5 + 3", etc.
            // assembleExpression() returns empty if tokens don't contain operators,
            // so verb-noun commands like "create chess" fall through.
            Optional<SemanticFrame> single = assembleExpression(tokens, resolver);
            if (single.isEmpty()) {
                single = assemble(tokens, resolver);
            }
            return single.map(List::of).orElse(List.of());
        }

        // Find conjunction indices that separate verb groups
        List<Integer> splitPoints = new ArrayList<>();
        for (int conjIdx : conjIndices) {
            boolean verbBefore = verbIndices.stream().anyMatch(v -> v < conjIdx);
            boolean verbAfter = verbIndices.stream().anyMatch(v -> v > conjIdx);
            if (verbBefore && verbAfter) {
                splitPoints.add(conjIdx);
            }
        }

        if (splitPoints.isEmpty()) {
            Optional<SemanticFrame> single = assemble(tokens, resolver);
            return single.map(List::of).orElse(List.of());
        }

        // Split tokens at conjunction points and assemble each segment
        List<SemanticFrame> frames = new ArrayList<>();
        int start = 0;
        for (int splitIdx : splitPoints) {
            List<ResolvedToken> segment = tokens.subList(start, splitIdx);
            Optional<SemanticFrame> frame = assemble(segment, resolver);
            frame.ifPresent(frames::add);
            start = splitIdx + 1; // skip the conjunction
        }
        // Last segment
        if (start < tokens.size()) {
            List<ResolvedToken> segment = tokens.subList(start, tokens.size());
            Optional<SemanticFrame> frame = assemble(segment, resolver);
            frame.ifPresent(frames::add);
        }

        return frames;
    }

    /**
     * Find the next unconsumed slot index starting from {@code from}.
     */
    private static int nextUnconsumed(List<Slot> slots, Set<Integer> consumed, int from) {
        for (int j = from; j < slots.size(); j++) {
            if (!consumed.contains(j)) return j;
        }
        return -1;
    }

    /**
     * Extract the bound value from a slot (Item or literal string), or null.
     */
    private static Object resolveSlotValue(Slot slot) {
        if (slot.item() != null) return slot.item();
        if (slot.token() instanceof ResolvedToken.Literal lit) return lit.value();
        return null;
    }

    private static String surface(ResolvedToken token) {
        return switch (token) {
            case ResolvedToken.Link link -> link.originalToken();
            case ResolvedToken.Literal lit -> String.valueOf(lit.originalToken());
            case ResolvedToken.Unresolved u -> u.token();
        };
    }

    private static boolean traceEnabled() {
        return Boolean.getBoolean("cg.eval.trace")
                || "1".equals(System.getenv("CG_EVAL_TRACE"))
                || "true".equalsIgnoreCase(System.getenv("CG_EVAL_TRACE"));
    }

    private static String summarizeTokens(List<ResolvedToken> tokens) {
        List<String> out = new ArrayList<>(tokens.size());
        for (ResolvedToken token : tokens) {
            switch (token) {
                case ResolvedToken.Link link ->
                        out.add("LINK(" + link.originalToken() + "->" + link.iid().encodeText() + ")");
                case ResolvedToken.Literal lit ->
                        out.add("LIT(" + lit.value() + ")");
                case ResolvedToken.Unresolved u ->
                        out.add("UNRESOLVED(" + u.token() + ")");
            }
        }
        return out.toString();
    }

    /**
     * After consuming the first object of a prepositional phrase, check for
     * conjunction-separated additional objects: "bob and jane and pat".
     *
     * @return A list of all values (including firstValue) if conjunctions found, else null
     */
    private static List<Object> collectConjoinedValues(
            List<Slot> slots, Set<Integer> consumed, int startFrom, Object firstValue) {
        List<Object> values = null;
        int cursor = startFrom;

        while (true) {
            int conjIndex = nextUnconsumed(slots, consumed, cursor);
            if (conjIndex < 0) break;

            // Check if this slot is a conjunction
            if (!slots.get(conjIndex).features().contains(PartOfSpeech.CONJUNCTION)) break;

            // Look for the object after the conjunction
            int nextObjIndex = nextUnconsumed(slots, consumed, conjIndex + 1);
            if (nextObjIndex < 0) break;

            Object nextValue = resolveSlotValue(slots.get(nextObjIndex));
            if (nextValue == null) break;

            // First conjunction found — initialize list with firstValue
            if (values == null) {
                values = new ArrayList<>();
                values.add(firstValue);
            }

            consumed.add(conjIndex);
            consumed.add(nextObjIndex);
            values.add(nextValue);
            cursor = nextObjIndex + 1;
        }

        return values;
    }

    /**
     * Choose the head predicate index by combining static priors and runtime dispatchability.
     *
     * <p>A sememe can be a head predicate if it has EXPECTS roles, or if its
     * lexeme features include VERB. Both are valid signals — EXPECTS is graph data,
     * VERB POS is a lexeme feature that the language layer provides.
     */
    private static int selectHeadVerbIndex(List<Slot> slots, ToIntFunction<Sememe> headVerbScorer) {
        int bestIndex = -1;
        int bestScore = Integer.MIN_VALUE;

        for (int i = 0; i < slots.size(); i++) {
            Item item = slots.get(i).item();
            if (!(item instanceof Sememe sememe)) continue;
            if (!canBePredicate(sememe, slots.get(i).features())) continue;

            int score = baseHeadScore(sememe) + headVerbScorer.applyAsInt(sememe);
            if (score > bestScore) {
                bestScore = score;
                bestIndex = i;
            }
            if (traceEnabled()) {
                logger.info("[Parse] head-candidate token={} predicate={} score={}",
                        i, sememe.displayToken(), score);
            }
        }
        return bestIndex;
    }

    /**
     * Can this sememe serve as a frame predicate in the current parse context?
     *
     * <p>Checks EXPECTS role declarations (graph data), ParseContribution
     * (fixity for operators), and lexeme features (VERB POS).
     * Uses slotRoles() which now filters to ROLE-qualified EXPECTS only,
     * excluding type-level FRAME expectations.
     */
    private static boolean canBePredicate(Sememe sememe, Set<ItemID> features) {
        // EXPECTS ROLE declarations = this sememe expects thematic role bindings
        if (!sememe.slotRoles().isEmpty()) return true;

        // ParseContribution declares fixity (operators) or expected roles (functions)
        ParseContribution contribution = sememe.contribute(null);
        if (contribution.isPresent()) {
            if (contribution.fixity() != null) return true;
            if (contribution.expectedRoles() != null && !contribution.expectedRoles().isEmpty()) return true;
        }

        // Lexeme features include VERB
        if (features.contains(PartOfSpeech.VERB)) return true;

        return false;
    }

    /**
     * Score a sememe's suitability as the head predicate of a frame.
     *
     * <p>Based on EXPECTS ROLE declarations (now filtered to role-qualified
     * only, excluding type-level frame expectations). More roles = more
     * likely to be the head predicate.
     */
    private static int baseHeadScore(Sememe sememe) {
        return Math.min(sememe.slotRoles().size(), 6) * 15;
    }

    /**
     * Infer POS-like features from an Item when the token pipeline didn't provide them.
     *
     * <p>This is a fallback for direct IID references (e.g., {@code @cg.sememe:item-view})
     * that bypass the TokenDictionary and thus have no Posting with features.
     * Returns features that allow FrameAssembler to categorize the token.
     */
    public static Set<ItemID> inferPOSFromItem(Item item) {
        if (!(item instanceof Sememe sememe)) return Set.of();

        // Ask the sememe for its parsing contribution
        ParseContribution contribution = sememe.contribute(null);
        if (contribution.isPresent()) {
            if (contribution.assignedRole() != null) return Set.of(PartOfSpeech.PREPOSITION);
            if (contribution.structuralRole() != null) {
                return switch (contribution.structuralRole()) {
                    case CONJUNCTION -> Set.of(PartOfSpeech.CONJUNCTION);
                    case PRONOUN -> Set.of(PartOfSpeech.PRONOUN);
                    default -> Set.of();
                };
            }
            if (contribution.fixity() != null) return Set.of(PartOfSpeech.VERB);
            if (contribution.expectedRoles() != null && !contribution.expectedRoles().isEmpty()) {
                return Set.of(PartOfSpeech.VERB);
            }
        }

        // EXPECTS roles = can be a predicate
        if (!sememe.slotRoles().isEmpty()) return Set.of(PartOfSpeech.VERB);

        return Set.of();
    }

    // ==================================================================================
    // Expression parsing — precedence-climbing for operators and functions
    // ==================================================================================

    /**
     * Try to parse tokens as an expression with operator precedence.
     *
     * <p>This handles infix operators ({@code 5 + 3 * 2}), prefix operators
     * ({@code -x}), function calls ({@code sqrt(16)}), and parenthesized
     * grouping ({@code (a + b) * c}). Operators declare their precedence,
     * fixity, and associativity via {@link ParseContribution} — no hardcoded
     * special cases.
     *
     * @return A SemanticFrame if the tokens form a valid expression, empty otherwise
     */
    public static Optional<SemanticFrame> assembleExpression(
            List<ResolvedToken> tokens,
            Function<ItemID, Optional<Item>> resolver) {

        if (tokens.isEmpty()) return Optional.empty();

        // Quick check: are there any operators or structural tokens?
        boolean hasExpressionSyntax = false;
        for (ResolvedToken token : tokens) {
            if (token instanceof ResolvedToken.Link link) {
                Optional<Item> item = resolver.apply(link.iid());
                if (item.isPresent() && item.get() instanceof Sememe s) {
                    ParseContribution c = s.contribute(null);
                    if (c.fixity() != null || c.structuralRole() == StructuralRole.OPEN_GROUP
                            || c.grouped()) {
                        hasExpressionSyntax = true;
                        break;
                    }
                }
            }
        }
        if (!hasExpressionSyntax) return Optional.empty();

        ExpressionParser parser = new ExpressionParser(tokens, resolver);
        try {
            Object result = parser.parseExpression(Integer.MIN_VALUE);
            if (parser.pos < tokens.size()) return Optional.empty();

            if (result instanceof SemanticFrame frame) {
                return Optional.of(frame);
            }
            // Bare literal or item — not an expression needing evaluation
            return Optional.empty();
        } catch (Exception e) {
            if (traceEnabled()) {
                logger.info("[Parse] expression parsing failed: {}", e.getMessage());
            }
            return Optional.empty();
        }
    }

    /**
     * Precedence-climbing expression parser.
     *
     * <p>Reads {@link ParseContribution} from each resolved sememe to determine
     * fixity, precedence, and associativity. Builds nested {@link SemanticFrame}
     * trees for operator expressions.
     */
    private static class ExpressionParser {
        private final List<ResolvedToken> tokens;
        private final Function<ItemID, Optional<Item>> resolver;
        private int pos = 0;

        ExpressionParser(List<ResolvedToken> tokens, Function<ItemID, Optional<Item>> resolver) {
            this.tokens = tokens;
            this.resolver = resolver;
        }

        /**
         * Parse an expression with minimum binding power (precedence-climbing).
         *
         * @param minPrec Minimum precedence for an infix operator to bind
         * @return The parsed value: a literal, an Item, or a nested SemanticFrame
         */
        Object parseExpression(int minPrec) {
            Object left = parsePrefix();

            while (pos < tokens.size()) {
                ResolvedToken token = tokens.get(pos);

                // Check for close group or separator — stop
                Sememe sem = resolveAsSememe(token);
                if (sem != null) {
                    ParseContribution c = sem.contribute(null);
                    if (c.structuralRole() == StructuralRole.CLOSE_GROUP
                            || c.structuralRole() == StructuralRole.SEPARATOR
                            || c.structuralRole() == StructuralRole.PIPE
                            || c.structuralRole() == StructuralRole.SEQUENCE) {
                        break;
                    }

                    // Infix operator
                    if (c.fixity() == Fixity.INFIX) {
                        if (c.precedence() < minPrec) break;

                        pos++; // consume operator
                        int nextMinPrec = (c.associativity() == Associativity.RIGHT)
                                ? c.precedence() : c.precedence() + 1;

                        Object right = parseExpression(nextMinPrec);

                        // Use expectedRoles from contribute() if declared,
                        // otherwise default to THEME (left) / GOAL (right)
                        ItemID leftRole = ThematicRole.Theme.IID;
                        ItemID rightRole = ThematicRole.Goal.IID;
                        List<ItemID> expected = c.expectedRoles();
                        if (expected != null && expected.size() >= 2) {
                            leftRole = expected.get(0);
                            rightRole = expected.get(1);
                        }

                        left = new SemanticFrame(sem,
                                new LinkedHashMap<>(Map.of(leftRole, left, rightRole, right)),
                                Map.of(), List.of(), List.of());
                        continue;
                    }
                }

                // No more infix operators — done
                break;
            }

            return left;
        }

        /**
         * Parse a prefix expression (unary operator, function call, literal, grouping).
         */
        Object parsePrefix() {
            if (pos >= tokens.size()) {
                throw new RuntimeException("Unexpected end of expression");
            }

            ResolvedToken token = tokens.get(pos);

            // Literal value
            if (token instanceof ResolvedToken.Literal lit) {
                pos++;
                return lit.value();
            }

            // Resolve as sememe to check contribution
            Sememe sem = resolveAsSememe(token);
            if (sem != null) {
                ParseContribution c = sem.contribute(null);

                // Open group: ( expr )
                if (c.structuralRole() == StructuralRole.OPEN_GROUP) {
                    pos++; // consume (
                    Object inner = parseExpression(0);
                    expectCloseGroup();
                    return inner;
                }

                // Prefix function with grouped args: sqrt(16), max(a, b)
                if (c.fixity() == Fixity.PREFIX && c.grouped()) {
                    pos++; // consume function name
                    return parseFunctionCall(sem);
                }

                // Prefix operator: -x, !x
                if (c.fixity() == Fixity.PREFIX) {
                    pos++; // consume operator
                    Object operand = parseExpression(c.precedence());
                    return new SemanticFrame(sem,
                            new LinkedHashMap<>(Map.of(ThematicRole.Theme.IID, operand)),
                            Map.of(), List.of(), List.of());
                }
            }

            // Non-operator item reference (variable, item, noun)
            if (token instanceof ResolvedToken.Link link) {
                pos++;
                Optional<Item> item = resolver.apply(link.iid());
                return item.orElse(null);
            }

            // Unresolved token — return as string
            if (token instanceof ResolvedToken.Unresolved u) {
                pos++;
                return u.token();
            }

            throw new RuntimeException("Unexpected token in expression: " + token);
        }

        /**
         * Parse a parenthesized function call: f(arg1, arg2, ...)
         */
        private SemanticFrame parseFunctionCall(Sememe function) {
            // Expect open group
            if (pos >= tokens.size()) {
                // No parens — treat as unary prefix: f x
                Object operand = parseExpression(Integer.MAX_VALUE);
                return new SemanticFrame(function,
                        new LinkedHashMap<>(Map.of(ThematicRole.Theme.IID, operand)),
                        Map.of(), List.of(), List.of());
            }

            Sememe openSem = resolveAsSememe(tokens.get(pos));
            if (openSem == null || openSem.contribute(null).structuralRole() != StructuralRole.OPEN_GROUP) {
                // No parens — treat as unary prefix
                Object operand = parseExpression(Integer.MAX_VALUE);
                return new SemanticFrame(function,
                        new LinkedHashMap<>(Map.of(ThematicRole.Theme.IID, operand)),
                        Map.of(), List.of(), List.of());
            }

            pos++; // consume (

            // Parse comma-separated arguments
            List<Object> args = new ArrayList<>();
            while (pos < tokens.size()) {
                Sememe closeSem = resolveAsSememe(tokens.get(pos));
                if (closeSem != null && closeSem.contribute(null).structuralRole() == StructuralRole.CLOSE_GROUP) {
                    pos++; // consume )
                    break;
                }

                args.add(parseExpression(0));

                // Consume separator if present
                if (pos < tokens.size()) {
                    Sememe sepSem = resolveAsSememe(tokens.get(pos));
                    if (sepSem != null && sepSem.contribute(null).structuralRole() == StructuralRole.SEPARATOR) {
                        pos++; // consume ,
                    }
                }
            }

            // Map arguments to roles: THEME for first, GOAL for second
            Map<ItemID, Object> bindings = new LinkedHashMap<>();
            List<ItemID> expectedRoles = function.contribute(null).expectedRoles();
            if (expectedRoles != null && !expectedRoles.isEmpty()) {
                for (int i = 0; i < args.size() && i < expectedRoles.size(); i++) {
                    bindings.put(expectedRoles.get(i), args.get(i));
                }
            } else {
                // Default: THEME, GOAL, INSTRUMENT for first three args
                if (args.size() > 0) bindings.put(ThematicRole.Theme.IID, args.get(0));
                if (args.size() > 1) bindings.put(ThematicRole.Goal.IID, args.get(1));
            }

            return new SemanticFrame(function, bindings, Map.of(), List.of(), List.of());
        }

        private void expectCloseGroup() {
            if (pos < tokens.size()) {
                Sememe closeSem = resolveAsSememe(tokens.get(pos));
                if (closeSem != null && closeSem.contribute(null).structuralRole() == StructuralRole.CLOSE_GROUP) {
                    pos++;
                    return;
                }
            }
            throw new RuntimeException("Expected closing parenthesis");
        }

        private Sememe resolveAsSememe(ResolvedToken token) {
            if (token instanceof ResolvedToken.Link link) {
                Optional<Item> item = resolver.apply(link.iid());
                if (item.isPresent() && item.get() instanceof Sememe s) {
                    return s;
                }
            }
            return null;
        }
    }
}
