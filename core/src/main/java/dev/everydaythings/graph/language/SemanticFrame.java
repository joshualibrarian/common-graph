package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Eval.ResolvedToken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * Use {@code ThematicRole.Agent.IID}, {@code ThematicRole.Patient.IID}, etc.
 *
 * @param verb             The verb sememe driving dispatch (null for non-verb frames)
 * @param bindings         Role sememe IID → bound value (Item or literal)
 * @param modifiers        Modifier bindings: target IID or role → list of modifier sememes
 * @param unmatchedArgs    Tokens that didn't match any slot (literals, overflow items)
 * @param unboundRoles     Argument slot roles still needing values
 */
public record SemanticFrame(
        Sememe verb,
        Map<ItemID, Object> bindings,
        Map<String, List<Sememe>> modifiers,
        List<ResolvedToken> unmatchedArgs,
        List<ItemID> unboundRoles
) {
    private static final String QUERY_PREFIX = "cg.query:";

    /**
     * Whether all argument slots have been filled with concrete values.
     *
     * <p>A role bound to a query quantifier (e.g. {@link Sememe.Every}) is
     * NOT considered filled — it signals "find all values for this role."
     */
    public boolean isComplete() {
        if (!unboundRoles.isEmpty()) return false;
        // Check that no binding is a query quantifier
        for (Object value : bindings.values()) {
            if (isQueryQuantifier(value)) return false;
        }
        return true;
    }

    /**
     * Whether this frame represents a query — either because roles are
     * unbound, or because a binding is a query quantifier like "all" or "any".
     */
    public boolean isQuery() {
        return !isComplete();
    }

    /**
     * Return a view of this frame with query quantifiers removed from bindings
     * and their roles added to unboundRoles. Used by the query engine to know
     * which roles to extract results from.
     */
    public SemanticFrame forQuery() {
        Map<ItemID, Object> concreteBindings = new LinkedHashMap<>();
        List<ItemID> effectiveUnbound = new ArrayList<>(unboundRoles);

        for (var entry : bindings.entrySet()) {
            if (isQueryQuantifier(entry.getValue())) {
                effectiveUnbound.add(entry.getKey());
            } else {
                concreteBindings.put(entry.getKey(), entry.getValue());
            }
        }

        return new SemanticFrame(verb, concreteBindings, modifiers, unmatchedArgs, effectiveUnbound);
    }

    /**
     * Get the value bound to a specific role (Item or literal).
     */
    public java.util.Optional<Object> binding(ItemID role) {
        return java.util.Optional.ofNullable(bindings.get(role));
    }

    /**
     * Get the Item bound to a specific role, if it is an Item.
     */
    public java.util.Optional<Item> itemBinding(ItemID role) {
        Object value = bindings.get(role);
        return value instanceof Item item ? java.util.Optional.of(item) : java.util.Optional.empty();
    }

    private static boolean isQueryQuantifier(Object value) {
        if (value instanceof Sememe s && s.canonicalKey() != null) {
            return s.canonicalKey().startsWith(QUERY_PREFIX);
        }
        return false;
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
