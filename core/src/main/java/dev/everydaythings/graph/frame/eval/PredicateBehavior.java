package dev.everydaythings.graph.frame.eval;

import dev.everydaythings.graph.frame.Binding;

import java.util.List;

/**
 * Unified behavior interface for predicates — combines parsing and evaluation.
 *
 * <p>Every predicate can carry BOTH a parsing contribution (how it participates
 * in parsing) AND an evaluation strategy (how it computes results from filled
 * bindings).
 *
 * <p>Still {@code @FunctionalInterface} because {@link #contribute} has a default
 * implementation — lambdas that provide only evaluation get a default no-op
 * parsing contribution.
 *
 * <p>The two methods represent two phases of the predicate lifecycle:
 * <ol>
 *   <li><b>Parsing</b> ({@link #contribute}): The language parser calls this when
 *       it encounters the predicate. The predicate can declare metadata (precedence,
 *       fixity, expected roles), chain additional frames, or delegate to a
 *       sub-language (e.g., chess notation, expression syntax).</li>
 *   <li><b>Evaluation</b> ({@link #evaluate}): The evaluator calls this with
 *       filled bindings to compute the result. The implementation controls
 *       evaluation order, enabling lazy evaluation and short-circuit logic.</li>
 * </ol>
 *
 * <p>This is the same pattern as {@link dev.everydaythings.graph.value.Operator}
 * and {@link dev.everydaythings.graph.value.Function}, which already carry both
 * parsing metadata (precedence, fixity) and evaluation code (applyBinary, apply).
 * PredicateBehavior makes this pattern explicit and universal.
 */
@FunctionalInterface
public interface PredicateBehavior {

    /**
     * Evaluate this predicate with filled bindings.
     *
     * <p>The implementation controls evaluation order. Use
     * {@link FrameEvaluator#resolve(dev.everydaythings.graph.frame.BindingTarget, Scope)}
     * to eagerly evaluate individual binding targets, or
     * {@link FrameEvaluator#evaluate(dev.everydaythings.graph.frame.FrameBody, Scope)}
     * to evaluate nested frames.
     *
     * @param bindings  the frame's role bindings
     * @param evaluator callback for resolving targets and evaluating nested frames
     * @param scope     the current evaluation scope (librarian, owner, variables)
     * @return the evaluation result
     */
    Object evaluate(List<Binding> bindings, FrameEvaluator evaluator, Scope scope);

    /**
     * How this predicate participates in parsing.
     *
     * <p>Called by the language parser when this predicate is encountered in
     * the token stream. The predicate can return:
     * <ul>
     *   <li>{@link ParseContribution#NONE} — no opinion (default)</li>
     *   <li>Declarative metadata — fixity, precedence, expected roles</li>
     *   <li>Active behavior — delegate to a sub-language, consume tokens, chain frames</li>
     * </ul>
     *
     * <p>Most predicates return declarative metadata. The language parser reads
     * this metadata and applies it according to its own grammar rules. Active
     * predicates (chess notation, WHERE clauses) can take control of parsing
     * by delegating to a sub-language or consuming tokens directly.
     *
     * @param context the current parsing context (tokens, bindings, scope)
     * @return the parsing contribution, never null
     */
    default ParseContribution contribute(ParseContext context) {
        return ParseContribution.NONE;
    }
}
