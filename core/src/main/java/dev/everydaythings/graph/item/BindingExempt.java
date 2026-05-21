package dev.everydaythings.graph.item;

/**
 * Marker interface for field types that {@link BodyBinder} leaves untouched
 * during hydration.
 *
 * <p>Used for typed handles whose construction requires runtime context the
 * BodyBinder doesn't possess — vault back-references, operation lambdas,
 * peer-specific encryption state.  Fields of types implementing this marker
 * are recognized and skipped; the owning subsystem (vault, etc.) hydrates
 * them in its own pass after BodyBinder runs.
 *
 * <p>The marker has no methods — it just signals "BodyBinder, don't try."
 * Without this marker, BodyBinder would throw when it encountered a field
 * type it couldn't convert; with the marker, it gracefully skips.
 *
 * <p>Implementing types:
 * <ul>
 *   <li>{@code SigningKey}, {@code KeyAgreementKey} — vault-managed typed
 *       key handles.</li>
 * </ul>
 */
public interface BindingExempt {
}
