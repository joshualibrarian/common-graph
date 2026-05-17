package dev.everydaythings.graph.canonical;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.BindingTarget;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Datum;
import dev.everydaythings.graph.datum.DatumNode;
import dev.everydaythings.graph.datum.Opaque;
import dev.everydaythings.graph.id.CompoundKey;
import dev.everydaythings.graph.id.HashID;

/**
 * Side-effect visitor base for walking a {@link Datum}'s structure.
 *
 * <p>Subclasses override per-position hook methods to do their work; the base
 * drives the traversal.  Each hook has a default no-op implementation — a
 * subclass overrides only what it cares about.
 *
 * <p>Sibling walkers in the codebase:
 * <ul>
 *   <li>{@link CanonWalker} — encoding-agnostic canonical-hash construction;
 *       returns a {@link Node} tree.  Does NOT extend this base because its
 *       result-producing shape differs; possibly refactored later if a clean
 *       unification emerges.</li>
 *   <li>{@code QueryWalker} (library) — routing decision: does this frame
 *       contain query-shaped references or matcher-producing sememes?</li>
 *   <li>{@code SchemaWalker} (library) — schema validation: does a candidate
 *       body satisfy an archetype's EXPECTS declarations?</li>
 * </ul>
 *
 * <p>The traversal visits:
 * <ol>
 *   <li>The datum's head reference.</li>
 *   <li>Each entry in the bindings list — most are Bindings, some may be
 *       {@link Opaque} stand-ins.  A {@link #visitOpaqueEntry} hook fires for
 *       opaques; the default no-op skips them.</li>
 *   <li>For each Binding: its compound-key parts (role + qualifiers, possibly
 *       with Opaque qualifier stand-ins), then its target.</li>
 * </ol>
 *
 * <p>Subclasses are free to short-circuit by checking accumulated state — the
 * base doesn't stop traversal automatically, but {@code walk()} can be made
 * to return early via the {@link #shouldStop()} hook.
 */
public abstract class DatumWalker {

    /**
     * Drive traversal of a datum.  Override sparingly; prefer overriding the
     * per-position hooks below.
     */
    public void walk(Datum d) {
        visitHead(d.head());
        if (shouldStop()) return;
        for (DatumNode entry : d.entries()) {
            if (entry instanceof Binding b) {
                walkBinding(b);
            } else if (entry instanceof Opaque op) {
                visitOpaqueEntry(op);
            }
            if (shouldStop()) return;
        }
    }

    /**
     * Walk a single binding: visit its compound-key parts, then descend into
     * its target.  Override only to alter the binding-level traversal order.
     */
    protected void walkBinding(Binding b) {
        visitBindingRole(b.role());
        for (DatumNode part : b.key().parts()) {
            if (part instanceof CompoundKey.Qualifier q) {
                visitBindingQualifier(q);
            } else if (part instanceof Opaque op) {
                visitOpaqueQualifier(op);
            }
            if (shouldStop()) return;
        }
        if (shouldStop()) return;
        walkTarget(b.target());
    }

    /**
     * Walk a binding's target — recurse into nested bodies; leaf targets visit once.
     */
    protected void walkTarget(Object target) {
        visitTarget(target);
        if (shouldStop()) return;
        if (target instanceof Body body) {
            walk(body);
        } else if (target instanceof BindingTarget.FrameTarget ft) {
            walk(ft.body());
        }
    }

    // ==================================================================================
    // Per-position hooks — subclasses override what they care about.
    // ==================================================================================

    /** Called once per datum, before its entries. */
    protected void visitHead(HashID head) {}

    /** Called for each binding's role reference. */
    protected void visitBindingRole(HashID role) {}

    /** Called for each qualifier on a binding's compound key. */
    protected void visitBindingQualifier(CompoundKey.Qualifier qualifier) {}

    /**
     * Called for each Opaque stand-in encountered at a qualifier position
     * inside a binding's compound key.  Default no-op — most walkers ignore
     * opacity; subclasses that care (validators, signature-aware code)
     * override.
     */
    protected void visitOpaqueQualifier(Opaque opaque) {}

    /**
     * Called for each binding's target value before recursion.  The target may
     * be a literal, a {@link HashID} reference, a {@link Body}, an
     * {@link Opaque}, or a {@link BindingTarget} variant.  If the target is a
     * nested body, the walker will recurse into it after this call.
     */
    protected void visitTarget(Object target) {}

    /**
     * Called for each Opaque stand-in encountered at a binding-list position
     * — replacing a whole {@link Binding}.  Default no-op.
     */
    protected void visitOpaqueEntry(Opaque opaque) {}

    /**
     * Returns true to short-circuit further traversal.  Subclasses that scan
     * for a single condition (e.g., "is this a query?") can flip an internal
     * flag and return true once it's set; the walker stops without visiting
     * remaining bindings.  Default: never short-circuits.
     */
    protected boolean shouldStop() {
        return false;
    }
}
