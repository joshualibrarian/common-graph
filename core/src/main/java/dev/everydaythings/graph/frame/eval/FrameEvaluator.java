package dev.everydaythings.graph.frame.eval;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.frame.Frame;
import dev.everydaythings.graph.frame.FrameBody;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.CoreVocabulary;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.value.Operator;
import dev.everydaythings.graph.value.Unit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Unified evaluator for frame bodies.
 *
 * <p>The ONE evaluation path: {@code FrameBody in → resolve predicate →
 * get implementation → impl.evaluate(bindings, evaluator, scope) → result}.
 *
 * <p>Implementation resolution goes through the graph: predicate sememe →
 * IMPLEMENTED_BY frames → (language, code-reference) → matching
 * {@link LanguageRuntime} → {@link PredicateBehavior}.
 *
 * <p>Registered runtimes handle different code formats:
 * <ul>
 *   <li>{@link FormulaRuntime} — FrameBody trees (universal, every host evaluates these)</li>
 *   <li>{@link JavaRuntime} — Java classes and @Verb methods (host-specific)</li>
 *   <li>Future: WASM, Python, etc.</li>
 * </ul>
 *
 * <p>Seed operators, functions, and control flow are resolved as a temporary
 * fallback until all seeds carry proper IMPLEMENTED_BY frames. This fallback
 * is equivalent to having those frames in memory — the same predicates,
 * the same implementations — just not yet migrated to frame structure.
 */
public final class FrameEvaluator {

    private static final int MAX_DEPTH = 64;

    private final List<LanguageRuntime> runtimes;
    private final JavaRuntime javaRuntime;
    private int depth;

    public FrameEvaluator() {
        this.javaRuntime = new JavaRuntime();
        this.runtimes = List.of(new FormulaRuntime(), javaRuntime);
        this.depth = 0;
    }

    public FrameEvaluator(List<LanguageRuntime> runtimes) {
        this.runtimes = List.copyOf(runtimes);
        this.javaRuntime = runtimes.stream()
                .filter(r -> r instanceof JavaRuntime)
                .map(r -> (JavaRuntime) r)
                .findFirst()
                .orElse(new JavaRuntime());
        this.depth = 0;
    }

    // ==================================================================================
    // Core Evaluation
    // ==================================================================================

    /**
     * Evaluate a FrameBody in the given scope.
     *
     * <p>This is the main entry point. Resolves the predicate to a
     * PredicateBehavior and delegates.
     */
    public Object evaluate(FrameBody frame, Scope scope) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(scope, "scope");

        if (++depth > MAX_DEPTH) {
            depth--;
            throw new IllegalStateException("Evaluation depth exceeded " + MAX_DEPTH);
        }

