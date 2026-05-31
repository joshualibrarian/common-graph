package dev.everydaythings.graph.cryptography;

import dev.everydaythings.graph.canonical.HashTree;
import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.datum.Frame;
import dev.everydaythings.graph.datum.Record;
import dev.everydaythings.graph.ref.DatumRef;
import dev.everydaythings.graph.ref.ItemRef;
import dev.everydaythings.graph.ThematicRole;
import dev.everydaythings.graph.runtime.librarian.LibrarianHandle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
    public static List<Frame> walk(LibrarianHandle librarian, ItemRef startIid) {
        return walk(librarian, startIid, DEFAULT_MAX_DEPTH);
    }

    /**
     * Walk Attestation frames starting from the given identity with an
     * explicit depth limit.  Returns the frames in DFS visit order.
     */
    public static List<Frame> walk(LibrarianHandle librarian, ItemRef startIid, int maxDepth) {
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

    private static void walkInto(LibrarianHandle librarian, ItemRef iid, int remainingDepth,
                                 List<Frame> chain, Set<ItemRef> visited) {
        if (!visited.add(iid)) return;       // cycle — already visited this IID
        if (remainingDepth <= 0) return;

        ItemRef themeRole = ItemRef.iid(ThematicRole.Theme.KEY);
        ItemRef attestationHead = ItemRef.iid(IdentityVocabulary.Attestation.KEY);

        List<DatumRef> candidateBodies =
                librarian.bodyCidsForReferenceBinding(themeRole, iid);

        // Frames whose body head is Attestation about this iid — pull each
        // into the chain, verify, then recurse on its AGENT.
        Set<ItemRef> nextAttesters = new LinkedHashSet<>();
        for (DatumRef bodyId : candidateBodies) {
            librarian.fetchFrame(bodyId).ifPresent(frame -> {
                if (!attestationHead.equals(frame.body().headRef())) return;
                chain.add(frame);
                if (verifyAttestation(librarian, frame)) {
                    emitVerifiedAnchor(librarian, frame);
                }
                Attestations.attester(frame.body()).ifPresent(nextAttesters::add);
            });
        }
        for (ItemRef next : nextAttesters) {
            walkInto(librarian, next, remainingDepth - 1, chain, visited);
        }
    }

    // ==================================================================================
    // Verification + anchor emission
    //
    // For each attestation visited, the walker attempts to verify it
    // cryptographically.  If verification succeeds AND the local librarian
    // holds a signing vault, a VERIFIED record (ACT = Verified) is emitted
    // signed by the librarian.  Future walks can use these records as
    // anchors (the walker stops descending past an attestation it has
    // already verified — task #187).
    //
    // Today only self-attestations (AGENT == THEME) are verified: the
    // subject's pubkey is right there in the INSTRUMENT binding.  Third-
    // party attestations need the attester's pubkey, which lives in another
    // attestation about the attester — verification of those falls out
    // naturally once chain-walking is wired to propagate verified keys.
    // For now, third-party attestations are walked but not VERIFIED-marked.
    // ==================================================================================

    /**
     * Verify a single attestation frame.  Returns true when at least one of
     * the frame's records carries a signature that checks against the
     * subject's INSTRUMENT pubkey AND the attestation is self-shaped
     * (AGENT == THEME).
     */
    private static boolean verifyAttestation(LibrarianHandle librarian, Frame frame) {
        Body body = frame.body();
        Optional<ItemRef> attester = Attestations.attester(body);
        Optional<ItemRef> subject = Attestations.subject(body);
        Optional<MultiKey> subjectPubkey = Attestations.subjectPubkey(body);

        if (attester.isEmpty() || subject.isEmpty() || subjectPubkey.isEmpty()) return false;
        // v1: only self-attestations.  Third-party attestation verification
        // needs the attester's pubkey from another attestation.
        if (!attester.get().equals(subject.get())) return false;

        byte[] payload = HashTree.signingPayload(body);
        MultiKey pubkey = subjectPubkey.get();
        for (Record record : frame.records()) {
            if (!record.isSigned()) continue;
            if (librarian.verify(pubkey, payload, record.varsig())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Emit a VERIFIED record (ACT = Verified) signed by the local librarian's
     * vault, attached to the attestation body that was verified.  No-op if
     * the librarian cannot sign.
     *
     * <p>Submission goes through the normal librarian path.  The body is
     * already stored (we just walked to it), so submitting Frame(body,
     * [newRecord]) effectively just appends the new record to the existing
     * frame's record set — content-addressing dedupes the body bytes.
     */
    private static void emitVerifiedAnchor(LibrarianHandle librarian, Frame frame) {
        if (!librarian.canSign()) return;
        Body body = frame.body();
        byte[] payload = HashTree.signingPayload(body);
        Record verifiedRecord = Record.of(
                DatumRef.of(body.datumId()),
                List.of(Binding.ref(
                        ItemRef.iid(RecordVocabulary.Act.KEY),
                        ItemRef.iid(RecordVocabulary.Verified.KEY))),
                librarian.sign(payload));
        librarian.submit(Frame.of(body, List.of(verifiedRecord)));
    }
}
