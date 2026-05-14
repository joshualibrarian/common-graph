package dev.everydaythings.graph.text;

import dev.everydaythings.graph.datum.BindingTarget;
import dev.everydaythings.graph.id.CompoundKey.Qualifier;
import dev.everydaythings.graph.id.ItemRef;
import dev.everydaythings.graph.text.FrameMap.BindingMap;
import dev.everydaythings.graph.text.FrameMap.Part;
import java.math.BigDecimal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Default merge implementation for the parse consensus circle.
 *
 * <p>Combines a prior draft with this round's deltas into a new draft via per-part
 * weighted reconciliation: for each frame slot, the highest-weighted proposal wins.
 * Locks (confidence 1.0) cannot be outweighed and pass through.
 *
 * <p>Items override {@code Item.merge} to substitute custom logic. Most don't.
 *
 * <p><b>v1 — basic implementation.</b> Handles the simple cases. Refinements pending:
 * <ul>
 *   <li>Same-value consolidation: when multiple sources propose the same value, their
 *       confidences should combine (sum, capped at 1.0). Currently the highest single
 *       weight wins regardless of how many sources agree.</li>
 *   <li>Binding deduplication by (role + qualifiers) identity. Currently all bindings
 *       from all sources accumulate; duplicates need merging.</li>
 *   <li>Predicate-loser binding discard. When a delta proposed both a predicate and
 *       bindings but its predicate didn't win, those bindings should drop. Currently
 *       all bindings survive regardless of predicate ownership.</li>
 *   <li>Binding-only-contribution latching. Deltas with no predicate opinion should
 *       have their bindings applied to the winning predicate's frame. Currently
 *       bindings are accumulated without this distinction.</li>
 *   <li>Tie-breaking by participant priority (inner-first). Currently ties are
 *       broken by iteration order (prior draft, then deltas as supplied).</li>
 * </ul>
 *
 * <p>Tests will drive these refinements as the parse pipeline matures.
 */
public final class FrameDraftMerger {

    private FrameDraftMerger() {}

