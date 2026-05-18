package dev.everydaythings.graph.identity;

import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.id.DatumRef;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.language.ThematicRole;
import dev.everydaythings.graph.runtime.librarian.Librarian;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Mechanical walker over {@link IdentityVocabulary.Attestation Attestation}
 * frames in the local graph.  Given a starting IID (typically a peer's
 * pubkey-derived IID), walks back through AGENT pointers — each Attestation
 * about an identity points at the attester via AGENT, and that attester is
 * itself an identity that may be attested by someone else.
 *
 * <p>The walker is <i>purely mechanical</i>: it produces the chain of
 * Attestation frames reachable from the starting IID.  It does not make
 * trust decisions, does not consult anchors, does not enforce validity
 * windows.  Those are policy concerns layered above; the walker just
 * returns the graph data.
 *
 * <h2>What the walker queries</h2>
 *
 * <p>For each visited IID, the walker asks the librarian's library for
 * bodies with the reference-binding {@code THEME → iid} (via
 * {@link dev.everydaythings.graph.library.Library#bodyCidsForReferenceBinding
 * bodyCidsForReferenceBinding}), then filters to those whose head is
 * {@link IdentityVocabulary.Attestation}.  For each match it loads the
 * full frame, records it in the chain, and recurses on the body's AGENT.
 *
 * <h2>Termination</h2>
 *
 * <ul>
 *   <li><b>Cycle detection</b>: each IID is visited at most once.</li>
 *   <li><b>Depth limit</b>: {@link #DEFAULT_MAX_DEPTH} or caller-specified.
 *       Stops descent past the limit; already-visited frames stay in the
 *       chain.</li>
 *   <li><b>Dead end</b>: an IID with no Attestation about it just adds
 *       nothing to the chain.</li>
 * </ul>
 *
 * <h2>TODO: anchor support</h2>
 *
 * <p>In a future iteration, the walker should recognize <b>local anchors
 * </b> — frames in the chain whose records include a signature by the
 * local Librarian's Vault.  Such a record is a "I have already validated
 * this binding" note, and the walker should stop descending past it (the
 * chain has reached a trusted point from the local perspective).  See
 * task #187.
 *
 * <p>Until that lands, callers receive the raw walked chain and apply
 * their own anchor / policy decisions on top.
 */
public final class AttestationChain {

    /** Maximum walk depth.  Beyond this, the walker stops descending. */
    public static final int DEFAULT_MAX_DEPTH = 8;

    private AttestationChain() {}

    /**
     * Walk Attestation frames starting from the given identity.  Uses the
     * default depth limit.
     */
    public static List<Frame> walk(Librarian librarian, ItemRef startIid) {
        return walk(librarian, startIid, DEFAULT_MAX_DEPTH);
    }

    /**
     * Walk Attestation frames starting from the given identity with an
     * explicit depth limit.  Returns the frames in DFS visit order.
     */
    public static List<Frame> walk(Librarian librarian, ItemRef startIid, int maxDepth) {
        Objects.requireNonNull(librarian, "librarian");
        Objects.requireNonNull(startIid, "startIid");
        if (maxDepth < 0) {
            throw new IllegalArgumentException("maxDepth must be >= 0, got " + maxDepth);
        }
        List<Frame> chain = new ArrayList<>();
        Set<ItemRef> visited = new HashSet<>();
        walkInto(librarian, startIid, maxDepth, chain, visited);
        return chain;
    }

    private static void walkInto(Librarian librarian, ItemRef iid, int remainingDepth,
                                 List<Frame> chain, Set<ItemRef> visited) {
        if (!visited.add(iid)) return;       // cycle — already visited this IID
        if (remainingDepth <= 0) return;

        ItemRef themeRole = ItemRef.iid(ThematicRole.Theme.KEY);
        ItemRef attestationHead = ItemRef.iid(IdentityVocabulary.Attestation.KEY);

        List<DatumRef> candidateBodies =
                librarian.library().bodyCidsForReferenceBinding(themeRole, iid);

        // Frames whose body head is Attestation about this iid — pull each
        // into the chain, then recurse on its AGENT.
        Set<ItemRef> nextAttesters = new LinkedHashSet<>();
        for (DatumRef bodyId : candidateBodies) {
            librarian.library().fetchFrame(bodyId).ifPresent(frame -> {
                if (!attestationHead.equals(frame.body().headRef())) return;
                chain.add(frame);
                Attestations.attester(frame.body()).ifPresent(nextAttesters::add);
            });
        }
        for (ItemRef next : nextAttesters) {
            walkInto(librarian, next, remainingDepth - 1, chain, visited);
        }
    }
}
