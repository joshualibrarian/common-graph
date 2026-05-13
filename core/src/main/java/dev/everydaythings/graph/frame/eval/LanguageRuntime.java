package dev.everydaythings.graph.frame.eval;

import dev.everydaythings.graph.datum.BindingTarget;
import dev.everydaythings.graph.item.id.ItemID;

/**
 * Adapts a code reference into a {@link PredicateBehavior}.
 *
 * <p>A LanguageRuntime knows how to load and execute implementations written
 * in its language. The evaluator discovers implementations through
 * IMPLEMENTED_BY frames on predicate sememes — each frame's GOAL binding
 * is a code reference (Java class name, WASM CID, formula FrameBody, etc.).
 * The matching runtime turns that reference into a callable PredicateBehavior.
 *
 * <p>Language runtimes are conceptually Items in the graph ({@code cg.language:java},
 * {@code cg.language:formula}, {@code cg.language:wasm}). On the Java host,
 * each runtime is a concrete implementation of this interface.
 *
 * <p>Sandboxing is a policy concern, not a runtime concern. Trust policies
 * on Items govern resource limits and capabilities. Runtimes enforce those
 * policies using whatever mechanism they have (WASM's linear memory model,
 * GraalVM resource limits, etc.).
 */
public interface LanguageRuntime {

    /**
     * The language this runtime handles (e.g., {@code cg.language:java}).
     */
    ItemID languageId();

    /**
     * Resolve a code reference to a PredicateBehavior.
     *
     * <p>The code reference comes from the GOAL binding of an IMPLEMENTED_BY
     * frame on a predicate sememe. Its type identifies the language:
     * <ul>
     *   <li>{@link dev.everydaythings.graph.item.Literal} with
     *       {@code TYPE_JAVA_CLASS} → Java class name</li>
     *   <li>{@link dev.everydaythings.graph.datum.BindingTarget.FrameTarget}
     *       → formula (FrameBody tree)</li>
     *   <li>Future: CID referencing WASM bytes, Python script, etc.</li>
     * </ul>
     *
     * @param codeReference the code reference from the IMPLEMENTED_BY frame
     * @param predicate     the predicate being implemented (for context)
     * @param scope         the evaluation scope
     * @return a PredicateBehavior, or null if this runtime cannot handle it
     */
    PredicateBehavior resolve(BindingTarget codeReference, ItemID predicate, Scope scope);
}
