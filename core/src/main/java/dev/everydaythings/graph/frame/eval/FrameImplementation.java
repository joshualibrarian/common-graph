package dev.everydaythings.graph.frame.eval;

import dev.everydaythings.graph.frame.Binding;

import java.util.List;

/**
 * The evaluation strategy for a frame predicate.
 *
 * <p>Each predicate (operator, function, verb, control flow) has an implementation
 * that knows how to evaluate its bindings and produce a result. The implementation
 * decides when and whether to evaluate each binding — enabling lazy evaluation
 * for short-circuit operators and conditional branching.
 *
 * <p>This is the ONE interface that unifies expression evaluation and verb dispatch.
 * Both paths resolve to: {@code FrameBody in → resolve predicate → get impl →
 * impl.evaluate(bindings, evaluator, scope) → result}.
 */
@FunctionalInterface
public interface FrameImplementation {

    /**
     * Evaluate a frame with the given bindings.
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
}
