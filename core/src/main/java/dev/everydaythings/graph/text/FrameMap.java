package dev.everydaythings.graph.text;

import dev.everydaythings.graph.datum.BindingTarget;
import dev.everydaythings.graph.item.id.CompoundKey.FrameToken;
import dev.everydaythings.graph.item.id.ItemRef;
import dev.everydaythings.graph.value.Decimal;
import lombok.Value;
import lombok.With;

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
 * with most parts left null. Confidence is {@link Decimal} in {@code [0, 1]} by
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
     * A part of the frame: a value with confidence weight and span attribution.
     *
     * <p>{@code spans} is a list because a single sememe can surface in multiple
     * non-contiguous text positions (German V2 separable verbs, infixes, clitics,
     * agreement morphemes). All spans share the same source {@code value}.
     */
    @Value @With
    public static class Part<T> {
        T value;
        Decimal confidence;
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
        List<Part<FrameToken>> qualifiers;
        Part<BindingTarget> target;
    }
}
