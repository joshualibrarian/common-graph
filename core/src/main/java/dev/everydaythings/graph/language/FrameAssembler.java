package dev.everydaythings.graph.language;

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
 *   <li>The verb (first {@link VerbSememe} found)</li>
 *   <li>Prepositional phrases ({@link PrepositionSememe} followed by an object)</li>
 *   <li>Bare nouns matched to the verb's {@link ArgumentSlot}s by first-fit</li>
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

    /** A resolved token paired with its Item (if resolved). */
    private record Slot(ResolvedToken token, Item item) {}

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
            VerbSememe verb,
            Map<ItemID, Object> bindings,
            Map<String, List<Sememe>> modifiers,
            List<ResolvedToken> unmatchedArgs,
            List<ArgumentSlot> unboundRequired,
            List<ArgumentSlot> unboundOptional,
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
            ToIntFunction<VerbSememe> headVerbScorer) {

        if (traceEnabled()) {
            logger.info("[Parse] analyze tokens={}", summarizeTokens(tokens));
        }

        if (tokens.isEmpty()) {
            return Optional.empty();
        }

        // Step 1: Resolve all Link tokens to (token, Optional<Item>) pairs
        List<Slot> slots = new ArrayList<>();
        for (ResolvedToken token : tokens) {
            if (token instanceof ResolvedToken.Link link) {
                Optional<Item> item = resolver.apply(link.iid());
                slots.add(new Slot(token, item.orElse(null)));
            } else {
                slots.add(new Slot(token, null));
            }
        }

        // Step 2: Pick head verb via score instead of first-hit.
        int verbIndex = selectHeadVerbIndex(slots, headVerbScorer != null ? headVerbScorer : v -> 0);
        VerbSememe verb = verbIndex >= 0 ? (VerbSememe) slots.get(verbIndex).item() : null;

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
            if (item instanceof PrepositionSememe prep && prep.assignedRole() != null) {
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

            Item item = slots.get(i).item();
            if (item instanceof AdverbSememe adverb) {
                // Adverbs modify the verb
                modifiers.computeIfAbsent("verb", k -> new ArrayList<>()).add(adverb);
                consumed.add(i);
            } else if (item instanceof AdjectiveSememe adj) {
                // Adjectives modify the next unconsumed noun/item
                int nextNoun = -1;
                for (int j = i + 1; j < slots.size(); j++) {
                    if (consumed.contains(j)) continue;
                    Item candidate = slots.get(j).item();
                    if (candidate != null
                            && !(candidate instanceof VerbSememe)
                            && !(candidate instanceof PrepositionSememe)
                            && !(candidate instanceof ConjunctionSememe)
                            && !(candidate instanceof AdjectiveSememe)
                            && !(candidate instanceof AdverbSememe)) {
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
        List<ArgumentSlot> arguments = verb.arguments();
        Set<ItemID> filledRoles = new HashSet<>(bindings.keySet());

        for (int i = 0; i < slots.size(); i++) {
            if (consumed.contains(i)) continue;

            Item item = slots.get(i).item();
            if (item == null) continue; // literals and unresolved handled in step 5
            if (item instanceof VerbSememe) continue; // extra verbs go unmatched, not to argument slots
            if (item instanceof PrepositionSememe) continue; // unconsumed prepositions go unmatched
            if (item instanceof ConjunctionSememe) continue; // unconsumed conjunctions go unmatched

            // Find first unfilled argument slot for this item
            for (ArgumentSlot slot : arguments) {
                if (!filledRoles.contains(slot.role())) {
                    bindings.put(slot.role(), item);
                    filledRoles.add(slot.role());
                    consumed.add(i);
                    thematicByTokenIndex.put(i, slot.role());

                    // Re-key modifiers from iid-based to role-based
                    String iidKey = "iid:" + item.iid().encodeText();
                    List<Sememe> mods = modifiers.remove(iidKey);
                    if (mods != null) {
                        modifiers.put("role:" + slot.role().encodeText(), mods);
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

        // Step 6: Compute unbound slots
        List<ArgumentSlot> unboundRequired = new ArrayList<>();
        List<ArgumentSlot> unboundOptional = new ArrayList<>();
        for (ArgumentSlot slot : arguments) {
            if (!filledRoles.contains(slot.role())) {
                if (slot.required()) {
                    unboundRequired.add(slot);
                } else {
                    unboundOptional.add(slot);
                }
            }
        }

        // Step 7: Track dangling preposition at end for completion UI.
        boolean lastTokenIsDanglingPreposition = false;
        if (!slots.isEmpty()) {
            int lastIndex = slots.size() - 1;
            Item lastItem = slots.get(lastIndex).item();
            lastTokenIsDanglingPreposition =
                    (lastItem instanceof PrepositionSememe) && !consumed.contains(lastIndex);
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
                    BindingRole.classify(item, token),
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
                Collections.unmodifiableList(unboundRequired),
                Collections.unmodifiableList(unboundOptional),
                lastTokenIsDanglingPreposition,
                Collections.unmodifiableList(termBindings)
        );

        if (traceEnabled()) {
            logger.info("[Parse] verb={} bindings={} unboundRequired={} unmatched={} termBindings={}",
                    verb.displayToken(), bindings, unboundRequired, unmatchedArgs, termBindings);
        }

        return Optional.of(analysis);
    }

    /**
     * Assemble a SemanticFrame from resolved tokens (order-agnostic).
     *
     * @param tokens   The resolved tokens from evaluation
     * @param resolver Resolves an ItemID to its Item (typically librarianHandle::get)
     * @return The assembled frame, or empty if no VerbSememe found in tokens
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
                a.unboundRequired(),
                a.unboundOptional()
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
            ToIntFunction<VerbSememe> headVerbScorer) {

        if (tokens.isEmpty()) return List.of();

        // Count verbs and find conjunction positions between them
        List<Integer> verbIndices = new ArrayList<>();
        List<Integer> conjIndices = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i) instanceof ResolvedToken.Link link) {
                Optional<Item> item = resolver.apply(link.iid());
                if (item.isPresent()) {
                    if (item.get() instanceof VerbSememe) verbIndices.add(i);
                    else if (item.get() instanceof ConjunctionSememe) conjIndices.add(i);
                }
            }
        }

        // Only split if there are 2+ verbs with a conjunction between them
        if (verbIndices.size() < 2 || conjIndices.isEmpty()) {
            Optional<SemanticFrame> single = assemble(tokens, resolver);
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

            // Check if this slot is a ConjunctionSememe
            Item conjItem = slots.get(conjIndex).item();
            if (!(conjItem instanceof ConjunctionSememe)) break;

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
     * Choose the head verb index by combining static priors and runtime dispatchability.
     */
    private static int selectHeadVerbIndex(List<Slot> slots, ToIntFunction<VerbSememe> headVerbScorer) {
        int bestIndex = -1;
        int bestScore = Integer.MIN_VALUE;

        for (int i = 0; i < slots.size(); i++) {
            Item item = slots.get(i).item();
            if (!(item instanceof VerbSememe verb)) continue;

            int score = baseHeadScore(verb) + headVerbScorer.applyAsInt(verb);
            // Stable tie-breaker: earlier token wins.
            if (score > bestScore) {
                bestScore = score;
                bestIndex = i;
            }
            if (traceEnabled()) {
                logger.info("[Parse] head-candidate token={} verb={} score={}",
                        i, verb.displayToken(), score);
            }
        }
        return bestIndex;
    }

    /**
     * Static priors for selecting dispatch heads.
     *
     * <p>Relational/type-link predicates should usually bind as arguments (THEME)
     * to an action verb (e.g. "find implemented-by"), not become the head action.
     */
    private static int baseHeadScore(VerbSememe verb) {
        int score = 0;
        String key = verb.canonicalKey();
        if (key != null) {
            if (key.startsWith("cg.rel:") || key.startsWith("cg.type:")) {
                score -= 200;
            }
            if (key.startsWith("cg.verb:") || key.startsWith("cg.session:")) {
                score += 25;
            }
        }
        // Verbs with explicit argument frames are typically command heads.
        score += Math.min(verb.arguments().size(), 4) * 10;
        return score;
    }
}
