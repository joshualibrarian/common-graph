package dev.everydaythings.graph.frame.eval;

import dev.everydaythings.graph.datum.BindingTarget;
import dev.everydaythings.graph.item.id.ItemID;

/**
 * Language runtime for formula implementations — FrameBody trees.
 *
 * <p>Formulas are the universal implementation language. Every Librarian,
 * regardless of host language (Java, Rust, C++), can evaluate them because
 * the evaluator algorithm is simple and FrameBody is CBOR-encoded.
 *
 * <p>A formula implementation is a {@link BindingTarget.FrameTarget} — an
 * inline nested FrameBody that the evaluator recursively evaluates.
 *
 * <p>Formulas are sandboxed by construction: they can only invoke predicates
 * that the evaluator can resolve, and they cannot escape the evaluation
 * model to access host-level resources.
 */
public final class FormulaRuntime implements LanguageRuntime {

    public static final String KEY = "cg.language:formula";
    public static final ItemID LANGUAGE_ID = ItemID.fromString(KEY);

    @Override
    public ItemID languageId() {
        return LANGUAGE_ID;
    }

    @Override
    public PredicateBehavior resolve(BindingTarget codeReference, ItemID predicate, Scope scope) {
        if (!(codeReference instanceof BindingTarget.FrameTarget ft)) return null;
        return (bindings, evaluator, evalScope) ->
                evaluator.evaluate(ft.body(), evalScope);
    }
}
