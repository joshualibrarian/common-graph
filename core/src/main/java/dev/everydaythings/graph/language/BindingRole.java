package dev.everydaythings.graph.language;

import dev.everydaythings.graph.item.ItemOld;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.runtime.Eval;

import java.util.Set;

/**
 * Runtime binding role for parser terms.
 *
 * <p>Unlike part-of-speech (lexical class), BindingRole captures how a term
 * behaves in an evaluated frame.
 */
public enum BindingRole {
    /** Imperative action term (typically a verb sememe used for dispatch). */
    ACTION,
    /** Entity/value term (items, nouns, literals, pronouns). */
    REFERENCE,
    /** Modifier/linking term (adjectives, adverbs, prepositions, conjunctions). */
    QUALIFIER;

    /**
     * Classify a resolved token into a runtime binding role.
     *
     * @param item     The resolved item (nullable)
     * @param token    The resolved token
     * @param features Grammatical features from the token pipeline (POS is a feature)
     */
    public static BindingRole classify(ItemOld item, Eval.ResolvedToken token, Set<ItemID> features) {
        if (!features.isEmpty()) {
            if (features.contains(PartOfSpeech.VERB)) return ACTION;
            if (features.contains(PartOfSpeech.NOUN) || features.contains(PartOfSpeech.PRONOUN)) return REFERENCE;
            // ADJECTIVE, ADVERB, PREPOSITION, CONJUNCTION, INTERJECTION
            return QUALIFIER;
        }
        // No features available — non-sememe items and literals behave as references.
        return REFERENCE;
    }
}

