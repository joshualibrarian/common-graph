package dev.everydaythings.graph.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as the handler for frames whose head is a given predicate.
 *
 * <p>The {@link Librarian} dispatch layer scans the receiving item's class at
 * invocation time, locates the method whose {@code @Handler(predicate=...)}
 * matches the incoming frame's head IID, decomposes the frame's bindings into
 * method parameters, and invokes the method. Return values become response
 * frames.
 *
 * <p>Eventual seed-time work: the scan also produces a HANDLES frame endorsed
 * by the embodying archetype, so the dispatch table is queryable as data and
 * inheritable across language runtimes. For Phase 1, only the Java-reflection
 * path is implemented; the HANDLES frames are pending.
 *
 * <p>The Java method named here is the truth — direct in-VM callers can invoke
 * it without constructing a frame. The annotation marks it as <i>also</i>
 * reachable via frame dispatch.
 *
 * <p>Phase 1 parameter mapping (will be refined):
 * <ul>
 *   <li>String parameter ↔ THEME binding's text literal</li>
 *   <li>Integer parameter ↔ ATTRIBUTE[LIMIT] binding's integer literal (may be null)</li>
 * </ul>
 * This is enough for LOOKUP; a more general role→position scheme will land
 * when more handlers exist.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Handler {

    /** Canonical key of the predicate whose frames this method handles. */
    String predicate();
}
