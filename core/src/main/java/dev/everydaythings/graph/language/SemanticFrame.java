package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Eval.ResolvedToken;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The assembled result of semantic parsing — a predicate with its role bindings.
 *
 * <p>A SemanticFrame captures the meaning of a user expression in an
 * order-agnostic way. The {@link FrameAssembler} scans resolved tokens
 * (in any order), finds the predicate, pairs prepositions with their objects,
 * and matches bare nouns to argument slots. The result is this frame.
 *
 * <p>Frames are the universal structure for three modes:
 * <ul>
 *   <li><b>Dispatch</b> — fully filled frame with a code-backed predicate → execute</li>
 *   <li><b>Assert</b> — fully filled frame → store as signed relation</li>
 *   <li><b>Query</b> — partially filled frame → search for completions</li>
 * </ul>
 *
 * <p>Role keys are {@link ItemID}s referencing {@link ThematicRole} sememes, not enum constants.
 * Use {@code ThematicRole.AGENT.iid()}, {@code ThematicRole.PATIENT.iid()}, etc.
 *
 * @param verb             The verb sememe driving dispatch (null for non-verb frames)
 * @param bindings         Role sememe IID → bound value (Item or literal)
 * @param modifiers        Modifier bindings: target IID or role → list of modifier sememes
 * @param unmatchedArgs    Tokens that didn't match any slot (literals, overflow items)
 * @param unboundRoles     Argument slot roles still needing values
 */
public record SemanticFrame(
        VerbSememe verb,
        Map<ItemID, Object> bindings,
        Map<String, List<Sememe>> modifiers,
        List<ResolvedToken> unmatchedArgs,
        List<ItemID> unboundRoles
) {
    /**
     * Whether all argument slots have been filled.
     */
    public boolean isComplete() {
        return unboundRoles.isEmpty();
    }

    /**
     * Get the value bound to a specific role (Item or literal).
     */
    public Optional<Object> binding(ItemID role) {
        return Optional.ofNullable(bindings.get(role));
    }

    /**
     * Get the Item bound to a specific role, if it is an Item.
     */
    public Optional<Item> itemBinding(ItemID role) {
        Object value = bindings.get(role);
        return value instanceof Item item ? Optional.of(item) : Optional.empty();
    }

    /**
     * Get adverbs modifying the verb.
     */
    public List<Sememe> verbModifiers() {
        List<Sememe> mods = modifiers.get("verb");
        return mods != null ? mods : Collections.emptyList();
    }

    /**
     * Get adjective modifiers for a specific role.
     */
    public List<Sememe> modifiersFor(ItemID role) {
        List<Sememe> mods = modifiers.get("role:" + role.encodeText());
        return mods != null ? mods : Collections.emptyList();
    }
}
