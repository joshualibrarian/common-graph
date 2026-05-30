package dev.everydaythings.graph.text;

import dev.everydaythings.graph.datum.Binding;
import dev.everydaythings.graph.datum.Body;
import dev.everydaythings.graph.ref.CompoundKey.Qualifier;
import dev.everydaythings.graph.ref.ItemRef;
import java.math.BigDecimal;
import lombok.Value;
import lombok.With;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared state primitive for parsing (text → frame) and rendering (frame → text).
 *
 * <p>Carries the text, the frame structure with confidence weights and span attribution
 * on each part, and the list of participating Languages. The same shape serves as:
 * <ul>
 *   <li>the orchestrator's running draft during a parse round,</li>
 *   <li>each participant's per-round delta contribution,</li>
 *   <li>the renderer's accumulated output after a Language walk.</li>
 * </ul>
 *
 * <p>Null fields signify "no opinion" — empty contributions are sparse FrameMaps
 * with most parts left null. Confidence is {@link BigDecimal} in {@code [0, 1]} by
 * convention; max confidence (1.0) acts as a lock that cannot be outweighed.
 *
 * <p>See {@code docs/text.md} for the full design.
 */
@Value @With
public class FrameMap {

    /** Input (parse) or output (render). May be empty/null in early parse rounds. */
    String text;

    /** The predicate of the frame. Null = no opinion. */
    Part<ItemRef> predicate;

    /** The bindings of the frame. Empty list = no bindings opined on. */
    List<BindingMap> bindings;

    /** Languages that participated in this FrameMap. Often size 1; multiple for mixed-language text. */
    List<ItemRef> languages;

    public static FrameMap empty() {
        return new FrameMap(null, null, List.of(), List.of());
    }

    public boolean isEmpty() {
        return predicate == null && bindings.isEmpty() && languages.isEmpty();
    }

    /**
     * Lift this settled parse into a {@link Body} — the bridge from parse output
     * to a submittable frame.  Head is the predicate; each {@link BindingMap}
     * becomes a {@link Binding} (role + qualifiers + target), and a nested
     * {@link FrameMapTarget} target recurses into a sub-Body.  Confidence and
     * span attribution are dropped here: the consensus has settled, so only the
     * structural result carries forward.
     *
     * @throws IllegalStateException if no predicate has been settled
     */
    public Body toBody() {
        if (predicate == null || predicate.value() == null) {
            throw new IllegalStateException(
                    "FrameMap has no settled predicate; nothing to submit");
        }
        List<Binding> out = new ArrayList<>(bindings.size());
        for (BindingMap bm : bindings) {
            if (bm.role() == null || bm.role().value() == null) continue;
            ItemRef role = bm.role().value();
            Object target = bm.target() == null ? null : bm.target().value();
            if (target instanceof FrameMapTarget nested) {
                target = nested.frameMap().toBody();
            }
            List<Qualifier> quals = new ArrayList<>();
            for (Part<Qualifier> q : bm.qualifiers()) {
                if (q != null && q.value() != null) quals.add(q.value());
            }
            out.add(quals.isEmpty()
                    ? new Binding(role, target)
                    : Binding.qualified(role, quals, target));
        }
        return Body.of(ItemRef.of(predicate.value()), out);
    }

    /**
     * A part of the frame: a value with confidence weight and span attribution.
     *
     * <p>{@code spans} is a list because a single sememe can surface in multiple
     * non-contiguous text positions (German V2 separable verbs, infixes, clitics,
     * agreement morphemes). All spans share the same source {@code value}.
     */
    @Value @With
    public static class Part<T> {
        T value;
        BigDecimal confidence;
        List<TextSpan> spans;
    }

    /**
     * A single binding's role / qualifiers / target with their parts.
     *
     * <p>Each component carries its own confidence and spans, so a participant
     * can be confident about a role assignment but uncertain about the qualifier set,
     * for example.
     */
    @Value @With
    public static class BindingMap {
        Part<ItemRef> role;
        List<Part<Qualifier>> qualifiers;
        Part<Object> target;
    }
}
