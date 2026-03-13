package dev.everydaythings.graph.item.component.expression;

import dev.everydaythings.graph.Canonical;
import dev.everydaythings.graph.Canonical.Canon;
import dev.everydaythings.graph.item.id.ContentID;
import dev.everydaythings.graph.item.component.BindingTarget;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.language.PronounSememe;
import dev.everydaythings.graph.library.Library;
import dev.everydaythings.graph.library.LibraryIndex;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A frame query — an incomplete frame with holes.
 *
 * <p>Queries are frames where some positions contain the sentinel
 * {@link PronounSememe.What#SEED} (a variable to solve for). The query evaluates
 * against the frame index to find matching frames.
 *
 * <p>Frame model positions:
 * <ul>
 *   <li>{@code predicate} — which frame type (may be WHAT to query for predicates)</li>
 *   <li>{@code theme} — the item this frame is about (may be WHAT)</li>
 *   <li>{@code bindings} — role→target map where targets may be WHAT</li>
 * </ul>
 *
 * <p>Examples:
 * <pre>{@code
 * // Who authored The Hobbit?
 * FrameQuery.of(AUTHOR, THE_HOBBIT);  // predicate=AUTHOR, theme=THE_HOBBIT, target=?
 *
 * // What did Tolkien author?
 * FrameQuery.builder().predicate(AUTHOR).binding(TARGET, TOLKIEN).build();
 *
 * // All predicates about The Hobbit
 * FrameQuery.about(THE_HOBBIT);  // predicate=?, theme=THE_HOBBIT
 * }</pre>
 */
public record FrameQuery(
        @Canon(order = 0) ItemID predicate,
        @Canon(order = 1) ItemID theme,
        @Canon(order = 2) Map<ItemID, BindingTarget> bindings
) implements Expression, Canonical {

    private static final Logger log = LogManager.getLogger(FrameQuery.class);

    private static final ItemID ANY = PronounSememe.Any.SEED.iid();
    private static final ItemID WHAT = PronounSememe.What.SEED.iid();
    private static final ItemID TARGET_ROLE = ItemID.fromString("cg.role:target");

    // ==================================================================================
    // Factories
    // ==================================================================================

    /**
     * Query: predicate + theme given, target is the variable.
     * "Who/what is the [predicate] of [theme]?"
     */
    public static FrameQuery of(ItemID predicate, ItemID theme) {
        return new FrameQuery(predicate, theme, Map.of());
    }

    /**
     * Query: all frames about a theme.
     * "What do we know about [theme]?"
     */
    public static FrameQuery about(ItemID theme) {
        return new FrameQuery(WHAT, theme, Map.of());
    }

    /**
     * Query: all frames with a given predicate.
     * "Who/what has [predicate]?"
     */
    public static FrameQuery withPredicate(ItemID predicate) {
        return new FrameQuery(predicate, WHAT, Map.of());
    }

    /**
     * Convert from a PatternExpression (S → P → O) to a FrameQuery.
     *
     * <p>The mapping: subject → theme, predicate → predicate, object → target binding.
     */
    @SuppressWarnings("deprecation")
    public static FrameQuery fromPattern(PatternExpression pattern) {
        ItemID pred = isHole(pattern.predicate()) ? pattern.predicate() : pattern.predicate();
        ItemID theme = isHole(pattern.subject()) ? pattern.subject() : pattern.subject();

        Map<ItemID, BindingTarget> bindings = new LinkedHashMap<>();
        if (!isHole(pattern.object())) {
            bindings.put(TARGET_ROLE, BindingTarget.iid(pattern.object()));
        }

        return new FrameQuery(pred, theme, bindings);
    }

    public static Builder builder() {
        return new Builder();
    }

    // ==================================================================================
    // Expression Implementation
    // ==================================================================================

    @Override
    public Object evaluate(EvaluationContext context) {
        log.debug("FrameQuery.evaluate: {}", toExpressionString());
        var libraryOpt = context.library();
        if (libraryOpt.isEmpty()) {
            return List.of();
        }

        Library library = libraryOpt.get();
        List<ItemID> results = evaluateQuery(library).toList();
        log.debug("FrameQuery: {} results", results.size());
        return results;
    }

    /**
     * Evaluate and return as a stream.
     */
    public Stream<ItemID> evaluateAsStream(EvaluationContext context) {
        var libraryOpt = context.library();
        if (libraryOpt.isEmpty()) {
            return Stream.empty();
        }
        return evaluateQuery(libraryOpt.get());
    }

    private Stream<ItemID> evaluateQuery(Library library) {
        boolean predicateKnown = isKnown(predicate);
        boolean themeKnown = isKnown(theme);

        // Use frame index for query
        Stream<LibraryIndex.FrameRef> frameRefs;

        if (themeKnown && predicateKnown) {
            frameRefs = library.framesByItemPredicate(theme, predicate);
        } else if (themeKnown) {
            frameRefs = library.framesByItem(theme);
        } else if (predicateKnown) {
            frameRefs = library.framesByPredicate(predicate);
        } else {
            // No constraints — can't query efficiently
            return Stream.empty();
        }

        // Return body hashes as ItemIDs (stable identity).
        // Full implementation would hydrate frame bodies and extract
        // the requested holes (theme, bindings, etc.)
        return frameRefs
                .map(ref -> ref.bodyHash())
                .map(cid -> new ItemID(cid.encodeBinary()))
                .distinct();
    }

    private static boolean isKnown(ItemID id) {
        return id != null && !WHAT.equals(id) && !ANY.equals(id);
    }

    private static boolean isHole(ItemID id) {
        return id == null || WHAT.equals(id) || ANY.equals(id);
    }

    @Override
    public ItemID resultType() {
        return null;
    }

    @Override
    public String toExpressionString() {
        StringBuilder sb = new StringBuilder();
        sb.append(formatId(predicate)).append(" { ");
        sb.append("theme: ").append(formatId(theme));
        for (var entry : bindings.entrySet()) {
            sb.append(", ").append(entry.getKey().encodeText()).append(": ");
            if (entry.getValue() instanceof BindingTarget.IidTarget iid) {
                sb.append(iid.iid().encodeText());
            } else {
                sb.append(entry.getValue());
            }
        }
        sb.append(" }");
        return sb.toString();
    }

    private String formatId(ItemID id) {
        if (id == null) return "?";
        if (WHAT.equals(id)) return "?";
        if (ANY.equals(id)) return "*";
        return id.encodeText();
    }

    @Override
    public boolean hasDependencies() {
        return true;
    }

    // ==================================================================================
    // Builder
    // ==================================================================================

    public static class Builder {
        private ItemID predicate = WHAT;
        private ItemID theme = WHAT;
        private final Map<ItemID, BindingTarget> bindings = new LinkedHashMap<>();

        public Builder predicate(ItemID predicate) {
            this.predicate = predicate;
            return this;
        }

        public Builder theme(ItemID theme) {
            this.theme = theme;
            return this;
        }

        public Builder binding(ItemID role, BindingTarget target) {
            this.bindings.put(role, target);
            return this;
        }

        public Builder binding(ItemID role, ItemID targetIid) {
            this.bindings.put(role, BindingTarget.iid(targetIid));
            return this;
        }

        public FrameQuery build() {
            return new FrameQuery(predicate, theme, Map.copyOf(bindings));
        }
    }
}
