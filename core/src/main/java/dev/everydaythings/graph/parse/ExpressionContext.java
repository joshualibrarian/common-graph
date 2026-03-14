package dev.everydaythings.graph.parse;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.FrameAssembler;
import dev.everydaythings.graph.language.PartOfSpeech;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.language.CoreVocabulary;

import java.util.*;
import java.util.function.Function;

/**
 * Partial-frame analysis of the current expression for completion narrowing.
 *
 * <p>As the user builds an expression token by token, ExpressionContext tracks
 * what the expression already contains and what it still needs. This enables
 * progressive semantic narrowing of completions — after selecting a verb,
 * the system stops offering other verbs and focuses on arguments.
 *
 * <p>This uses the same semantic analyzer as execution ({@link FrameAssembler})
 * so completion filtering and dispatch share one parsing flow.
 *
 * <h2>Disambiguation Rules</h2>
 * <ol>
 *   <li><b>Verb exclusion</b>: Once a verb is accepted, exclude other verb sememes</li>
 *   <li><b>Preposition exclusion</b>: After a dangling preposition, exclude other PrepositionSememes</li>
 *   <li><b>Role type constraints</b>: When verb is known, prefer candidates that could fill unfilled roles</li>
 *   <li><b>Compound noun resolution</b>: Adjacent CandidateTokens — check if pair forms a known compound</li>
 *   <li><b>Prepositional object constraint</b>: After a preposition, prefer items matching that role's type</li>
 *   <li><b>POS exclusion cascade</b>: A locked noun sememe for a singular role excludes other noun candidates for that role</li>
 * </ol>
 *
 * @param verb                   The verb found in accepted tokens, or null
 * @param filledRoles            Thematic roles already bound by accepted tokens
 * @param unfilledRoles          Argument slot roles still available
 * @param lastTokenIsPreposition Whether the last token is a preposition awaiting its object
 */