        try {
            PredicateBehavior impl = resolveImplementation(frame.predicate(), scope);
            if (impl == null) {
                throw new IllegalArgumentException(
                        "No implementation for predicate: " + frame.predicate());
            }
            return impl.evaluate(frame.frameBindings(), this, scope);
        } finally {
            depth--;
        }
    }

    /**
     * Resolve a single BindingTarget to a Java value.
     *
     * <p>Used by implementations to eagerly evaluate individual bindings.
     * <ul>
     *   <li>Literal → extract Java value (String, long, boolean)</li>
     *   <li>FrameTarget → recursively evaluate the nested frame</li>
     *   <li>IidTarget → resolve to item or return the ItemID</li>
     *   <li>RefTarget → resolve reference</li>
     * </ul>
     */
    public Object resolve(BindingTarget target, Scope scope) {
        if (target == null) return null;

        if (target instanceof Literal lit) {
            return decodeLiteral(lit);
        }

        if (target instanceof BindingTarget.FrameTarget ft) {
            return evaluate(ft.body(), scope);
        }

        if (target instanceof BindingTarget.IidTarget iid) {
            return resolveItemId(iid.iid(), scope);
        }

        if (target instanceof BindingTarget.RefTarget ref) {
            return resolveItemId(ref.asItemId(), scope);
        }

        return target;
    }

    /**
     * Create a child scope with a new variable binding.
     */
    public Scope withVariable(Scope scope, String name, Object value) {
        return scope.withVariable(name, value);
    }

    // ==================================================================================
    // Binding Helpers (for implementations)
    // ==================================================================================

    /**
     * Find the first binding with the given role from a binding list.
     */
    public static Binding findBinding(List<Binding> bindings, ItemID role) {
        if (bindings == null) return null;
        for (Binding b : bindings) {
            if (b.isSimpleKey() && role.equals(b.role())) return b;
        }
        return null;
    }

    /**
     * Find ALL bindings with the given role (for multi-valued roles like SEQUENCE).
     */
    public static List<Binding> findAllBindings(List<Binding> bindings, ItemID role) {
        if (bindings == null) return List.of();
        List<Binding> result = new ArrayList<>();
        for (Binding b : bindings) {
            if (b.isSimpleKey() && role.equals(b.role())) result.add(b);
        }
        return result;
    }

    /**
     * Eagerly resolve a specific role's binding target to a value.
     */
    public Object resolveRole(List<Binding> bindings, ItemID role, Scope scope) {
        Binding b = findBinding(bindings, role);
        return b != null ? resolve(b.target(), scope) : null;
    }

    // ==================================================================================
    // Implementation Resolution
    // ==================================================================================

    /**
     * Resolve a predicate ItemID to a PredicateBehavior.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>IMPLEMENTED_BY frames on the predicate sememe — ask each
     *       registered {@link LanguageRuntime} to handle the code reference</li>
     *   <li>Verb dispatch via {@link JavaRuntime} — checks vocabulary chain</li>
     *   <li>Seed fallback — operators, functions, control flow (temporary,
     *       until all seeds carry proper IMPLEMENTED_BY frames)</li>
     * </ol>
     */
    PredicateBehavior resolveImplementation(ItemID predicate, Scope scope) {
        if (predicate == null) return null;

        // 1. Control flow primitives (evaluator built-ins — like CPU instructions)
        PredicateBehavior controlFlow = controlFlowImpl(predicate);
        if (controlFlow != null) return controlFlow;

        // 2. IMPLEMENTED_BY frame on the predicate sememe → ask runtimes
        BindingTarget codeRef = lookupImplementedBy(predicate, scope);
        if (codeRef != null) {
            for (LanguageRuntime runtime : runtimes) {
                PredicateBehavior impl = runtime.resolve(codeRef, predicate, scope);
                if (impl != null) return impl;
            }
        }

        // No implementation found
        return null;
    }

    /**
     * Look up the IMPLEMENTED_BY frame's GOAL binding for a predicate sememe.
     *
     * <p>Fetches the predicate item from the graph, inspects its frames for
     * an IMPLEMENTED_BY relation, and returns the code reference (GOAL binding).
     */
    private BindingTarget lookupImplementedBy(ItemID predicate, Scope scope) {
        if (scope.librarian() == null) return null;

        Optional<Item> item = scope.librarian().get(predicate, Item.class);
        if (item.isEmpty()) return null;

        Item sememe = item.get();
        if (sememe.frames() == null) return null;

        ItemID implPredicate = CoreVocabulary.ImplementedBy.IID;
        var it = sememe.frames().bareFrames().iterator();
        while (it.hasNext()) {
            Frame frame = it.next();
            Optional<Object> live = sememe.frames().getLive(frame.frameKey());
            if (live.isPresent() && live.get() instanceof FrameBody body) {
                if (implPredicate.equals(body.predicate())) {
                    return body.binding(ThematicRole.Goal.IID);
                }
            }
        }
        return null;
    }

    // ==================================================================================
    // Control Flow Implementations (evaluator primitives)
    // ==================================================================================

    private PredicateBehavior controlFlowImpl(ItemID predicate) {
        if (CoreVocabulary.Conditional.IID.equals(predicate)) return conditionalImpl();
        if (CoreVocabulary.Sequence.IID.equals(predicate)) return sequenceImpl();
        if (CoreVocabulary.Let.IID.equals(predicate)) return letImpl();
        if (CoreVocabulary.Resolve.IID.equals(predicate)) return resolveImpl();
        if (CoreVocabulary.Access.IID.equals(predicate)) return accessImpl();
        return null;
    }

    /**
     * CONDITIONAL: if THEME then RESULT else GOAL.
     */
    private PredicateBehavior conditionalImpl() {
        return (bindings, evaluator, scope) -> {
            Binding condition = findBinding(bindings, ThematicRole.Theme.IID);
            Object condValue = condition != null ? evaluator.resolve(condition.target(), scope) : null;

            if (Operator.toBoolean(condValue)) {
                Binding thenBranch = findBinding(bindings, ThematicRole.Result.IID);
                return thenBranch != null ? evaluator.resolve(thenBranch.target(), scope) : null;
            } else {
                Binding elseBranch = findBinding(bindings, ThematicRole.Goal.IID);
                return elseBranch != null ? evaluator.resolve(elseBranch.target(), scope) : null;
            }
        };
    }

    /**
     * SEQUENCE: evaluate each THEME binding in order, return last.
     */
    private PredicateBehavior sequenceImpl() {
        return (bindings, evaluator, scope) -> {
            List<Binding> steps = findAllBindings(bindings, ThematicRole.Theme.IID);
            Object result = null;
            for (Binding step : steps) {
                result = evaluator.resolve(step.target(), scope);
            }
            return result;
        };
    }

    /**
     * LET: bind GOAL (name) = THEME (value), evaluate RESULT in child scope.
     */
    private PredicateBehavior letImpl() {
        return (bindings, evaluator, scope) -> {
            Binding nameBinding = findBinding(bindings, ThematicRole.Goal.IID);
            Object value = evaluator.resolveRole(bindings, ThematicRole.Theme.IID, scope);

            String name = null;
            if (nameBinding != null && nameBinding.target() instanceof Literal lit) {
                name = lit.asText();
            }
            if (name == null) {
                throw new IllegalArgumentException("LET requires a name (GOAL binding as text literal)");
            }

            Scope childScope = scope.withVariable(name, value);
            Binding body = findBinding(bindings, ThematicRole.Result.IID);
            return body != null ? evaluator.resolve(body.target(), childScope) : value;
        };
    }

    /**
     * RESOLVE: look up a variable name from the scope chain.
     */
    private PredicateBehavior resolveImpl() {
        return (bindings, evaluator, scope) -> {
            Binding nameBinding = findBinding(bindings, ThematicRole.Theme.IID);
            if (nameBinding == null) return null;

            if (nameBinding.target() instanceof Literal lit) {
                String name = lit.asText();
                return scope.lookup(name).orElse(null);
            }
            return evaluator.resolve(nameBinding.target(), scope);
        };
    }

    /**
     * ACCESS: property access — resolve THEME (object), access GOAL (property).
     */
    private PredicateBehavior accessImpl() {
        return (bindings, evaluator, scope) -> {
            Object target = evaluator.resolveRole(bindings, ThematicRole.Theme.IID, scope);
            Binding propBinding = findBinding(bindings, ThematicRole.Goal.IID);
            if (propBinding == null || target == null) return target;

            // Property name as text literal — look up in scope
            if (propBinding.target() instanceof Literal lit) {
                String propName = lit.asText();
                // If target is an Item, try frame lookup via body predicate matching
                // For now, return the property name for use by callers
                return propName;
            }
            return evaluator.resolve(propBinding.target(), scope);
        };
    }

    // ==================================================================================
    // Value Resolution Helpers
    // ==================================================================================

    private Object resolveItemId(ItemID iid, Scope scope) {
        if (iid == null) return null;
        if (scope.librarian() == null) return iid;

        // Check if it's a known unit
        Unit u = Unit.lookupSeed(iid);
        if (u != null) return u;

        // Try to resolve from librarian
        Optional<Item> item = scope.librarian().get(iid, Item.class);
        return item.isPresent() ? item.get() : iid;
    }

    /**
     * Decode a Literal to a Java value.
     */
    static Object decodeLiteral(Literal lit) {
        if (lit == null) return null;
        try { return lit.asText(); } catch (Exception ignored) {}
        try { return lit.asInteger(); } catch (Exception ignored) {}
        try { return lit.asBoolean(); } catch (Exception ignored) {}
        // Fall back to raw payload
        return lit;
    }

}
