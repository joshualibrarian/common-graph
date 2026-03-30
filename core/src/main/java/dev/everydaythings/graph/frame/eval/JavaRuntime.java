package dev.everydaythings.graph.frame.eval;

import dev.everydaythings.graph.frame.Binding;
import dev.everydaythings.graph.frame.BindingTarget;
import dev.everydaythings.graph.item.Literal;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.value.Function;
import dev.everydaythings.graph.value.Operator;

import java.util.List;
import java.util.Optional;

/**
 * Language runtime for Java implementations.
 *
 * <p>Handles Java code references from IMPLEMENTED_BY frames:
 * <ul>
 *   <li><b>Java class names</b> — {@link Literal} with {@code TYPE_JAVA_CLASS}.
 *       The class is loaded and checked for {@link PredicateBehavior}. If it
 *       implements the interface directly, it's used as-is.</li>
 * </ul>
 *
 * <p>The Java runtime is NOT sandboxed — it runs in the host JVM with full
 * access. Untrusted code should use sandboxed runtimes (WASM, formula).
 */
public final class JavaRuntime implements LanguageRuntime {

    public static final String KEY = "cg.language:java";
    public static final ItemID LANGUAGE_ID = ItemID.fromString(KEY);

    @Override
    public ItemID languageId() {
        return LANGUAGE_ID;
    }

    @Override
    public PredicateBehavior resolve(BindingTarget codeReference, ItemID predicate, Scope scope) {
        // Java class reference from IMPLEMENTED_BY frame
        if (codeReference instanceof Literal lit
                && Literal.TYPE_JAVA_CLASS.equals(lit.valueType())) {
            return resolveJavaClass(lit, predicate, scope);
        }
        return null;
    }

    // ==================================================================================
    // Java Class Resolution
    // ==================================================================================

    private PredicateBehavior resolveJavaClass(Literal classLit, ItemID predicate, Scope scope) {
        String className = classLit.asText();
        if (className == null) return null;

        try {
            Class<?> clazz = Class.forName(className);

            // Operator subclass — get from graph, wrap applyBinary/applyUnary
            if (Operator.class.isAssignableFrom(clazz)) {
                return resolveOperator(predicate, scope);
            }

            // Function subclass — get from graph, wrap apply
            if (Function.class.isAssignableFrom(clazz)) {
                return resolveFunction(predicate, scope);
            }

            // If the class directly implements PredicateBehavior, use it
            if (PredicateBehavior.class.isAssignableFrom(clazz)) {
                return (PredicateBehavior) clazz.getDeclaredConstructor().newInstance();
            }

        } catch (ClassNotFoundException e) {
            // Class not available on this host — not an error, just can't handle it
        } catch (Exception e) {
            // Instantiation failed
        }
        return null;
    }

    /**
     * Wrap an Operator into a PredicateBehavior.
     *
     * <p>Gets the hydrated Operator instance from the graph via the librarian,
     * then wraps its applyBinary/applyUnary into a PredicateBehavior.
     */
    private PredicateBehavior resolveOperator(ItemID predicate, Scope scope) {
        if (scope.librarian() == null) return null;
        Optional<Operator> opt = scope.librarian().get(predicate, Operator.class);
        if (opt.isEmpty()) return null;
        Operator op = opt.get();

        if (op.isShortCircuit()) {
            return (bindings, evaluator, evalScope) -> {
                Binding theme = FrameEvaluator.findBinding(bindings, ThematicRole.Theme.IID);
                Binding goal = FrameEvaluator.findBinding(bindings, ThematicRole.Goal.IID);
                Object left = theme != null ? evaluator.resolve(theme.target(), evalScope) : null;

                if (op instanceof Operator.And) {
                    if (!Operator.toBoolean(left)) return false;
                    Object right = goal != null ? evaluator.resolve(goal.target(), evalScope) : null;
                    return Operator.toBoolean(right);
                }
                if (op instanceof Operator.Or) {
                    if (Operator.toBoolean(left)) return true;
                    Object right = goal != null ? evaluator.resolve(goal.target(), evalScope) : null;
                    return Operator.toBoolean(right);
                }
                throw new UnsupportedOperationException("Unknown short-circuit op: " + op.canonicalKey());
            };
        }

        if (op.isPrefix() && op.arity() == 1) {
            return (bindings, evaluator, evalScope) -> {
                Object operand = evaluator.resolveRole(bindings, ThematicRole.Theme.IID, evalScope);
                return op.applyUnary(operand);
            };
        }

        // Binary operator
        return (bindings, evaluator, evalScope) -> {
            Object left = evaluator.resolveRole(bindings, ThematicRole.Theme.IID, evalScope);
            Object right = evaluator.resolveRole(bindings, ThematicRole.Goal.IID, evalScope);
            return op.applyBinary(left, right);
        };
    }

    /**
     * Wrap a Function into a PredicateBehavior.
     *
     * <p>Gets the hydrated Function instance from the graph via the librarian,
     * then wraps its apply() into a PredicateBehavior.
     */
    private PredicateBehavior resolveFunction(ItemID predicate, Scope scope) {
        if (scope.librarian() == null) return null;
        Optional<Function> opt = scope.librarian().get(predicate, Function.class);
        if (opt.isEmpty()) return null;
        Function fn = opt.get();

        return (bindings, evaluator, evalScope) -> {
            List<Binding> themeBindings = FrameEvaluator.findAllBindings(bindings, ThematicRole.Theme.IID);
            List<Object> args = new java.util.ArrayList<>();
            for (Binding b : themeBindings) {
                args.add(evaluator.resolve(b.target(), evalScope));
            }
            if (args.size() == 1) {
                Binding goal = FrameEvaluator.findBinding(bindings, ThematicRole.Goal.IID);
                if (goal != null) args.add(evaluator.resolve(goal.target(), evalScope));
            }
            return fn.apply(args);
        };
    }

}