public record ExpressionContext(
        Sememe verb,
        Set<ItemID> filledRoles,
        List<ItemID> unfilledRoles,
        boolean lastTokenIsPreposition
) {

    /**
     * Empty context — no analysis possible (no tokens or no librarian).
     */
    public static final ExpressionContext EMPTY = new ExpressionContext(
            null, Set.of(), List.of(), false);

    /**
     * Analyze the current accepted tokens to build an expression context.
     *
     * <p>Runs ExpressionTokens through the shared semantic analysis pipeline:
     * <ol>
     *   <li>Load each RefToken's target via the resolver</li>
     *   <li>Find the first verb sememe → the verb</li>
     *   <li>Track preposition-object pairs → fill roles</li>
     *   <li>Match bare items to verb argument slots by first-fit</li>
     *   <li>Compute unfilled roles</li>
     *   <li>Check if the last token is a dangling preposition</li>
     * </ol>
     *
     * @param tokens   The accepted ExpressionTokens
     * @param resolver Resolves ItemID to Item (typically librarianHandle::get)
     * @return The expression context
     */
    public static ExpressionContext analyze(
            List<ExpressionToken> tokens,
            Function<ItemID, Optional<Item>> resolver) {

        if (tokens.isEmpty()) {
            return EMPTY;
        }

        // Convert UI tokens into resolved tokens and run through the same
        // semantic analyzer used by execution.
        List<dev.everydaythings.graph.runtime.Eval.ResolvedToken> resolved = new ArrayList<>();
        for (ExpressionToken token : tokens) {
            if (token instanceof ExpressionToken.CandidateToken) {
                // Ambiguous — skip; contributes no definite information to the frame
                continue;
            } else if (token instanceof ExpressionToken.RefToken ref) {
                resolved.add(new dev.everydaythings.graph.runtime.Eval.ResolvedToken.Link(
                        ref.target(), ref.displayText()));
            } else if (token instanceof ExpressionToken.LiteralToken lit) {
                resolved.add(new dev.everydaythings.graph.runtime.Eval.ResolvedToken.Literal(
                        lit.value(), lit.displayText()));
            } else {
                // Operators and parentheses are currently not role-bearing in
                // frame assembly; keep them as literals so token order remains stable.
                resolved.add(new dev.everydaythings.graph.runtime.Eval.ResolvedToken.Literal(
                        token.displayText(), token.displayText()));
            }
        }

        Optional<FrameAssembler.Analysis> analysis = FrameAssembler.analyze(resolved, resolver);
        if (analysis.isEmpty()) {
            return EMPTY;
        }
        FrameAssembler.Analysis a = analysis.get();

        return new ExpressionContext(
                a.verb(),
                Collections.unmodifiableSet(new HashSet<>(a.bindings().keySet())),
                a.unboundRoles(),
                a.lastTokenIsDanglingPreposition()
        );
    }

    /**
     * Filter completions based on the current expression context.
     *
     * <p>Narrowing rules:
     * <ul>
     *   <li>No verb yet → show everything (no filter)</li>
     *   <li>Verb accepted → exclude other verb sememes (you have your action)</li>
     *   <li>Last token is open preposition → also exclude preposition sememes</li>
     * </ul>
     *
     * <p>Items that are not Sememes always pass through (they are valid arguments).
     * Unresolvable postings (target not found) also pass through.
     *
     * @param postings The raw completion postings from lookup
     * @param resolver Resolves ItemID to Item for type checking
     * @return Filtered postings
     */
    public List<Posting> filter(
            List<Posting> postings,
            Function<ItemID, Optional<Item>> resolver) {

        if (verb == null) {
            // No verb yet — show everything
            return postings;
        }

        List<Posting> filtered = new ArrayList<>(postings.size());
        for (Posting posting : postings) {
            Optional<Item> target = resolver.apply(posting.target());
            if (target.isEmpty()) {
                // Can't resolve → keep it (don't penalize unknown items)
                filtered.add(posting);
                continue;
            }

            Item item = target.get();

            // Rule 1: Once we have a verb, exclude other verbs from completions
            if (item instanceof Sememe sv && sv.pos().equals(PartOfSpeech.VERB)) {
                continue;
            }

            // Rule 2: Preposition needs an object, not another preposition
            if (lastTokenIsPreposition && item instanceof Sememe sp && sp.pos().equals(PartOfSpeech.PREPOSITION)) {
                continue;
            }

            filtered.add(posting);
        }

        return filtered;
    }

    /**
     * Position-aware pruning for CandidateToken disambiguation.
     *
     * <p>Applies all disambiguation rules in context of where the token sits
     * in the expression and what other tokens are resolved. Rules 1-2 from
     * {@link #filter} plus enhanced rules 3-6.
     *
     * @param tokenIndex index of the CandidateToken being pruned
     * @param candidates current candidates for this token
     * @param allTokens  all tokens in the expression (for compound/adjacency checks)
     * @param resolver   resolves ItemID to Item
     * @return surviving candidates after all applicable rules
     */
    public List<Posting> pruneForPosition(
            int tokenIndex,
            List<Posting> candidates,
            List<ExpressionToken> allTokens,
            Function<ItemID, Optional<Item>> resolver) {

        if (candidates.isEmpty()) {
            return candidates;
        }

        List<Posting> surviving = new ArrayList<>(candidates);

        // Rule 1: Verb exclusion — if we already have a verb, remove verb candidates
        if (verb != null) {
            surviving.removeIf(p -> {
                Optional<Item> item = resolver.apply(p.target());
                return item.isPresent() && item.get() instanceof Sememe sv && sv.pos().equals(PartOfSpeech.VERB);
            });
        }

        // Rule 2: Preposition exclusion — after a dangling preposition, remove preposition candidates
        if (lastTokenIsPreposition) {
            surviving.removeIf(p -> {
                Optional<Item> item = resolver.apply(p.target());
                return item.isPresent() && item.get() instanceof Sememe sp && sp.pos().equals(PartOfSpeech.PREPOSITION);
            });
        }

        // Rule 3: Role type constraints — when verb is known with unfilled roles,
        // check if any candidate is a createable noun (has a CREATE verb) that matches
        // the THEME expectation. Prefer createable nouns over other candidates.
        if (verb != null && !unfilledRoles.isEmpty()) {
            ItemID createId = ItemID.fromString(CoreVocabulary.Create.KEY);
            boolean hasCreateable = false;
            boolean hasNonCreateable = false;
            for (Posting p : surviving) {
                Optional<Item> item = resolver.apply(p.target());
                if (item.isPresent()) {
                    if (item.get().vocabulary().lookup(createId).isPresent()) {
                        hasCreateable = true;
                    } else if (!(item.get() instanceof Sememe sv2 && sv2.pos().equals(PartOfSpeech.VERB))) {
                        hasNonCreateable = true;
                    }
                }
            }
            // If we have both createable and non-createable nouns, and the verb
            // has unfilled roles, prefer createable nouns (they fill the THEME role)
            if (hasCreateable && hasNonCreateable && surviving.size() > 1) {
                List<Posting> createableCandidates = new ArrayList<>();
                for (Posting p : surviving) {
                    Optional<Item> item = resolver.apply(p.target());
                    if (item.isPresent() && item.get().vocabulary().lookup(createId).isPresent()) {
                        createableCandidates.add(p);
                    }
                }
                if (!createableCandidates.isEmpty()) {
                    surviving = createableCandidates;
                }
            }
        }

        // Rule 4: Compound noun resolution — check adjacent tokens for known compounds.
        // If this CandidateToken has a neighbor that's also ambiguous or resolved,
        // check if any pair of (candidate, neighbor) forms a known compound.
        if (tokenIndex > 0 || tokenIndex < allTokens.size() - 1) {
            surviving = filterByAdjacency(tokenIndex, surviving, allTokens, resolver);
        }

        // Rule 5: Prepositional object constraint — if the previous resolved token
        // is a preposition, filter candidates to items that make sense for that role.
        if (tokenIndex > 0) {
            ExpressionToken prev = allTokens.get(tokenIndex - 1);
            if (prev instanceof ExpressionToken.RefToken ref) {
                Optional<Item> prevItem = resolver.apply(ref.target());
                if (prevItem.isPresent() && prevItem.get() instanceof Sememe sp3 && sp3.pos().equals(PartOfSpeech.PREPOSITION)) {
                    // After a preposition — exclude other prepositions from candidates
                    surviving.removeIf(p -> {
                        Optional<Item> item = resolver.apply(p.target());
                        return item.isPresent() && item.get() instanceof Sememe sp4 && sp4.pos().equals(PartOfSpeech.PREPOSITION);
                    });
                }
            }
        }

        // Rule 6: POS exclusion cascade — if THEME is already filled by a noun sememe,
        // exclude noun sememe candidates that would also want to fill THEME.
        // (Only applies when the expression already has a noun filling a singular role.)
        // This is a conservative rule — only exclude if ALL unfilled roles are gone
        // for the specific POS category.
        if (verb != null && unfilledRoles.isEmpty() && !filledRoles.isEmpty()) {
            // All roles filled — exclude noun sememes (they can't fill any role)
            surviving.removeIf(p -> {
                Optional<Item> item = resolver.apply(p.target());
                return item.isPresent() && item.get() instanceof Sememe sn && sn.pos().equals(PartOfSpeech.NOUN);
            });
        }

        return surviving;
    }

    /**
     * Filter candidates by checking adjacency with neighboring tokens.
     *
     * <p>If a neighboring token is a resolved RefToken whose display text,
     * combined with this candidate's text, forms a known compound in the
     * candidate list, prefer that candidate.
     *
     * <p>Example: "set" (ambiguous) + "game" (resolved to SetGame's type) →
     * if any candidate for "set" has the same target as a compound "set game",
     * prefer it.
     */
    private List<Posting> filterByAdjacency(
            int tokenIndex,
            List<Posting> candidates,
            List<ExpressionToken> allTokens,
            Function<ItemID, Optional<Item>> resolver) {

        // Check right neighbor
        if (tokenIndex + 1 < allTokens.size()) {
            ExpressionToken right = allTokens.get(tokenIndex + 1);
            if (right instanceof ExpressionToken.RefToken ref) {
                // See if any candidate's target matches the neighbor's target
                // (indicating they refer to the same item — compound resolution)
                List<Posting> matching = candidates.stream()
                        .filter(p -> p.target().equals(ref.target()))
                        .toList();
                if (!matching.isEmpty()) {
                    return matching;
                }
            }
        }

        // Check left neighbor
        if (tokenIndex > 0) {
            ExpressionToken left = allTokens.get(tokenIndex - 1);
            if (left instanceof ExpressionToken.RefToken ref) {
                List<Posting> matching = candidates.stream()
                        .filter(p -> p.target().equals(ref.target()))
                        .toList();
                if (!matching.isEmpty()) {
                    return matching;
                }
            }
        }

        return candidates;
    }
}