    /**
     * Merge a prior draft with this round's deltas via weighted reconciliation.
     *
     * @param priorDraft the orchestrator's running consensus from prior rounds; may be null in round 1
     * @param deltas     this round's participant contributions
     * @return the new draft FrameMap
     */
    public static FrameMap weighted(FrameMap priorDraft, List<FrameMap> deltas) {
        List<FrameMap> sources = new ArrayList<>(deltas.size() + 1);
        if (priorDraft != null) sources.add(priorDraft);
        sources.addAll(deltas);

        // Pick the outer predicate via claim-region containment. A FrameMap whose
        // claim region (anchor span + binding-target spans) is contained by another
        // FrameMap's claim region is "inner" and yields outer-position to the wider
        // claim. Among non-contained candidates, highest predicate-confidence wins.
        // This is what lets a paren-wrapped Multiply (with a wide claim across the
        // parens) outrank an inner Add even though Add has higher predicate-bid
        // confidence by precedence alone.
        Part<ItemRef> mergedPredicate = pickOutermostPredicate(sources);

        // Discard bindings from sources whose predicate didn't win. A delta that
        // proposed both a predicate and bindings is treated as a unit — if its
        // predicate lost the bid, its bindings shouldn't somehow survive in another
        // operator's frame. Sources that didn't claim a predicate (e.g., the prior
        // draft, or a binding-only contribution) keep their bindings unconditionally.
        ItemRef winnerIid = (mergedPredicate == null || mergedPredicate.value() == null)
                ? null : mergedPredicate.value().iid();

        // Group bindings by their compound key (role + qualifiers) and keep only the
        // highest-confidence proposal per group. Without this dedupe, the same delta
        // submitted across multiple consensus rounds would accumulate into the draft
        // (one fresh copy each round), preventing fixpoint detection.
        Map<String, BindingMap> mergedBindingByKey = new LinkedHashMap<>();
        for (FrameMap source : sources) {
            // Skip bindings from a source whose predicate lost — see above.
            if (source.predicate() != null && source.predicate().value() != null) {
                ItemRef sourceIid = source.predicate().value().iid();
                if (winnerIid != null && !Objects.equals(sourceIid, winnerIid)) {
                    continue;
                }
            }
            for (BindingMap b : source.bindings()) {
                String key = bindingKeyString(b);
                BindingMap existing = mergedBindingByKey.get(key);
                if (existing == null || bindingConfidence(b) > bindingConfidence(existing)) {
                    mergedBindingByKey.put(key, b);
                }
            }
        }
        List<BindingMap> mergedBindings = new ArrayList<>(mergedBindingByKey.values());

        // Sub-frame nesting pass. For each binding in the winning frame, see if any
        // OTHER predicate-bidder's claim region (its anchor + binding-target spans)
        // covers the binding's target span. If yes, that loser becomes a nested
        // sub-frame as the binding's target — its operator-level structure is kept
        // even though it lost the outer-predicate bid.
        //
        // This is what produces {@code Add{ THEME=5, GOAL=Multiply{3,2} }} for the
        // input {@code 5 + 3 * 2}: Add wins outer position (lower precedence), but
        // Multiply's claim covers Add's GOAL token, so Multiply nests as that target.
        // Same mechanism handles arbitrarily deep nesting — sub-frames are themselves
        // FrameMaps which can be re-merged recursively (future) to handle inputs like
        // {@code 5 + 3 * 2 + 1}.
        if (winnerIid != null && mergedPredicate != null) {
            TextSpan winnerAnchor = mergedPredicate.spans().isEmpty()
                    ? null : mergedPredicate.spans().get(0);
            List<FrameMap> losers = new ArrayList<>();
            for (FrameMap source : sources) {
                if (source.predicate() == null || source.predicate().value() == null) continue;
                if (Objects.equals(source.predicate().value().iid(), winnerIid)) continue;
                // Rival-claim filter: if a loser anchors at the same token as the winner,
                // they're competing interpretations (e.g., Subtract vs. Negate both at "-"),
                // not a parent-child nesting. Skip — the loser is just discarded.
                if (winnerAnchor != null && !source.predicate().spans().isEmpty()
                        && winnerAnchor.overlaps(source.predicate().spans().get(0))) {
                    continue;
                }
                losers.add(source);
            }
            if (!losers.isEmpty()) {
                List<BindingMap> nested = new ArrayList<>(mergedBindings.size());
                for (BindingMap b : mergedBindings) {
                    nested.add(absorbSubFrame(b, losers));
                }
                mergedBindings = nested;
            }
        }

        Set<ItemRef> seenLanguages = new LinkedHashSet<>();
        for (FrameMap source : sources) {
            seenLanguages.addAll(source.languages());
        }
        List<ItemRef> mergedLanguages = List.copyOf(seenLanguages);

        String text = sources.stream()
                .map(FrameMap::text)
                .filter(t -> t != null)
                .findFirst()
                .orElse(null);

        return new FrameMap(text, mergedPredicate, mergedBindings, mergedLanguages);
    }

    private static <T> Part<T> pickHighestWeighted(List<Part<T>> parts) {
        if (parts.isEmpty()) return null;
        Part<T> winner = parts.get(0);
        for (int i = 1; i < parts.size(); i++) {
            Part<T> candidate = parts.get(i);
            if (candidate.confidence().doubleValue() > winner.confidence().doubleValue()) {
                winner = candidate;
            }
        }
        return winner;
    }

    /**
     * Pick the outer predicate via claim-region containment. A candidate's claim
     * region is the bounding interval of its anchor span and all binding-target
     * spans. A candidate whose claim region is contained by another candidate's
     * region is "inner" and yields. Among the non-contained "outermost"
     * candidates, the highest-confidence predicate wins.
     */
    private static Part<ItemRef> pickOutermostPredicate(List<FrameMap> sources) {
        record Candidate(FrameMap source, ClaimRegion region) {}
        List<Candidate> candidates = new ArrayList<>();
        for (FrameMap s : sources) {
            if (s.predicate() == null || s.predicate().value() == null) continue;
            ClaimRegion region = claimRegion(s);
            if (region == null) continue;
            candidates.add(new Candidate(s, region));
        }
        if (candidates.isEmpty()) return null;

        // Filter to "outermost" candidates: those whose claim region is not
        // strictly contained by any other candidate's region.
        List<Candidate> outermost = new ArrayList<>();
        for (Candidate c : candidates) {
            boolean containedByOther = false;
            for (Candidate other : candidates) {
                if (other == c) continue;
                if (regionContains(other.region, c.region) && !regionEquals(other.region, c.region)) {
                    containedByOther = true;
                    break;
                }
            }
            if (!containedByOther) outermost.add(c);
        }
        if (outermost.isEmpty()) outermost = candidates;

        // Among outermost candidates, pick the highest-confidence predicate.
        Candidate winner = outermost.get(0);
        for (int i = 1; i < outermost.size(); i++) {
            Candidate cand = outermost.get(i);
            if (cand.source.predicate().confidence().doubleValue()
                    > winner.source.predicate().confidence().doubleValue()) {
                winner = cand;
            }
        }
        return winner.source.predicate();
    }

