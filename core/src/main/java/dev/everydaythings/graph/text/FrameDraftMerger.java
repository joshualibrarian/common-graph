package dev.everydaythings.graph.text;

import dev.everydaythings.graph.item.id.ItemRef;
import dev.everydaythings.graph.text.FrameMap.BindingMap;
import dev.everydaythings.graph.text.FrameMap.Part;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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

        Part<ItemRef> mergedPredicate = pickHighestWeighted(
                sources.stream()
                        .map(FrameMap::predicate)
                        .filter(p -> p != null && p.value() != null)
                        .toList()
        );

        List<BindingMap> mergedBindings = new ArrayList<>();
        for (FrameMap source : sources) {
            mergedBindings.addAll(source.bindings());
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
            if (candidate.confidence().toDouble() > winner.confidence().toDouble()) {
                winner = candidate;
            }
        }
        return winner;
    }
}
