package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.runtime.Eval.ResolvedToken;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The assembled result of semantic parsing — a verb with its role bindings.
 *
 * <p>A SemanticFrame captures the meaning of a user expression in an
 * order-agnostic way. The {@link FrameAssembler} scans resolved tokens
 * (in any order), finds the verb, pairs prepositions with their objects,
 * and matches bare nouns to argument slots. The result is this frame.
 *
 * <p>Eval then uses the frame for dispatch: the verb determines the action,
 * bindings determine the arguments, and unmatched tokens become positional
 * args for VerbInvoker (backwards-compatible with string-arg dispatch).
 *
 * <p>Bindings can hold Items (resolved nouns) or plain values (literals
 * bound via prepositions like "with game.pgn"). Use {@link #itemBinding}
 * when you specifically need an Item, or {@link #binding} for any value.
 *
 * @param verb             The verb sememe driving dispatch
 * @param bindings         Thematic role to bound value (Item or literal)
 * @param unmatchedArgs    Tokens that didn't match any slot (literals, overflow items)
 * @param unboundRequired  Required argument slots still needing values
 * @param unboundOptional  Optional argument slots still available
 */
public record SemanticFrame(
        VerbSememe verb,
        Map<ThematicRole, Object> bindings,
        List<ResolvedToken> unmatchedArgs,
        List<ArgumentSlot> unboundRequired,
        List<ArgumentSlot> unboundOptional
) {
    /**
     * Whether all required argument slots have been filled.
     */
    public boolean isComplete() {
        return unboundRequired.isEmpty();
    }

    /**
     * Get the value bound to a specific thematic role (Item or literal).
     */
    public Optional<Object> binding(ThematicRole role) {
        return Optional.ofNullable(bindings.get(role));
    }

    /**
     * Get the Item bound to a specific thematic role, if it is an Item.
     */
    public Optional<Item> itemBinding(ThematicRole role) {
        Object value = bindings.get(role);
        return value instanceof Item item ? Optional.of(item) : Optional.empty();
    }
}