    private record ClaimRegion(int start, int end) {}

    /** Compute claim region from a FrameMap's anchor and binding-target spans. */
    private static ClaimRegion claimRegion(FrameMap f) {
        int start = Integer.MAX_VALUE;
        int end = Integer.MIN_VALUE;
        if (f.predicate() != null) {
            for (TextSpan s : f.predicate().spans()) {
                if (s.start() < start) start = s.start();
                if (s.end() > end) end = s.end();
            }
        }
        for (BindingMap b : f.bindings()) {
            if (b.target() == null) continue;
            for (TextSpan s : b.target().spans()) {
                if (s.start() < start) start = s.start();
                if (s.end() > end) end = s.end();
            }
        }
        if (start == Integer.MAX_VALUE) return null;
        return new ClaimRegion(start, end);
    }

    private static boolean regionContains(ClaimRegion outer, ClaimRegion inner) {
        return outer.start() <= inner.start() && outer.end() >= inner.end();
    }

    private static boolean regionEquals(ClaimRegion a, ClaimRegion b) {
        return a.start() == b.start() && a.end() == b.end();
    }

    /**
     * String key for binding deduplication: role IID plus all qualifiers (each
     * a sememe IID or a literal). Bindings with the same compound key are
     * considered the same slot and reconciled by confidence.
     */
    private static String bindingKeyString(BindingMap b) {
        StringBuilder sb = new StringBuilder();
        if (b.role() != null && b.role().value() != null) {
            sb.append(b.role().value().iid());
        }
        for (Part<Qualifier> q : b.qualifiers()) {
            sb.append('|');
            if (q != null && q.value() != null) sb.append(q.value());
        }
        return sb.toString();
    }

    /** Use the target's confidence as the dominant signal for binding ranking. */
    private static double bindingConfidence(BindingMap b) {
        if (b.target() == null || b.target().confidence() == null) return 0.0;
        return b.target().confidence().doubleValue();
    }

    /**
     * Locked-confidence value for FrameMapTarget bindings. A nested sub-frame is
     * a stronger answer than a single-token literal target — once nesting is
     * resolved, subsequent rounds shouldn't overturn it. Just below 1.0 to avoid
     * the strict "lock" interpretation but high enough to win all comparisons.
     */
    private static final BigDecimal NESTED_BINDING_CONFIDENCE = new BigDecimal("0.999");

    /**
     * If any of the loser FrameMaps has a claim region that covers this binding's
     * target span, replace the target with a {@link FrameMapTarget} wrapping the
     * loser's full FrameMap. Otherwise return the binding unchanged.
     *
     * <p>If the binding's target is already a FrameMapTarget (from a prior round's
     * nesting), it stays — we don't overturn established nesting.
     */
    private static BindingMap absorbSubFrame(BindingMap binding, List<FrameMap> losers) {
        if (binding.target() == null || binding.target().value() == null) return binding;
        if (binding.target().value() instanceof FrameMapTarget) return binding;

        List<TextSpan> targetSpans = binding.target().spans();
        if (targetSpans == null || targetSpans.isEmpty()) return binding;
        TextSpan targetSpan = targetSpans.get(0);

        for (FrameMap loser : losers) {
            if (claimContainsSpan(loser, targetSpan)) {
                Object nestedTarget = new FrameMapTarget(loser);
                return new BindingMap(
                        binding.role(),
                        binding.qualifiers(),
                        new Part<>(nestedTarget, NESTED_BINDING_CONFIDENCE, targetSpans));
            }
        }
        return binding;
    }

    /**
     * Does {@code loser}'s claim region — the union of its predicate anchor span
     * and all its binding-target spans — cover {@code span}?
     */
    private static boolean claimContainsSpan(FrameMap loser, TextSpan span) {
        if (loser.predicate() != null) {
            for (TextSpan s : loser.predicate().spans()) {
                if (containsSpan(s, span)) return true;
            }
        }
        for (BindingMap b : loser.bindings()) {
            if (b.target() == null) continue;
            for (TextSpan s : b.target().spans()) {
                if (containsSpan(s, span)) return true;
            }
        }
        return false;
    }

    private static boolean containsSpan(TextSpan outer, TextSpan inner) {
        return outer.start() <= inner.start() && outer.end() >= inner.end();
    }
}
