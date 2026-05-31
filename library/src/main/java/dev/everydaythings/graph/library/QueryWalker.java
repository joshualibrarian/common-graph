package dev.everydaythings.graph.library;

import dev.everydaythings.graph.canonical.DatumWalker;
import dev.everydaythings.graph.datum.Datum;
import dev.everydaythings.graph.ref.CompoundKey;
import dev.everydaythings.graph.ref.HashID;
import dev.everydaythings.graph.ref.TypeRef;

/**
 * Routing decision: does this datum represent a query?
 *
 * <p>Per the query model (docs/query.md), a frame is a query iff:
 * <ol>
 *   <li>Any reference in any position is a {@link TypeRef} ({@code ?<iid>}); OR</li>
 *   <li>Any sememe in any position has {@code Returns = !Bool} AND sits in a
 *       binding-target position with a missing operand (partial application).</li>
 * </ol>
 *
 * <p>This first cut handles rule (1) — TypeRef detection.  Rule (2) requires
 * looking up the operator's {@code Returns} declaration and checking the
 * operator's arity against the bindings present; deferred to a follow-up that
 * extends this walker with Library access.
 *
 * <p>Usage:
 * <pre>{@code
 *   boolean isQuery = QueryWalker.isQuery(frameBody);
 * }</pre>
 *
 * <p>Single-shot — instances are cheap; no caching, no shared state.
 */
public final class QueryWalker extends DatumWalker {

    private boolean queryDetected = false;

    /**
     * Convenience: walk a datum and report whether it's a query.
     */
    public static boolean isQuery(Datum d) {
        QueryWalker walker = new QueryWalker();
        walker.walk(d);
        return walker.queryDetected;
    }

    /** Whether the walk so far has detected a query-shape. */
    public boolean queryDetected() {
        return queryDetected;
    }

    // ==================================================================================
    // Detection hooks
    // ==================================================================================

    @Override
    protected void visitHead(HashID head) {
        if (head instanceof TypeRef) queryDetected = true;
    }

    @Override
    protected void visitBindingRole(HashID role) {
        if (role instanceof TypeRef) queryDetected = true;
    }

    @Override
    protected void visitBindingQualifier(CompoundKey.Qualifier qualifier) {
        // Qualifiers are currently ItemRef-only via CompoundKey.Sememe — TypeRef
        // qualifiers aren't supported yet.  Future-proofing: if qualifier shape
        // is widened to admit other ref variants, detect TypeRefs here.
    }

    @Override
    protected void visitTarget(Object target) {
        if (target instanceof TypeRef) queryDetected = true;
        // Future: also detect partially-applied Bool-returning operators
        // (target is a Body whose head is a sememe declaring Returns=!Bool,
        // with fewer operand bindings than its declared Arity).  Needs
        // Library access to look up the head's Returns; landing as a
        // follow-up.
    }

    @Override
    protected boolean shouldStop() {
        return queryDetected;
    }
}
