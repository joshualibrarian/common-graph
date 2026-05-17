package dev.everydaythings.graph.library;

import dev.everydaythings.graph.CoreVocabulary;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.id.CompoundKey;
import dev.everydaythings.graph.id.DatumRef;
import dev.everydaythings.graph.id.HashID;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.id.TypeRef;
import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.Manifest;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.librarian.Librarian;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Query evaluator — given a query frame, finds items that satisfy it and
 * emits RESULT frames for each match.
 *
 * <p>First-cut scope:
 * <ul>
 *   <li>Query head must be a {@link TypeRef}.  The matcher enumerates items
 *       whose archetype matches via the librarian's archetype index
 *       ({@code manifestsForType}).</li>
 *   <li>Each query binding constrains the match: the candidate's manifest
 *       must have a binding with the same role whose target satisfies the
 *       query target's shape.</li>
 *   <li>Binding-target matchers handled today:
 *     <ul>
 *       <li><b>Concrete reference</b> (ItemRef) — candidate's target must
 *           equal it exactly.</li>
 *       <li><b>TypeRef</b> — candidate's target must be a reference to an
 *           item whose archetype matches.</li>
 *       <li><b>Literal</b> (Long, String, Boolean, byte[]) — candidate's
 *           target must equal by value.</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>Deferred to a follow-on:
 * <ul>
 *   <li>The {@code @any} sememe as a universal matcher.</li>
 *   <li>Partially-applied Bool-returning operators as matchers (BETWEEN,
 *       GT, etc.).</li>
 *   <li>Variable binding across compound queries ({@code $author} linking
 *       multiple query frames).</li>
 *   <li>Querying for inline values inside other bodies (today's matcher
 *       only finds top-level items).</li>
 * </ul>
 */
public final class MatcherOrchestrator {

    private final Librarian librarian;

    public MatcherOrchestrator(Librarian librarian) {
        this.librarian = librarian;
    }

    /**
     * Evaluate a query body and return one RESULT frame per matching item.
     * Returns an empty list if the query head isn't a TypeRef (today's
     * scope) or if no items match.
     */
    public List<Frame> match(Body queryBody) {
        if (!(queryBody.head() instanceof TypeRef typeRef)) {
            return List.of();
        }
        ItemRef archetype = typeRef.iid();

        List<Frame> results = new ArrayList<>();
        for (DatumRef manifestCid : librarian.library().manifestCidsForType(archetype)) {
            Optional<Manifest> manifestOpt = librarian.fetchManifest(manifestCid);
            if (manifestOpt.isEmpty()) continue;
            Manifest manifest = manifestOpt.get();
            if (!matchesAllBindings(queryBody, manifest.body())) continue;
            results.add(buildResultFrame(manifest.itemId()));
        }
        return results;
    }

    /**
     * Whether {@code candidate}'s bindings satisfy every binding-constraint
     * in {@code query}.  Missing-binding on the candidate is a match
     * failure; extra bindings on the candidate are ignored (the query only
     * constrains what it mentions).
     */
    private boolean matchesAllBindings(Body query, Body candidate) {
        // Bindings only on both sides.  A query with Opaque entries can't
        // meaningfully constrain (we don't see the role/target inside the
        // opacity), so they're ignored.  A candidate with Opaque entries
        // can't satisfy a binding-shaped query constraint either — the
        // matcher conservatively reports no-match for that constraint.
        // Future: surface "match indeterminate due to opacity" when callers
        // can act on it.
        for (Binding queryBinding : query.bindings()) {
            if (!matchesBinding(queryBinding, candidate)) return false;
        }
        return true;
    }

    /**
     * Whether {@code queryBinding} is satisfied by some binding on
     * {@code candidate} with the same role and a matching target.
     */
    private boolean matchesBinding(Binding queryBinding, Body candidate) {
        CompoundKey queryKey = queryBinding.key();
        // Look up the candidate's binding with the same compound key.
        // For role-only matching (qualifiers absent on query), check
        // bare-role match; otherwise full key match.
        Optional<Binding> candidateBinding = candidate.binding(queryKey);
        if (candidateBinding.isEmpty()) return false;
        return matchesTarget(queryBinding.target(), candidateBinding.get().target());
    }

    /** Whether {@code candidateTarget} satisfies the constraint expressed by {@code queryTarget}. */
    private boolean matchesTarget(Object queryTarget, Object candidateTarget) {
        if (queryTarget == null) {
            // Null query target = "anything is fine here" (membership by role alone).
            return true;
        }
        if (queryTarget instanceof TypeRef typeRef) {
            // Candidate target must be a reference to an item whose archetype matches.
            ItemRef wantedArchetype = typeRef.iid();
            ItemRef candidateIid = asItemRef(candidateTarget);
            if (candidateIid == null) return false;
            return isInstanceOf(candidateIid, wantedArchetype);
        }
        if (queryTarget instanceof ItemRef queryIid) {
            // Exact-item match.
            ItemRef candidateIid = asItemRef(candidateTarget);
            return candidateIid != null && queryIid.equals(candidateIid);
        }
        if (queryTarget instanceof HashID queryRef && candidateTarget instanceof HashID candidateRef) {
            return queryRef.equals(candidateRef);
        }
        // Fall through: byte-equal for literals.
        return queryTarget.equals(candidateTarget);
    }

    /** Coerce a binding target to an ItemRef when possible, else null. */
    private static ItemRef asItemRef(Object target) {
        if (target instanceof ItemRef ir) return ir;
        return null;
    }

    /**
     * Whether {@code candidateIid}'s archetype chain includes
     * {@code wantedArchetype}.  Today's first-cut: direct-archetype match
     * only (no walking up the head chain).  Archetype-hierarchy walking
     * lands when the {@code Archetype|IID} index does.
     */
    private boolean isInstanceOf(ItemRef candidateIid, ItemRef wantedArchetype) {
        Optional<Item> candidate = librarian.fetchItem(candidateIid);
        if (candidate.isEmpty()) return false;
        Manifest manifest = candidate.get().current();
        if (manifest == null) return false;
        HashID head = manifest.body().head();
        if (!(head instanceof ItemRef headIid)) return false;
        return wantedArchetype.equals(headIid);
    }

    /** Wrap a matched IID as a RESULT frame: {@code {@result, [@theme → @<iid>]}}. */
    private static Frame buildResultFrame(ItemRef matchedIid) {
        Body body = Body.of(
                ItemRef.iid(CoreVocabulary.Result.KEY),
                List.of(Binding.ref(ItemRef.iid(ThematicRole.Theme.KEY), matchedIid)));
        return Frame.of(body, List.of());
    }
}
