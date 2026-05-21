package dev.everydaythings.graph.scene;

import dev.everydaythings.graph.ref.ItemRef;

import java.util.Optional;

/**
 * VariableResolver — the contract the {@code Presenter} consults when it
 * encounters a {@link dev.everydaythings.graph.CoreVocabulary.Variable
 * Variable} reference in a scene-tree binding.
 *
 * <p>Variables ({@code CurrentTime}, {@code Viewport}, {@code BaseFontSize},
 * {@code DevicePixelSize}, ...) are sememes whose values come from the
 * resolution context rather than the scene declaration itself.  The
 * session binds them at runtime and provides this resolver.
 *
 * <h2>Contract</h2>
 *
 * <p>{@link #resolve(ItemRef)} takes a Variable's IID and returns the
 * current value the resolver knows for it.  Returns {@link Optional#empty()}
 * when the resolver has no binding for the given Variable — the presenter
 * leaves the reference in place so a downstream stage (or a remote
 * client's resolver) can substitute it later.
 *
 * <p>The returned value can be any object the scene-binding slot will
 * accept: a {@link String} (for text), a primitive number boxed (for
 * numeric properties), an {@link java.time.Instant Instant} or epoch
 * {@code long} (for CurrentTime), a {@code Viewport} value, etc.  The
 * presenter substitutes the resolved value into the binding target;
 * whatever type the slot expects is the resolver's responsibility to
 * supply.
 *
 * <h2>Cardinality</h2>
 *
 * <p>One resolver per render pass.  Variables read multiple times within
 * a single pass should return consistent values; the session captures
 * "now" once per tick rather than letting different parts of the scene
 * see different times within the same frame.  Resolvers are not
 * required to memoize on their own; the session is responsible for
 * snapshot semantics if it cares.
 */
@FunctionalInterface
public interface VariableResolver {

    /**
     * Return the current value bound for the given Variable IID, or
     * empty if this resolver doesn't supply that Variable.
     */
    Optional<Object> resolve(ItemRef variable);

    /** A resolver that knows no variables — every {@code resolve} returns empty. */
    VariableResolver NONE = ref -> Optional.empty();
}
