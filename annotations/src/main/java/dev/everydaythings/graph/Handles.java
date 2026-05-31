package dev.everydaythings.graph;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as the handler for frames whose head is a given predicate.
 *
 * <p>At seed-processing time, the annotation produces a HANDLES frame on
 * the enclosing archetype's manifest (predicate-as-data, queryable and
 * inheritable across language runtimes) and an endorsement on the
 * embodying CodeItem.  At dispatch time, the librarian's two-hop walk
 * ({@code HANDLES → IMPLEMENTS}) routes incoming frames to a live
 * instance of the handling archetype and invokes its method via
 * {@link dev.everydaythings.graph.runtime.stage.ItemStage#deliver
 * ItemStage.deliver}.
 *
 * <p><b>Method signature:</b> {@code Object handler(Frame frame)}.  The
 * universal shape is a single {@link
 * dev.everydaythings.graph.datum.Frame Frame} parameter; handlers that
 * need specific values read them from the frame's bindings inside the
 * method body.  Return value (a single Frame or a {@code List<Frame>})
 * becomes the response.
 *
 * <p>The Java method named here is the truth — direct in-VM callers can
 * invoke it without constructing a frame.  The annotation marks it as
 * <i>also</i> reachable via frame dispatch.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Handles {

    /**
     * Canonical key of the predicate whose frames this method handles.
     */
    String predicate();

    /**
     * Optional canonical key of the binding-role on incoming frames that
     * names the <i>target instance</i> for dispatch.
     *
     * <p>When set, the dispatcher reads the incoming frame's
     * {@code binding[role]} to find the IID of the item-instance this
     * frame is about; it then looks up the live instance of the handling
     * archetype at that IID and invokes the handler on it.  Used for
     * archetypes whose handlers care about <i>which specific instance</i>
     * is being addressed (Session via {@code Location}, Signer via
     * {@code Agent}, etc.).
     *
     * <p>When empty (default), dispatch is singleton: the handler runs on
     * a code-item instance (cached if previously materialized, freshly
     * hydrated otherwise) without consulting frame bindings.  Used for
     * stateless operator-style archetypes (Add, Between, Sqrt, ...).
     */
    String role() default "";
}
