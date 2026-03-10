package dev.everydaythings.graph.expression;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.ArgumentSlot;
import dev.everydaythings.graph.language.FrameAssembler;
import dev.everydaythings.graph.language.Posting;
import dev.everydaythings.graph.language.PrepositionSememe;
import dev.everydaythings.graph.language.Sememe;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.language.VerbSememe;

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
 * @param verb                   The verb found in accepted tokens, or null
 * @param filledRoles            Thematic roles already bound by accepted tokens
 * @param unfilledRequired       Required argument slots still needing values
 * @param unfilledOptional       Optional argument slots still available
 * @param lastTokenIsPreposition Whether the last token is a preposition awaiting its object
 */
public record ExpressionContext(
        VerbSememe verb,
        Set<ThematicRole> filledRoles,
        List<ArgumentSlot> unfilledRequired,
        List<ArgumentSlot> unfilledOptional,
        boolean lastTokenIsPreposition
) {

    /**
     * Empty context — no analysis possible (no tokens or no librarian).
     */
    public static final ExpressionContext EMPTY = new ExpressionContext(
            null, Set.of(), List.of(), List.of(), false);

    /**
     * Analyze the current accepted tokens to build an expression context.
     *
     * <p>Runs ExpressionTokens through the shared semantic analysis pipeline:
     * <ol>
     *   <li>Load each RefToken's target via the resolver</li>
     *   <li>Find the first VerbSememe → the verb</li>
     *   <li>Track preposition-object pairs → fill roles</li>
     *   <li>Match bare items to verb argument slots by first-fit</li>
     *   <li>Compute unfilled required/optional slots</li>
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
                a.unboundRequired(),
                a.unboundOptional(),
                a.lastTokenIsDanglingPreposition()
        );
    }

    /**
     * Filter completions based on the current expression context.
     *
     * <p>Narrowing rules:
     * <ul>
     *   <li>No verb yet → show everything (no filter)</li>
     *   <li>Verb accepted → exclude other VerbSememes (you have your action)</li>
     *   <li>Last token is open preposition → also exclude PrepositionSememes</li>
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

            // Once we have a verb, exclude other verbs from completions
            if (item instanceof VerbSememe) {
                continue;
            }

            if (lastTokenIsPreposition && item instanceof PrepositionSememe) {
                // Preposition needs an object, not another preposition.
                continue;
            }

            filtered.add(posting);
        }

        return filtered;
    }
}
